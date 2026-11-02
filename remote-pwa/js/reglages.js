// Onglet « Réglages » : rendu GÉNÉRIQUE de tout réglage décrit par la TV (curseur, interrupteur,
// choix, boutons), dans l'ordre reçu, en sections par `groupe`. Aucun réglage n'est codé en dur.
// Les `reglages` reçus mettent l'affichage à jour sur place (défilement et glissé préservés) ;
// il n'est reconstruit que si la structure change (et jamais pendant un glissé).

import { grouperReglages, signatureReglages, proportion, limiteur, Attentes } from "./pilotage.js";
import { h } from "./dom.js";
import { journal } from "./journal.js";

const ATTENTE_LISTE_MS = 8000;
const FIN_GLISSE_MS = 700;        // sans pointeur (clavier…) : fin du glissé après ce délai sans saisie
const FIN_GLISSE_DOIGT_MS = 5000; // doigt posé : garde-fou si le relâcher n'est pas signalé
// iOS 17.4+ : interrupteur natif (<input type=checkbox switch>, retour haptique) ; ailleurs, dessiné en CSS
const SWITCH_NATIF = typeof HTMLInputElement !== "undefined" && "switch" in HTMLInputElement.prototype;

export class OngletReglages {
  /**
   * @param {object} o
   * @param {import("./controleur.js").Controleur} o.ctrl
   * @param {(texte:string, o?:{erreur?:boolean}) => void} o.annoncer
   * @param {(ms?:number|number[]) => void} o.vibrer
   */
  constructor({ ctrl, annoncer, vibrer }) {
    Object.assign(this, { ctrl, annoncer, vibrer });
    this.defile = document.getElementById("reglages-defile");
    this.liste = document.getElementById("reglages-liste");
    this.message = document.getElementById("reglages-message");
    this.texteMessage = document.getElementById("reglages-message-texte");
    this.btnReessayer = document.getElementById("reglages-reessayer");
    this.visible = false;
    this.items = null;
    this.sig = null;
    this.controles = new Map(); // cle → { maj(item) }
    this.attentes = new Attentes();
    this.glisse = null;         // cle du curseur sous le doigt
    this.doigt = false;         // pointeur posé sur un curseur
    this.differe = false;       // structure changée pendant un glissé : à reconstruire au relâcher
    this.installation = new Set(); // réglages dont « installer » a été touché (cette connexion)
    this.n = 0;                 // identifiants uniques des éléments

    this.btnReessayer.addEventListener("click", () => { this.vibrer(); this.demander(); });
    ctrl.addEventListener("reglages", (e) => this.recevoir(e.detail.items));
    // Relâcher : après l'événement `change` du curseur (valeur finale envoyée avant toute reconstruction)
    const fin = () => { if (this.glisse) setTimeout(() => { if (this.glisse) this.finGlisse(); }, 60); };
    addEventListener("pointerup", fin);
    addEventListener("pointercancel", fin);
  }

  // ---------- Cycle de vie ----------

  changerTV() {
    this.items = null;
    this.sig = null;
    this.controles.clear();
    this.liste.replaceChildren();
    this.installation.clear();
    this.attentes.vider();
  }

  afficher() {
    this.visible = true;
    if (!this.items) this.montrerSquelette();
    this.demander();
  }

  masquer() { this.visible = false; }

  surConnexion() {
    this.installation.clear();
    this.attentes.vider();
    // Abonnement valable pour une connexion : renouvelé si l'onglet est ouvert
    if (this.visible) this.demander();
  }

  /** `reglages_liste` : état complet, et abonnement aux changements pendant cette connexion. */
  demander() {
    clearTimeout(this._minuterie);
    this.btnReessayer.hidden = true;
    if (!this.ctrl.reglagesListe()) {
      if (!this.items) this.montrerMessage("La TV n'est pas connectée. Les réglages s'afficheront dès qu'elle répondra.");
      return;
    }
    this._minuterie = setTimeout(() => {
      if (!this.items && this.visible) this.montrerMessage("La TV ne répond pas.", true);
    }, ATTENTE_LISTE_MS);
  }

