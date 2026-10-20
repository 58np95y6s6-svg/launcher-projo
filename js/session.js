// Une session avec UNE TV : WebRTC (offre du téléphone), DataChannel « telecommande »,
// authentification mutuelle (hello signés), appairage éventuel, puis commandes.
//
// Événements émis (CustomEvent, détail entre accolades) :
//   etat {etat}            "connexion" → "authentification" → ("appairage") → "prete" ; "fermee"
//   tv {nomTV, versionApp, cle}   hello de la TV vérifié
//   paired {nomTV, idTV, cle, empreinte}
//   focus_text {type, s, action} | focus_lost
//   version {verdict}      "maj_tv" | "recharger"
//   fin {raison}           "injoignable" | "auth" | "perdue" | "version" | "revoked"
//                          | "refus" (+ detail.refus) | "fermee"

import { message, encoder, decoder, verifierVersion, verdictErreurVersion, hexAleatoire } from "./codec.js";
import {
  empreinteDtls, empreinteCle, importerSpki, nouveauNonce, signer, verifier,
  texteHelloTel, texteHelloTV, egaliteConstante,
} from "./crypto.js";
import { estIdTV } from "./appairage.js";
import { journal } from "./journal.js";

export const ETIQUETTE_CANAL = "telecommande";

export class Session extends EventTarget {
  /**
   * @param {object} o
   * @param {import("./signalisation.js").Signalisation} o.signal
   * @param {{id:string, clePrivee:CryptoKey, cleSpki:string}} o.identite  identité du téléphone
   * @param {{id:string, cle?:string, empreinte?:string}} o.cible  TV à joindre (id PeerJS) ;
   *        cle = SPKI enregistrée (TV appairée) ; empreinte = attendue (QR code) ; aucune = code de secours
   * @param {{secret:string, nom:string, empreinteTV?:string}|null} o.appairage
   * @param {object} o.config  IBIPROJO_CONFIG
   */
  constructor({ signal, identite, cible, appairage = null, config, RTC = globalThis.RTCPeerConnection }) {
    super();
    Object.assign(this, { signal, identite, cible, appairage, config, RTC });
    this.etat = "nouvelle";
    this.connectionId = "tc_" + hexAleatoire(8);
    this.pc = null;
    this.canal = null;
    this.tvVerifiee = false;
    this.candidatsEnAttente = [];
    this.nPing = 0;
    this.pongsManques = 0;
    this._minuteries = new Set();
    this._surSignal = (e) => this._signal(e.detail);
  }

  get prete() { return this.etat === "prete"; }

  _etat(etat) {
    if (this.etat === etat || this.etat === "fermee") return;
    this.etat = etat;
    this.dispatchEvent(new CustomEvent("etat", { detail: { etat } }));
  }

  _emettre(type, detail = {}) { this.dispatchEvent(new CustomEvent(type, { detail })); }

  _delai(ms, fn) {
    const t = setTimeout(() => { this._minuteries.delete(t); fn(); }, ms);
    this._minuteries.add(t);
    return t;
  }

  async demarrer() {
    this._etat("connexion");
    journal("connexion", this.appairage ? "appairage" : "tv");
    this.signal.addEventListener("message", this._surSignal);
    this._minuterieConnexion = this._delai(this.config.delais.connexion, () => this.fermer("injoignable"));

    const pc = (this.pc = new this.RTC({ iceServers: this.config.iceServers }));
    const canal = (this.canal = pc.createDataChannel(ETIQUETTE_CANAL, { ordered: true }));
    canal.onopen = () => this._canalOuvert();
    // Messages traités strictement l'un après l'autre (la vérification du hello est asynchrone)
    this._file = Promise.resolve();
    canal.onmessage = (ev) => { this._file = this._file.then(() => this._recevoir(ev.data)).catch(() => {}); };
    canal.onclose = () => this.fermer("perdue");
    pc.onicecandidate = (ev) => {
      const c = ev.candidate;
      if (!c || !c.candidate) return; // fin des candidats : rien à transmettre
      this.signal.envoyer("CANDIDATE", this.cible.id, {
        candidate: { candidate: c.candidate, sdpMid: c.sdpMid, sdpMLineIndex: c.sdpMLineIndex },
        type: "data",
        connectionId: this.connectionId,
      });
    };
    pc.onconnectionstatechange = () => {
      const s = pc.connectionState;
      if (s === "failed" || s === "closed") this.fermer("perdue");
      else if (s === "disconnected") {
        this._delai(5000, () => { if (pc.connectionState === "disconnected") this.fermer("perdue"); });
      }
    };

    try {
      const offre = await pc.createOffer();
      await pc.setLocalDescription(offre);
      if (this.etat === "fermee") return;
      const envoye = this.signal.envoyer("OFFER", this.cible.id, {
        sdp: { type: "offer", sdp: pc.localDescription.sdp },
        type: "data",
        connectionId: this.connectionId,
        label: ETIQUETTE_CANAL,
        reliable: true,
        serialization: "json",
      });
      if (!envoye) this.fermer("injoignable");
    } catch {
      this.fermer("perdue");
    }
  }

