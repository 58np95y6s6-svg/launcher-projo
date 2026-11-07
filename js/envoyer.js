// Onglet « Envoyer » : un lien du téléphone s'ouvre sur la TV (page web, vidéo, YouTube…).
// Champ + « Coller », état du VPN de la TV (`liens`), résultat (`lien_ouvert`), récents gardés par
// TV, et écran « Bouton Partager » (raccourci iOS : adresse de la TV à copier).
// `liens_etat` part à chaque ouverture de l'onglet et à chaque reconnexion s'il est ouvert.
// Journaux : `lien_envoye`, `lien_refuse <raison>` ; jamais l'URL.

import {
  lireLien, champsLienOuvrir, interpreterResultat, etatVpn, ajouterRecent, normaliserRecents,
  libelleLien, detailRecent, hoteDe, teinteDe, adresseAChange, retirerRecent,
  DELAI_REPONSE_MS, DELAI_ATTENTE_VPN_MS, TEXTE_SANS_REPONSE,
} from "./liens.js";
import * as stockage from "./stockage.js";
import { h, icone } from "./dom.js";
import { journal } from "./journal.js";

const GARDE_REPONSE_TARDIVE_MS = 60000; // réponse arrivée après le délai : encore prise en compte
const TEXTE_RACCOURCI = "Envoie la page que tu regardes, sans quitter Safari";
const TEXTE_ADRESSE_CHANGEE = "L'adresse de la TV a changé : mets à jour ton raccourci";

export class OngletEnvoyer {
  /**
   * @param {object} o
   * @param {import("./controleur.js").Controleur} o.ctrl
   * @param {(texte:string, o?:{erreur?:boolean, duree?:number}) => void} o.annoncer
   * @param {(ms?:number|number[]) => void} o.vibrer
   * @param {(o:{titre:string, texte:string, ok?:string, annuler?:string}) => Promise<boolean>} [o.confirmer]
   */
  constructor({ ctrl, annoncer, vibrer, confirmer = async () => true }) {
    Object.assign(this, { ctrl, annoncer, vibrer, confirmer });
    const $ = (id) => document.getElementById(id);
    this.el = {
      principal: $("envoyer-principal"), defile: $("envoyer-defile"),
      form: $("form-envoyer"), champ: $("champ-lien"), coller: $("btn-coller"), aide: $("envoyer-aide"),
      bouton: $("btn-envoyer"), vpn: $("etat-vpn"), vpnTexte: $("etat-vpn-texte"),
      recents: $("recents"), modifier: $("btn-recents-modifier"), effacer: $("btn-recents-effacer"),
      raccourci: $("btn-raccourci"), raccourciDetail: $("raccourci-detail"),
      guide: $("envoyer-guide"), guideDefile: $("guide-defile"), guideRetour: $("btn-guide-retour"),
      bandeau: $("guide-bandeau"), nomTV: $("guide-nom-tv"),
      adresse: $("guide-adresse"), adresseTexte: $("guide-adresse-texte"), copier: $("btn-copier"),
      activer: $("guide-activer"), btnActiver: $("btn-activer"), attente: $("guide-attente"),
      feuille: $("feuille-vpn"), feuilleTexte: $("texte-vpn"), feuilleLien: $("lien-vpn"),
      allumer: $("btn-vpn-allumer"), sansVpn: $("btn-vpn-sans"), annuler: $("btn-vpn-annuler"),
    };
    this.visible = false;
    this.guideOuvert = false;
    this.idTV = null;
    this.etat = null;        // dernier `liens` de la TV active
    this.recents = [];
    this.edition = false;    // « Modifier » : boutons « Retirer » à la place de « Renvoyer »
    this.copiee = null;      // dernière adresse du raccourci copiée sur ce téléphone (TV active)
    this.envois = new Map(); // url → { phase: "envoi" | "attente_vpn" | "expire", minuterie }
    this.lignes = new Map(); // url → ligne des récents
    this.feuilleUrl = null;  // lien concerné par la feuille « VPN coupé »
    this.activation = null;  // « Activer sur la TV » en attente : { minuterie, etatRecu }
    this._copie = null;

    const e = this.el;
    e.champ.addEventListener("input", () => { this.aide(null); this.majBouton(); });
    e.form.addEventListener("submit", (ev) => { ev.preventDefault(); this.envoyerChamp(); });
    e.coller.addEventListener("click", () => this.coller());
    e.raccourci.addEventListener("click", () => { this.vibrer(); this.ouvrirGuide(); });
    e.guideRetour.addEventListener("click", () => { this.vibrer(); this.fermerGuide(); });
    e.copier.addEventListener("click", () => this.copier());
    e.modifier.addEventListener("click", () => { this.vibrer(); this.basculerEdition(); });
    e.effacer.addEventListener("click", () => this.effacerTout());
    e.btnActiver.addEventListener("click", () => this.activer());
    e.allumer.addEventListener("click", () => this.choixVpn({ vpnDabord: true }));
    e.sansVpn.addEventListener("click", () => this.choixVpn({ sansVpn: true }));
    e.annuler.addEventListener("click", () => { this.vibrer(); this.fermerFeuille(); });
    e.feuille.addEventListener("click", (ev) => { if (ev.target === e.feuille) this.fermerFeuille(); });
    addEventListener("keydown", (ev) => { if (ev.key === "Escape" && !e.feuille.hidden) this.fermerFeuille(); });

    ctrl.addEventListener("liens", (ev) => this.recevoirEtat(ev.detail));
    ctrl.addEventListener("lien_ouvert", (ev) => this.recevoirResultat(ev.detail));
    this.rendreRecents();
    this.majEtat();
  }

