// Identité et authentification : ECDSA P-256 (WebCrypto) liée aux empreintes DTLS.
// Formats exacts : voir docs/telecommande.md, section « Encodages ».

import { versBase64url, depuisBase64url, aleatoire } from "./codec.js";

const subtle = () => globalThis.crypto.subtle;
const ALGO = { name: "ECDSA", namedCurve: "P-256" };
const SIGNATURE = { name: "ECDSA", hash: "SHA-256" };
const utf8 = (s) => new TextEncoder().encode(s);

/** Paire de clés du téléphone. La clé privée n'est pas exportable (sauf repli explicite). */
export function genererCles(exportable = false) {
  return subtle().generateKey(ALGO, exportable, ["sign", "verify"]);
}

/** Clé publique → SPKI (DER) en base64url. */
export async function exporterSpki(clePublique) {
  return versBase64url(new Uint8Array(await subtle().exportKey("spki", clePublique)));
}

/** SPKI base64url → clé publique de vérification. */
export function importerSpki(spkiB64) {
  return subtle().importKey("spki", depuisBase64url(spkiB64), ALGO, true, ["verify"]);
}

/** Empreinte d'une clé = SHA-256 de la SPKI, tronquée à 16 octets, en base64url (22 caractères). */
export async function empreinteCle(spkiB64) {
  const h = new Uint8Array(await subtle().digest("SHA-256", depuisBase64url(spkiB64)));
  return versBase64url(h.slice(0, 16));
}

/** Nonce : 16 octets aléatoires en base64url. */
export const nouveauNonce = () => versBase64url(aleatoire(16));

/**
 * Empreinte DTLS lue dans un SDP, normalisée : « sha-256 AB:CD:… »
 * (algorithme en minuscules, une espace, octets en hexadécimal MAJUSCULE séparés par « : »).
 * On prend la première ligne a=fingerprint en sha-256 (sinon la première tout court).
 */
export function empreinteDtls(sdp) {
  if (typeof sdp !== "string") return null;
  const lignes = [...sdp.matchAll(/^a=fingerprint:(\S+)\s+([0-9A-Fa-f:]+)\s*$/gm)];
  if (!lignes.length) return null;
  const l = lignes.find((x) => x[1].toLowerCase() === "sha-256") || lignes[0];
  return `${l[1].toLowerCase()} ${l[2].toUpperCase()}`;
}

/** Texte signé par le téléphone. */
export const texteHelloTel = (fpTel, fpTV, nonceTel) =>
  `ibiprojo-v1|hello|${fpTel}|${fpTV}|${nonceTel}`;

/** Texte signé par la TV. */
export const texteHelloTV = (fpTV, fpTel, nonceTel, nonceTV) =>
  `ibiprojo-v1|hello-tv|${fpTV}|${fpTel}|${nonceTel}|${nonceTV}`;

/**
 * Signature ECDSA P-256 / SHA-256 du texte encodé en UTF-8.
 * Format : IEEE P1363 (r ‖ s, 64 octets) en base64url — format natif de WebCrypto.
 */
export async function signer(clePrivee, texte) {
  return versBase64url(new Uint8Array(await subtle().sign(SIGNATURE, clePrivee, utf8(texte))));
}

/** Vérifie une signature P1363 base64url. Ne lève jamais : renvoie false si invalide. */
export async function verifier(clePublique, texte, sigB64) {
  try {
    const sig = depuisBase64url(sigB64);
    if (sig.length !== 64) return false;
    return await subtle().verify(SIGNATURE, clePublique, sig, utf8(texte));
  } catch {
    return false;
  }
}

/** Comparaison à temps constant de deux chaînes (empreintes). */
export function egaliteConstante(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}
