// Thème de la télécommande : il suit le design affiché sur la TV (docs/telecommande.md,
// « Design de la TV : thème de la télécommande »). `hello` de la TV donne `design`, puis la TV
// envoie `{t: "design", id}` quand il change. Le dernier design est gardé avec chaque TV.
// Sans TV, ou design absent / inconnu : Galerie (clair), ou Verre si le téléphone est en mode sombre.
// Logique pure (testée sous Node) ; seul appliquerTheme() touche au DOM.

export const DESIGNS = Object.freeze(["galerie", "verre", "pastel", "console", "une", "chaines", "studio"]);

export const NOMS_DESIGN = Object.freeze({
  galerie: "Galerie", verre: "Verre", pastel: "Pastel", console: "Console",
  une: "À la une", chaines: "Chaînes", studio: "Studio",
});

/** Clé (localStorage) du dernier thème venu d'une TV, lue au démarrage par theme-initial.js. */
export const CLE_THEME = "ibiprojo-theme";

/** Identifiant de design connu, sinon null (absent, inconnu ou mal formé). */
export const lireDesign = (x) => (typeof x === "string" && DESIGNS.includes(x) ? x : null);

/** Thème à afficher pour le design d'une TV (ou sans TV). */
export const themePour = (design, sombre = false) => lireDesign(design) ?? (sombre ? "verre" : "galerie");

/** « Connectée · design Galerie » ; sans design connu : « Connectée ». */
export const texteConnectee = (design) => {
  const d = lireDesign(design);
  return d ? `Connectée · design ${NOMS_DESIGN[d]}` : "Connectée";
};

/** Le téléphone est-il en mode sombre ? */
export const modeSombre = () => {
  try { return matchMedia("(prefers-color-scheme: dark)").matches; } catch { return false; }
};

/**
 * Applique le thème (`data-theme` sur <html>, couleur de la barre d'état) et retient, pour le
 * prochain démarrage, celui qui vient d'une TV (`retenir` vrai) ; un thème par défaut n'est pas retenu.
 */
export function appliquerTheme(theme, { retenir = false } = {}) {
  const racine = document.documentElement;
  if (racine.dataset.theme !== theme) racine.dataset.theme = theme;
  const couleur = getComputedStyle(racine).getPropertyValue("--couleur-etat").trim();
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta && couleur && meta.content !== couleur) meta.content = couleur;
  try {
    if (retenir) localStorage.setItem(CLE_THEME, theme);
    else localStorage.removeItem(CLE_THEME);
  } catch {}
}
