// Lecture des informations d'appairage : fragment « #pair=… » du QR code et code de secours.

export const ALPHABET_CODE = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
const RE_ID_TV = /^ibiprojo-[0-9a-f]{32}$/;
const RE_B64_16 = /^[A-Za-z0-9_-]{22}$/; // 16 octets en base64url sans remplissage

/**
 * Lit « #pair=<idTV>.<secret>.<empreinte clé TV> » (le # est facultatif ; on accepte aussi
 * une URL complète). Renvoie null si absent ou invalide.
 * @returns {{mode:"qr", idTV:string, secret:string, empreinteTV:string} | null}
 */
export function lireFragment(texte) {
  if (typeof texte !== "string") return null;
  const i = texte.indexOf("#");
  let frag = i >= 0 ? texte.slice(i + 1) : texte;
  frag = frag.trim();
  // Le fragment peut contenir d'autres paramètres : « #pair=…&x=y »
  const params = new URLSearchParams(frag);
  const valeur = params.get("pair");
  if (!valeur) return null;
  const parties = valeur.split(".");
  if (parties.length !== 3) return null;
  const [idTV, secret, empreinteTV] = parties;
  if (!RE_ID_TV.test(idTV) || !RE_B64_16.test(secret) || !RE_B64_16.test(empreinteTV)) return null;
  return { mode: "qr", idTV, secret, empreinteTV };
}

/** Normalise une saisie de code de secours : majuscules, sans espaces ni tirets. */
export function normaliserCode(saisie) {
  return String(saisie ?? "").toUpperCase().replace(/[\s\-_.]/g, "");
}

/**
 * Lit un code de secours « ABCD-EFGH ».
 * @returns {{mode:"code", idTV:string, secret:string, code:string} | null}
 *   idTV = « ibiprojo-c-ABCD » (identifiant PeerJS temporaire), secret = « EFGH ».
 */
export function lireCode(saisie) {
  const c = normaliserCode(saisie);
  if (c.length !== 8) return null;
  for (const ch of c) if (!ALPHABET_CODE.includes(ch)) return null;
  return { mode: "code", idTV: `ibiprojo-c-${c.slice(0, 4)}`, secret: c.slice(4), code: formaterCode(c) };
}

/** « ABCDEFGH » → « ABCD-EFGH » (affichage). */
export function formaterCode(code) {
  const c = normaliserCode(code);
  return c.length > 4 ? `${c.slice(0, 4)}-${c.slice(4)}` : c;
}

/** Accepte indifféremment un lien d'appairage (avec #pair=) ou un code de secours. */
export function lireSaisieAppairage(saisie) {
  const s = String(saisie ?? "").trim();
  if (s.includes("pair=")) return lireFragment(s);
  return lireCode(s);
}

/** Identifiant stable d'une TV ? (sinon identifiant temporaire de code de secours) */
export const estIdTV = (id) => RE_ID_TV.test(id);
