// Box score NBA : paris suivis (si la TV annonce « nba_paris »). Feuille « Mon pari » : les lignes
// que la TV suit (`nba.paris` : état, valeur), le collage du texte d'un pari (`nba_paris_ajouter`),
// « Tout effacer » (`nba_paris_effacer`, après confirmation) et le bilan d'un ajout
// (« 3 lignes suivies · 1 non suivie », d'après les lignes nouvelles du `nba` suivant).
// Combinés (si la TV annonce aussi « nba_paris_groupes ») : les lignes rangées par combiné en
// accordéon (combines-nba.js), « Effacer ce combiné » (`nba_paris_effacer {groupe}`, après
// confirmation), bilan « Combiné 3 ajouté · 5 lignes suivies » ou « Déjà ajouté » ; sinon la liste
// à plat, comme avant.
// Journaux : `nba_paris_ajouter` / `nba_paris_effacer` / `nba_paris_ouvrir` seuls : jamais le texte
// collé, une ligne, une cote, une mise ni l'id d'un combiné.

import {
  NOMS_ETAT_PARI, texteAEnvoyer, valeurPari, avancementPari, matchPari, textePari, bilanAjout, messageAjout,
  nouveauCombine, messageAjoutCombine,
} from "./nba.js";
import { AccordeonCombines } from "./combines-nba.js";
import { h, icone } from "./dom.js";
import { journal } from "./journal.js";

// Sans ligne nouvelle dans ce délai : « Aucune nouvelle ligne » (déjà suivies, ou texte non reconnu)
const ATTENTE_BILAN_MS = 4000;
// Pastille d'état : ✓ gagné, ✗ perdu, – non suivi ; en cours (point plein) et en attente (anneau) en CSS
const ICONES_ETAT = { gagne: "i-ok", perdu: "i-croix", non_suivi: "i-moins" };

/** Ligne d'un pari (liste à plat, ou dans un combiné) : `li`, et `maj(p)` pour la mettre à jour sur place. */
function construireLigne(p) {
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
  return { li, maj };
}

