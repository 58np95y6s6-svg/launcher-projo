// Contrôleur : identité, TV appairées, connexion à la TV active, reconnexion automatique.
// Ne touche pas au DOM : l'interface (app.js) écoute ses événements.
//
// Événements :
//   etat {etat, essais}      "aucune" | "connexion" | "connecte" | "injoignable" | "erreur"
//                            (+ detail.raison pour "erreur" : "auth" | "version" | "id_pris")
//   tvs {tvs, active}        liste des TV modifiée
//   appairage {etape, …}     "connexion" | "confirmation" | "reussi" (tv) | "echec" (raison, refus)
//   focus_text {type, s, action} | focus_lost
//   version {verdict}
//   revoquee {tv}

import { Signalisation, ErreurSignalisation } from "./signalisation.js";
import { Session } from "./session.js";
import * as stockage from "./stockage.js";
import { journal } from "./journal.js";

export class Controleur extends EventTarget {
  constructor(config, { RTC, WebSocketImpl } = {}) {
    super();
    this.config = config;
    this.RTC = RTC || globalThis.RTCPeerConnection;
    this.WebSocketImpl = WebSocketImpl || globalThis.WebSocket;
    this.identite = null;
    this.tvs = [];
    this.active = null;     // id de la TV pilotée
    this.signal = null;
    this.session = null;    // session de pilotage
    this.sessionAppairage = null;
    this.etat = "aucune";
    this.essais = 0;
    this._relance = null;
    this.arrete = false;    // vrai après une erreur définitive (version, authentification)
  }

  _emettre(type, detail = {}) { this.dispatchEvent(new CustomEvent(type, { detail })); }

  _etat(etat, extra = {}) {
    this.etat = etat;
    this._emettre("etat", { etat, essais: this.essais, ...extra });
  }

  async init() {
    journal("demarrage");
    this.identite = await stockage.obtenirIdentite();
    this.tvs = await stockage.listerTVs();
    const active = await stockage.lireReglage("tvActive");
    this.active = this.tvs.some((t) => t.id === active) ? active : this.tvs[0]?.id ?? null;
    this._emettreTVs();
    return this;
  }

  get tvActive() { return this.tvs.find((t) => t.id === this.active) || null; }

  _emettreTVs() { this._emettre("tvs", { tvs: this.tvs, active: this.active }); }

  // ---------- Signalisation ----------

  async _signalisation() {
    if (this.signal?.ouverte) return this.signal;
    if (this._ouvertureSignal) return this._ouvertureSignal;
    const s = new Signalisation({
      peerjs: this.config.peerjs, id: this.identite.id, jeton: this.identite.jeton, WebSocketImpl: this.WebSocketImpl,
    });
    this._ouvertureSignal = s.ouvrir().then(() => {
      this.signal = s;
      s.addEventListener("fermee", () => {
        if (this.signal === s) this.signal = null;
        // Une session établie survit (le DataChannel est direct) ; sinon on relance.
        if (this.active && !this.session?.prete) this._planifier();
      });
      return s;
    }).finally(() => { this._ouvertureSignal = null; });
    return this._ouvertureSignal;
  }

  // ---------- Pilotage de la TV active ----------

  /** Connecte (ou reconnecte) la TV active. */
  async connecter({ reinitialiser = false } = {}) {
    clearTimeout(this._relance);
    if (reinitialiser) { this.essais = 0; this.arrete = false; }
    const tv = this.tvActive;
    if (!tv || this.arrete || this.sessionAppairage) return;
    if (this.session && this.session.etat !== "fermee") {
      if (this.session.cible.id === tv.id) return; // déjà en cours
      this.session.fermer();
    }
    this._etat(this.essais > 0 ? "injoignable" : "connexion");
    let signal;
    try {
      signal = await this._signalisation();
    } catch (e) {
      if (e instanceof ErreurSignalisation && e.code === "id_pris") return this._planifier("id_pris");
      return this._planifier();
    }
    if (this.tvActive?.id !== tv.id || this.sessionAppairage) return;
    const session = (this.session = new Session({
      signal, identite: this.identite, cible: { id: tv.id, cle: tv.cle }, config: this.config, RTC: this.RTC,
    }));
    this._brancher(session);
    session.addEventListener("tv", (e) => this._majTV(tv.id, { versionApp: e.detail.versionApp, nomTV: e.detail.nomTV }));
    session.addEventListener("etat", (e) => {
      if (this.session !== session) return;
      if (e.detail.etat === "prete") { this.essais = 0; this._etat("connecte"); }
    });
    session.addEventListener("fin", (e) => this._finSession(session, tv, e.detail));
    session.demarrer();
  }

  _brancher(session) {
    session.addEventListener("focus_text", (e) => this._emettre("focus_text", e.detail));
    session.addEventListener("focus_lost", () => this._emettre("focus_lost"));
    session.addEventListener("version", (e) => this._emettre("version", e.detail));
  }

