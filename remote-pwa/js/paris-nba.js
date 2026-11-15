// Box score NBA : paris suivis (si la TV annonce « nba_paris »). Feuille « Mon pari » : les lignes
// que la TV suit (`nba.paris` : état, valeur), le collage du texte d'un pari (`nba_paris_ajouter`),
// « Tout effacer » (`nba_paris_effacer`, après confirmation) et le bilan d'un ajout
// (« 3 lignes suivies · 1 non suivie », d'après les lignes nouvelles du `nba` suivant).
// Journaux : `nba_paris_ajouter` / `nba_paris_effacer` seuls : jamais le texte collé, une ligne,
// une cote ni une mise.

import {
  NOMS_ETAT_PARI, texteAEnvoyer, valeurPari, avancementPari, matchPari, textePari, bilanAjout, messageAjout,
} from "./nba.js";
import { h, icone } from "./dom.js";
import { journal } from "./journal.js";

// Sans ligne nouvelle dans ce délai : « Aucune nouvelle ligne » (déjà suivies, ou texte non reconnu)
const ATTENTE_BILAN_MS = 4000;
// Pastille d'état : ✓ gagné, ✗ perdu, – non suivi ; en cours (point plein) et en attente (anneau) en CSS
const ICONES_ETAT = { gagne: "i-ok", perdu: "i-croix", non_suivi: "i-moins" };

export class FeuilleParis {
  /**
   * @param {object} o
   * @param {import("./controleur.js").Controleur} o.ctrl
   * @param {(ms?:number|number[]) => void} o.vibrer
   * @param {(o:{titre:string, texte:string, ok?:string, annuler?:string}) => Promise<boolean>} o.confirmer
   * @param {HTMLElement} o.bouton « Mon pari » (focus rendu à la fermeture)
   */
  constructor({ ctrl, vibrer, confirmer, bouton }) {
    Object.assign(this, { ctrl, vibrer, confirmer, bouton });
    const $ = (id) => document.getElementById(id);
    this.el = {
      voile: $("feuille-pari"), titre: $("titre-pari"), fermer: $("btn-pari-fermer"),
      liste: $("liste-pari"), vide: $("pari-vide"), effacer: $("btn-pari-effacer"),
      form: $("form-pari"), champ: $("champ-pari"), ajouter: $("btn-pari-ajouter"), retour: $("pari-retour"),
    };
    this.paris = [];          // dernières lignes de la TV
    this.bilan = null;        // ajout envoyé : { avant: Set des ids, minuterie }
    this.lignes = new Map();  // id → { li, maj(p) }
    this.sig = "";

    const e = this.el;
    e.fermer.addEventListener("click", () => { this.vibrer(); this.fermer({ rendreFocus: true }); });
    e.voile.addEventListener("click", (ev) => { if (ev.target === e.voile) this.fermer({ rendreFocus: true }); });
    addEventListener("keydown", (ev) => {
      // Échap ferme la feuille, sauf pendant la confirmation de « Tout effacer » (l'alerte passe devant)
      if (ev.key === "Escape" && this.ouverte && !document.querySelector("#alerte:not([hidden])")) this.fermer({ rendreFocus: true });
    });
    e.champ.addEventListener("input", () => this.majAjouter());
    // Ctrl / ⌘ + Entrée : ajouter (Entrée seule passe à la ligne)
    e.champ.addEventListener("keydown", (ev) => {
      if (ev.key === "Enter" && (ev.ctrlKey || ev.metaKey)) { ev.preventDefault(); this.ajouter(); }
    });
    // Champ touché, clavier du téléphone ouvert : le champ reste visible
    e.champ.addEventListener("focus", () => setTimeout(() => e.champ.scrollIntoView({ block: "nearest" }), 300));
    e.form.addEventListener("submit", (ev) => { ev.preventDefault(); this.ajouter(); });
    e.effacer.addEventListener("click", () => this.effacerTout());
    // iOS superpose son clavier : la feuille remonte juste au-dessus (comme la feuille du clavier)
    for (const ev of ["resize", "scroll"]) window.visualViewport?.addEventListener(ev, () => { if (this.ouverte) this.placer(); });
  }

  get ouverte() { return !this.el.voile.hidden; }

  // ---------- Cycle de vie ----------

  /** Autre TV, ou paris plus annoncés : rien n'est gardé (le texte en cours de collage reste dans le champ). */
  oublier() {
    this.paris = [];
    this.finBilan();
    this.dire("");
    this.fermer();
    this.rendre();
  }

  /** Lignes reçues de la TV (`nba.paris`) : liste à jour, bilan d'un ajout dès que ses lignes arrivent. */
  maj(paris) {
    this.paris = paris;
    if (this.bilan && paris.some((p) => !this.bilan.avant.has(p.id))) {
      const b = bilanAjout(this.bilan.avant, paris);
      this.finBilan();
      this.dire(messageAjout(b));
    }
    if (this.ouverte) this.rendre();
  }

  // ---------- Feuille ----------

  ouvrir() {
    this.rendre();
    this.majAjouter();
    this.el.voile.hidden = false;
    this.placer();
    this.el.titre.focus({ preventScroll: true });
  }