  async _signal(m) {
    if (this.etat === "fermee" || !m || m.src !== this.cible.id) return;
    const p = m.payload || {};
    try {
      switch (m.type) {
        case "ANSWER":
          if (p.connectionId !== this.connectionId || this.pc.remoteDescription) return;
          if (!p.sdp || p.sdp.type !== "answer" || typeof p.sdp.sdp !== "string") return;
          await this.pc.setRemoteDescription({ type: "answer", sdp: p.sdp.sdp });
          for (const c of this.candidatsEnAttente.splice(0)) await this.pc.addIceCandidate(c).catch(() => {});
          break;
        case "CANDIDATE":
          if (p.connectionId !== this.connectionId || !p.candidate) return;
          if (this.pc.remoteDescription) await this.pc.addIceCandidate(p.candidate).catch(() => {});
          else this.candidatsEnAttente.push(p.candidate);
          break;
        case "EXPIRE": // le serveur n'a pas pu remettre notre message : TV hors ligne
          this.fermer("injoignable");
          break;
        case "LEAVE":
          if (!p.connectionId || p.connectionId === this.connectionId) this.fermer("perdue");
          break;
      }
    } catch {
      this.fermer("perdue");
    }
  }

  async _canalOuvert() {
    this._etat("authentification");
    this.fpTel = empreinteDtls(this.pc.localDescription?.sdp);
    this.fpTV = empreinteDtls(this.pc.remoteDescription?.sdp);
    if (!this.fpTel || !this.fpTV) return this._echecAuth();
    this.nonceTel = nouveauNonce();
    const sig = await signer(this.identite.clePrivee, texteHelloTel(this.fpTel, this.fpTV, this.nonceTel));
    const hello = { appareil: this.identite.id, nonce: this.nonceTel, sig };
    if (this.appairage) {
      hello.cle = this.identite.cleSpki;
      hello.nom = this.appairage.nom;
    }
    this._brut(message("hello", hello));
  }

  _brut(msg) {
    if (!this.canal || this.canal.readyState !== "open") return false;
    try { this.canal.send(encoder(msg)); return true; } catch { return false; }
  }

  async _recevoir(brut) {
    if (this.etat === "fermee") return;
    const m = decoder(brut);
    if (!m) return;

    // Avant la vérification du hello de la TV : seuls « hello » et « erreur » sont lus.
    if (!this.tvVerifiee) {
      if (m.t === "erreur") return this._erreur(m);
      if (m.t !== "hello" || !this.nonceTel) return;
      return this._helloTV(m);
    }

    switch (m.t) {
      case "erreur": return this._erreur(m);
      case "pong": if (m.n <= this.nPing) this.pongsManques = 0; return;
      case "revoked": journal("revocation"); return this.fermer("revoked");
      case "pair_refused":
        if (this.etat !== "appairage") return;
        journal("appairage_refuse", m.raison);
        this.refus = m.raison;
        return this.fermer("refus");
      case "paired": return this._paired(m);
    }
    if (this.etat !== "prete") return; // commandes TV ignorées pendant l'appairage
    if (m.t === "focus_text") this._emettre("focus_text", { type: m.type, s: m.s, action: m.action });
    else if (m.t === "focus_lost") this._emettre("focus_lost");
  }

