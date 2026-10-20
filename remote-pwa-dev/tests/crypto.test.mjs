// Signature et vérification des « hello » (WebCrypto), empreintes, vecteurs partagés avec la TV.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import nodeCrypto from "node:crypto";
import "../../remote-pwa/config.js";
import {
  genererCles, exporterSpki, importerSpki, empreinteCle, empreinteDtls, nouveauNonce,
  texteHelloTel, texteHelloTV, signer, verifier, egaliteConstante,
} from "../../remote-pwa/js/crypto.js";

const V = JSON.parse(readFileSync(new URL("./vecteurs.json", import.meta.url)));

test("empreinte DTLS normalisée : « sha-256 » + hexadécimal majuscule", () => {
  assert.equal(empreinteDtls(V.sdpTel), V.empreinteDtlsTel);
  assert.equal(empreinteDtls(V.sdpTV), V.empreinteDtlsTV);
  assert.match(V.empreinteDtlsTel, /^sha-256 ([0-9A-F]{2}:){31}[0-9A-F]{2}$/);
  const deux = V.sdpTel.replace("a=fingerprint:sha-256", "a=fingerprint:sha-1 AA:BB\r\na=fingerprint:sha-256");
  assert.equal(empreinteDtls(deux), V.empreinteDtlsTel); // sha-256 préférée
  assert.equal(empreinteDtls("v=0\r\n"), null);
  assert.equal(empreinteDtls(undefined), null);
});

test("textes signés : ordre exact des champs", () => {
  assert.equal(texteHelloTel(V.empreinteDtlsTel, V.empreinteDtlsTV, V.nonceTel), V.texteHelloTel);
  assert.equal(texteHelloTV(V.empreinteDtlsTV, V.empreinteDtlsTel, V.nonceTel, V.nonceTV), V.texteHelloTV);
  assert.ok(V.texteHelloTel.startsWith("ibiprojo-v1|hello|sha-256 4F:1A"));
  assert.ok(V.texteHelloTV.startsWith("ibiprojo-v1|hello-tv|sha-256 01:23"));
});

test("empreinte de clé : SHA-256(SPKI) tronqué à 16 octets, base64url (22 caractères)", async () => {
  assert.equal(await empreinteCle(V.cleTel), V.empreinteCleTel);
  assert.equal(await empreinteCle(V.cleTV), V.empreinteCleTV);
  const attendu = nodeCrypto.createHash("sha256").update(Buffer.from(V.cleTV, "base64url")).digest().subarray(0, 16).toString("base64url");
  assert.equal(V.empreinteCleTV, attendu);
  assert.equal(V.empreinteCleTV.length, 22);
});

test("vecteurs : signatures P1363 vérifiées par WebCrypto, DER vérifiées par Node (format Java)", async () => {
  assert.ok(await verifier(await importerSpki(V.cleTel), V.texteHelloTel, V.sigHelloTelP1363));
  assert.ok(await verifier(await importerSpki(V.cleTV), V.texteHelloTV, V.sigHelloTVP1363));
  for (const [cle, texte, der] of [[V.cleTel, V.texteHelloTel, V.sigHelloTelDer], [V.cleTV, V.texteHelloTV, V.sigHelloTVDer]]) {
    const pub = nodeCrypto.createPublicKey({ key: Buffer.from(cle, "base64url"), format: "der", type: "spki" });
    assert.ok(nodeCrypto.verify("sha256", Buffer.from(texte, "utf8"), { key: pub, dsaEncoding: "der" }, Buffer.from(der, "base64url")));
  }
  assert.equal(Buffer.from(V.sigHelloTelP1363, "base64url").length, 64);
});

test("hello : signature puis vérification ; toute altération est refusée", async () => {
  const tel = await genererCles();
  const tv = await genererCles();
  const spkiTel = await exporterSpki(tel.publicKey);
  const nonce = nouveauNonce();
  assert.equal(nonce.length, 22);
  const texte = texteHelloTel(V.empreinteDtlsTel, V.empreinteDtlsTV, nonce);
  const sig = await signer(tel.privateKey, texte);
  const pub = await importerSpki(spkiTel);
  assert.ok(await verifier(pub, texte, sig));
  // Autre empreinte DTLS (homme du milieu), autre nonce, autre clé, signature abîmée
  assert.ok(!(await verifier(pub, texteHelloTel(V.empreinteDtlsTV, V.empreinteDtlsTV, nonce), sig)));
  assert.ok(!(await verifier(pub, texteHelloTel(V.empreinteDtlsTel, V.empreinteDtlsTV, nouveauNonce()), sig)));
  assert.ok(!(await verifier(tv.publicKey, texte, sig)));
  const abimee = sig.slice(0, 10) + (sig[10] === "A" ? "B" : "A") + sig.slice(11);
  assert.ok(!(await verifier(pub, texte, abimee)));
  assert.equal(await verifier(pub, texte, "pas-une-signature"), false);
  assert.equal(await verifier(pub, texte, V.sigHelloTelDer), false); // DER refusé : P1363 exigé
});

test("clé privée non exportable par défaut", async () => {
  const { privateKey } = await genererCles();
  assert.equal(privateKey.extractable, false);
  await assert.rejects(crypto.subtle.exportKey("pkcs8", privateKey));
});

test("egaliteConstante", () => {
  assert.ok(egaliteConstante("abc", "abc"));
  assert.ok(!egaliteConstante("abc", "abd"));
  assert.ok(!egaliteConstante("abc", "ab"));
  assert.ok(!egaliteConstante(undefined, "ab"));
});