  // ---------- Réception ----------

  recevoir(items) {
    clearTimeout(this._minuterie);
    this.items = items;
    this.appliquer();
  }

  appliquer() {
    if (!this.items) return;
    const sig = signatureReglages(this.items);
    if (sig === this.sig) {
      for (const it of this.items) this.controles.get(it.cle)?.maj(it);
      return;
    }
    if (this.glisse) { this.differe = true; return; }
    this.reconstruire(sig);
  }

  reconstruire(sig) {
    this.message.hidden = true;
    this.liste.setAttribute("aria-busy", "false");
    if (!this.items.length) {
      this.sig = sig;
      this.controles.clear();
      this.liste.replaceChildren();
      return this.montrerMessage("Aucun réglage disponible sur cette TV.");
    }
    const haut = this.defile.scrollTop;
    const focus = document.activeElement?.dataset?.focus;
    this.controles.clear();
    const sections = grouperReglages(this.items).map(({ groupe, items }) => {
      const idTitre = `rg-${++this.n}`;
      const carte = h("div", { class: "groupe-carte" });
      for (const it of items) {
        const c = this.construire(it);
        this.controles.set(it.cle, c);
        carte.append(c.el);
        c.maj(it);
      }
      return h("section", { class: "groupe", "aria-labelledby": groupe ? idTitre : null },
        groupe && h("h3", { class: "groupe-titre", id: idTitre }, groupe), carte);
    });
    this.sig = sig;
    this.liste.replaceChildren(...sections);
    this.defile.scrollTop = haut;
    if (focus) this.liste.querySelector(`[data-focus="${CSS.escape(focus)}"]`)?.focus({ preventScroll: true });
  }

  montrerMessage(texte, reessayer = false) {
    this.liste.replaceChildren();
    this.controles.clear();
    this.sig = null;
    this.liste.setAttribute("aria-busy", "false");
    this.texteMessage.textContent = texte;
    this.btnReessayer.hidden = !reessayer;
    this.message.hidden = false;
  }

  montrerSquelette() {
    this.message.hidden = true;
    this.liste.setAttribute("aria-busy", "true");
    const ligne = () => h("div", { class: "reglage" }, h("div", { class: "reglage-tete" },
      h("span", { class: "reglage-textes" },
        h("span", { class: "squelette barre-squelette" }),
        h("span", { class: "squelette barre-squelette courte" }))));
    const section = (n) => h("section", { class: "groupe", "aria-hidden": "true" },
      h("span", { class: "squelette barre-squelette titre" }),
      h("div", { class: "groupe-carte" }, ...Array.from({ length: n }, ligne)));
    this.liste.replaceChildren(section(2), section(3));
  }

  // ---------- Envois ----------

  /** Envoie `reglage` ; la valeur affichée est gardée jusqu'à la réponse de la TV. */
  envoyer(cle, action) {
    if ("valeur" in action) this.attentes.noter(cle, action.valeur);
    if (this.ctrl.reglage(cle, action)) return true;
    this.attentes.liste.delete(cle);
    this.annoncer("La TV n'est pas connectée", { erreur: true });
    this.recaler(cle);
    return false;
  }

  /** Réaffiche la dernière valeur connue de la TV (envoi impossible, ou réponse différente). */
  recaler(cle, delai = 0) {
    clearTimeout(this._recalages?.get(cle));
    this._recalages ??= new Map();
    this._recalages.set(cle, setTimeout(() => {
      const it = this.items?.find((i) => i.cle === cle);
      if (it && this.glisse !== cle) this.controles.get(cle)?.maj(it);
    }, delai));
  }

  /**
   * Valeur à afficher : celle de la TV, sauf si une valeur envoyée attend encore sa confirmation
   * (réponse en retard d'un envoi précédent) ; recalage sur la TV à l'échéance.
   */
  valeurAffichee(it) {
    if (this.attentes.accepter(it.cle, it.valeur)) return it.valeur;
    this.recaler(it.cle, this.attentes.restant(it.cle) + 20);
    return this.attentes.liste.get(it.cle).valeur;
  }

