// Envoyer sur la TV (liens) : logique pure, sans DOM, testée sous Node.
// Messages : docs/telecommande.md, « Envoyer sur la TV : liens ».

export const MAX_URL = 2048;
export const MAX_TITRE = 200;
export const MAX_RECENTS = 20;
export const DELAI_REPONSE_MS = 6000;
// Après `attente_vpn`, la TV attend le VPN 60 s au plus, puis répond (`ok` ou `delai_vpn`)
export const DELAI_ATTENTE_VPN_MS = 75000;
export const TEXTE_SANS_REPONSE = "La TV n'a pas confirmé l'envoi du lien";

const str = (x) => typeof x === "string";
const CONTROLE = /[\u0000-\u001f\u007f-\u009f]/g;
/** Texte reçu ou gardé : caractères de contrôle retirés, espaces réduits, longueur bornée. */
const nettoyer = (x, max) => (str(x) ? x.replace(CONTROLE, " ").replace(/\s+/g, " ").trim().slice(0, max) : "");

// ---------- Adresses ----------

/** URL `http://` ou `https://` avec un hôte, 2 048 caractères au plus → forme normalisée, sinon null. */
export function urlValable(texte) {
  if (!str(texte) || texte.length > MAX_URL) return null;
  let u;
  try { u = new URL(texte); } catch { return null; }
  if ((u.protocol !== "http:" && u.protocol !== "https:") || !u.hostname) return null;
  return u.href.length <= MAX_URL ? u.href : null;
}

