// Box score NBA : logique pure, sans DOM, testée sous Node.
// Messages : docs/telecommande.md, « Box score NBA ».
// Le téléphone choisit le format du box score sur la TV (caché, résumé, détails) et le match ;
// la TV envoie l'état (`nba`) : matchs en cours, match choisi, format, décalage, erreur.

export const AFFICHAGES = Object.freeze(["cache", "resume", "details"]);
export const NOMS_AFFICHAGE = Object.freeze({ cache: "Caché", resume: "Résumé", details: "Détails" });
export const MAX_MATCHS = 15;
export const MAX_DECALAGE = 3600;
export const ERREURS = Object.freeze({
  accessibilite: "Active l'accessibilité d'iBiTV sur la TV",
  source: "Scores NBA indisponibles pour l'instant",
});

const str = (x) => typeof x === "string";
const CONTROLE = /[\u0000-\u001f\u007f-\u009f]/g;
/** Texte reçu : caractères de contrôle retirés, espaces réduits, longueur bornée. */
const nettoyer = (x, max) => (str(x) ? x.replace(CONTROLE, " ").replace(/\s+/g, " ").trim().slice(0, max) : "");

// `id` : "<EXT>-<DOM>" (codes NBA à 3 lettres), renvoyé tel quel à la TV
const RE_ID = /^[A-Za-z0-9]{2,5}-[A-Za-z0-9]{2,5}$/;
const RE_CODE = /^[A-Z0-9]{2,5}$/;
// Couleurs des badges : `#rrggbb` seulement (posées en propriétés CSS, jamais en texte de style)
const RE_COULEUR = /^#[0-9a-f]{6}$/i;

/** `#rrggbb` → forme en minuscules, sinon null (« red », `#fff`, `url(…)`, `#000;…` refusés). */
export const couleurValable = (x) => (str(x) && RE_COULEUR.test(x) ? x.toLowerCase() : null);

/** Lettres lisibles sur un fond (couleur reçue sans `texte` valable) : blanc ou presque noir, au meilleur contraste. */
export function texteLisible(fond) {
  const c = couleurValable(fond);
  if (!c) return null;
  const lin = (i) => {
    const v = parseInt(c.slice(i, i + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  };
  const l = 0.2126 * lin(1) + 0.7152 * lin(3) + 0.0722 * lin(5);
  // Contraste avec le blanc (1,05 / (l + 0,05)) contre le presque-noir #111111 (l + 0,05) / 0,0556
  return 1.05 / (l + 0.05) >= (l + 0.05) / 0.0556 ? "#ffffff" : "#111111";
}

/** Équipe `{code, nom, score, couleur, texte}` → forme normalisée, ou null (code ou score invalide). */
export function normaliserEquipe(e) {
  if (!e || typeof e !== "object") return null;
  const code = str(e.code) ? e.code.trim().toUpperCase() : "";
  if (!RE_CODE.test(code)) return null;
  if (!Number.isInteger(e.score) || e.score < 0 || e.score > 999) return null;
  // Couleur invalide : badge neutre (couleurs du thème) ; lettres invalides : déduites du fond
  const couleur = couleurValable(e.couleur);
  return {
    code,
    nom: nettoyer(e.nom, 40) || code,
    score: e.score,
    couleur,
    texte: couleur ? couleurValable(e.texte) ?? texteLisible(couleur) : null,
  };
}

/** Match `{id, ext, dom, statut, direct}` → forme normalisée, ou null. */
export function normaliserMatch(m) {
  if (!m || typeof m !== "object" || !str(m.id) || !RE_ID.test(m.id)) return null;
  const ext = normaliserEquipe(m.ext);
  const dom = normaliserEquipe(m.dom);
  if (!ext || !dom) return null;
  return { id: m.id, ext, dom, statut: nettoyer(m.statut, 40), direct: m.direct === true };
}

/**
 * Message `nba` → état normalisé {matchs, choisi, affichage, decalage, erreur?}, ou null (`matchs`
 * absent). Matchs invalides ou en double écartés, 15 au plus. `choisi` : un match de la liste
 * (sinon le premier en cours, comme la TV ; null sans match). `affichage` inconnu → null (aucun
 * segment coché : la TV montre un format que cette version ne connaît pas). Erreur inconnue ignorée.
 */
export function normaliserNba(m) {
  if (!m || typeof m !== "object" || !Array.isArray(m.matchs)) return null;
  const vus = new Set();
  const matchs = [];
  for (const x of m.matchs) {
    if (matchs.length >= MAX_MATCHS) break;
    const n = normaliserMatch(x);
    if (!n || vus.has(n.id)) continue;
    vus.add(n.id);
    matchs.push(n);
  }
  const choisi = vus.has(m.choisi) ? m.choisi : (matchs.find((x) => x.direct) ?? matchs[0])?.id ?? null;
  const e = {
    matchs,
    choisi,
    affichage: AFFICHAGES.includes(m.affichage) ? m.affichage : null,
    decalage: Number.isInteger(m.decalage) && m.decalage >= 0 && m.decalage <= MAX_DECALAGE ? m.decalage : 0,
  };
  if (str(m.erreur) && Object.hasOwn(ERREURS, m.erreur)) e.erreur = m.erreur;
  return e;
}

// ---------- Messages ----------

/** Champs de `nba_afficher` : le format, et le match affiché sur le téléphone (sauf pour cacher). */
export function champsNbaAfficher(affichage, id = null) {
  const c = { affichage };
  if (affichage !== "cache" && str(id) && RE_ID.test(id)) c.id = id;
  return c;
}

// ---------- Affichage ----------

/** Bandeau visible : au moins un match, ou un box score affiché sur la TV. */
export const bandeauVisible = (etat) => !!etat && (etat.matchs.length > 0 || etat.affichage !== "cache");

/** Match à afficher (celui de `choisi`), ou null. */
export const matchDe = (etat, id = etat?.choisi) => etat?.matchs.find((x) => x.id === id) ?? null;

/** Équipe menée (affichée en retrait) : "ext", "dom", ou null à égalité. */
export function equipeMenee(match) {
  const { ext, dom } = match;
  return ext.score === dom.score ? null : ext.score < dom.score ? "ext" : "dom";
}

/**
 * Ligne d'état : « DIRECT » + statut, ou « TERMINÉ » (le statut n'est répété que s'il dit autre
 * chose que « Terminé »).
 */
export function ligneStatut(match) {
  if (match.direct) return { direct: true, etiquette: "DIRECT", statut: match.statut };
  const statut = /^termin[ée]$/i.test(match.statut) ? "" : match.statut;
  return { direct: false, etiquette: "TERMINÉ", statut };
}

/** « Lakers – Celtics » */
export const nomsMatch = (match) => `${match.ext.nom} – ${match.dom.nom}`;

/** Lecteur d'écran : « Lakers – Celtics, 98 à 102, en direct, 4e QT · 2:31 ». */
export function texteMatch(match) {
  const s = ligneStatut(match);
  const etat = s.direct ? "en direct" : "terminé";
  return `${nomsMatch(match)}, ${match.ext.score} à ${match.dom.score}, ${etat}${s.statut ? `, ${s.statut}` : ""}`;
}

/** Annonce polie quand le format change sur la TV (jamais à chaque point marqué). */
export function annonceAffichage(affichage) {
  switch (affichage) {
    case "cache": return "Box score caché sur la TV";
    case "resume": return "Box score en résumé sur la TV";
    case "details": return "Box score en détails sur la TV";
    default: return "";
  }
}
