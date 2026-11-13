// Thème appliqué AVANT le premier affichage (script classique, dans <head>, après styles.css) :
// le dernier design de TV retenu, sinon le thème par défaut. app.js applique ensuite celui de la
// TV active (gardé avec elle dans IndexedDB) ; voir js/theme.js.
(() => {
  const designs = ["galerie", "verre", "pastel", "console", "une", "chaines", "studio", "nba"];
  let theme = null;
  try { theme = localStorage.getItem("ibiprojo-theme"); } catch {}
  if (!designs.includes(theme)) {
    let sombre = false;
    try { sombre = matchMedia("(prefers-color-scheme: dark)").matches; } catch {}
    theme = sombre ? "verre" : "galerie";
  }
  const racine = document.documentElement;
  racine.dataset.theme = theme;
  const couleur = getComputedStyle(racine).getPropertyValue("--couleur-etat").trim();
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta && couleur) meta.content = couleur;
})();