  // ---------- Cycle de vie ----------

  /** TV active changée : état, récents et adresse copiée de cette TV. */
  async changerTV(idTV) {
    if (idTV === this.idTV) return;
    this.idTV = idTV;
    this.etat = null;
    this.recents = [];
    this.edition = false;
    this.copiee = null;
    for (const x of this.envois.values()) clearTimeout(x.minuterie);
    this.envois.clear();
    this.finActivation();
    this.fermerFeuille();
    this.fermerGuide();
    this.rendreRecents();
    this.majEtat();
    if (!idTV) return;
    let gardes = null, copiee = null;
    try {
      [gardes, copiee] = await Promise.all([stockage.lireReglage(`liens:${idTV}`), stockage.lireReglage(`raccourci:${idTV}`)]);
    } catch {}
    if (this.idTV !== idTV) return;
    // Un lien ouvert pendant la lecture reste en tête
    this.recents = normaliserRecents([...this.recents, ...(Array.isArray(gardes) ? gardes : [])]);
    if (typeof copiee === "string") this.copiee = copiee;
    this.rendreRecents();
    this.majRaccourci();
  }

  afficher() {
    this.visible = true;
    this.rendreRecents(); // dates relatives à jour
    this.demanderEtat();
  }

  masquer() {
    this.visible = false;
    if (this.edition) { this.edition = false; this.rendreRecents(); }
  }

  /** Nouvelle connexion : l'abonnement à `liens` vaut pour une connexion, renouvelé si l'onglet est ouvert. */
  surConnexion() {
    if (this.visible) this.demanderEtat();
  }

  demanderEtat() { this.ctrl.liensEtat(); }

  /** Défilement de la vue affichée (onglet touché de nouveau : retour en haut). */
  get defile() { return this.guideOuvert ? this.el.guideDefile : this.el.defile; }

  // ---------- Champ et envoi ----------

  aide(texte) {
    this.el.aide.textContent = texte || "";
    this.el.aide.hidden = !texte;
  }

  majBouton() {
    const url = lireLien(this.el.champ.value);
    const enCours = !!url && this.envois.get(url)?.phase === "envoi";
    this.el.bouton.disabled = !url || enCours;
    this.el.bouton.classList.toggle("en-cours", enCours);
    this.el.bouton.setAttribute("aria-busy", String(enCours));
  }

  async coller() {
    this.vibrer();
    this.aide(null);
    let texte;
    try {
      if (!navigator.clipboard?.readText) throw new Error("indisponible");
      texte = await navigator.clipboard.readText();
    } catch {
      this.aide("Accès au presse-papiers refusé : colle le lien dans le champ (appui long → Coller).");
      try { this.el.champ.focus({ preventScroll: true }); } catch {}
      return;
    }
    texte = String(texte ?? "").trim();
    if (!texte) return this.aide("Le presse-papiers est vide : copie d'abord un lien.");
    this.el.champ.value = texte.slice(0, 4096);
    this.majBouton();
    if (!lireLien(texte)) this.aide("Le presse-papiers ne contient pas de lien.");
  }

