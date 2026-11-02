// Pilotage depuis le téléphone (applis et réglages) : logique pure, sans DOM, testée sous Node.
// Messages : docs/telecommande.md, « Pilotage depuis le téléphone : applis et réglages ».

export const FONCTIONS = Object.freeze(["apps", "reglages"]);
export const TYPES_REGLAGE = Object.freeze(["curseur", "interrupteur", "choix", "boutons"]);
export const MAX_IDS_IMAGES = 100;
export const INTERVALLE_CURSEUR_MS = 150;

const str = (x) => typeof x === "string";
const texte = (x, max) => (str(x) ? x.trim().slice(0, max) : "");
const borner = (x, min, max) => Math.min(max, Math.max(min, x));
// Identifiant d'option : chaîne ou entier (renvoyé tel quel à la TV)
const estId = (x) => (str(x) && x.length > 0 && x.length <= 200) || Number.isInteger(x);

/** Fonctions annoncées par la TV dans son `hello` (absent = aucune). */
export function lireFonctions(f) {
  if (!Array.isArray(f)) return [];
  return FONCTIONS.filter((x) => f.includes(x));
}

// ---------- Applis ----------

/**
 * Liste `apps` : entrées invalides ou en double écartées ; favoris en premier
 * (ordre reçu conservé dans chaque partie). null si ce n'est pas une liste.
 */
export function normaliserApps(liste) {
  if (!Array.isArray(liste)) return null;
  const vus = new Set();
  const favoris = [], autres = [];
  for (const a of liste) {
    if (!a || typeof a !== "object" || !str(a.id) || !a.id || a.id.length > 255 || vus.has(a.id)) continue;
    vus.add(a.id);
    const app = {
      id: a.id,
      nom: texte(a.nom, 80) || a.id,
      favori: a.favori === true,
      maj: Number.isFinite(a.maj) ? Math.trunc(a.maj) : 0,
    };
    (app.favori ? favoris : autres).push(app);
  }
  return [...favoris, ...autres];
}

const RE_IMAGE = /^data:image\/(?:webp|png|jpeg);base64,[A-Za-z0-9+/]+={0,2}$/;

/** Message `app_image` → {id, maj, forme, img}, ou null s'il est invalide. */
export function normaliserImage(m) {
  if (!m || !str(m.id) || !m.id || !Number.isFinite(m.maj)) return null;
  if (m.forme !== "banniere" && m.forme !== "icone") return null;
  if (!str(m.img) || m.img.length > 65536 || !RE_IMAGE.test(m.img)) return null;
  return { id: m.id, maj: Math.trunc(m.maj), forme: m.forme, img: m.img };
}

/** Clé d'une image en cache : l'identifiant et la version (`maj`) de l'appli. */
export const cleImage = (id, maj) => `${id}|${maj}`;

/** Identifiants dont l'image n'est ni connue, ni déjà demandée (dans l'ordre de la liste). */
export function imagesADemander(liste, connues, demandees = new Set()) {
  const ids = [];
  for (const a of liste) {
    const c = cleImage(a.id, a.maj);
    if (!connues.has(c) && !demandees.has(c)) ids.push(a.id);
  }
  return ids;
}

/** Découpe en paquets de `n` au plus (`apps_images` : 100 identifiants par message). */
export function paquets(ids, n = MAX_IDS_IMAGES) {
  const r = [];
  for (let i = 0; i < ids.length; i += n) r.push(ids.slice(i, i + n));
  return r;
}

// ---------- Réglages ----------

function options(liste) {
  if (!Array.isArray(liste)) return [];
  const vus = new Set();
  const r = [];
  for (const o of liste) {
    if (!o || !estId(o.id) || vus.has(o.id)) continue;
    vus.add(o.id);
    const opt = { id: o.id, nom: texte(o.nom, 60) || String(o.id) };
    const d = texte(o.detail, 120);
    if (d) opt.detail = d;
    r.push(opt);
  }
  return r;
}

function boutons(liste) {
  if (!Array.isArray(liste)) return [];
  const vus = new Set();
  const r = [];
  for (const b of liste) {
    if (!b || !estId(b.id) || vus.has(b.id)) continue;
    vus.add(b.id);
    r.push({ id: b.id, nom: texte(b.nom, 40) || String(b.id), principal: b.principal === true });
  }
  return r;
}

/** Un réglage reçu → forme normalisée, ou null (type inconnu, champs manquants). */
export function normaliserReglage(o) {
  if (!o || typeof o !== "object" || !str(o.cle) || !o.cle || o.cle.length > 64) return null;
  if (!TYPES_REGLAGE.includes(o.type)) return null;
  const r = {
    cle: o.cle,
    type: o.type,
    groupe: texte(o.groupe, 60),
    titre: texte(o.titre, 80), // peut être vide (par ex. une rangée de boutons seule)
    detail: texte(o.detail, 240),
    actif: o.actif !== false,
  };
  switch (o.type) {
    case "curseur":
      if (!Number.isFinite(o.min) || !Number.isFinite(o.max)) return null;
      r.min = Math.round(o.min);
      r.max = Math.round(o.max);
      if (r.max <= r.min) return null;
      r.valeur = borner(Math.round(Number.isFinite(o.valeur) ? o.valeur : r.min), r.min, r.max);
      break;
    case "interrupteur":
      r.valeur = o.valeur === true;
      break;
    case "choix":
      r.options = options(o.options);
      if (!r.options.length) return null;
      r.valeur = r.options.some((x) => x.id === o.valeur) ? o.valeur : null;
      break;
    case "boutons":
      r.boutons = boutons(o.boutons);
      if (Number.isFinite(o.progression)) r.progression = borner(o.progression, 0, 1);
      break;
  }
  return r;
}

