// Onglet « Applis » : grille des applis de la TV, ouverture d'un toucher, images en cache.
// La liste est demandée à chaque affichage de l'onglet ; la dernière reçue est gardée par TV
// (affichage immédiat à la réouverture). Les images sont gardées dans IndexedDB (clé id + maj) :
// seules les manquantes sont demandées, une seule fois par connexion.

import { cleImage, imagesADemander, paquets } from "./pilotage.js";
import * as stockage from "./stockage.js";
import { h, icone } from "./dom.js";
import { journal } from "./journal.js";

const ATTENTE_LISTE_MS = 8000;   // sans réponse : « La TV ne répond pas »
const ATTENTE_IMAGES_MS = 8000;  // image jamais reçue : initiale de l'appli
const ATTENTE_OUVERTURE_MS = 6000;
const NB_SQUELETTES = 8;

export class OngletApplis {
  /**
   * @param {object} o
   * @param {import("./controleur.js").Controleur} o.ctrl
   * @param {(texte:string, o?:{erreur?:boolean}) => void} o.annoncer  confirmation discrète
   * @param {(ms?:number|number[]) => void} o.vibrer
   */
  constructor({ ctrl, annoncer, vibrer }) {
    Object.assign(this, { ctrl, annoncer, vibrer });
    this.grille = document.getElementById("applis-grille");
    this.message = document.getElementById("applis-message");
    this.texteMessage = document.getElementById("applis-message-texte");
    this.btnReessayer = document.getElementById("applis-reessayer");
    this.visible = false;
    this.idTV = null;
    this.liste = null;          // dernière liste reçue (ou gardée) pour la TV active
    this.tuiles = new Map();    // id → { el, visuel, nom, marque, app, image }
    this.images = new Map();    // cle « id|maj » → { forme, img } (mémoire)
    this.demandees = new Set(); // images demandées pendant cette connexion
    this.ouvertures = new Map(); // id → minuterie (en attente d'app_ouverte)
    this._minuterieListe = null;
    this._minuterieImages = null;

    this.btnReessayer.addEventListener("click", () => { this.vibrer(); this.demanderListe(); });
    ctrl.addEventListener("apps", (e) => this.recevoirListe(e.detail.liste));
    ctrl.addEventListener("app_image", (e) => this.recevoirImage(e.detail));
    ctrl.addEventListener("app_ouverte", (e) => this.recevoirOuverture(e.detail));
  }

  // ---------- Cycle de vie ----------

  /** TV active changée (ou première ouverture) : liste gardée pour cette TV. */
  async changerTV(idTV) {
    if (idTV === this.idTV) return;
    this.idTV = idTV;
    this.liste = null;
    this.tuiles.clear();
    this.grille.replaceChildren();
    this.demandees.clear();
    if (!idTV) return;
    let gardee = null;
    try { gardee = await stockage.lireReglage(`apps:${idTV}`); } catch {}
    if (this.idTV !== idTV || this.liste || !Array.isArray(gardee)) return;
    this.liste = gardee;
    if (this.visible) { this.rendre(); this.chargerImages({ demander: false }); }
  }

  afficher() {
    this.visible = true;
    this.rendre();
    if (this.liste) this.chargerImages({ demander: false });
    this.demanderListe();
  }

  masquer() { this.visible = false; }

  /** Nouvelle connexion à la TV : les images manquantes pourront être redemandées. */
  surConnexion() {
    this.demandees.clear();
    if (this.visible) this.demanderListe();
  }

  demanderListe() {
    clearTimeout(this._minuterieListe);
    this.btnReessayer.hidden = true;
    if (!this.ctrl.appsListe()) {
      if (!this.liste) this.montrerMessage("La TV n'est pas connectée. Les applis s'afficheront dès qu'elle répondra.");
      return;
    }
    if (!this.liste) this.montrerSquelettes();
    this._minuterieListe = setTimeout(() => {
      if (!this.liste && this.visible) this.montrerMessage("La TV ne répond pas.", true);
    }, ATTENTE_LISTE_MS);
  }