  envoyerChamp() {
    const url = lireLien(this.el.champ.value);
    if (!url) {
      if (this.el.champ.value.trim()) this.aide("Ce n'est pas un lien : par exemple arte.tv/… ou https://…");
      return;
    }
    this.el.champ.blur(); // ferme le clavier : la confirmation s'affiche au-dessus des onglets
    this.envoyer(url);
  }

  /** `lien_ouvrir` ; la réponse est attendue 6 s. */
  envoyer(url, options = {}) {
    this.vibrer();
    if (this.envois.get(url)?.phase === "envoi") return false; // déjà en route
    if (!this.ctrl.lienOuvrir(champsLienOuvrir(url, options))) {
      this.annoncer("La TV n'est pas connectée", { erreur: true });
      return false;
    }
    this.suivre(url, "envoi");
    return true;
  }

  suivre(url, phase) {
    clearTimeout(this.envois.get(url)?.minuterie);
    const x = { phase };
    x.minuterie = setTimeout(() => this.sansReponse(url, x), phase === "envoi" ? DELAI_REPONSE_MS : DELAI_ATTENTE_VPN_MS);
    this.envois.set(url, x);
    this.majEnCours();
  }

  sansReponse(url, x) {
    if (this.envois.get(url) !== x) return;
    if (x.phase === "envoi") {
      journal("lien_refuse", "sans_reponse");
      this.vibrer(60);
      this.annoncer(TEXTE_SANS_REPONSE, { erreur: true });
    }
    // Une réponse tardive est encore prise en compte un moment
    x.phase = "expire";
    x.minuterie = setTimeout(() => { if (this.envois.get(url) === x) this.envois.delete(url); }, GARDE_REPONSE_TARDIVE_MS);
    this.majEnCours();
  }

  /** Lien en route : bouton (si c'est celui du champ) et ligne des récents en attente. */
  majEnCours() {
    this.majBouton();
    for (const [url, ligne] of this.lignes) {
      const enCours = ["envoi", "attente_vpn"].includes(this.envois.get(url)?.phase);
      ligne.classList.toggle("en-cours", enCours);
    }
  }

  // ---------- Réception ----------

  recevoirEtat(etat) {
    this.etat = etat;
    if (this.activation) {
      if (etat.raccourci) {
        this.finActivation();
        this.annoncer("Envoi depuis l'iPhone activé sur la TV");
      } else this.activation.etatRecu = true;
    }
    this.majEtat();
  }

  recevoirResultat(r) {
    const x = this.envois.get(r.url);
    if (!x) return; // lien qui n'a pas été envoyé d'ici (ou trop ancien)
    clearTimeout(x.minuterie);
    const res = interpreterResultat(r, { nomTV: this.ctrl.tvActive?.nom || "la TV" });
    if (res.type === "attente") {
      // L'appli VPN s'ouvre sur la TV : un second `lien_ouvert` suivra
      this.suivre(r.url, "attente_vpn");
      if (this.feuilleUrl === r.url) this.fermerFeuille();
      this.vibrer(20);
      this.annoncer(res.texte, { duree: 6000 });
      return;
    }
    this.envois.delete(r.url);
    this.majEnCours();
    if (this.feuilleUrl === r.url) this.fermerFeuille();
    if (res.type === "ok") {
      journal("lien_envoye");
      this.vibrer([12, 40, 12]);
      this.annoncer(res.texte);
      if (lireLien(this.el.champ.value) === r.url) {
        this.el.champ.value = "";
        this.aide(null);
        this.majBouton();
      }
      this.ajouterRecent({ url: r.url, ouvertDans: r.ouvertDans, date: Date.now() });
      return;
    }
    journal("lien_refuse", r.raison || "inconnue");
    if (res.type === "vpn_coupe") {
      this.vibrer(30);
      this.ouvrirFeuille(r.url);
      return;
    }
    this.vibrer(60);
    this.annoncer(res.texte, { erreur: true, duree: 4500 });
  }

  // ---------- VPN coupé : feuille de choix ----------