  async _helloTV(m) {
    const verdict = verifierVersion(m.v);
    if (verdict !== "ok") {
      journal("version_incompatible", verdict);
      this._emettre("version", { verdict });
      return this.fermer("version");
    }
    try {
      // La clé présentée doit être celle qu'on connaît (TV appairée) ou correspondre à
      // l'empreinte du QR code. Mode code de secours : clé acceptée (confirmation sur la TV).
      if (this.cible.cle) {
        if (!egaliteConstante(m.cle, this.cible.cle)) return this._echecAuth();
      } else if (this.cible.empreinte) {
        if (!egaliteConstante(await empreinteCle(m.cle), this.cible.empreinte)) return this._echecAuth();
      }
      const cle = await importerSpki(m.cle);
      const ok = await verifier(cle, texteHelloTV(this.fpTV, this.fpTel, this.nonceTel, m.nonce), m.sig);
      if (!ok || this.etat === "fermee") return this._echecAuth();
    } catch {
      return this._echecAuth();
    }
    this.tvVerifiee = true;
    this.cleTV = m.cle;
    clearTimeout(this._minuterieConnexion);
    this._minuteries.delete(this._minuterieConnexion);
    this._emettre("tv", { nomTV: typeof m.nomTV === "string" ? m.nomTV : "", versionApp: m.versionApp, cle: m.cle });
    this._demarrerPing();
    if (this.appairage) {
      this._etat("appairage");
      journal("appairage_demande");
      const req = { secret: this.appairage.secret, nom: this.appairage.nom, cle: this.identite.cleSpki };
      if (this.appairage.empreinteTV) req.empreinteTV = this.appairage.empreinteTV;
      this._brut(message("pair_request", req));
      // Le secret expire en 5 min côté TV : garde-fou si la TV ne répond jamais
      this._delai(6 * 60 * 1000, () => { if (this.etat === "appairage") this.fermer("perdue"); });
    } else {
      journal("connectee");
      this._etat("prete");
    }
  }

  async _paired(m) {
    if (this.etat !== "appairage") return;
    const idTV = estIdTV(m.idTV) ? m.idTV : estIdTV(this.cible.id) ? this.cible.id : null;
    if (!idTV) return this._echecAuth();
    journal("appairage_accepte");
    this.appairage = null;
    this._emettre("paired", {
      nomTV: typeof m.nomTV === "string" && m.nomTV ? m.nomTV : "TV",
      idTV,
      cle: this.cleTV,
      empreinte: await empreinteCle(this.cleTV),
    });
    journal("connectee");
    this._etat("prete");
  }

  _erreur(m) {
    if (m.code === "version") {
      const verdict = verdictErreurVersion(m.vMax);
      journal("version_incompatible", verdict);
      this._emettre("version", { verdict });
      this.fermer("version");
    }
  }

  _echecAuth() {
    journal("authentification_echouee");
    this.fermer("auth");
  }

  _demarrerPing() {
    const { ping, pongsManquesMax } = this.config.delais;
    this._ping = setInterval(() => {
      if (this.pongsManques >= pongsManquesMax) return this.fermer("perdue");
      this.pongsManques++;
      this._brut(message("ping", { n: ++this.nPing }));
    }, ping);
  }

  /** Vérifie rapidement que la TV répond (retour au premier plan). */
  verifierVivante(delaiMs = 4000) {
    if (!this.tvVerifiee) return;
    const n = ++this.nPing;
    const avant = this.pongsManques;
    this.pongsManques++;
    this._brut(message("ping", { n }));
    this._delai(delaiMs, () => { if (this.pongsManques > avant) this.fermer("perdue"); });
  }

  // ---------- Commandes (refusées tant que la session n'est pas authentifiée et prête) ----------

  envoyer(t, champs = {}) {
    if (!this.prete) return false;
    return this._brut(message(t, champs));
  }
  touche(k) { return this.envoyer("key", { k }); }
  texte(s, curseur) {
    return this.envoyer("text", Number.isInteger(curseur) ? { s, curseur } : { s });
  }
  valider() { return this.envoyer("submit"); }

  fermer(raison = "fermee") {
    if (this.etat === "fermee") return;
    const ancien = this.etat;
    this.etat = "fermee";
    clearInterval(this._ping);
    for (const t of this._minuteries) clearTimeout(t);
    this._minuteries.clear();
    this.signal.removeEventListener("message", this._surSignal);
    if (raison === "fermee" && this.pc) {
      // Départ volontaire : on prévient la TV par le serveur (facultatif pour elle)
      this.signal.envoyer("LEAVE", this.cible.id, { type: "data", connectionId: this.connectionId });
    }
    try { if (this.canal) { this.canal.onclose = null; this.canal.close(); } } catch {}
    try { if (this.pc) { this.pc.onconnectionstatechange = null; this.pc.close(); } } catch {}
    if (ancien === "prete" || raison !== "fermee") journal("deconnectee", raison);
    this.dispatchEvent(new CustomEvent("etat", { detail: { etat: "fermee" } }));
    this._emettre("fin", { raison, refus: this.refus });
  }
}
