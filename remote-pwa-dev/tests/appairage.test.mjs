// Lecture du fragment #pair=… et du code de secours.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  lireFragment, lireCode, lireSaisieAppairage, formaterCode, normaliserCode, ALPHABET_CODE, estIdTV,
} from "../../remote-pwa/js/appairage.js";

const V = JSON.parse(readFileSync(new URL("./vecteurs.json", import.meta.url)));
const ID = "ibiprojo-0123456789abcdef0123456789abcdef";
const SECRET = "BwcHBwcHBwcHBwcHBwcHBw";

test("fragment du QR code (vecteur)", () => {
  assert.deepEqual(lireFragment(V.fragment), { mode: "qr", idTV: ID, secret: SECRET, empreinteTV: V.empreinteCleTV });
});

test("fragment : URL complète, sans #, avec paramètres en plus", () => {
  const f = V.fragment.slice(1);
  assert.equal(lireFragment(`https://58np95y6s6-svg.github.io/launcher-projo/#${f}`).idTV, ID);
  assert.equal(lireFragment(f).secret, SECRET);
  assert.equal(lireFragment(`#${f}&autre=1`).empreinteTV, V.empreinteCleTV);
});

test("fragment invalide → null", () => {
  for (const s of [
    "", "#", "#pair=", "#autre=1", `#pair=${ID}.${SECRET}`, `#pair=${ID}.${SECRET}.${V.empreinteCleTV}.x`,
    `#pair=ibiprojo-XYZ.${SECRET}.${V.empreinteCleTV}`, `#pair=${ID.toUpperCase()}.${SECRET}.${V.empreinteCleTV}`,
    `#pair=${ID}.${SECRET}==.${V.empreinteCleTV}`, `#pair=${ID}.court.${V.empreinteCleTV}`,
    `#pair=${ID}.${SECRET}.${V.empreinteCleTV}A`, null, undefined, 12,
  ]) assert.equal(lireFragment(s), null, String(s));
});

test("code de secours : identifiant temporaire et secret court", () => {
  assert.deepEqual(lireCode(V.code), { mode: "code", idTV: V.codeIdTV, secret: V.codeSecret, code: V.code });
  assert.equal(lireCode("k7m2pq9x").idTV, "ibiprojo-c-K7M2");
  assert.equal(lireCode(" k7m2 - pq9x ").secret, "PQ9X");
  assert.equal(formaterCode("K7M2PQ9X"), "K7M2-PQ9X");
  assert.equal(normaliserCode("ab-cd ef_gh"), "ABCDEFGH");
});

test("code de secours invalide → null (longueur, caractères ambigus)", () => {
  assert.equal(ALPHABET_CODE.length, 31);
  for (const c of ["", "K7M2-PQ9", "K7M2-PQ9XY", "K7M2-PQ0X", "K7M2-PQ1X", "K7M2-PQOX", "K7M2-PQIX", "K7M2-PQLX", "K7M2-PQ9!"]) {
    assert.equal(lireCode(c), null, c);
  }
});

test("saisie libre : lien ou code", () => {
  assert.equal(lireSaisieAppairage(`https://exemple/#${V.fragment.slice(1)}`).mode, "qr");
  assert.equal(lireSaisieAppairage("K7M2-PQ9X").mode, "code");
  assert.equal(lireSaisieAppairage("n'importe quoi"), null);
  assert.ok(estIdTV(ID));
  assert.ok(!estIdTV("ibiprojo-c-K7M2"));
});
