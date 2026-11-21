// Box score NBA : logique pure, sans DOM, testée sous Node.
// Messages : docs/telecommande.md, « Box score NBA ».
// Le téléphone choisit le format du box score sur la TV (caché, résumé, détails, et pari si la TV
// annonce « nba_paris ») et le match ; la TV envoie l'état (`nba`) : matchs en cours, match choisi,
// format, décalage, erreur, les lignes des paris suivis (`paris`) et, si elle annonce
// « nba_paris_groupes », les combinés qui les regroupent (`groupes`).

export const AFFICHAGES = Object.freeze(["cache", "resume", "details", "pari"]);
export const NOMS_AFFICHAGE = Object.freeze({ cache: "Caché", resume: "Résumé", details: "Détails", pari: "Pari" });
/** Formats proposés : « pari » seulement si la TV annonce « nba_paris ». */
export const affichagesProposes = (paris) => (paris ? AFFICHAGES : AFFICHAGES.slice(0, 3));
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
 * Message `nba` → état normalisé {matchs, choisi, affichage, decalage, erreur?, paris?, groupes?}, ou
 * null (`matchs` absent). Matchs invalides ou en double écartés, 15 au plus. `choisi` : un match de la
 * liste (sinon le premier en cours, comme la TV ; null sans match). `affichage` inconnu → null (aucun
 * segment coché : la TV montre un format que cette version ne connaît pas). Erreur inconnue ignorée.
 * `paris` : seulement si la TV l'envoie (tableau), voir normaliserParis ; 40 lignes au plus avec des
 * combinés (`groupes`, voir normaliserCombines), 20 sinon.
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
  const groupes = normaliserCombines(m.groupes);
  const paris = normaliserParis(m.paris, groupes ? MAX_LIGNES_COMBINES : MAX_PARIS);
  if (paris) e.paris = paris;
  if (groupes) e.groupes = groupes;
  return e;
}

// ---------- Paris suivis (« nba_paris ») ----------
// Le téléphone colle le texte d'un pari ; la TV en tire les lignes qu'elle sait suivre et les
// renvoie dans `nba.paris` avec leur état et leur valeur (au décalage près).

export const ETATS_PARI = Object.freeze(["attente", "en_cours", "gagne", "perdu", "non_suivi"]);
export const NOMS_ETAT_PARI = Object.freeze({
  attente: "En attente", en_cours: "En cours", gagne: "Gagné", perdu: "Perdu", non_suivi: "Non suivi",
});
export const MAX_PARIS = 20;
export const MAX_TEXTE_PARI = 4000;
const MAX_VALEUR = 100000;
const nombre = (x) => (typeof x === "number" && Number.isFinite(x) && Math.abs(x) <= MAX_VALEUR ? x : undefined);

/** Identifiant reçu de la TV (ligne « p1 », combiné « g1 ») : chaîne de 1 à 64 caractères, renvoyée telle quelle. */
const idValable = (x) => str(x) && x.length > 0 && x.length <= 64;

/**
 * Ligne `{id, libelle, etat, groupe?, match?, valeur?, seuil?}` → forme normalisée, ou null (id ou
 * libellé absents). État inconnu → "attente" (ligne montrée, sans conclure) ; groupe, match, valeur,
 * seuil invalides ignorés.
 */
export function normaliserPari(p) {
  if (!p || typeof p !== "object" || !idValable(p.id)) return null;
  const libelle = nettoyer(p.libelle, 160);
  if (!libelle) return null;
  const l = { id: p.id, libelle, etat: ETATS_PARI.includes(p.etat) ? p.etat : "attente" };
  if (idValable(p.groupe)) l.groupe = p.groupe;
  if (str(p.match) && RE_ID.test(p.match)) l.match = p.match;
  const valeur = nombre(p.valeur), seuil = nombre(p.seuil);
  if (valeur !== undefined) l.valeur = valeur;
  if (seuil !== undefined) l.seuil = seuil;
  return l;
}

/**
 * `paris` → lignes normalisées (invalides ou en double écartées, 20 au plus, ou `max`), ou null si ce
 * n'est pas un tableau.
 */
export function normaliserParis(liste, max = MAX_PARIS) {
  if (!Array.isArray(liste)) return null;
  const vus = new Set();
  const paris = [];
  for (const x of liste) {
    if (paris.length >= max) break;
    const p = normaliserPari(x);
    if (!p || vus.has(p.id)) continue;
    vus.add(p.id);
    paris.push(p);
  }
  return paris;
}

/**
 * Texte collé → texte de `nba_paris_ajouter` : fins de ligne unifiées, blancs autour retirés,
 * 4000 caractères au plus (sans couper un caractère en deux) ; "" s'il ne reste rien.
 */
export function texteAEnvoyer(s) {
  if (!str(s)) return "";
  let t = s.replace(/\r\n?/g, "\n").trim();
  if (t.length > MAX_TEXTE_PARI) {
    t = t.slice(0, MAX_TEXTE_PARI);
    if (/[\ud800-\udbff]$/.test(t)) t = t.slice(0, -1);
    t = t.trimEnd();
  }
  return t;
}

