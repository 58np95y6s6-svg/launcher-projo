// Génère remote-pwa-dev/tests/vecteurs.json : vecteurs de test du protocole, partagés avec
// l'implémentation Kotlin (TV). Ne contient QUE des données publiques (clés publiques,
// signatures) : les clés privées, jetables, ne sont jamais écrites.
// Usage : node remote-pwa-dev/outils/vecteurs.mjs

import { writeFile } from "node:fs/promises";
import "../../remote-pwa/config.js";
import { versBase64url } from "../../remote-pwa/js/codec.js";
import { exporterSpki, empreinteCle, signer, texteHelloTel, texteHelloTV, empreinteDtls } from "../../remote-pwa/js/crypto.js";

/** Signature P1363 (r ‖ s, 64 octets) → DER (SEQUENCE { INTEGER r, INTEGER s }), format Java. */
export function p1363VersDer(sig) {
  const entier = (o) => {
    let i = 0;
    while (i < o.length - 1 && o[i] === 0) i++;
    let v = o.slice(i);
    if (v[0] & 0x80) v = Uint8Array.of(0, ...v);
    return Uint8Array.of(0x02, v.length, ...v);
  };
  const r = entier(sig.slice(0, 32)), s = entier(sig.slice(32));
  return Uint8Array.of(0x30, r.length + s.length, ...r, ...s);
}
const deB64u = (s) => Uint8Array.from(Buffer.from(s, "base64url"));

const sdpTel = [
  "v=0", "o=- 4611731400430051336 2 IN IP4 127.0.0.1", "s=-", "t=0 0", "a=group:BUNDLE 0",
  "m=application 9 UDP/DTLS/SCTP webrtc-datachannel", "c=IN IP4 0.0.0.0",
  "a=fingerprint:sha-256 4f:1a:9C:2E:77:0B:5D:83:A1:6E:F0:12:34:56:78:9A:BC:DE:F0:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD",
  "a=setup:actpass", "a=mid:0", "a=sctp-port:5000",
].join("\r\n") + "\r\n";
const sdpTV = sdpTel.replace(/a=fingerprint:.*/, "a=fingerprint:SHA-256 01:23:45:67:89:AB:CD:EF:FE:DC:BA:98:76:54:32:10:0F:1E:2D:3C:4B:5A:69:78:87:96:A5:B4:C3:D2:E1:F0")
  .replace("a=setup:actpass", "a=setup:active");

const ALGO = { name: "ECDSA", namedCurve: "P-256" };
const tel = await crypto.subtle.generateKey(ALGO, false, ["sign", "verify"]);
const tv = await crypto.subtle.generateKey(ALGO, false, ["sign", "verify"]);
const cleTel = await exporterSpki(tel.publicKey);
const cleTV = await exporterSpki(tv.publicKey);
const fpTel = empreinteDtls(sdpTel);
const fpTV = empreinteDtls(sdpTV);
const nonceTel = versBase64url(new TextEncoder().encode("nonce-tel-16oct!"));
const nonceTV = versBase64url(new TextEncoder().encode("nonce-tv--16oct!"));
const texteTel = texteHelloTel(fpTel, fpTV, nonceTel);
const texteTV = texteHelloTV(fpTV, fpTel, nonceTel, nonceTV);
const sigTel = await signer(tel.privateKey, texteTel);
const sigTV = await signer(tv.privateKey, texteTV);

const vecteurs = {
  _note: "Vecteurs de test du protocole télécommande v1 (docs/telecommande.md). Données publiques uniquement.",
  sdpTel, sdpTV,
  empreinteDtlsTel: fpTel,
  empreinteDtlsTV: fpTV,
  cleTel, empreinteCleTel: await empreinteCle(cleTel),
  cleTV, empreinteCleTV: await empreinteCle(cleTV),
  nonceTel, nonceTV,
  texteHelloTel: texteTel,
  sigHelloTelP1363: sigTel,
  sigHelloTelDer: versBase64url(p1363VersDer(deB64u(sigTel))),
  texteHelloTV: texteTV,
  sigHelloTVP1363: sigTV,
  sigHelloTVDer: versBase64url(p1363VersDer(deB64u(sigTV))),
  fragment: `#pair=ibiprojo-0123456789abcdef0123456789abcdef.${versBase64url(new Uint8Array(16).fill(7))}.${await empreinteCle(cleTV)}`,
  code: "K7M2-PQ9X", codeIdTV: "ibiprojo-c-K7M2", codeSecret: "PQ9X",
};

const sortie = new URL("../tests/vecteurs.json", import.meta.url);
await writeFile(sortie, JSON.stringify(vecteurs, null, 2) + "\n");
console.log("Écrit :", sortie.pathname);