  // ---------- Réception ----------

  recevoirListe(liste) {
    clearTimeout(this._minuterieListe);
    this.liste = liste;
    if (this.idTV) stockage.ecrireReglage(`apps:${this.idTV}`, liste).catch(() => {});
    this.rendre();
    this.chargerImages();
  }

  /** Images de la liste : mémoire, puis IndexedDB, puis (si `demander`) la TV pour les manquantes. */
  async chargerImages({ demander = true } = {}) {
    const liste = this.liste;
    if (!liste?.length) return;
    const absentes = liste.map((a) => cleImage(a.id, a.maj)).filter((c) => !this.images.has(c));
    if (absentes.length) {
      try {
        for (const [c, enreg] of await stockage.lireImages(absentes)) this.images.set(c, enreg);
      } catch {}
    }
    if (liste !== this.liste) return;
    for (const a of liste) this.peindreImage(a);
    const ids = imagesADemander(liste, this.images, this.demandees);
    if (demander && ids.length) {
      const envoye = paquets(ids).every((p) => this.ctrl.appsImages(p));
      if (envoye) for (const a of liste) if (ids.includes(a.id)) this.demandees.add(cleImage(a.id, a.maj));
    }
    // Images jamais reçues (TV qui n'en a pas, ou hors connexion) : initiale de l'appli
    clearTimeout(this._minuterieImages);
    this._minuterieImages = setTimeout(() => {
      for (const t of this.tuiles.values()) if (t.visuel.classList.contains("attente")) this.peindreLettre(t);
    }, ATTENTE_IMAGES_MS);
  }

  recevoirImage(image) {
    const c = cleImage(image.id, image.maj);
    this.images.set(c, image);
    stockage.enregistrerImage(image).catch(() => {});
    const t = this.tuiles.get(image.id);
    if (t) this.peindreImage(t.app, image);
  }

  recevoirOuverture({ id, ok }) {
    const t = this.tuiles.get(id);
    const nom = t?.app.nom || "L'appli";
    if (this.ouvertures.has(id)) {
      clearTimeout(this.ouvertures.get(id));
      this.ouvertures.delete(id);
    }
    t?.el.classList.remove("ouverture");
    journal(ok ? "appli_ouverte" : "appli_refusee");
    if (ok) {
      this.vibrer([12, 40, 12]);
      this.annoncer(`${nom} s'ouvre sur la TV`);
    } else {
      this.vibrer(60);
      this.annoncer(`Impossible d'ouvrir ${nom} sur la TV`, { erreur: true });
    }
  }

  // ---------- Affichage ----------

  montrerMessage(texte, reessayer = false) {
    this.grille.replaceChildren();
    this.tuiles.clear();
    this.grille.setAttribute("aria-busy", "false");
    this.texteMessage.textContent = texte;
    this.btnReessayer.hidden = !reessayer;
    this.message.hidden = false;
  }

  montrerSquelettes() {
    this.message.hidden = true;
    this.tuiles.clear();
    this.grille.setAttribute("aria-busy", "true");
    const s = [];
    for (let i = 0; i < NB_SQUELETTES; i++) {
      s.push(h("div", { class: "tuile tuile-squelette", "aria-hidden": "true" },
        h("span", { class: "tuile-visuel squelette" }), h("span", { class: "tuile-nom squelette" })));
    }
    this.grille.replaceChildren(...s);
  }

