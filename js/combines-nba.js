// Box score NBA : combinés (si la TV annonce « nba_paris_groupes »). Dans la feuille « Mon pari »,
// les lignes sont rangées par combiné, en accordéon : un seul ouvert, celui de la TV (`ouvert`).
// En-tête de chaque combiné (bouton, `aria-expanded`) : état, nom, cote, « 2/5 », non suivies ;
// le toucher ouvre ce combiné (la feuille le demande à la TV : `nba_paris_ouvrir`) et replie les
// autres. Dans le combiné ouvert : ses lignes, puis « Effacer ce combiné ».
// Le combiné touché s'ouvre tout de suite ; sans confirmation de la TV (1,5 s), l'accordéon revient
// à celui de la TV. Flèches haut / bas, Début, Fin : d'un en-tête à l'autre.
// Aucun journal ici : la feuille écrit les seuls noms de messages.

import { lignesCombine, combineOuvert, compteCombine, detailCombine, texteCombine } from "./nba.js";
import { Attentes } from "./pilotage.js";
import { h, icone } from "./dom.js";

// Pastille d'état du combiné : ✓ gagné, ✗ perdu ; en cours (point) et en attente (anneau) en CSS
const ICONES_ETAT = { gagne: "i-ok", perdu: "i-croix" };

export class AccordeonCombines {
  /**
   * @param {object} o
   * @param {HTMLElement} o.liste liste des combinés (<ul>)
   * @param {(p:object) => {li:HTMLElement, maj:(p:object) => void}} o.construireLigne ligne d'un pari (comme la liste à plat)
   * @param {(id:string) => boolean} o.ouvrir en-tête touché : demande à la TV (faux : rien n'est parti)
   * @param {(g:object) => void} o.effacer « Effacer ce combiné » touché
   * @param {() => void} o.focusVide plus aucun combiné à qui rendre le focus
   */
  constructor({ liste, construireLigne, ouvrir, effacer, focusVide }) {
    Object.assign(this, { liste, construireLigne, ouvrir, effacer, focusVide });
    this.groupes = [];
    this.paris = [];
    this.attentes = new Attentes();  // combiné touché ici, ouvert tout de suite jusqu'à la réponse de la TV
    this.combines = new Map();       // id → { li, tete, effacer, maj(g, ouvert) }
    this.lignes = new Map();         // id de ligne → { li, maj(p) }
    this.ordre = [];                 // ids des combinés affichés
    this.sig = "";
    this.n = 0;                      // identifiants d'éléments (aria-controls), jamais ceux de la TV
    this._recalage = null;
    liste.addEventListener("keydown", (ev) => this.clavier(ev));
  }

  /** Combinés et lignes reçus de la TV. */
  maj(groupes, paris) {
    this.groupes = groupes;
    this.paris = paris;
    this.rendre();
  }

  vider() {
    clearTimeout(this._recalage);
    this.attentes.vider();
    this.groupes = [];
    this.paris = [];
    this.combines.clear();
    this.lignes.clear();
    this.ordre = [];
    this.sig = "";
    this.liste.replaceChildren();
  }

  /** Combiné ouvert à l'écran : celui touché ici tant que la TV ne l'a pas confirmé, sinon celui de la TV. */
  ouvertAffiche() {
    const tv = combineOuvert(this.groupes);
    if (this.attentes.accepter("ouvert", tv)) return tv;
    const id = this.attentes.liste.get("ouvert").valeur;
    return this.groupes.some((g) => g.id === id) ? id : tv;
  }

  /** En-tête touché, message parti : ce combiné s'ouvre (les autres se replient) en attendant la TV. */
  noterOuverture(id) {
    this.attentes.noter("ouvert", id);
    this.rendre();
    this.combines.get(id)?.tete.scrollIntoView({ block: "nearest" });
    clearTimeout(this._recalage);
    this._recalage = setTimeout(() => this.rendre(), this.attentes.restant("ouvert") + 20);
  }

  /** Nouveau combiné ajouté : la TV l'ouvre, un combiné touché avant n'est plus attendu. */
  oublierOuverture() {
    clearTimeout(this._recalage);
    this.attentes.vider();
  }

  /** Focus sur un élément d'un combiné (« tete » ou « effacer », s'il est visible) ; faux s'il n'y est plus. */
  focus(id, role = "tete") {
    const c = this.combines.get(id);
    if (!c) return false;
    const el = role === "effacer" && c.effacer.getClientRects().length ? c.effacer : c.tete;
    el.focus({ preventScroll: true });
    el.scrollIntoView({ block: "nearest" });
    return true;
  }

  // ---------- Rendu ----------