  async _finSession(session, tv, { raison }) {
    if (this.session !== session) return;
    this.session = null;
    this._emettre("focus_lost");
    switch (raison) {
      case "fermee": return;
      case "revoked":
        await this.oublier(tv.id, { silencieux: true });
        this._emettre("revoquee", { tv });
        return;
      case "version":
      case "auth":
        this.arrete = true;
        return this._etat("erreur", { raison });
      default:
        if (raison === "injoignable") journal("injoignable");
        return this._planifier();
    }
  }

  _planifier(raison) {
    clearTimeout(this._relance);
    if (!this.tvActive || this.arrete) return;
    const delais = this.config.delais.reconnexion;
    const d = delais[Math.min(this.essais, delais.length - 1)];
    this.essais++;
    this._etat("injoignable", { prochainEssaiMs: d, raison });
    this._relance = setTimeout(() => this.connecter(), d);
  }

  /** Au retour au premier plan ou quand le réseau revient. */
  reveil() {
    if (this.sessionAppairage) return;
    if (this.session?.prete) this.session.verifierVivante();
    else if (!this.session && this.tvActive && !this.arrete) { this.essais = Math.min(this.essais, 1); this.connecter(); }
  }

  async choisir(id) {
    if (!this.tvs.some((t) => t.id === id)) return;
    this.active = id;
    await stockage.ecrireReglage("tvActive", id);
    this._emettreTVs();
    if (this.session) { const s = this.session; this.session = null; s.fermer(); }
    this.connecter({ reinitialiser: true });
  }

  async oublier(id, { silencieux = false } = {}) {
    await stockage.oublierTV(id);
    if (!silencieux) journal("tv_oubliee");
    this.tvs = this.tvs.filter((t) => t.id !== id);
    if (this.active === id) {
      if (this.session) { const s = this.session; this.session = null; s.fermer(); }
      clearTimeout(this._relance);
      this.active = this.tvs[0]?.id ?? null;
      await stockage.ecrireReglage("tvActive", this.active);
      this.essais = 0;
      this.arrete = false;
      if (this.active) this.connecter();
      else this._etat("aucune");
    }
    this._emettreTVs();
  }

  async _majTV(id, { versionApp, nomTV }) {
    const tv = this.tvs.find((t) => t.id === id);
    if (!tv) return;
    if (typeof versionApp === "string") tv.versionApp = versionApp;
    if (nomTV) tv.nom = nomTV;
    await stockage.enregistrerTV(tv);
    this._emettreTVs();
  }

  // ---------- Appairage ----------

  /** @param infos résultat de lireFragment() ou lireCode() ; @param nom nom du téléphone */
  async appairer(infos, nom) {
    this.annulerAppairage();
    clearTimeout(this._relance);
    if (this.session) { const s = this.session; this.session = null; s.fermer(); }
    this.identite.nom = nom;
    stockage.enregistrerNom(nom);
    this._emettre("appairage", { etape: "connexion" });
    let signal;
    try {
      signal = await this._signalisation();
    } catch (e) {
      return this._emettre("appairage", { etape: "echec", raison: e?.code === "id_pris" ? "id_pris" : "serveur" });
    }
    const session = (this.sessionAppairage = new Session({
      signal,
      identite: this.identite,
      cible: { id: infos.idTV, empreinte: infos.empreinteTV },
      appairage: { secret: infos.secret, nom, empreinteTV: infos.empreinteTV },
      config: this.config,
      RTC: this.RTC,
    }));
    this._brancher(session);
    session.addEventListener("etat", (e) => {
      if (e.detail.etat === "appairage") this._emettre("appairage", { etape: "confirmation" });
    });
    session.addEventListener("paired", async (e) => {
      const { idTV, nomTV, cle, empreinte } = e.detail;
      const tv = { id: idTV, nom: nomTV, cle, empreinte, ajouteeLe: Date.now(), versionApp: session.versionAppTV };
      await stockage.enregistrerTV(tv);
      this.tvs = [...this.tvs.filter((t) => t.id !== idTV), tv];
      this.active = idTV;
      await stockage.ecrireReglage("tvActive", idTV);
      // La session d'appairage devient la session de pilotage.
      this.sessionAppairage = null;
      this.session = session;
      this.essais = 0;
      this.arrete = false;
      session.addEventListener("fin", (f) => this._finSession(session, tv, f.detail));
      this._emettreTVs();
      this._etat("connecte");
      this._emettre("appairage", { etape: "reussi", tv });
    });
    session.addEventListener("tv", (e) => { session.versionAppTV = e.detail.versionApp; });
    session.addEventListener("fin", (e) => {
      if (this.sessionAppairage !== session) return;
      this.sessionAppairage = null;
      this._emettre("appairage", { etape: "echec", raison: e.detail.raison, refus: e.detail.refus });
      if (this.tvActive) this.connecter({ reinitialiser: true });
    });
    session.demarrer();
  }

  annulerAppairage() {
    const s = this.sessionAppairage;
    if (!s) return;
    this.sessionAppairage = null;
    s.fermer();
    if (this.tvActive) this.connecter({ reinitialiser: true });
  }

  // ---------- Commandes ----------

  touche(k) { return this.session?.touche(k) ?? false; }
  texte(s, curseur) { return this.session?.texte(s, curseur) ?? false; }
  valider() { return this.session?.valider() ?? false; }
}