  finGlisse() {
    clearTimeout(this._finGlisse);
    this.glisse = null;
    this.doigt = false;
    if (this.differe) { this.differe = false; this.appliquer(); }
  }

  // ---------- Construction ----------

  construire(item) {
    const id = ++this.n;
    const ids = { titre: `r-${id}-titre`, detail: `r-${id}-detail` };
    const titre = h("span", { class: "reglage-titre", id: ids.titre });
    const detail = h("span", { class: "reglage-detail", id: ids.detail });
    const textes = h("span", { class: "reglage-textes" }, titre, detail);
    const c = this[item.type](item, ids, textes);
    const majControle = c.maj;
    c.maj = (it) => {
      titre.textContent = it.titre;
      titre.hidden = !it.titre;
      detail.textContent = it.detail;
      detail.hidden = !it.detail;
      c.el.classList.toggle("inactif", !it.actif);
      majControle(it);
    };
    c.el.dataset.cle = item.cle;
    c.el.dataset.type = item.type;
    return c;
  }

  curseur(item, ids, textes) {
    let cle = item.cle, min = item.min, max = item.max;
    const sortie = h("span", { class: "reglage-valeur", "aria-hidden": "true" });
    const range = h("input", {
      type: "range", class: "curseur", min, max, step: 1,
      "aria-labelledby": ids.titre, "aria-describedby": ids.detail, "data-focus": `${cle}`,
    });
    // Valeur affichée en % de la plage (le type `curseur` n'a pas d'unité)
    const peindre = (v) => {
      const p = proportion(v, min, max);
      sortie.textContent = `${Math.round(p * 100)} %`;
      range.setAttribute("aria-valuetext", sortie.textContent);
      range.style.setProperty("--pct", `${(p * 100).toFixed(2)}%`);
    };
    // Pendant le glissé : un envoi toutes les 150 ms au plus, puis la valeur finale au relâcher
    const lim = limiteur((v) => this.envoyer(cle, { valeur: v }));
    const glisser = () => {
      this.glisse = cle;
      clearTimeout(this._finGlisse);
      this._finGlisse = setTimeout(() => this.finGlisse(), this.doigt ? FIN_GLISSE_DOIGT_MS : FIN_GLISSE_MS);
    };
    range.addEventListener("pointerdown", () => { this.doigt = true; glisser(); });
    range.addEventListener("input", () => {
      const v = Number(range.value);
      peindre(v);
      glisser();
      lim.appeler(v);
    });
    range.addEventListener("change", () => {
      // La TV a pu changer la valeur pendant le glissé (réponse ignorée) : on la recale au relâcher
      const v = Number(range.value);
      lim.terminer(v, this.items?.find((i) => i.cle === cle)?.valeur !== v);
      journal("reglage", cle);
      this.finGlisse();
    });
    const el = h("div", { class: "reglage" }, h("div", { class: "reglage-tete" }, textes),
      h("div", { class: "curseur-ligne" }, range, sortie));
    return {
      el,
      maj: (it) => {
        cle = it.cle; min = it.min; max = it.max;
        range.disabled = !it.actif;
        if (this.glisse === it.cle) return; // ne pas contrarier le doigt
        const v = this.valeurAffichee(it);
        range.value = String(v);
        peindre(v);
      },
    };
  }

  interrupteur(item, ids, textes) {
    const cle = item.cle;
    const input = h("input", {
      type: "checkbox", switch: true, class: SWITCH_NATIF ? "interrupteur natif" : "interrupteur",
      role: SWITCH_NATIF ? null : "switch",
      "aria-labelledby": ids.titre, "aria-describedby": ids.detail, "data-focus": cle,
    });
    input.addEventListener("change", () => {
      this.vibrer();
      journal("reglage", cle);
      this.envoyer(cle, { valeur: input.checked });
    });
    // Toute la ligne est cliquable (label)
    const el = h("label", { class: "reglage reglage-ligne" }, textes, input);
    return {
      el,
      maj: (it) => {
        input.disabled = !it.actif;
        input.checked = this.valeurAffichee(it);
      },
    };
  }