  /** Combinés mis à jour sur place (focus gardé) ; reconstruits si les combinés ou leurs lignes changent. */
  rendre() {
    const sig = this.groupes.map((g) => `${g.id}:${lignesCombine(this.paris, g.id).map((p) => p.id).join(",")}`).join("\n");
    const rendreFocus = sig !== this.sig ? this.reconstruire(sig) : null;
    const ouvert = this.ouvertAffiche();
    for (const g of this.groupes) this.combines.get(g.id)?.maj(g, g.id === ouvert);
    for (const p of this.paris) this.lignes.get(p.id)?.maj(p);
    rendreFocus?.();
  }

  /**
   * Liste reconstruite. Le focus qui y était revient au même élément du même combiné, ou, si ce
   * combiné est retiré, à l'en-tête voisin (sinon au titre de la feuille) : renvoyé pour après la
   * mise à jour (un combiné replié n'a plus de bouton « Effacer » visible).
   */
  reconstruire(sig) {
    const actif = this.liste.contains(document.activeElement) ? document.activeElement : null;
    const avant = actif?.closest(".combine")?.dataset.id;
    const role = actif?.dataset.role === "effacer" ? "effacer" : "tete";
    const index = this.ordre.indexOf(avant);
    this.sig = sig;
    this.combines.clear();
    this.lignes.clear();
    this.liste.replaceChildren(...this.groupes.map((g) => this.construireCombine(g)));
    this.ordre = this.groupes.map((g) => g.id);
    if (!actif) return null;
    return () => {
      if (this.focus(avant, role)) return;
      const voisin = this.ordre[Math.min(Math.max(index, 0), this.ordre.length - 1)];
      if (!voisin || !this.focus(voisin)) this.focusVide();
    };
  }

  construireCombine(g) {
    const k = ++this.n;
    const pastille = h("span", { class: "pari-etat", "aria-hidden": "true" });
    const nom = h("span", { class: "combine-nom" });
    const detail = h("span", { class: "combine-detail" });
    const compte = h("span", { class: "combine-compte", "aria-hidden": "true" });
    const tete = h("button", {
      type: "button", class: "combine-tete", id: `combine-${k}`, "aria-controls": `combine-${k}-corps`, "data-role": "tete",
    }, pastille, h("span", { class: "combine-textes", "aria-hidden": "true" }, nom, detail), compte, icone("i-chevron", "ic combine-chevron"));
    const lignes = lignesCombine(this.paris, g.id).map((p) => {
      const l = this.construireLigne(p);
      this.lignes.set(p.id, l);
      return l.li;
    });
    const effacer = h("button", { type: "button", class: "btn-effacer-combine", "data-role": "effacer" },
      icone("i-croix"), "Effacer ce combiné");
    const corps = h("div", { class: "combine-corps", id: `combine-${k}-corps`, role: "region", "aria-labelledby": `combine-${k}`, hidden: true },
      ...(lignes.length ? [h("ul", { class: "liste-pari combine-lignes" }, ...lignes)] : []), effacer);
    const li = h("li", { class: "combine", "data-id": g.id }, h("h3", { class: "combine-titre" }, tete), corps);

    let courant = g;
    let icn = null;
    // Le combiné ouvert ne se replie pas (la TV en garde toujours un ouvert) : toucher son en-tête ne fait rien
    tete.addEventListener("click", () => { if (tete.getAttribute("aria-expanded") !== "true") this.ouvrir(courant.id); });
    effacer.addEventListener("click", () => this.effacer(courant));
    const maj = (x, ouvert) => {
      courant = x;
      tete.dataset.etat = x.etat;
      if (icn !== x.etat) {
        icn = x.etat;
        pastille.replaceChildren(...(ICONES_ETAT[x.etat] ? [icone(ICONES_ETAT[x.etat])] : []));
      }
      nom.textContent = x.nom;
      detail.textContent = detailCombine(x);
      compte.textContent = compteCombine(x);
      compte.hidden = !compte.textContent;
      tete.setAttribute("aria-label", texteCombine(x));
      tete.setAttribute("aria-expanded", String(ouvert));
      if (ouvert) tete.setAttribute("aria-disabled", "true");
      else tete.removeAttribute("aria-disabled");
      li.classList.toggle("ouvert", ouvert);
      corps.hidden = !ouvert;
    };
    this.combines.set(g.id, { li, tete, effacer, maj });
    return li;
  }

  /** Flèches haut / bas, Début, Fin : en-tête précédent, suivant, premier, dernier. */
  clavier(ev) {
    const t = ev.target.closest?.(".combine-tete");
    if (!t || ev.altKey || ev.ctrlKey || ev.metaKey) return;
    const tetes = [...this.liste.querySelectorAll(".combine-tete")];
    const i = tetes.indexOf(t);
    const j = { ArrowDown: i + 1, ArrowUp: i - 1, Home: 0, End: tetes.length - 1 }[ev.key];
    if (j === undefined || j < 0 || j >= tetes.length) return;
    ev.preventDefault();
    tetes[j].focus();
  }
}