  ouvrirFeuille(url) {
    const e = this.el;
    this.feuilleUrl = url;
    const appli = this.etat?.appliVpn;
    e.allumer.hidden = !appli;
    if (appli) e.allumer.textContent = `Allumer le VPN (${appli}) et ouvrir`;
    e.feuilleTexte.textContent = appli
      ? "La TV n'ouvre les liens qu'avec le VPN. Allume-le : le lien s'ouvrira juste après."
      : "La TV n'ouvre les liens qu'avec le VPN. Allume-le sur la TV puis renvoie le lien, ou ouvre-le sans VPN.";
    e.feuilleLien.textContent = libelleLien({ url });
    e.feuille.hidden = false;
    (appli ? e.allumer : e.sansVpn).focus({ preventScroll: true });
  }

  fermerFeuille() {
    this.feuilleUrl = null;
    this.el.feuille.hidden = true;
  }

  /** « Allumer le VPN et ouvrir » (vpnDabord) ou « Ouvrir sans VPN » (sansVpn) : renvoi du lien. */
  choixVpn(options) {
    const url = this.feuilleUrl;
    this.fermerFeuille();
    if (url) this.envoyer(url, options);
  }

  // ---------- Récents ----------

  ajouterRecent(entree) {
    this.recents = ajouterRecent(this.recents, entree);
    this.garderRecents();
    this.rendreRecents(entree.url);
  }

  /** Écrit les récents de la TV active ; la promesse se résout une fois l'écriture terminée. */
  garderRecents() {
    if (!this.idTV) return Promise.resolve();
    return stockage.ecrireReglage(`liens:${this.idTV}`, this.recents).catch(() => {});
  }

  /** « Modifier » / « OK ». */
  basculerEdition(oui = !this.edition) {
    this.edition = oui && this.recents.length > 0;
    this.rendreRecents();
  }

  /** Retire un lien des récents (gardés sur ce téléphone seulement). */
  retirer(url) {
    this.vibrer();
    this.recents = retirerRecent(this.recents, url);
    this.garderRecents();
    if (!this.recents.length) this.edition = false;
    this.rendreRecents();
  }

  /** « Tout effacer », après confirmation. */
  async effacerTout() {
    this.vibrer();
    const n = this.recents.length;
    if (!n) return;
    const ok = await this.confirmer({
      titre: "Effacer les liens récents ?",
      texte: n === 1 ? "Le lien envoyé sera retiré de ce téléphone." : `Les ${n} liens envoyés seront retirés de ce téléphone.`,
      ok: "Effacer", annuler: "Annuler",
    });
    if (!ok) return;
    this.recents = [];
    this.edition = false;
    this.rendreRecents();
    // Confirmation seulement une fois l'effacement enregistré (appli fermée juste après : rien ne revient)
    await this.garderRecents();
    this.annoncer("Liens récents effacés");
  }

  rendreRecents(nouveau = null) {
    this.lignes.clear();
    const vide = !this.recents.length;
    if (vide) this.edition = false;
    this.el.modifier.hidden = vide;
    this.el.modifier.textContent = this.edition ? "OK" : "Modifier";
    this.el.modifier.setAttribute("aria-pressed", String(this.edition));
    this.el.effacer.hidden = !this.edition;
    if (vide) {
      this.el.recents.replaceChildren(h("p", { class: "recents-vide" }, "Les liens envoyés sur la TV s'afficheront ici."));
      return;
    }
    const maintenant = Date.now();
    this.el.recents.replaceChildren(...this.recents.map((e) => {
      const hote = hoteDe(e.url);
      const libelle = libelleLien(e);
      const pastille = h("span", { class: "recent-icone", "aria-hidden": "true" }, (hote.match(/[\p{L}\p{N}]/u)?.[0] || "?").toUpperCase());
      pastille.style.setProperty("--teinte", String(teinteDe(hote)));
      const renvoyer = this.edition
        ? h("button", { type: "button", class: "btn-ligne btn-retirer", "aria-label": `Retirer ${libelle}` }, icone("i-croix"), "Retirer")
        : h("button", { type: "button", class: "btn-ligne", "aria-label": `Renvoyer ${libelle}` }, icone("i-renvoyer"), "Renvoyer");
      renvoyer.addEventListener("click", () => (this.edition ? this.retirer(e.url) : this.envoyer(e.url, { titre: e.titre })));
      const ligne = h("div", { class: e.url === nouveau ? "recent nouveau" : "recent" },
        pastille,
        h("span", { class: "recent-textes" },
          h("span", { class: "recent-titre" }, libelle),
          h("span", { class: "recent-detail" }, detailRecent(e, maintenant))),
        renvoyer);
      this.lignes.set(e.url, ligne);
      return ligne;
    }));
    this.majEnCours();
  }

