// Thème de la télécommande : il suit le design de la TV (docs/telecommande.md, « Design de la TV »).
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  DESIGNS, NOMS_DESIGN, lireDesign, themePour, texteConnectee,
} from "../../remote-pwa/js/theme.js";

test("les 7 designs de la TV, chacun nommé", () => {
  assert.deepEqual([...DESIGNS].sort(), ["chaines", "console", "galerie", "pastel", "studio", "une", "verre"]);
  for (const d of DESIGNS) assert.ok(NOMS_DESIGN[d], d);
  assert.equal(NOMS_DESIGN.une, "À la une");
  assert.equal(NOMS_DESIGN.chaines, "Chaînes");
});

test("lireDesign() : identifiant connu, sinon null", () => {
  assert.equal(lireDesign("pastel"), "pastel");
  for (const x of [undefined, null, "", "Pastel", "inconnu", 3, {}, ["verre"]]) assert.equal(lireDesign(x), null, String(x));
});

test("themePour() : design de la TV, sinon Galerie (clair) ou Verre (mode sombre)", () => {
  assert.equal(themePour("console"), "console");
  assert.equal(themePour("console", true), "console");
  assert.equal(themePour(null), "galerie");
  assert.equal(themePour("futur"), "galerie");
  assert.equal(themePour(undefined, true), "verre");
  assert.equal(themePour("futur", true), "verre");
});

test("texteConnectee() : « Connectée · design X »", () => {
  assert.equal(texteConnectee("une"), "Connectée · design À la une");
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
  }
});
