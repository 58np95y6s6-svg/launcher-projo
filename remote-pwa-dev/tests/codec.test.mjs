// Encodage / décodage des messages et validation de version.
import { test } from "node:test";
import assert from "node:assert/strict";
import "../../remote-pwa/config.js";
import {
  versBase64url, depuisBase64url, message, encoder, decoder,
  verifierVersion, verdictErreurVersion, TEXTES_VERSION, TOUCHES,
} from "../../remote-pwa/js/codec.js";

test("base64url sans remplissage, aller-retour", () => {
  for (let n = 0; n < 40; n++) {
    const o = crypto.getRandomValues(new Uint8Array(n));
    const t = versBase64url(o);
    assert.doesNotMatch(t, /[+/=]/);
    assert.deepEqual(depuisBase64url(t), o);
  }
  assert.equal(versBase64url(new Uint8Array([0xfb, 0xff])), "-_8");
  assert.throws(() => depuisBase64url("abc="));
  assert.throws(() => depuisBase64url("a"));
});

test("message() ajoute v et t ; encoder() produit du JSON", () => {
  const m = message("key", { k: "up" });
  assert.deepEqual(m, { v: 1, t: "key", k: "up" });
  assert.equal(encoder(m), '{"v":1,"t":"key","k":"up"}');
  assert.equal(encoder(message("submit")), '{"v":1,"t":"submit"}');
  assert.throws(() => encoder({ t: "x" }));
  assert.ok(TOUCHES.includes("vol+") && TOUCHES.includes("playpause") && TOUCHES.length === 12);
});

test("decoder() : messages TV valides", () => {
  assert.deepEqual(decoder('{"v":1,"t":"pong","n":3}'), { v: 1, t: "pong", n: 3 });
  assert.equal(decoder('{"v":1,"t":"hello","nonce":"a","sig":"b","cle":"c","nomTV":"Salon"}').nomTV, "Salon");
  assert.deepEqual(decoder('{"v":1,"t":"focus_text","type":"motdepasse","s":"","action":"go"}'),
    { v: 1, t: "focus_text", type: "motdepasse", s: "", action: "go" });
  assert.equal(decoder('{"v":1,"t":"revoked"}').t, "revoked");
  assert.equal(decoder('{"v":1,"t":"pair_refused","raison":"expire"}').raison, "expire");
  assert.equal(decoder('{"v":1,"t":"erreur","code":"version","vMax":0}').vMax, 0);
});

test("decoder() : valeurs inconnues normalisées, messages invalides ignorés", () => {
  const f = decoder('{"v":1,"t":"focus_text","type":"bizarre","action":"zzz"}');
  assert.equal(f.type, "texte"); assert.equal(f.action, "done"); assert.equal(f.s, "");
  for (const brut of [
    "pas du json", "[]", "null", '{"t":"pong","n":1}', '{"v":"1","t":"pong","n":1}', '{"v":1}',
    '{"v":1,"t":"inconnu"}', '{"v":1,"t":"hello","nonce":"a","sig":"b"}', '{"v":1,"t":"pong"}',
    '{"v":1,"t":"pair_refused"}', 42, "x".repeat(70000),
  ]) assert.equal(decoder(brut), null, String(brut).slice(0, 40));
});

test("verifierVersion() : version annoncée par la TV", () => {
  assert.equal(verifierVersion(1), "ok");
  assert.equal(verifierVersion(2), "recharger"); // TV plus récente → recharger la PWA
  assert.equal(verifierVersion(0), "maj_tv");    // TV plus ancienne → mettre à jour la TV
  assert.equal(verifierVersion("1"), "recharger");
  assert.equal(verifierVersion(undefined), "recharger");
});

test("verdictErreurVersion() : erreur {code:version, vMax}", () => {
  assert.equal(verdictErreurVersion(0), "maj_tv");
  assert.equal(TEXTES_VERSION[verdictErreurVersion(0)], "Mettez à jour iBiProjo sur la TV");
  assert.equal(verdictErreurVersion(1), "recharger");
  assert.equal(verdictErreurVersion(5), "recharger");
  assert.equal(TEXTES_VERSION[verdictErreurVersion(undefined)], "Rechargez la télécommande");
});