/** Liste `reglages.items` normalisée (doublons de clé écartés), ou null. */
export function normaliserReglages(items) {
  if (!Array.isArray(items)) return null;
  const vus = new Set();
  const r = [];
  for (const o of items) {
    const n = normaliserReglage(o);
    if (!n || vus.has(n.cle)) continue;
    vus.add(n.cle);
    r.push(n);
  }
  return r;
}

/**
 * Sections par `groupe`, dans l'ordre de première apparition ; ordre reçu conservé dans chacune
 * (un groupe qui réapparaît plus loin rejoint sa section).
 */
export function grouperReglages(items) {
  const sections = new Map();
  for (const it of items) {
    if (!sections.has(it.groupe)) sections.set(it.groupe, { groupe: it.groupe, items: [] });
    sections.get(it.groupe).items.push(it);
  }
  return [...sections.values()];
}

/**
 * Signature de la STRUCTURE (ce qui impose de reconstruire l'affichage) : ordre, groupes, types,
 * bornes, options et boutons. Valeurs, titres, détails, progression et `actif` se mettent à jour
 * sur place, sans toucher au défilement ni à un glissé en cours.
 */
export function signatureReglages(items) {
  return JSON.stringify(items.map((i) => [
    i.cle, i.type, i.groupe, i.min, i.max,
    i.options?.map((o) => [o.id, o.nom, o.detail ?? ""]),
    i.boutons?.map((b) => [b.id, b.nom, b.principal]),
  ]));
}

/** Valeur d'un curseur en proportion 0..1 (remplissage de la piste). */
export const proportion = (valeur, min, max) => (max > min ? borner((valeur - min) / (max - min), 0, 1) : 0);

// ---------- Envois ----------

const HORLOGE = {
  maintenant: () => Date.now(),
  plusTard: (f, ms) => setTimeout(f, ms),
  annuler: (t) => clearTimeout(t),
};

/**
 * Limiteur d'envoi pour un glissé : au plus un envoi toutes les `intervalleMs`
 * (le premier part tout de suite, la dernière valeur en attente part à l'échéance),
 * puis `terminer(v)` envoie la valeur finale sans attendre (sauf si c'est déjà la dernière envoyée
 * et que `forcer` est faux : `forcer` sert quand la TV a changé la valeur pendant le glissé).
 */
export function limiteur(envoyer, intervalleMs = INTERVALLE_CURSEUR_MS, horloge = HORLOGE) {
  let dernierEnvoi = -Infinity;
  let minuterie = null;
  let enAttente = false;
  let valeurAttente;
  let derniere;
  const partir = (v) => {
    dernierEnvoi = horloge.maintenant();
    enAttente = false;
    derniere = v;
    envoyer(v);
  };
  const arreter = () => {
    if (minuterie !== null) horloge.annuler(minuterie);
    minuterie = null;
    enAttente = false;
  };
  return {
    appeler(v) {
      const ecart = horloge.maintenant() - dernierEnvoi;
      if (minuterie === null && ecart >= intervalleMs) return partir(v);
      valeurAttente = v;
      enAttente = true;
      if (minuterie === null) {
        minuterie = horloge.plusTard(() => {
          minuterie = null;
          if (enAttente) partir(valeurAttente);
        }, intervalleMs - ecart);
      }
    },
    terminer(v, forcer = false) {
      arreter();
      if (forcer || v !== derniere) partir(v);
    },
    annuler: arreter,
  };
}

/**
 * Valeurs envoyées en attente de confirmation : la TV renvoie `reglages` après chaque `reglage`,
 * et les réponses aux envois intermédiaires (glissé) arrivent en retard. On garde la valeur choisie
 * à l'écran tant que la TV n'a pas renvoyé la même, ou jusqu'à l'échéance.
 */
export class Attentes {
  constructor(delaiMs = 1500, maintenant = () => Date.now()) {
    this.delaiMs = delaiMs;
    this.maintenant = maintenant;
    this.liste = new Map(); // cle → { valeur, jusqua }
  }
  noter(cle, valeur) { this.liste.set(cle, { valeur, jusqua: this.maintenant() + this.delaiMs }); }
  /** Faut-il afficher la valeur reçue de la TV pour ce réglage ? */
  accepter(cle, valeurRecue) {
    const a = this.liste.get(cle);
    if (!a) return true;
    if (a.valeur === valeurRecue || this.maintenant() >= a.jusqua) { this.liste.delete(cle); return true; }
    return false;
  }
  /** Délai restant avant l'échéance (0 si rien n'attend). */
  restant(cle) {
    const a = this.liste.get(cle);
    return a ? Math.max(0, a.jusqua - this.maintenant()) : 0;
  }
  vider() { this.liste.clear(); }
}