// Domaine sans schéma : étiquettes séparées par des points, extension en lettres (« arte.tv/x »)
const RE_DOMAINE = /^(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,61}[\p{L}\p{N}])?\.)+(?:\p{L}{2,63}|xn--[a-z0-9-]{1,59})(?::\d{1,5})?(?:[/?#]\S*)?$/iu;
const RE_SCHEMA = /^[a-z][a-z0-9+.-]*:/i;
const RE_DANS_TEXTE = /https?:\/\/[^\s<>"«»]+/i;

/** Ponctuation collée à la fin d'une adresse citée dans un texte (« … https://a.fr/x. »). */
function rogner(u) {
  let s = u.replace(/[.,;:!?'»]+$/, "");
  while (s.endsWith(")") && (s.match(/\(/g) || []).length < (s.match(/\)/g) || []).length) s = s.slice(0, -1);
  return s;
}

/**
 * Saisie ou presse-papiers → URL à envoyer, ou null.
 * - adresse complète `http(s)://…` ;
 * - domaine sans schéma, complété en `https://` (`arte.tv/x` → `https://arte.tv/x`) ;
 * - texte partagé contenant une adresse complète (« Regarde : https://… ») : la première.
 * Tout autre schéma (`javascript:`, `file:`, `intent:`, `mailto:`…) est refusé.
 */
export function lireLien(texte) {
  if (!str(texte)) return null;
  const t = texte.trim();
  if (!t || t.length > 2 * MAX_URL) return null;
  if (!/\s/.test(t)) {
    if (/^https?:\/\//i.test(t)) return urlValable(t);
    if (!RE_DOMAINE.test(t)) return null; // autre schéma, adresse e-mail, mot seul…
    return urlValable(`https://${t}`);
  }
  const m = t.match(RE_DANS_TEXTE);
  return m ? urlValable(rogner(m[0])) : null;
}

/** Hôte affiché : sans « www. ». */
export function hoteDe(url) {
  try { return new URL(url).hostname.replace(/^www\./, ""); } catch { return ""; }
}

const decoder = (s, f = decodeURI) => { try { return f(s); } catch { return s; } };

/** Adresse lisible : sans schéma ni « www. », décodée (« arte.tv/fr/videos/… »). */
export function adresseLisible(url) {
  let u;
  try { u = new URL(url); } catch { return String(url); }
  const s = `${hoteDe(url)}${u.port ? `:${u.port}` : ""}${u.pathname}${u.search}${u.hash}`;
  return decoder(s.replace(/\/$/, ""));
}

const EXT_VIDEO = /\.(?:mp4|m4v|mkv|webm|mov|avi|ts|m3u8|mpd)$/i;

/**
 * Titre tiré de l'adresse quand elle en contient un : nom d'un fichier vidéo, ou dernier segment
 * fait de mots (« …/les-grands-documentaires » → « Les grands documentaires »). Sinon "".
 */
export function titreDeduit(url) {
  let u;
  try { u = new URL(url); } catch { return ""; }
  const dernier = u.pathname.split("/").filter(Boolean).at(-1);
  if (!dernier) return "";
  const s = decoder(dernier, decodeURIComponent);
  if (EXT_VIDEO.test(s)) return nettoyer(s, 120);
  const mots = s.replace(/\.[a-z0-9]{1,5}$/i, "").split(/[-_+\s]+/).filter(Boolean);
  while (mots.length && /^\d+$/.test(mots[0])) mots.shift();
  while (mots.length && /^\d+$/.test(mots.at(-1))) mots.pop();
  const lettres = mots.filter((m) => /^\p{L}{2,}$/u.test(m)).length;
  if (mots.length < 2 || lettres < 2 || lettres * 2 < mots.length) return "";
  const t = nettoyer(mots.join(" "), 120);
  return t.charAt(0).toUpperCase() + t.slice(1);
}

/** Libellé d'un lien récent : son titre, sinon un titre tiré de l'adresse, sinon l'adresse lisible. */
export const libelleLien = (e) => e.titre || titreDeduit(e.url) || adresseLisible(e.url);

/** Teinte (0..359) de la pastille d'un lien, tirée de l'hôte ; YouTube et Netflix en rouge. */
export function teinteDe(hote) {
  if (/(^|\.)(youtube\.com|youtu\.be|netflix\.com)$/.test(hote)) return 0;
  let x = 0;
  for (let i = 0; i < hote.length; i++) x = (x * 31 + hote.charCodeAt(i)) >>> 0;
  return x % 360;
}

// ---------- Messages ----------

/** Champs de `lien_ouvrir` : `url`, et `titre`, `sansVpn`, `vpnDabord` seulement s'ils servent. */
export function champsLienOuvrir(url, { titre, sansVpn = false, vpnDabord = false } = {}) {
  const c = { url };
  const t = nettoyer(titre, MAX_TITRE);
  if (t) c.titre = t;
  if (sansVpn === true) c.sansVpn = true;
  if (vpnDabord === true) c.vpnDabord = true;
  return c;
}

/** Message `liens` → état normalisé {vpn, seulementVpn, appliPages, appliVpn?, raccourci?}, ou null. */
export function normaliserLiens(m) {
  if (!m || typeof m !== "object") return null;
  const e = { vpn: m.vpn === true, seulementVpn: m.seulementVpn === true, appliPages: nettoyer(m.appliPages, 80) };
  const appliVpn = nettoyer(m.appliVpn, 80);
  if (appliVpn) e.appliVpn = appliVpn;
  const adresse = m.raccourci && typeof m.raccourci === "object" ? m.raccourci.adresse : null;
  if (urlValable(adresse)) e.raccourci = { adresse };
  return e;
}

/** Message `lien_ouvert` → {url, ok, ouvertDans?, raison?}, ou null. */
export function normaliserLienOuvert(m) {
  if (!m || typeof m !== "object" || !str(m.url) || !m.url || m.url.length > 2 * MAX_URL) return null;
  const r = { url: m.url, ok: m.ok === true };
  const dans = nettoyer(m.ouvertDans, 80);
  if (dans) r.ouvertDans = dans;
  if (!r.ok && str(m.raison) && /^[a-z0-9_]{1,32}$/.test(m.raison)) r.raison = m.raison;
  return r;
}

/**
 * Ligne d'état sous le bouton, en morceaux [texte, gras] :
 * « VPN de la TV actif · les pages s'ouvrent dans X », « VPN de la TV coupé » (« · la TV demandera
 * de l'allumer » si seulementVpn). `etat` : actif | coupe (VPN exigé) | libre. null avant `liens`.
 */
export function etatVpn(liens) {
  if (!liens) return null;
  let etat, morceaux;
  if (liens.vpn) {
    etat = "actif";
    morceaux = [["VPN de la TV ", false], ["actif", true]];
    if (liens.appliPages) morceaux.push([" · les pages s'ouvrent dans ", false], [liens.appliPages, true]);
  } else {
    etat = liens.seulementVpn ? "coupe" : "libre";
    morceaux = [["VPN de la TV ", false], ["coupé", true]];
    if (liens.seulementVpn) morceaux.push([" · la TV demandera de l'allumer", false]);
  }
  return { etat, morceaux, texte: morceaux.map(([t]) => t).join("") };
}

const MESSAGES_REFUS = {
  delai_vpn: "Le VPN de la TV ne s'est pas allumé à temps : le lien ne s'ouvre pas",
  pas_d_appli_vpn: "Aucune appli VPN connue sur la TV : allume le VPN à la main, ou ouvre sans VPN",
  remplace: "Lien annulé : un autre lien attend le VPN à sa place",
  invalide: "La TV refuse ce lien : adresse non valable",
  aucune_appli: "Aucune appli de la TV ne sait ouvrir ce lien",
};

/**
 * Que faire d'un `lien_ouvert` :
 *   ok         → confirmation « Envoyé sur <TV> · ouverture dans X » (fin) ;
 *   vpn_coupe  → feuille « Le VPN de la TV est coupé » (fin de cet envoi) ;
 *   attente    → « Allume le VPN sur la TV… » : un second `lien_ouvert` suivra ;
 *   erreur     → message d'erreur (fin).
 */
export function interpreterResultat(r, { nomTV = "la TV" } = {}) {
  if (r.ok) {
    return { type: "ok", texte: r.ouvertDans ? `Envoyé sur ${nomTV} · ouverture dans ${r.ouvertDans}` : `Envoyé sur ${nomTV}` };
  }
  if (r.raison === "vpn_coupe") return { type: "vpn_coupe" };
  if (r.raison === "attente_vpn") return { type: "attente", texte: "Allume le VPN sur la TV : le lien s'ouvrira ensuite" };
  return { type: "erreur", texte: MESSAGES_REFUS[r.raison] || "La TV n'a pas pu ouvrir ce lien" };
}

// ---------- Récents ----------
// { url, titre?, ouvertDans?, date (ms) }, le plus récent en tête, 20 au plus, sans doublon.

/** Liste gardée sur le téléphone → entrées valables seulement (doublons et excédent écartés). */
export function normaliserRecents(liste, max = MAX_RECENTS) {
  if (!Array.isArray(liste)) return [];
  const vus = new Set();
  const r = [];
  for (const e of liste) {
    const url = e && typeof e === "object" ? urlValable(e.url) : null;
    if (!url || vus.has(url) || !Number.isFinite(e.date)) continue;
    vus.add(url);
    const x = { url, date: e.date };
    const titre = nettoyer(e.titre, MAX_TITRE);
    if (titre) x.titre = titre;
    const dans = nettoyer(e.ouvertDans, 80);
    if (dans) x.ouvertDans = dans;
    r.push(x);
    if (r.length >= max) break;
  }
  return r;
}

/** Lien ouvert : en tête des récents (un lien déjà présent remonte, en gardant son titre). */
export function ajouterRecent(liste, entree, max = MAX_RECENTS) {
  const ancien = liste.find((e) => e.url === entree.url);
  const e = { ...entree };
  if (!e.titre && ancien?.titre) e.titre = ancien.titre;
  return normaliserRecents([e, ...liste.filter((x) => x.url !== entree.url)], max);
}

/** Lien retiré des récents (les autres gardent leur ordre). */
export function retirerRecent(liste, url) {
  return liste.filter((e) => e.url !== url);
}

const jour = (t) => { const d = new Date(t); return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime(); };
const FORMAT_HEURE = new Intl.DateTimeFormat("fr-FR", { hour: "2-digit", minute: "2-digit" });
const FORMAT_JOUR = new Intl.DateTimeFormat("fr-FR", { weekday: "long" });
const FORMAT_DATE = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "short" });
const FORMAT_DATE_ANNEE = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "short", year: "numeric" });

/** « à l'instant », « il y a 5 min », « à 14:32 », « hier », « lundi », « 2 nov. », « 2 nov. 2025 ». */
export function dateRelative(t, maintenant = Date.now()) {
  const d = maintenant - t;
  if (d < 60_000) return "à l'instant";
  if (d < 3_600_000) return `il y a ${Math.floor(d / 60_000)} min`;
  const j = Math.round((jour(maintenant) - jour(t)) / 86_400_000);
  if (j <= 0) return `à ${FORMAT_HEURE.format(t)}`;
  if (j === 1) return "hier";
  if (j < 7) return FORMAT_JOUR.format(t);
  const memeAnnee = new Date(t).getFullYear() === new Date(maintenant).getFullYear();
  return (memeAnnee ? FORMAT_DATE : FORMAT_DATE_ANNEE).format(t);
}

/** Détail d'un récent : « hôte · ouvert dans X · date ». */
export function detailRecent(e, maintenant = Date.now()) {
  const parties = [hoteDe(e.url)];
  if (e.ouvertDans) parties.push(`ouvert dans ${e.ouvertDans}`);
  parties.push(dateRelative(e.date, maintenant));
  return parties.filter(Boolean).join(" · ");
}

// ---------- Raccourci iOS ----------

/** L'adresse du raccourci a-t-elle changé depuis la dernière copie sur ce téléphone ? */
export const adresseAChange = (actuelle, copiee) => Boolean(actuelle && copiee && actuelle !== copiee);