/** 19.5 → « 19,5 », 12 → « 12 », -3 → « −3 » (une décimale au plus, virgule française). */
export function nombreFr(x) {
  const r = Math.round(x * 10) / 10;
  const t = String(Math.abs(r)).replace(".", ",");
  return r < 0 ? `\u2212${t}` : t;
}

/** Valeur affichée : « 12 / 19,5 » (avec un seuil), écart du vainqueur « +2 » / « −3 » / « 0 », sinon "". */
export function valeurPari(p) {
  if (p.valeur === undefined) return "";
  if (p.seuil !== undefined) return `${nombreFr(p.valeur)} / ${nombreFr(p.seuil)}`;
  return p.valeur > 0 ? `+${nombreFr(p.valeur)}` : nombreFr(p.valeur);
}

/** Avancement vers le seuil (barre), entre 0 et 1 ; null sans valeur ou sans seuil. */
export function avancementPari(p) {
  if (p.valeur === undefined || p.seuil === undefined || p.seuil <= 0) return null;
  return Math.min(1, Math.max(0, p.valeur / p.seuil));
}

/** « PHI – IND » (codes du match de la ligne), ou "". */
export const matchPari = (p) => (p.match ? p.match.replace("-", " – ") : "");

const points = (n) => `${nombreFr(n)} ${Math.abs(n) >= 2 ? "points" : "point"}`;

/** Lecteur d'écran : « T. Maxey · + de 19,5 pts : en cours, 12 sur 19,5 » ; « 76ers vainqueurs : en cours, mène de 4 points ». */
export function textePari(p) {
  let v = "";
  if (p.valeur !== undefined && p.seuil !== undefined) v = `${nombreFr(p.valeur)} sur ${nombreFr(p.seuil)}`;
  else if (p.valeur !== undefined) v = p.valeur > 0 ? `mène de ${points(p.valeur)}` : p.valeur < 0 ? `mené de ${points(-p.valeur)}` : "à égalité";
  return `${p.libelle} : ${NOMS_ETAT_PARI[p.etat].toLowerCase()}${v ? `, ${v}` : ""}`;
}

/**
 * Résumé des lignes (bandeau sans match, Réglages) : « 4 lignes en attente », « 1 ligne gagnée »,
 * « 2 gagnées · 1 perdue · 1 en cours » ; les lignes non suivies ne comptent que si elles sont seules
 * (« 2 lignes non suivies ») ; "" sans ligne.
 */
export function resumeParis(paris) {
  if (!paris.length) return "";
  const lignes = (k) => (k === 1 ? "1 ligne" : `${k} lignes`);
  const parts = [["gagne", "gagnée", "gagnées"], ["perdu", "perdue", "perdues"], ["en_cours", "en cours", "en cours"], ["attente", "en attente", "en attente"]]
    .map(([etat, un, plusieurs]) => {
      const k = paris.filter((p) => p.etat === etat).length;
      return { k, mot: k === 1 ? un : plusieurs };
    })
    .filter((x) => x.k);
  if (!parts.length) return `${lignes(paris.length)} ${paris.length === 1 ? "non suivie" : "non suivies"}`;
  if (parts.length === 1) return `${lignes(parts[0].k)} ${parts[0].mot}`;
  return parts.map((x) => `${x.k} ${x.mot}`).join(" · ");
}

/** Lignes apparues depuis un ajout (ids absents de `avant`) : {suivies, nonSuivies}. */
export function bilanAjout(avant, paris) {
  const b = { suivies: 0, nonSuivies: 0 };
  for (const p of paris) if (!avant.has(p.id)) b[p.etat === "non_suivi" ? "nonSuivies" : "suivies"]++;
  return b;
}

/** « 3 lignes suivies · 1 non suivie », « 1 ligne suivie », « Aucune ligne suivie · 2 non suivies », « Aucune nouvelle ligne ». */
export function messageAjout({ suivies, nonSuivies }) {
  const s = suivies === 1 ? "1 ligne suivie" : `${suivies} lignes suivies`;
  const n = nonSuivies === 1 ? "1 non suivie" : `${nonSuivies} non suivies`;
  if (suivies && nonSuivies) return `${s} · ${n}`;
  if (suivies) return s;
  if (nonSuivies) return `Aucune ligne suivie · ${n}`;
  return "Aucune nouvelle ligne";
}

// ---------- Combinés (« nba_paris_groupes ») ----------
// Chaque collage est un combiné : ses lignes (`groupe` de chaque ligne) restent groupées, en
// accordéon : un seul combiné ouvert, celui de la TV (`ouvert`).

export const ETATS_COMBINE = Object.freeze(["attente", "en_cours", "gagne", "perdu"]);
export const MAX_COMBINES = 10;
export const MAX_LIGNES_COMBINES = 40;
// Cote totale telle que lue sur le ticket (« 5,10 », « 10.06 ») : chiffres seulement
const RE_COTE = /^\d{1,5}(?:[.,]\d{1,3})?$/;
const compteur = (x) => (Number.isInteger(x) && x >= 0 && x <= MAX_LIGNES_COMBINES ? x : 0);

