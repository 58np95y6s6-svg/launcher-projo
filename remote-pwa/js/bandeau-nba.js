// Box score NBA : bandeau en haut de l'onglet Télécommande (sous la puce de la TV).
// Score du match choisi (badges aux couleurs des équipes, équipe menée en retrait), « DIRECT » +
// statut (ou « TERMINÉ »), erreur de la TV, « Changer de match » (feuille) s'il y en a plusieurs,
// puis le sélecteur Caché · Résumé · Détails (`nba_afficher`).
// `nba_etat` part après chaque authentification si la TV annonce « nba » (abonnement par connexion).
// Lecteurs d'écran : le format et le match choisis sont annoncés, jamais chaque point marqué.
// Journaux : `nba_afficher <format>` ; jamais de score, d'équipe ni de match.

import {
  AFFICHAGES, ERREURS, bandeauVisible, matchDe, equipeMenee, ligneStatut, nomsMatch, texteMatch,
  champsNbaAfficher, annonceAffichage, couleurValable,
} from "./nba.js";
import { Attentes } from "./pilotage.js";
import { h } from "./dom.js";
import { journal } from "./journal.js";

const ECLAT_MS = 900; // score qui change : bref éclat du chiffre

/** Badge d'une équipe : cercle de sa couleur avec son code (propriétés CSS, jamais d'attribut style). */
function peindreBadge(el, equipe) {
  el.textContent = equipe.code;
  const fond = couleurValable(equipe.couleur);
  const texte = couleurValable(equipe.texte);
  if (fond && texte) {
    el.style.setProperty("--equipe", fond);
    el.style.setProperty("--equipe-texte", texte);
  } else {
    el.style.removeProperty("--equipe");
    el.style.removeProperty("--equipe-texte");
  }
}

const badge = (equipe) => {
  const b = h("span", { class: "nba-badge" });
  peindreBadge(b, equipe);
  return b;
};

export class BandeauNba {
  /**
   * @param {object} o
   * @param {import("./controleur.js").Controleur} o.ctrl
   * @param {(texte:string, o?:{erreur?:boolean}) => void} o.annoncer
   * @param {(ms?:number|number[]) => void} o.vibrer
   */
  constructor({ ctrl, annoncer, vibrer }) {
    Object.assign(this, { ctrl, annoncer, vibrer });
    const $ = (id) => document.getElementById(id);
    const bandeau = $("bandeau-nba");
    this.el = {
      bandeau, score: bandeau.querySelector(".nba-score"), statut: bandeau.querySelector(".nba-statut"),
      badgeExt: $("nba-badge-ext"), badgeDom: $("nba-badge-dom"), pointsExt: $("nba-points-ext"), pointsDom: $("nba-points-dom"),
      direct: $("nba-direct"), statutTexte: $("nba-statut-texte"), lecteur: $("nba-lecteur"), aucun: $("nba-aucun"),
      matchs: $("btn-nba-matchs"), erreur: $("nba-erreur"), erreurTexte: $("nba-erreur-texte"),
      segments: $("nba-segments"), radios: [...bandeau.querySelectorAll('input[name="nba-affichage"]')],
      annonce: $("nba-annonce"),
      feuille: $("feuille-nba"), liste: $("liste-nba"), fermer: $("btn-nba-fermer"),
    };
    this.etat = null;               // dernier `nba` de la TV active
    this.attentes = new Attentes(); // format et match envoyés, affichés tout de suite jusqu'à la réponse
    this.affichageTV = undefined;   // derniers format et match de la TV (annonces)
    this.choisiTV = undefined;
    this.scores = null;             // { id, ext, dom } affichés (éclat quand un score change)
    this.lignes = new Map();        // feuille : id → { btn, maj(match, actuel) }
    this.sigListe = "";
    this._recalage = null;

    const e = this.el;
    for (const r of e.radios) r.addEventListener("change", () => { if (r.checked) this.afficher(r.value); });
    e.matchs.addEventListener("click", () => { this.vibrer(); this.ouvrirFeuille(); });
    e.fermer.addEventListener("click", () => { this.vibrer(); this.fermerFeuille({ rendreFocus: true }); });
    e.feuille.addEventListener("click", (ev) => { if (ev.target === e.feuille) this.fermerFeuille({ rendreFocus: true }); });
    addEventListener("keydown", (ev) => { if (ev.key === "Escape" && !e.feuille.hidden) this.fermerFeuille({ rendreFocus: true }); });
    ctrl.addEventListener("nba", (ev) => this.recevoir(ev.detail));
  }

  /** La TV active annonce-t-elle le box score ? */
  get disponible() { return this.ctrl.fonctions.includes("nba"); }

