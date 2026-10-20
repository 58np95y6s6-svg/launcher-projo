// Client de signalisation PeerJS contre le faux serveur local (WebSocket natif de Node 22).
import { test, after, before } from "node:test";
import assert from "node:assert/strict";
import "../../remote-pwa/config.js";
import { Signalisation, urlSignalisation, ErreurSignalisation } from "../../remote-pwa/js/signalisation.js";
import { demarrerFauxPeerJS } from "../e2e/faux-peerjs.mjs";

let serveur;
let peerjs;
before(async () => {
  serveur = await demarrerFauxPeerJS({ expirationMs: 300 });
  peerjs = { hote: "127.0.0.1", port: serveur.port, chemin: "/", securise: false, cle: "peerjs", intervalleHeartbeatMs: 100, delaiOpenMs: 300 };
});
after(() => serveur.fermer());

const prochain = (s, type) => new Promise((r) => s.addEventListener("message", function f(e) {
  if (e.detail.type === type) { s.removeEventListener("message", f); r(e.detail); }
}));

test("URL du serveur PeerJS", () => {
  const cfg = globalThis.IBIPROJO_CONFIG.peerjs;
  assert.equal(urlSignalisation(cfg, "tel-abc", "0123"), "wss://0.peerjs.com:443/peerjs?key=peerjs&id=tel-abc&token=0123");
  assert.equal(urlSignalisation({ ...cfg, chemin: "/signal" }, "x", "y"), "wss://0.peerjs.com:443/signal/peerjs?key=peerjs&id=x&token=y");
});

test("OPEN, relais OFFER/ANSWER avec src, EXPIRE si destinataire absent", async () => {
  const a = new Signalisation({ peerjs, id: "tel-aaaa", jeton: "j1" });
  const b = new Signalisation({ peerjs, id: "ibiprojo-bbbb", jeton: "j2" });
  await a.ouvrir();
  await b.ouvrir();
  const recu = prochain(b, "OFFER");
  a.envoyer("OFFER", "ibiprojo-bbbb", { sdp: { type: "offer", sdp: "x" }, type: "data", connectionId: "tc_1" });
  assert.deepEqual(await recu, { type: "OFFER", src: "tel-aaaa", dst: "ibiprojo-bbbb", payload: { sdp: { type: "offer", sdp: "x" }, type: "data", connectionId: "tc_1" } });
  const rep = prochain(a, "ANSWER");
  b.envoyer("ANSWER", "tel-aaaa", { sdp: { type: "answer", sdp: "y" }, type: "data", connectionId: "tc_1" });
  assert.equal((await rep).src, "ibiprojo-bbbb");
  const exp = prochain(a, "EXPIRE");
  a.envoyer("OFFER", "ibiprojo-absente", { type: "data", connectionId: "tc_2" });
  assert.deepEqual(await exp, { type: "EXPIRE", src: "ibiprojo-absente", dst: "tel-aaaa" });
  a.fermer(); b.fermer();
});

test("ID-TAKEN avec un autre jeton ; reprise avec le même jeton (sans OPEN)", async () => {
  const a = new Signalisation({ peerjs, id: "tel-cccc", jeton: "j1" });
  await a.ouvrir();
  const intrus = new Signalisation({ peerjs, id: "tel-cccc", jeton: "autre" });
  await assert.rejects(intrus.ouvrir(), (e) => e instanceof ErreurSignalisation && e.code === "id_pris");
  const reprise = new Signalisation({ peerjs, id: "tel-cccc", jeton: "j1" });
  const t0 = Date.now();
  await reprise.ouvrir(); // pas d'OPEN : ouverte après delaiOpenMs
  assert.ok(Date.now() - t0 >= 250);
  assert.ok(reprise.ouverte);
  // La reprise reçoit bien les messages
  const b = new Signalisation({ peerjs, id: "ibiprojo-dddd", jeton: "j" });
  await b.ouvrir();
  const recu = prochain(reprise, "CANDIDATE");
  b.envoyer("CANDIDATE", "tel-cccc", { candidate: { candidate: "c", sdpMid: "0", sdpMLineIndex: 0 }, type: "data", connectionId: "tc_3" });
  assert.equal((await recu).src, "ibiprojo-dddd");
  a.fermer(); reprise.fermer(); b.fermer();
});

test("mauvaise clé de serveur → ERROR ; serveur absent → échec", async () => {
  const s = new Signalisation({ peerjs: { ...peerjs, cle: "mauvaise" }, id: "tel-eeee", jeton: "j" });
  await assert.rejects(s.ouvrir(), (e) => e.code === "serveur");
  const t = new Signalisation({ peerjs: { ...peerjs, port: 1 }, id: "tel-ffff", jeton: "j" });
  await assert.rejects(t.ouvrir(), (e) => e.code === "fermee");
});

test("HEARTBEAT envoyé régulièrement ; perte de la socket signalée", async () => {
  const s = new Signalisation({ peerjs, id: "tel-gggg", jeton: "j" });
  await s.ouvrir();
  const fermee = new Promise((r) => s.addEventListener("fermee", r));
  await new Promise((r) => setTimeout(r, 250));
  serveur.couper("tel-gggg");
  await fermee;
  assert.equal(s.ouverte, false);
});