  /** Grille à jour : tuiles réutilisées par identifiant (images gardées, défilement intact). */
  rendre() {
    if (!this.liste) {
      if (!this.grille.querySelector(".tuile-squelette")) this.montrerSquelettes();
      return;
    }
    if (!this.liste.length) return this.montrerMessage("Aucune appli à ouvrir sur cette TV.");
    this.message.hidden = true;
    this.grille.setAttribute("aria-busy", "false");
    const anciennes = this.tuiles;
    this.tuiles = new Map();
    const elements = [];
    for (const app of this.liste) {
      let t = anciennes.get(app.id);
      if (t) {
        t.app = app;
        t.nom.textContent = app.nom;
        t.marque.hidden = !app.favori;
        t.libelleFavori.hidden = !app.favori;
      } else {
        t = this.creerTuile(app);
      }
      this.tuiles.set(app.id, t);
      elements.push(t.el);
      this.peindreImage(app);
    }
    // Même ordre : rien à déplacer (un appui en cours sur une tuile n'est pas dérangé)
    const enfants = this.grille.children;
    if (enfants.length !== elements.length || elements.some((el, i) => enfants[i] !== el)) {
      this.grille.replaceChildren(...elements);
    }
  }

  creerTuile(app) {
    const visuel = h("span", { class: "tuile-visuel attente squelette" });
    const marque = h("span", { class: "tuile-favori", "aria-hidden": "true", hidden: !app.favori },
      icone("i-etoile"));
    visuel.append(marque);
    const nom = h("span", { class: "tuile-nom" }, app.nom);
    const libelleFavori = h("span", { class: "invisible-lecteur", hidden: !app.favori }, ", favori");
    const el = h("button", { type: "button", class: "tuile", "data-id": app.id }, visuel, nom, libelleFavori);
    const t = { el, visuel, nom, marque, libelleFavori, app, image: null };
    // Retour visuel immédiat à l'appui ; ouverture au relâcher (un défilement n'ouvre rien)
    el.addEventListener("pointerdown", () => el.classList.add("appui"));
    for (const ev of ["pointerup", "pointercancel", "pointerleave"]) {
      el.addEventListener(ev, () => el.classList.remove("appui"));
    }
    el.addEventListener("click", () => this.ouvrir(t));
    el.addEventListener("contextmenu", (e) => e.preventDefault());
    return t;
  }

  peindreImage(app, image = this.images.get(cleImage(app.id, app.maj))) {
    const t = this.tuiles.get(app.id);
    if (!t || !image || t.image === image.img) return;
    t.image = image.img;
    t.el.dataset.forme = image.forme;
    t.visuel.classList.remove("attente", "squelette", "sans-image");
    t.visuel.querySelector(".tuile-lettre")?.remove();
    let img = t.visuel.querySelector("img");
    if (!img) {
      img = h("img", { alt: "", decoding: "async", draggable: "false" });
      t.visuel.prepend(img);
    }
    img.src = image.img;
  }

  /** Pas d'image : initiale du nom dans un carré coloré (teinte tirée de l'identifiant). */
  peindreLettre(t) {
    if (!t.visuel.classList.contains("attente")) return;
    t.visuel.classList.remove("attente", "squelette");
    t.visuel.classList.add("sans-image");
    t.el.dataset.forme = "icone";
    const lettre = h("span", { class: "tuile-lettre", "aria-hidden": "true" }, initiale(t.app.nom));
    lettre.style.setProperty("--teinte", String(teinteDe(t.app.id)));
    t.visuel.prepend(lettre);
  }

  ouvrir(t) {
    this.vibrer();
    if (!this.ctrl.appOuvrir(t.app.id)) {
      this.annoncer("La TV n'est pas connectée", { erreur: true });
      return;
    }
    clearTimeout(this.ouvertures.get(t.app.id));
    t.el.classList.add("ouverture");
    this.ouvertures.set(t.app.id, setTimeout(() => {
      this.ouvertures.delete(t.app.id);
      t.el.classList.remove("ouverture");
      this.annoncer(`La TV n'a pas confirmé l'ouverture de ${t.app.nom}`, { erreur: true });
    }, ATTENTE_OUVERTURE_MS));
  }
}

const initiale = (nom) => (nom.trim().match(/[\p{L}\p{N}]/u)?.[0] || "?").toUpperCase();

function teinteDe(id) {
  let x = 0;
  for (let i = 0; i < id.length; i++) x = (x * 31 + id.charCodeAt(i)) >>> 0;
  return x % 360;
}