  choix(item, ids, textes) {
    const cle = item.cle;
    const options = item.options;
    // Pastilles ; options avec détail : cartes (nom + détail).
    // Seul cas propre à une clé : le design de l'accueil, en cartes avec un aperçu coloré par id.
    const design = cle === "design";
    const mode = design ? "designs" : options.some((o) => o.detail) ? "cartes" : "pastilles";
    const nom = `r-${++this.n}-choix`;
    const groupe = h("div", {
      class: `options options-${mode}`, role: "radiogroup",
      "aria-labelledby": ids.titre, "aria-describedby": ids.detail,
    });
    const radios = options.map((o, i) => {
      const input = h("input", { type: "radio", name: nom, value: String(i), "data-focus": `${cle}:${i}` });
      const label = h("label", { class: "option" }, input,
        design && h("span", { class: "apercu-design", "data-id": String(o.id), "aria-hidden": "true" }),
        h("span", { class: "option-nom" }, o.nom),
        o.detail && h("span", { class: design ? "invisible-lecteur" : "option-detail" }, o.detail));
      input.addEventListener("change", () => {
        if (!input.checked) return;
        this.vibrer();
        cocher(i);
        journal("reglage", cle);
        this.envoyer(cle, { valeur: o.id });
      });
      groupe.append(label);
      return { input, label };
    });
    const cocher = (i) => radios.forEach((r, j) => {
      r.input.checked = i === j;
      r.label.classList.toggle("choisie", i === j);
    });
    const el = h("div", { class: "reglage" }, h("div", { class: "reglage-tete" }, textes), groupe);
    return {
      el,
      maj: (it) => {
        for (const r of radios) r.input.disabled = !it.actif;
        const v = this.valeurAffichee(it);
        cocher(options.findIndex((o) => o.id === v));
      },
    };
  }

  boutons(item, ids, textes) {
    const cle = item.cle;
    const barre = h("span", { class: "progression-barre" });
    const progression = h("div", {
      class: "progression", role: "progressbar", "aria-labelledby": ids.titre,
      "aria-valuemin": 0, "aria-valuemax": 100, hidden: true,
    }, barre);
    // Côte à côte, dans l'ordre reçu ; le principal en dégradé
    const boutons = item.boutons.map((b) => {
      const btn = h("button", { type: "button", class: b.principal ? "btn-reglage principal" : "btn-reglage" }, b.nom);
      btn.addEventListener("click", () => {
        this.vibrer();
        journal("reglage", cle);
        if (!this.envoyer(cle, { bouton: b.id })) return;
        btn.classList.add("envoye");
        setTimeout(() => btn.classList.remove("envoye"), 900);
        if (b.id === "installer") { this.installation.add(cle); majNote(); }
      });
      return btn;
    });
    // Installation lancée : Android la fait confirmer sur l'écran de la TV (touche OK)
    const note = h("p", { class: "reglage-note", role: "note", hidden: true },
      "La TV va demander de confirmer l'installation : appuyez sur OK avec la télécommande.");
    const majNote = () => { note.hidden = !this.installation.has(cle); };
    const tete = h("div", { class: "reglage-tete" }, textes);
    const el = h("div", { class: "reglage" }, tete, progression, note,
      boutons.length > 0 && h("div", { class: "boutons-reglage" }, ...boutons));
    return {
      el,
      maj: (it) => {
        tete.hidden = !it.titre && !it.detail;
        for (const b of boutons) b.disabled = !it.actif;
        const p = it.progression;
        progression.hidden = p === undefined;
        if (p !== undefined) {
          const pc = Math.round(p * 100);
          barre.style.width = `${pc}%`;
          progression.setAttribute("aria-valuenow", String(pc));
        }
        majNote();
      },
    };
  }
}