/**
 * Combiné `{id, nom, cote?, etat, gagnees, suivies, nonSuivies, ouvert}` → forme normalisée, ou null
 * (id absent). Nom absent → « Combiné » ; état inconnu → "attente" ; cote illisible ignorée ;
 * compteurs invalides → 0 (gagnées bornées aux suivies).
 */
export function normaliserCombine(g) {
  if (!g || typeof g !== "object" || !idValable(g.id)) return null;
  const cote = nettoyer(g.cote, 12);
  const suivies = compteur(g.suivies);
  return {
    id: g.id,
    nom: nettoyer(g.nom, 40) || "Combiné",
    ...(RE_COTE.test(cote) ? { cote } : {}),
    etat: ETATS_COMBINE.includes(g.etat) ? g.etat : "attente",
    gagnees: Math.min(compteur(g.gagnees), suivies),
    suivies,
    nonSuivies: compteur(g.nonSuivies),
    ouvert: g.ouvert === true,
  };
}

/**
 * `groupes` → combinés normalisés (invalides ou en double écartés, 10 au plus, ordre d'ajout ; un
 * seul ouvert : le premier marqué), ou null si ce n'est pas un tableau.
 */
export function normaliserCombines(liste) {
  if (!Array.isArray(liste)) return null;
  const vus = new Set();
  const groupes = [];
  let ouvert = false;
  for (const x of liste) {
    if (groupes.length >= MAX_COMBINES) break;
    const g = normaliserCombine(x);
    if (!g || vus.has(g.id)) continue;
    vus.add(g.id);
    if (g.ouvert && ouvert) g.ouvert = false;
    ouvert ||= g.ouvert;
    groupes.push(g);
  }
  return groupes;
}

/** Lignes d'un combiné, dans l'ordre d'ajout. */
export const lignesCombine = (paris, id) => paris.filter((p) => p.groupe === id);

/** Combiné ouvert sur la TV (son id), ou null. */
export const combineOuvert = (groupes) => groupes.find((g) => g.ouvert)?.id ?? null;

const texteNonSuivies = (n) => (n === 1 ? "1 non suivie" : `${n} non suivies`);
const lignesSuivies = (n) => (n === 1 ? "1 ligne suivie" : `${n} lignes suivies`);

/** « 2/5 » : lignes gagnées sur lignes suivies ; "" sans ligne suivie. */
export const compteCombine = (g) => (g.suivies ? `${g.gagnees}/${g.suivies}` : "");

/** Sous le nom : « En cours · cote 5,10 · 1 non suivie ». */
export function detailCombine(g) {
  return [NOMS_ETAT_PARI[g.etat], g.cote ? `cote ${g.cote}` : "", g.nonSuivies ? texteNonSuivies(g.nonSuivies) : ""]
    .filter(Boolean).join(" · ");
}

/** Lecteur d'écran : « Combiné 2, en cours, cote 5,10, 2 lignes gagnées sur 5, 1 non suivie ». */
export function texteCombine(g) {
  const parts = [g.nom, NOMS_ETAT_PARI[g.etat].toLowerCase()];
  if (g.cote) parts.push(`cote ${g.cote}`);
  if (g.suivies) parts.push(`${g.gagnees} ${g.gagnees >= 2 ? "lignes gagnées" : "ligne gagnée"} sur ${g.suivies}`);
  if (g.nonSuivies) parts.push(texteNonSuivies(g.nonSuivies));
  return parts.join(", ");
}

/** Résumé du bandeau et de Réglages : « 3 combinés », « 1 combiné » ; "" sans combiné. */
export const resumeCombines = (groupes) => (!groupes.length ? "" : groupes.length === 1 ? "1 combiné" : `${groupes.length} combinés`);

/** Combiné apparu depuis un ajout (id absent de `avant`) : le plus récent, ou null. */
export const nouveauCombine = (avant, groupes) => groupes.filter((g) => !avant.has(g.id)).at(-1) ?? null;

/**
 * Bilan d'un ajout : « Combiné 3 ajouté · 5 lignes suivies · 1 non suivie »,
 * « Combiné 3 ajouté · aucune ligne suivie · 2 non suivies » ; rien de nouveau : « Déjà ajouté ».
 */
export function messageAjoutCombine(g) {
  if (!g) return "Déjà ajouté";
  const parts = [`${g.nom} ajouté`, g.suivies ? lignesSuivies(g.suivies) : "aucune ligne suivie"];
  if (g.nonSuivies) parts.push(texteNonSuivies(g.nonSuivies));
  return parts.join(" · ");
}

// ---------- Messages ----------

/** Champs de `nba_afficher` : le format, et le match affiché sur le téléphone (sauf pour cacher). */
export function champsNbaAfficher(affichage, id = null) {
  const c = { affichage };
  if (affichage !== "cache" && str(id) && RE_ID.test(id)) c.id = id;
  return c;
}

// ---------- Affichage ----------

/** Box score dans le bandeau (score et formats) : au moins un match, ou un box score affiché sur la TV. */
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
    case "pari": return "Mon pari affiché sur la TV";
    default: return "";
  }
}
