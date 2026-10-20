// Encodages et messages du protocole télécommande (docs/telecommande.md).
// Module pur : aucune dépendance au DOM, testé sous Node.

const cfg = () => globalThis.IBIPROJO_CONFIG || {};
export const versionProtocole = () => cfg().versionProtocole ?? 1;
export const versionProtocoleMin = () => cfg().versionProtocoleMin ?? 1;

// ---------- base64url (RFC 4648 §5, SANS remplissage « = ») ----------

export function versBase64url(octets) {
  const u8 = octets instanceof Uint8Array ? octets : new Uint8Array(octets);
  let bin = "";
  for (let i = 0; i < u8.length; i++) bin += String.fromCharCode(u8[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function depuisBase64url(texte) {
  if (typeof texte !== "string" || !/^[A-Za-z0-9_-]*$/.test(texte) || texte.length % 4 === 1) {
    throw new Error("base64url invalide");
  }
  const b64 = texte.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((texte.length + 3) % 4);
  const bin = atob(b64);
  const u8 = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) u8[i] = bin.charCodeAt(i);
  return u8;
}

export function aleatoire(n) {
  return globalThis.crypto.getRandomValues(new Uint8Array(n));
}

export function hexAleatoire(nOctets) {
  return Array.from(aleatoire(nOctets), (b) => b.toString(16).padStart(2, "0")).join("");
}

// ---------- Messages (DataChannel) ----------

// Touches autorisées pour `key`
export const TOUCHES = Object.freeze([
  "up", "down", "left", "right", "ok", "back", "home", "vol+", "vol-", "mute", "menu", "playpause",
]);
export const TYPES_CHAMP = Object.freeze(["texte", "email", "motdepasse", "nombre", "url", "recherche"]);
export const ACTIONS_CHAMP = Object.freeze(["go", "search", "send", "next", "done"]);
export const RAISONS_REFUS = Object.freeze(["refuse", "expire", "invalide", "trop_d_essais"]);

/** Construit un message téléphone → TV (toujours avec `v` et `t`). */
export function message(t, champs = {}) {
  return { v: versionProtocole(), t, ...champs };
}

export function encoder(msg) {
  if (!msg || typeof msg.t !== "string" || typeof msg.v !== "number") throw new Error("message invalide");
  return JSON.stringify(msg);
}

/**
 * Décode un message reçu de la TV. Renvoie null s'il est illisible ou mal formé
 * (le message est alors ignoré, sans détail dans les journaux).
 */
export function decoder(brut) {
  if (typeof brut !== "string" || brut.length > 65536) return null;
  let m;
  try { m = JSON.parse(brut); } catch { return null; }
  if (!m || typeof m !== "object" || Array.isArray(m)) return null;
  if (!Number.isInteger(m.v) || typeof m.t !== "string") return null;
  const str = (x) => typeof x === "string";
  switch (m.t) {
    case "hello":
      return str(m.nonce) && str(m.sig) && str(m.cle) ? m : null;
    case "paired":
      return m;
    case "pair_refused":
      return str(m.raison) ? m : null;
    case "revoked":
    case "focus_lost":
      return m;
    case "focus_text":
      return {
        ...m,
        type: TYPES_CHAMP.includes(m.type) ? m.type : "texte",
        s: str(m.s) ? m.s : "",
        action: ACTIONS_CHAMP.includes(m.action) ? m.action : "done",
      };
    case "pong":
      return Number.isInteger(m.n) ? m : null;
    case "erreur":
      return str(m.code) ? m : null;
    default:
      return null; // type inconnu : ignoré
  }
}

// ---------- Compatibilité de version ----------

/**
 * Compare la version annoncée par la TV (dans `hello`) à celle de la PWA.
 * @returns "ok" | "maj_tv" (TV trop ancienne) | "recharger" (PWA trop ancienne)
 */
export function verifierVersion(vTV) {
  if (!Number.isInteger(vTV)) return "recharger";
  if (vTV < versionProtocoleMin()) return "maj_tv";
  if (vTV > versionProtocole()) return "recharger";
  return "ok";
}

/**
 * Réaction à `erreur` { code: "version", vMax } envoyé par la TV.
 * « Mettez à jour iBiProjo sur la TV » si la PWA est la plus récente, sinon « Rechargez ».
 */
export function verdictErreurVersion(vMax) {
  if (Number.isInteger(vMax) && versionProtocole() > vMax) return "maj_tv";
  return "recharger";
}

export const TEXTES_VERSION = Object.freeze({
  maj_tv: "Mettez à jour iBiProjo sur la TV",
  recharger: "Rechargez la télécommande",
});