  // ---------- Cycle de vie ----------

  /** Autre TV : rien n'est gardé (l'état arrive avec le `nba` suivant). */
  changerTV() { this.oublier(); }

  /** Fonctions annoncées changées : une TV qui n'annonce plus « nba » n'affiche plus rien. */
  majFonctions() { if (!this.disponible && this.etat) this.oublier(); }

  oublier() {
    this.etat = null;
    this.affichageTV = this.choisiTV = undefined;
    this.scores = null;
    this.attentes.vider();
    this.rendre();
  }

  /** Nouvelle connexion : `nba_etat` (état et abonnement, valable pour cette connexion). */
  surConnexion() {
    this.attentes.vider();
    if (this.disponible) this.ctrl.nbaEtat();
  }

  // ---------- Réception ----------

  recevoir(etat) {
    if (!this.disponible) return;
    const avant = this.etat;
    this.etat = etat;
    // Annonces polies : format ou match changés sur la TV, erreur nouvelle ; jamais les scores
    const phrases = [];
    if (this.affichageTV !== undefined && etat.affichage && etat.affichage !== this.affichageTV) phrases.push(annonceAffichage(etat.affichage));
    if (this.choisiTV !== undefined && etat.choisi && etat.choisi !== this.choisiTV) phrases.push(`Match affiché : ${nomsMatch(matchDe(etat))}`);
    if (etat.erreur && etat.erreur !== avant?.erreur) phrases.push(ERREURS[etat.erreur]);
    this.affichageTV = etat.affichage;
    this.choisiTV = etat.choisi;
    this.rendre();
    if (phrases.length && bandeauVisible(etat)) this.parler(phrases.join(". "));
  }

  /** Région polie du bandeau (vidée d'abord : une même phrase est relue). */
  parler(texte) {
    const a = this.el.annonce;
    a.textContent = "";
    setTimeout(() => { a.textContent = texte; }, 60);
  }

  // ---------- Envois ----------

  /** Segment touché : `nba_afficher` (avec le match affiché ici, sauf pour cacher). */
  afficher(affichage) {
    if (!AFFICHAGES.includes(affichage)) return;
    this.vibrer();
    if (!this.ctrl.nbaAfficher(champsNbaAfficher(affichage, this.matchAffiche()?.id))) {
      this.annoncer("La TV n'est pas connectée", { erreur: true });
      this.rendre(); // segments recalés sur la TV
      return;
    }
    journal("nba_afficher", affichage);
    this.attentes.noter("affichage", affichage);
    this.rendre();
    this.planifierRecalage();
  }

  /** Match touché dans la feuille : `nba_choisir`. */
  choisir(id) {
    this.vibrer();
    this.fermerFeuille({ rendreFocus: true });
    if (id === this.matchAffiche()?.id) return;
    if (!this.ctrl.nbaChoisir(id)) {
      this.annoncer("La TV n'est pas connectée", { erreur: true });
      return;
    }
    this.attentes.noter("choisi", id);
    this.rendre();
    this.planifierRecalage();
  }

  /** Sans réponse conforme de la TV : retour à son état à l'échéance. */
  planifierRecalage() {
    clearTimeout(this._recalage);
    const d = Math.max(this.attentes.restant("affichage"), this.attentes.restant("choisi"));
    this._recalage = setTimeout(() => this.rendre(), d + 20);
  }

  // ---------- Affichage ----------

  /** Format affiché : celui envoyé tant que la TV ne l'a pas confirmé (1,5 s au plus), sinon celui de la TV. */
  affichageAffiche() {
    const tv = this.etat?.affichage ?? null;
    return this.attentes.accepter("affichage", tv) ? tv : this.attentes.liste.get("affichage").valeur;
  }

  /** Match affiché : celui choisi ici en attente de confirmation, sinon celui de la TV. */
  matchAffiche() {
    if (!this.etat) return null;
    const tv = this.etat.choisi;
    const id = this.attentes.accepter("choisi", tv) ? tv : this.attentes.liste.get("choisi").valeur;
    return matchDe(this.etat, id) ?? matchDe(this.etat);
  }