  fermer({ rendreFocus = false } = {}) {
    if (!this.ouverte) return;
    if (document.activeElement === this.el.champ) this.el.champ.blur();
    this.el.voile.hidden = true;
    this.el.voile.style.setProperty("--clavier-bas", "0px");
    if (rendreFocus && this.bouton.offsetParent) this.bouton.focus({ preventScroll: true });
  }

  /** Feuille au-dessus du clavier du téléphone (zone visible du visualViewport). */
  placer() {
    const vv = window.visualViewport;
    const bas = vv ? Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop)) : 0;
    this.el.voile.style.setProperty("--clavier-bas", `${bas}px`);
    this.el.voile.firstElementChild.classList.toggle("clavier-ouvert", bas > 40);
  }

  // ---------- Envois ----------

  majAjouter() { this.el.ajouter.disabled = !texteAEnvoyer(this.el.champ.value); }

  /** « Ajouter » : le texte part vers la TV, le champ se vide ; le bilan suit avec le `nba` de réponse. */
  ajouter() {
    const texte = texteAEnvoyer(this.el.champ.value);
    if (!texte) return;
    this.vibrer();
    if (!this.ctrl.nbaParisAjouter(texte)) {
      this.dire("La TV n'est pas connectée", { erreur: true });
      return;
    }
    journal("nba_paris_ajouter");
    this.el.champ.value = "";
    this.majAjouter();
    this.finBilan();
    this.bilan = {
      avant: new Set(this.paris.map((p) => p.id)),
      minuterie: setTimeout(() => {
        this.finBilan();
        this.dire(messageAjout({ suivies: 0, nonSuivies: 0 }));
      }, ATTENTE_BILAN_MS),
    };
    this.dire("Lecture du pari…");
  }

  finBilan() {
    if (!this.bilan) return;
    clearTimeout(this.bilan.minuterie);
    this.bilan = null;
  }

  /** « Tout effacer », après confirmation. */
  async effacerTout() {
    this.vibrer();
    const n = this.paris.length;
    if (!n) return;
    const ok = await this.confirmer({
      titre: "Effacer ton pari ?",
      texte: n === 1 ? "La ligne suivie sera retirée de la TV." : `Les ${n} lignes suivies seront retirées de la TV.`,
      ok: "Tout effacer", annuler: "Annuler",
    });
    if (!ok) {
      if (this.ouverte) this.el.effacer.focus({ preventScroll: true });
      return;
    }
    if (!this.ctrl.nbaParisEffacer()) {
      this.dire("La TV n'est pas connectée", { erreur: true });
      return;
    }
    journal("nba_paris_effacer");
    this.finBilan();
    this.dire("Lignes effacées");
    if (this.ouverte) this.el.titre.focus({ preventScroll: true });
  }

  /** Retour sous le titre (région polie : lu par les lecteurs d'écran), ramené à l'écran avec les lignes. */
  dire(texte, { erreur = false } = {}) {
    const r = this.el.retour;
    r.textContent = texte;
    r.classList.toggle("erreur", erreur);
    if (texte && this.ouverte) r.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }

  // ---------- Lignes ----------

  /** Liste mise à jour sur place (focus gardé) ; reconstruite si les lignes changent. */
  rendre() {
    const e = this.el;
    const paris = this.paris;
    e.liste.hidden = !paris.length;
    e.vide.hidden = !!paris.length;
    e.effacer.hidden = !paris.length;
    const sig = paris.map((p) => p.id).join("\n");
    if (sig !== this.sig) {
      this.sig = sig;
      this.lignes.clear();
      e.liste.replaceChildren(...paris.map((p) => this.construireLigne(p)));
    }
    for (const p of paris) this.lignes.get(p.id)?.maj(p);
  }

  construireLigne(p) {
    const pastille = h("span", { class: "pari-etat", "aria-hidden": "true" });
    const libelle = h("span", { class: "pari-libelle" });
    const detail = h("span", { class: "pari-detail" });
    const valeur = h("span", { class: "pari-valeur", "aria-hidden": "true" });
    const remplissage = h("span", {});
    const barre = h("span", { class: "pari-barre", "aria-hidden": "true" }, remplissage);
    // Lecteurs d'écran : une phrase par ligne (« … : en cours, 12 sur 19,5 »)
    const lecteur = h("span", { class: "invisible-lecteur" });
    const li = h("li", { class: "pari", "data-id": p.id },
      pastille, h("span", { class: "pari-textes", "aria-hidden": "true" }, libelle, detail), valeur, barre, lecteur);
    let icn = null;
    const maj = (l) => {
      li.dataset.etat = l.etat;
      if (icn !== l.etat) {
        icn = l.etat;
        pastille.replaceChildren(...(ICONES_ETAT[l.etat] ? [icone(ICONES_ETAT[l.etat])] : []));
      }
      libelle.textContent = l.libelle;
      const m = matchPari(l);
      detail.textContent = m ? `${NOMS_ETAT_PARI[l.etat]} · ${m}` : NOMS_ETAT_PARI[l.etat];
      valeur.textContent = valeurPari(l);
      valeur.hidden = !valeur.textContent;
      const a = avancementPari(l);
      barre.hidden = a === null;
      if (a !== null) remplissage.style.setProperty("--p", String(a));
      lecteur.textContent = textePari(l);
    };
    this.lignes.set(p.id, { li, maj });
    return li;
  }
}
