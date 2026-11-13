// Stockage local (IndexedDB) : identité du téléphone, TV appairées, réglages, images des applis.
// Rien de tout cela ne quitte le téléphone (seule la clé PUBLIQUE est envoyée à la TV).

import { hexAleatoire, versBase64url, depuisBase64url } from "./codec.js";
import { genererCles, exporterSpki, empreinteCle } from "./crypto.js";
import { cleImage } from "./pilotage.js";

const NOM_BASE = "ibiprojo-telecommande";

function ouvrirBase() {
  return new Promise((ok, ko) => {
    const req = indexedDB.open(NOM_BASE, 2);
    req.onupgradeneeded = (e) => {
      const db = req.result;
      if (e.oldVersion < 1) {
        db.createObjectStore("identite");
        db.createObjectStore("tvs", { keyPath: "id" });
        db.createObjectStore("reglages");
      }
      // v2 : images des applis de la TV (clé « id|maj »)
      if (e.oldVersion < 2) db.createObjectStore("images", { keyPath: "cle" });
    };
    req.onsuccess = () => {
      const db = req.result;
      // Une version plus récente (autre onglet) doit pouvoir mettre la base à niveau
      db.onversionchange = () => { db.close(); basePromise = null; };
      ok(db);
    };
    req.onerror = () => ko(req.error);
  });
}

let basePromise = null;
const base = () => (basePromise ??= ouvrirBase());

async function operation(store, mode, fn) {
  const db = await base();
  return new Promise((ok, ko) => {
    const tx = db.transaction(store, mode);
    const req = fn(tx.objectStore(store));
    tx.oncomplete = () => ok(req?.result);
    tx.onerror = () => ko(tx.error);
    tx.onabort = () => ko(tx.error);
  });
}

const lire = (store, cle) => operation(store, "readonly", (s) => s.get(cle));
const ecrire = (store, valeur, cle) => operation(store, "readwrite", (s) => s.put(valeur, cle));

// ---------- Identité ----------

/**
 * Identité du téléphone, créée au premier lancement :
 * { id: "tel-<32 hex>", jeton (PeerJS), clePrivee: CryptoKey, cleSpki, empreinte, nom }
 */
export async function obtenirIdentite() {
  const brute = await lire("identite", "moi");
  if (brute) return reconstituer(brute);

  const id = "tel-" + hexAleatoire(16);
  const jeton = hexAleatoire(8);
  let paire = await genererCles(false);
  let cleSpki = await exporterSpki(paire.publicKey);
  let enreg = { id, jeton, clePrivee: paire.privateKey, cleSpki, empreinte: await empreinteCle(cleSpki), nom: "" };
  try {
    await ecrire("identite", enreg, "moi");
  } catch {
    // Repli (navigateur incapable de stocker une CryptoKey) : clé exportable conservée en PKCS#8
    paire = await genererCles(true);
    cleSpki = await exporterSpki(paire.publicKey);
    const pkcs8 = versBase64url(new Uint8Array(await crypto.subtle.exportKey("pkcs8", paire.privateKey)));
    enreg = { id, jeton, clePriveePkcs8: pkcs8, cleSpki, empreinte: await empreinteCle(cleSpki), nom: "" };
    await ecrire("identite", enreg, "moi");
    enreg = { ...enreg, clePrivee: paire.privateKey };
  }
  return enreg;
}

async function reconstituer(brute) {
  if (brute.clePrivee) return brute;
  const clePrivee = await crypto.subtle.importKey(
    "pkcs8", depuisBase64url(brute.clePriveePkcs8), { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  return { ...brute, clePrivee };
}

export async function enregistrerNom(nom) {
  const brute = await lire("identite", "moi");
  if (brute) await ecrire("identite", { ...brute, nom }, "moi");
}

// ---------- TV appairées ----------
// { id, nom, cle (SPKI), empreinte, ajouteeLe, versionApp?, fonctions?, design? (thème de la télécommande) }

export const listerTVs = () => operation("tvs", "readonly", (s) => s.getAll()).then((l) =>
  (l || []).sort((a, b) => (a.ajouteeLe || 0) - (b.ajouteeLe || 0)));
export const lireTV = (id) => lire("tvs", id);
export const enregistrerTV = (tv) => operation("tvs", "readwrite", (s) => s.put(tv));
export const oublierTV = (id) => operation("tvs", "readwrite", (s) => s.delete(id));

// ---------- Réglages ----------

export const lireReglage = (cle) => lire("reglages", cle);
export const ecrireReglage = (cle, valeur) => ecrire("reglages", valeur, cle);
export const effacerReglage = (cle) => operation("reglages", "readwrite", (s) => s.delete(cle));

// ---------- Images des applis de la TV ----------
// { cle: "id|maj", id, maj, forme: "banniere" | "icone", img: URL data: }

/** Images connues parmi `cles` : Map cle → enregistrement. */
export async function lireImages(cles) {
  const db = await base();
  return new Promise((ok, ko) => {
    const tx = db.transaction("images", "readonly");
    const store = tx.objectStore("images");
    const trouvees = new Map();
    for (const c of cles) {
      const req = store.get(c);
      req.onsuccess = () => { if (req.result) trouvees.set(c, req.result); };
    }
    tx.oncomplete = () => ok(trouvees);
    tx.onerror = tx.onabort = () => ko(tx.error);
  });
}

/** Enregistre une image et retire les versions plus anciennes de la même appli. */
export async function enregistrerImage({ id, maj, forme, img }) {
  const db = await base();
  return new Promise((ok, ko) => {
    const tx = db.transaction("images", "readwrite");
    const store = tx.objectStore("images");
    const anciennes = store.getAllKeys(IDBKeyRange.bound(`${id}|`, `${id}|\uffff`));
    anciennes.onsuccess = () => {
      for (const c of anciennes.result) if (Number(c.slice(id.length + 1)) < maj) store.delete(c);
    };
    store.put({ cle: cleImage(id, maj), id, maj, forme, img });
    tx.oncomplete = () => ok();
    tx.onerror = tx.onabort = () => ko(tx.error);
  });
}