  // ---------- État de la TV ----------

  majEtat() {
    const v = etatVpn(this.etat);
    this.el.vpn.hidden = !v;
    if (v) {
      this.el.vpn.dataset.etat = v.etat;
      this.el.vpnTexte.replaceChildren(...v.morceaux.map(([t, gras]) => (gras ? h("b", {}, t) : t)));
    }
    this.majRaccourci();
  }

  // ---------- « Bouton Partager » (raccourci iOS) ----------

  ouvrirGuide() {
    this.guideOuvert = true;
    this.el.principal.hidden = true;
    this.el.guide.hidden = false;
    this.el.guideDefile.scrollTop = 0;
    this.majRaccourci();
    this.el.guideRetour.focus({ preventScroll: true });
  }

  fermerGuide() {
    if (!this.guideOuvert) return;
    this.guideOuvert = false;
    this.el.guide.hidden = true;
    this.el.principal.hidden = false;
    if (this.visible) this.el.raccourci.focus({ preventScroll: true });
  }

  majRaccourci() {
    const e = this.el;
    const adresse = this.etat?.raccourci?.adresse || null;
    const change = adresseAChange(adresse, this.copiee);
    // Vue principale : la carte « Depuis Safari » prévient aussi
    e.raccourciDetail.textContent = change ? TEXTE_ADRESSE_CHANGEE : TEXTE_RACCOURCI;
    e.raccourci.classList.toggle("alerte", change);
    // Écran « Bouton Partager »
    e.nomTV.textContent = this.ctrl.tvActive?.nom || "la TV";
    e.bandeau.hidden = !change;
    e.adresse.hidden = !adresse;
    if (adresse && e.adresseTexte.textContent !== adresse) {
      e.adresseTexte.textContent = adresse;
      this.etiquetteCopier("Copier");
    }
    e.activer.hidden = !this.etat || !!adresse;
    e.attente.hidden = !!this.etat;
  }

  etiquetteCopier(texte) {
    clearTimeout(this._copie);
    this.el.copier.querySelector("span").textContent = texte;
  }

  async copier() {
    const adresse = this.etat?.raccourci?.adresse;
    if (!adresse) return;
    this.vibrer();
    try {
      if (!navigator.clipboard?.writeText) throw new Error("indisponible");
      await navigator.clipboard.writeText(adresse);
    } catch {
      // Sélection de l'adresse : copie à la main
      try {
        const plage = document.createRange();
        plage.selectNodeContents(this.el.adresseTexte);
        getSelection().removeAllRanges();
        getSelection().addRange(plage);
      } catch {}
      this.annoncer("Copie impossible : l'adresse est sélectionnée, copie-la à la main", { erreur: true });
      return;
    }
    this.copiee = adresse;
    if (this.idTV) stockage.ecrireReglage(`raccourci:${this.idTV}`, adresse).catch(() => {});
    this.majRaccourci();
    this.etiquetteCopier("Copiée");
    this._copie = setTimeout(() => this.etiquetteCopier("Copier"), 2500);
    this.annoncer("Adresse copiée : colle-la dans le raccourci");
  }

  /** « Activer sur la TV » : réglage `liens_raccourci` ; l'adresse arrive avec le `liens` suivant. */
  activer() {
    this.vibrer();
    if (!this.ctrl.reglage("liens_raccourci", { valeur: true })) {
      this.annoncer("La TV n'est pas connectée", { erreur: true });
      return;
    }
    journal("reglage", "liens_raccourci");
    this.finActivation();
    this.el.btnActiver.disabled = true;
    this.el.btnActiver.textContent = "Activation…";
    const a = { etatRecu: false };
    a.minuterie = setTimeout(() => {
      if (this.activation !== a) return;
      this.finActivation();
      this.annoncer(a.etatRecu
        ? "La TV n'a pas d'adresse sur le réseau local : vérifie sa connexion (Wi-Fi ou Ethernet)"
        : "La TV n'a pas répondu", { erreur: true, duree: 4500 });
    }, DELAI_REPONSE_MS);
    this.activation = a;
  }

  finActivation() {
    if (!this.activation) return;
    clearTimeout(this.activation.minuterie);
    this.activation = null;
    this.el.btnActiver.disabled = false;
    this.el.btnActiver.textContent = "Activer sur la TV";
  }
}
