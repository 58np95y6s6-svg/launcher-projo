// Thème de la télécommande : il suit le design de la TV (docs/telecommande.md, « Design de la TV »).
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  DESIGNS, NOMS_DESIGN, lireDesign, themePour, texteConnectee,
} from "../../remote-pwa/js/theme.js";

test("les 8 designs de la TV, chacun nommé", () => {
  assert.deepEqual([...DESIGNS].sort(), ["chaines", "console", "galerie", "nba", "pastel", "studio", "une", "verre"]);
  for (const d of DESIGNS) assert.ok(NOMS_DESIGN[d], d);
  assert.equal(NOMS_DESIGN.une, "À la une");
  assert.equal(NOMS_DESIGN.chaines, "Chaînes");
  assert.equal(NOMS_DESIGN.nba, "NBA");
});

test("lireDesign() : identifiant connu, sinon null", () => {
  assert.equal(lireDesign("pastel"), "pastel");
  assert.equal(lireDesign("nba"), "nba");
  for (const x of ["NBA", "nba2", " nba"]) assert.equal(lireDesign(x), null, x);
  for (const x of [undefined, null, "", "Pastel", "inconnu", 3, {}, ["verre"]]) assert.equal(lireDesign(x), null, String(x));
});

test("themePour() : design de la TV, sinon Galerie (clair) ou Verre (mode sombre)", () => {
  assert.equal(themePour("console"), "console");
  assert.equal(themePour("console", true), "console");
  // NBA : thème sombre, en mode clair comme en mode sombre
  assert.equal(themePour("nba"), "nba");
  assert.equal(themePour("nba", true), "nba");
  assert.equal(themePour(null), "galerie");
  assert.equal(themePour("futur"), "galerie");
  assert.equal(themePour(undefined, true), "verre");
  assert.equal(themePour("futur", true), "verre");
});

test("texteConnectee() : « Connectée · design X »", () => {
  assert.equal(texteConnectee("une"), "Connectée · design À la une");
  assert.equal(texteConnectee("nba"), "Connectée · design NBA");
  assert.equal(texteConnectee(null), "Connectée");
  assert.equal(texteConnectee("futur"), "Connectée");
});

test("chaque design a son thème dans styles.css (jetons, couleur de la barre d'état) et dans theme-initial.js", () => {
  const css = readFileSync(new URL("../../remote-pwa/styles.css", import.meta.url), "utf8");
  const initial = readFileSync(new URL("../../remote-pwa/js/theme-initial.js", import.meta.url), "utf8");
  for (const d of DESIGNS) {
    const bloc = css.match(new RegExp(`\\[data-theme="${d}"\\][^{]*\\{([^}]*)\\}`))?.[1];
    assert.ok(bloc, `thème ${d} absent de styles.css`);
    for (const jeton of ["--fond:", "--couleur-etat:", "--texte:", "--texte-2:", "--ok-fond:", "--principal:", "--actif:", "--titre:"]) {
      assert.ok(bloc.includes(jeton), `${d} : ${jeton} manquant`);
    }
    assert.ok(initial.includes(`"${d}"`), `${d} absent de theme-initial.js`);
    assert.match(css, new RegExp(`\\.apercu-design\\[data-id="${d}"\\]`), `${d} : aperçu absent du choix du design`);
  }
});