  rendre() {
    const e = this.el;
    const visible = this.disponible && bandeauVisible(this.etat);
    e.bandeau.hidden = !visible;
    if (!visible) {
      this.fermerFeuille();
      return;
    }
    const match = this.matchAffiche();
    e.score.hidden = e.statut.hidden = !match;
    e.aucun.hidden = !!match;
    e.lecteur.textContent = match ? texteMatch(match) : "";
    if (match) this.rendreMatch(match);

    const plusieurs = this.etat.matchs.length > 1;
    e.matchs.hidden = !plusieurs;
    if (!plusieurs) this.fermerFeuille();
    else if (!e.feuille.hidden) this.rendreListe();

    const erreur = this.etat.erreur ? ERREURS[this.etat.erreur] : "";
    e.erreur.hidden = !erreur;
    e.erreurTexte.textContent = erreur;

    const i = AFFICHAGES.indexOf(this.affichageAffiche());
    e.radios.forEach((r, j) => {
      r.checked = i === j;
      r.closest(".segment").classList.toggle("choisi", i === j);
    });
    if (i >= 0) {
      e.segments.style.setProperty("--i", String(i));
      e.segments.dataset.choix = AFFICHAGES[i];
    } else delete e.segments.dataset.choix;
  }

  rendreMatch(match) {
    const e = this.el;
    peindreBadge(e.badgeExt, match.ext);
    peindreBadge(e.badgeDom, match.dom);
    const menee = equipeMenee(match);
    const avant = this.scores?.id === match.id ? this.scores : null;
    for (const [cote, el] of [["ext", e.pointsExt], ["dom", e.pointsDom]]) {
      el.textContent = String(match[cote].score);
      el.classList.toggle("menee", menee === cote);
      if (avant && avant[cote] !== match[cote].score) {
        el.classList.remove("eclat");
        void el.offsetWidth; // relance l'animation
        el.classList.add("eclat");
        setTimeout(() => el.classList.remove("eclat"), ECLAT_MS);
      }
    }
    this.scores = { id: match.id, ext: match.ext.score, dom: match.dom.score };
    const s = ligneStatut(match);
    e.direct.textContent = s.etiquette;
    e.direct.classList.toggle("termine", !s.direct);
    e.statutTexte.textContent = s.statut;
    e.statutTexte.hidden = !s.statut;
  }

  // ---------- Feuille « Matchs en direct » ----------

  ouvrirFeuille() {
    if (!this.etat || this.etat.matchs.length < 2) return;
    this.rendreListe();
    this.el.feuille.hidden = false;
    (this.el.liste.querySelector('[aria-current="true"]') || this.el.liste.querySelector("button"))?.focus({ preventScroll: true });
  }

  fermerFeuille({ rendreFocus = false } = {}) {
    if (this.el.feuille.hidden) return;
    this.el.feuille.hidden = true;
    if (rendreFocus && !this.el.matchs.hidden && !this.el.bandeau.hidden) this.el.matchs.focus({ preventScroll: true });
  }

  /** Liste des matchs : mise à jour sur place (focus du lecteur d'écran gardé), reconstruite si les matchs changent. */
  rendreListe() {
    const matchs = this.etat?.matchs || [];
    const actuel = this.matchAffiche()?.id;
    const sig = matchs.map((m) => m.id).join(" ");
    if (sig !== this.sigListe) {
      const focus = this.el.liste.contains(document.activeElement) ? document.activeElement.dataset.id : null;
      this.sigListe = sig;
      this.lignes.clear();
      this.el.liste.replaceChildren(...matchs.map((m) => h("li", {}, this.construireLigne(m))));
      if (focus) this.lignes.get(focus)?.btn.focus({ preventScroll: true });
    }
    for (const m of matchs) this.lignes.get(m.id)?.maj(m, actuel);
  }

  construireLigne(match) {
    const ext = badge(match.ext), dom = badge(match.dom);
    const noms = h("span", { class: "match-noms" });
    const etiquette = h("span", { class: "nba-direct" });
    const statut = h("span", { class: "match-statut" });
    const score = h("span", { class: "match-score", "aria-hidden": "true" });
    const btn = h("button", { type: "button", class: "match-choix", "data-id": match.id },
      h("span", { class: "match-badges", "aria-hidden": "true" }, ext, dom),
      h("span", { class: "match-textes" }, noms, h("span", { class: "match-detail", "aria-hidden": "true" }, etiquette, statut)),
      score);
    btn.addEventListener("click", () => this.choisir(match.id));
    const maj = (m, actuel) => {
      peindreBadge(ext, m.ext);
      peindreBadge(dom, m.dom);
      noms.textContent = nomsMatch(m);
      const s = ligneStatut(m);
      etiquette.textContent = s.etiquette;
      etiquette.classList.toggle("termine", !s.direct);
      statut.textContent = s.statut;
      score.textContent = `${m.ext.score} – ${m.dom.score}`;
      btn.setAttribute("aria-label", texteMatch(m));
      btn.setAttribute("aria-current", String(m.id === actuel));
    };
    this.lignes.set(match.id, { btn, maj });
    return btn;
  }
}