export class FeuilleParis {
  /**
   * @param {object} o
   * @param {import("./controleur.js").Controleur} o.ctrl
   * @param {(ms?:number|number[]) => void} o.vibrer
   * @param {(o:{titre:string, texte:string, ok?:string, annuler?:string}) => Promise<boolean>} o.confirmer
   * @param {HTMLElement} o.bouton « Mon pari » du bandeau (focus rendu à la fermeture, par défaut)
   */
  constructor({ ctrl, vibrer, confirmer, bouton }) {
    Object.assign(this, { ctrl, vibrer, confirmer, bouton });
    this.retour = bouton;     // bouton qui a ouvert la feuille (bandeau ou Réglages)
    const $ = (id) => document.getElementById(id);
    this.el = {
      voile: $("feuille-pari"), titre: $("titre-pari"), fermer: $("btn-pari-fermer"),
      liste: $("liste-pari"), combines: $("combines-pari"), vide: $("pari-vide"), effacer: $("btn-pari-effacer"),
      form: $("form-pari"), champ: $("champ-pari"), ajouter: $("btn-pari-ajouter"), retour: $("pari-retour"),
    };
    this.paris = [];          // dernières lignes de la TV
    this.groupes = null;      // combinés de la TV (« nba_paris_groupes ») ; null : liste à plat
    this.bilan = null;        // ajout envoyé : { avant, avantGroupes : Set des ids, minuterie }
    this.lignes = new Map();  // liste à plat : id → { li, maj(p) }
    this.sig = "";
    this.accordeon = new AccordeonCombines({
      liste: this.el.combines, construireLigne,
      ouvrir: (id) => this.ouvrirCombine(id),
      effacer: (g) => this.effacerCombine(g),
      focusVide: () => this.el.titre.focus({ preventScroll: true }),
    });

    const e = this.el;
    e.fermer.addEventListener("click", () => { this.vibrer(); this.fermer({ rendreFocus: true }); });
    e.voile.addEventListener("click", (ev) => { if (ev.target === e.voile) this.fermer({ rendreFocus: true }); });
    addEventListener("keydown", (ev) => {
      // Échap ferme la feuille, sauf pendant une confirmation (« Tout effacer », « Effacer ce combiné » : l'alerte passe devant)
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
  /** Ce qui est suivi, compté sur « Mon pari » : les combinés (« nba_paris_groupes »), sinon les lignes. */
  get nombre() { return this.groupes ? this.groupes.length : this.paris.length; }

  // ---------- Cycle de vie ----------

  /** Autre TV, ou paris plus annoncés : rien n'est gardé (le texte en cours de collage reste dans le champ). */
  oublier() {
    this.paris = [];
    this.groupes = null;
    this.accordeon.vider();
    this.finBilan();
    this.dire("");
    this.fermer();
    this.rendre();
  }

  /**
   * Lignes reçues de la TV (`nba.paris`) et combinés (`nba.groupes`, null sans « nba_paris_groupes ») :
   * feuille à jour, bilan d'un ajout dès que ses lignes (ou son combiné) arrivent.
   */
  maj(paris, groupes = null) {
    this.paris = paris;
    this.groupes = groupes;
    if (this.bilan && groupes) {
      const g = nouveauCombine(this.bilan.avantGroupes, groupes);
      if (g) {
        this.finBilan();
        this.dire(messageAjoutCombine(g));
      }
    } else if (this.bilan && paris.some((p) => !this.bilan.avant.has(p.id))) {
      const b = bilanAjout(this.bilan.avant, paris);
      this.finBilan();
      this.dire(messageAjout(b));
    }
    if (this.ouverte) this.rendre();
  }

  // ---------- Feuille ----------

  /** @param {HTMLElement} [retour] bouton qui ouvre la feuille (focus rendu à la fermeture) */
  ouvrir(retour = this.bouton) {
    this.retour = retour;
    if (!this.bilan) this.dire(""); // bilan d'un ajout précédent : plus d'actualité
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
    if (rendreFocus && this.retour.offsetParent) this.retour.focus({ preventScroll: true });
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
    // Combinés : le nouveau combiné sera ouvert par la TV ; rien de nouveau dans le délai : « Déjà ajouté »
    this.accordeon.oublierOuverture();
    this.bilan = {
      avant: new Set(this.paris.map((p) => p.id)),
      avantGroupes: new Set((this.groupes ?? []).map((g) => g.id)),
      minuterie: setTimeout(() => {
        this.finBilan();
        this.dire(this.groupes ? messageAjoutCombine(null) : messageAjout({ suivies: 0, nonSuivies: 0 }));
      }, ATTENTE_BILAN_MS),
    };
    this.dire("Lecture du pari…");
  }

  finBilan() {
    if (!this.bilan) return;
    clearTimeout(this.bilan.minuterie);
    this.bilan = null;
  }

  /** « Tout effacer », après confirmation : toutes les lignes (et tous les combinés). */
  async effacerTout() {
    this.vibrer();
    const n = this.nombre;
    if (!n) return;
    const combines = !!this.groupes;
    let texte;
    if (combines) texte = n === 1 ? "Le combiné sera retiré de la TV." : `Les ${n} combinés seront retirés de la TV.`;
    else texte = n === 1 ? "La ligne suivie sera retirée de la TV." : `Les ${n} lignes suivies seront retirées de la TV.`;
    const ok = await this.confirmer({ titre: "Effacer ton pari ?", texte, ok: "Effacer", annuler: "Annuler" });
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
    this.dire(!combines ? "Lignes effacées" : n === 1 ? "Combiné effacé" : "Combinés effacés");
    if (this.ouverte) this.el.titre.focus({ preventScroll: true });
  }

  // ---------- Combinés ----------

  /** En-tête d'un combiné touché : `nba_paris_ouvrir`, l'accordéon s'ouvre sur lui (la TV suit). */
  ouvrirCombine(id) {
    this.vibrer();
    if (!this.ctrl.nbaParisOuvrir(id)) {
      this.dire("La TV n'est pas connectée", { erreur: true });
      return false;
    }
    journal("nba_paris_ouvrir");
    this.accordeon.noterOuverture(id);
    return true;
  }

  /** « Effacer ce combiné », après confirmation : `nba_paris_effacer {groupe}`. */
  async effacerCombine(g) {
    this.vibrer();
    const n = g.suivies + g.nonSuivies;
    const ok = await this.confirmer({
      titre: "Effacer ce combiné ?",
      texte: `${g.nom} (${n === 1 ? "1 ligne" : `${n} lignes`}) sera retiré de la TV.`,
      ok: "Effacer", annuler: "Annuler",
    });
    // Focus rendu au combiné (« Effacer ce combiné », ou son en-tête) ; retiré par la TV : au voisin
    const rendreFocus = (role) => { if (this.ouverte && !this.accordeon.focus(g.id, role)) this.el.titre.focus({ preventScroll: true }); };
    if (!ok) return rendreFocus("effacer");
    if (!this.ctrl.nbaParisEffacer(g.id)) {
      this.dire("La TV n'est pas connectée", { erreur: true });
      return rendreFocus("effacer");
    }
    journal("nba_paris_effacer");
    this.finBilan();
    this.dire(`${g.nom} effacé`);
    rendreFocus("tete");
  }

  /** Retour sous le titre (région polie : lu par les lecteurs d'écran), ramené à l'écran avec les lignes. */
  dire(texte, { erreur = false } = {}) {
    const r = this.el.retour;
    r.textContent = texte;
    r.classList.toggle("erreur", erreur);
    if (texte && this.ouverte) r.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }

  // ---------- Lignes ----------

  /**
   * Liste à plat, ou combinés en accordéon : mise à jour sur place (focus gardé), reconstruite si
   * les lignes changent.
   */
  rendre() {
    const e = this.el;
    const paris = this.paris;
    const combines = !!this.groupes;
    const vide = !this.nombre;
    e.liste.hidden = combines || vide;
    e.combines.hidden = !combines || vide;
    e.vide.hidden = !vide;
    e.vide.textContent = combines ? "Aucun combiné pour l'instant." : "Aucune ligne suivie pour l'instant.";
    e.effacer.hidden = vide;
    if (combines) {
      if (this.sig) {
        this.sig = "";
        this.lignes.clear();
        e.liste.replaceChildren();
      }
      this.accordeon.maj(this.groupes, paris);
      return;
    }
    if (this.accordeon.ordre.length) this.accordeon.vider();
    const sig = paris.map((p) => p.id).join("\n");
    if (sig !== this.sig) {
      this.sig = sig;
      this.lignes.clear();
      e.liste.replaceChildren(...paris.map((p) => {
        const l = construireLigne(p);
        this.lignes.set(p.id, l);
        return l.li;
      }));
    }
    for (const p of paris) this.lignes.get(p.id)?.maj(p);
  }
}