test("thème NBA : polices Oswald et Barlow Condensed (OFL) déclarées, en cache hors ligne", () => {
  const css = readFileSync(new URL("../../remote-pwa/styles.css", import.meta.url), "utf8");
  const sw = readFileSync(new URL("../../remote-pwa/sw.js", import.meta.url), "utf8");
  const bloc = css.match(/\[data-theme="nba"\][^{]*\{([^}]*)\}/)[1];
  assert.match(bloc, /--titre: "Oswald"/);
  assert.match(bloc, /--condensee: "Barlow Condensed"/);
  assert.match(bloc, /--couleur-etat: #10204a/);
  const polices = ["oswald-demi-gras", "oswald-gras", "barlow-condensed-demi-gras", "barlow-condensed-gras", "barlow-condensed-extra-gras"];
  for (const f of polices) {
    assert.ok(css.includes(`url("polices/${f}.woff2")`), `${f} : @font-face manquant`);
    assert.ok(sw.includes(`"polices/${f}.woff2"`), `${f} absent du cache du service worker`);
    assert.ok(readFileSync(new URL(`../../remote-pwa/polices/${f}.woff2`, import.meta.url)).subarray(0, 4).equals(Buffer.from("wOF2")), f);
  }
  for (const l of ["oswald-OFL.txt", "barlow-condensed-OFL.txt"]) {
    assert.match(readFileSync(new URL(`../../remote-pwa/polices/${l}`, import.meta.url), "utf8"), /SIL Open Font License/);
    assert.ok(sw.includes(`"polices/${l}"`), `${l} absent du cache du service worker`);
  }
});

test("thème NBA : contrastes WCAG AA sur le fond, le verre sombre, les champs et les feuilles", () => {
  const css = readFileSync(new URL("../../remote-pwa/styles.css", import.meta.url), "utf8");
  const bloc = css.match(/\[data-theme="nba"\][^{]*\{([^}]*)\}/)[1];
  const jeton = (n) => bloc.match(new RegExp(`--${n}:\\s*([^;]+);`))[1].trim();
  /** « #rgb », « #rrggbb » ou « rgba(r, g, b, a) » → [r, g, b, a] */
  const couleur = (c) => {
    const h = c.replace(/^#([0-9a-f])([0-9a-f])([0-9a-f])$/i, "#$1$1$2$2$3$3").match(/^#([0-9a-f]{6})$/i);
    if (h) return [0, 2, 4].map((i) => parseInt(h[1].slice(i, i + 2), 16)).concat(1);
    const [r, g, b, a = 1] = c.match(/^rgba?\(([^)]+)\)$/)[1].split(",").map(Number);
    return [r, g, b, a];
  };
  const sur = (c, [fr, fg, fb]) => [c[0] * c[3] + fr * (1 - c[3]), c[1] * c[3] + fg * (1 - c[3]), c[2] * c[3] + fb * (1 - c[3]), 1];
  const lum = (c) => c.slice(0, 3).map((v) => v / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
    .reduce((s, v, i) => s + v * [0.2126, 0.7152, 0.0722][i], 0);
  const contraste = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((m, n) => n - m); return (x + 0.05) / (y + 0.05); };

  // Fond : chaque couleur du dégradé (du haut éclairé au bas presque noir)
  const fonds = jeton("fond").match(/#[0-9a-f]{6}/gi).map(couleur);
  assert.ok(fonds.length >= 3);
  const surface = couleur(jeton("surface"));
  const forte = couleur(jeton("surface-forte"));
  const plans = {
    ...Object.fromEntries(fonds.map((f, i) => [`fond ${i}`, f])),
    ...Object.fromEntries(fonds.map((f, i) => [`verre sur fond ${i}`, sur(surface, f)])),
    "champ sur verre": sur(forte, sur(surface, fonds[0])),
    feuille: couleur(jeton("feuille")),
    "champ sur feuille": sur(forte, couleur(jeton("feuille"))),
    "barre d'onglets": sur(couleur(jeton("barre")), fonds[0]),
  };
  for (const [nom, plan] of Object.entries(plans)) {
    for (const t of ["texte", "texte-2", "lien", "actif", "erreur", "direct"]) {
      const c = contraste(sur(couleur(jeton(t)), plan), plan);
      assert.ok(c >= 4.5, `--${t} sur ${nom} : ${c.toFixed(2)}:1`);
    }
  }
  for (const [texte, fond] of [["principal-texte", "principal"], ["ok-texte", "ok-fond"]]) {
    const c = contraste(couleur(jeton(texte)), couleur(jeton(fond)));
    assert.ok(c >= 4.5, `--${texte} sur --${fond} : ${c.toFixed(2)}:1`);
  }
});
