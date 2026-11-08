// Session (côté téléphone) avec un faux RTCPeerConnection et une fausse signalisation :
// format des messages PeerJS, hello signé, refus des commandes avant authentification,
// appairage, version, révocation.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import "../../remote-pwa/config.js";
import { Session } from "../../remote-pwa/js/session.js";
import {
  genererCles, exporterSpki, importerSpki, empreinteCle, signer, verifier, texteHelloTel, texteHelloTV, nouveauNonce,
} from "../../remote-pwa/js/crypto.js";

const V = JSON.parse(readFileSync(new URL("./vecteurs.json", import.meta.url)));
const config = { ...globalThis.IBITV_CONFIG, delais: { ...globalThis.IBITV_CONFIG.delais, connexion: 2000 } };
const ID_TV = "ibiprojo-0123456789abcdef0123456789abcdef";

class FauxCanal {
  constructor(label) { this.label = label; this.readyState = "connecting"; this.envoyes = []; }
  send(t) { this.envoyes.push(JSON.parse(t)); }
  close() { this.readyState = "closed"; }
  ouvrir() { this.readyState = "open"; this.onopen?.(); }
  recevoir(o) { this.onmessage?.({ data: typeof o === "string" ? o : JSON.stringify(o) }); }
}
class FauxPC {
  constructor(cfg) { this.cfg = cfg; this.localDescription = null; this.remoteDescription = null; this.candidats = []; this.connectionState = "new"; FauxPC.dernier = this; }
  createDataChannel(label, opts) { this.opts = opts; return (this.canal = new FauxCanal(label)); }
  async createOffer() { return { type: "offer", sdp: V.sdpTel }; }
  async setLocalDescription(d) { this.localDescription = d; }
  async setRemoteDescription(d) { this.remoteDescription = d; }
  async addIceCandidate(c) { this.candidats.push(c); }
  close() { this.connectionState = "closed"; }
}
class FausseSignalisation extends EventTarget {
  constructor() { super(); this.envoyes = []; }
  envoyer(type, dst, payload) { this.envoyes.push({ type, dst, payload }); return true; }
  injecter(m) { this.dispatchEvent(new CustomEvent("message", { detail: m })); }
}

const attendre = async (cond, ms = 2000) => {
  const t0 = Date.now();
  while (!cond()) {
    if (Date.now() - t0 > ms) throw new Error("délai dépassé");
    await new Promise((r) => setTimeout(r, 5));
  }
};

async function preparer({ appairage = null, cible = null } = {}) {
  const tel = await genererCles();
  const tv = await genererCles();
  const cleTV = await exporterSpki(tv.publicKey);
  const identite = { id: "tel-" + "a".repeat(32), clePrivee: tel.privateKey, cleSpki: await exporterSpki(tel.publicKey) };
  const signal = new FausseSignalisation();
  cible ??= { id: ID_TV, cle: cleTV };
  const s = new Session({ signal, identite, config, RTC: FauxPC, appairage, cible });
  const evts = [];
  for (const t of ["etat", "tv", "paired", "focus_text", "focus_lost", "version", "fin", "apps", "app_image", "app_ouverte", "reglages", "liens", "lien_ouvert", "design"]) {
    s.addEventListener(t, (e) => evts.push({ t, ...e.detail }));
  }
  await s.demarrer();
  const pc = FauxPC.dernier;
  const offre = signal.envoyes.find((m) => m.type === "OFFER");
  signal.injecter({ type: "ANSWER", src: cible.id, dst: identite.id, payload: { sdp: { type: "answer", sdp: V.sdpTV }, type: "data", connectionId: offre.payload.connectionId } });
  await attendre(() => pc.remoteDescription);
  pc.canal.ouvrir();
  await attendre(() => pc.canal.envoyes.length > 0);
  const hello = pc.canal.envoyes[0];
  const helloTV = async ({ v = 1, cle = cleTV, cleSignature = tv.privateKey, nonceTel = hello.nonce, fpTV = V.empreinteDtlsTV, fonctions, design } = {}) => {
    const nonce = nouveauNonce();
    const sig = await signer(cleSignature, texteHelloTV(fpTV, V.empreinteDtlsTel, nonceTel, nonce));
    const h = { v, t: "hello", nonce, sig, nomTV: "Salon", versionApp: "2.0", cle };
    if (fonctions !== undefined) h.fonctions = fonctions;
    if (design !== undefined) h.design = design;
    return h;
  };
  return { s, signal, pc, canal: pc.canal, hello, helloTV, identite, tv, cleTV, evts, offre };
}

test("OFFER au format PeerJS, puis ANSWER et CANDIDATE filtrés par connectionId", async () => {
  const { s, signal, pc, offre } = await preparer();
  assert.equal(offre.dst, ID_TV);
  assert.equal(offre.payload.type, "data");
  assert.equal(offre.payload.label, "telecommande");
  assert.equal(offre.payload.sdp.type, "offer");
  assert.equal(offre.payload.sdp.sdp, V.sdpTel);
  assert.match(offre.payload.connectionId, /^tc_[0-9a-f]{16}$/);
  assert.equal(pc.canal.label, "telecommande");
  assert.equal(pc.opts.ordered, true);
  assert.deepEqual(pc.cfg.iceServers, config.iceServers);
  const cand = { candidate: "candidate:1 1 udp 2122260223 192.168.1.20 50000 typ host", sdpMid: "0", sdpMLineIndex: 0 };
  signal.injecter({ type: "CANDIDATE", src: ID_TV, payload: { candidate: cand, type: "data", connectionId: "tc_autre" } });
  signal.injecter({ type: "CANDIDATE", src: "ibiprojo-ffffffffffffffffffffffffffffffff", payload: { candidate: cand, type: "data", connectionId: offre.payload.connectionId } });
  signal.injecter({ type: "CANDIDATE", src: ID_TV, payload: { candidate: cand, type: "data", connectionId: offre.payload.connectionId } });
  await attendre(() => pc.candidats.length === 1);
  pc.onicecandidate({ candidate: { candidate: "candidate:2 1 udp 1 10.0.0.2 4000 typ host", sdpMid: "0", sdpMLineIndex: 0 } });
  pc.onicecandidate({ candidate: null });
  const c = signal.envoyes.filter((m) => m.type === "CANDIDATE");
  assert.equal(c.length, 1);
  assert.deepEqual(c[0].payload, { candidate: { candidate: "candidate:2 1 udp 1 10.0.0.2 4000 typ host", sdpMid: "0", sdpMLineIndex: 0 }, type: "data", connectionId: offre.payload.connectionId });
  s.fermer();
});

test("hello du téléphone : signé sur les empreintes DTLS (pilotage : sans clé)", async () => {
  const { s, hello, identite } = await preparer();
  assert.equal(hello.v, 1);
  assert.equal(hello.t, "hello");
  assert.equal(hello.appareil, identite.id);
  assert.equal(hello.cle, undefined);
  assert.equal(hello.nom, undefined);
  const pub = await importerSpki(identite.cleSpki);
  assert.ok(await verifier(pub, texteHelloTel(V.empreinteDtlsTel, V.empreinteDtlsTV, hello.nonce), hello.sig));
  s.fermer();
});

test("commandes refusées avant authentification ; messages TV ignorés avant hello vérifié", async () => {
  const { s, canal, evts } = await preparer();
  assert.equal(s.touche("up"), false);
  assert.equal(s.texte("abc"), false);
  assert.equal(s.valider(), false);
  canal.recevoir({ v: 1, t: "focus_text", type: "texte", s: "x", action: "go" });
  canal.recevoir({ v: 1, t: "revoked" });
  canal.recevoir({ v: 1, t: "paired", nomTV: "Pirate", idTV: ID_TV });
  canal.recevoir({ v: 1, t: "focus_lost" });
  await new Promise((r) => setTimeout(r, 30));
  assert.equal(canal.envoyes.length, 1); // le hello seulement
  assert.ok(!evts.some((e) => ["focus_text", "focus_lost", "paired", "fin"].includes(e.t)));
  assert.equal(s.etat, "authentification");
  s.fermer();
});

test("hello TV valide → prête ; commandes envoyées ; focus_text transmis ; ping/pong", async () => {
  const { s, canal, helloTV, evts } = await preparer();
  canal.recevoir(await helloTV());
  await attendre(() => s.prete);
  assert.ok(evts.some((e) => e.t === "tv" && e.nomTV === "Salon" && e.versionApp === "2.0"));
  assert.ok(s.touche("vol+"));
  assert.ok(s.texte("bonjour", 7));
  assert.ok(s.valider());
  assert.deepEqual(canal.envoyes.slice(1), [
    { v: 1, t: "key", k: "vol+" }, { v: 1, t: "text", s: "bonjour", curseur: 7 }, { v: 1, t: "submit" },
  ]);
  canal.recevoir({ v: 1, t: "focus_text", type: "motdepasse", s: "", action: "go" });
  canal.recevoir({ v: 1, t: "focus_lost" });
  await attendre(() => evts.some((e) => e.t === "focus_lost"));
  assert.ok(evts.some((e) => e.t === "focus_text" && e.type === "motdepasse" && e.action === "go"));
  s.verifierVivante(50);
  const ping = canal.envoyes.at(-1);
  assert.equal(ping.t, "ping");
  canal.recevoir({ v: 1, t: "pong", n: ping.n });
  await new Promise((r) => setTimeout(r, 100));
  assert.ok(s.prete);
  s.fermer();
});

test("hello TV refusé : mauvaise signature, autre clé, autre empreinte DTLS, mauvais nonce", async () => {
  const autre = await genererCles();
  const cas = [
    async (h) => ({ ...(await h()), sig: (await h()).sig.replace(/^./, (c) => (c === "A" ? "B" : "A")) }),
    async (h) => h({ cleSignature: autre.privateKey }),
    async (h, cleTV) => h({ cle: await exporterSpki(autre.publicKey), cleSignature: autre.privateKey }),
    async (h) => h({ fpTV: V.empreinteDtlsTel }),
    async (h) => h({ nonceTel: nouveauNonce() }),
  ];
  for (const fabriquer of cas) {
    const { s, canal, helloTV, evts, cleTV } = await preparer();
    canal.recevoir(await fabriquer(helloTV, cleTV));
    await attendre(() => s.etat === "fermee");
    assert.ok(evts.some((e) => e.t === "fin" && e.raison === "auth"));
    assert.equal(s.touche("up"), false);
  }
});

test("QR code : la clé de la TV doit correspondre à l'empreinte du QR", async () => {
  const appairage = { secret: "BwcHBwcHBwcHBwcHBwcHBw", nom: "Téléphone de Marie" };
  // Mauvaise empreinte attendue
  {
    const { s, canal, helloTV, evts } = await preparer({ appairage: { ...appairage, empreinteTV: V.empreinteCleTV }, cible: { id: ID_TV, empreinte: V.empreinteCleTV } });
    canal.recevoir(await helloTV());
    await attendre(() => s.etat === "fermee");
    assert.ok(evts.some((e) => e.t === "fin" && e.raison === "auth"));
  }
});

test("appairage (QR) : hello avec clé et nom, pair_request, commandes bloquées jusqu'à paired", async () => {
  const tv = await genererCles();
  const cleTV = await exporterSpki(tv.publicKey);
  const empreinteTV = await empreinteCle(cleTV);
  // preparer() génère sa propre clé TV : on la remplace par celle-ci via la cible
  const prep = await preparer({
    appairage: { secret: "BwcHBwcHBwcHBwcHBwcHBw", nom: "Téléphone de Marie", empreinteTV },
    cible: { id: ID_TV, empreinte: empreinteTV },
  });
  const { s, canal, hello, identite, evts } = prep;
  assert.equal(hello.cle, identite.cleSpki);
  assert.equal(hello.nom, "Téléphone de Marie");
  canal.recevoir(await prep.helloTV({ cle: cleTV, cleSignature: tv.privateKey }));
  await attendre(() => s.etat === "appairage");
  const req = canal.envoyes[1];
  assert.deepEqual(req, { v: 1, t: "pair_request", secret: "BwcHBwcHBwcHBwcHBwcHBw", nom: "Téléphone de Marie", cle: identite.cleSpki, empreinteTV });
  assert.equal(s.touche("ok"), false);
  canal.recevoir({ v: 1, t: "focus_text", type: "texte", s: "", action: "go" });
  canal.recevoir({ v: 1, t: "paired", nomTV: "Salon", idTV: ID_TV });
  await attendre(() => s.prete);
  const p = evts.find((e) => e.t === "paired");
  assert.deepEqual(p, { t: "paired", nomTV: "Salon", idTV: ID_TV, cle: cleTV, empreinte: empreinteTV });
  assert.ok(!evts.some((e) => e.t === "focus_text"));
  assert.ok(s.touche("ok"));
  s.fermer();
});

test("appairage par code : pas d'empreinte envoyée ; paired doit donner idTV", async () => {
  const appairage = { secret: "PQ9X", nom: "Tel" };
  const a = await preparer({ appairage, cible: { id: "ibiprojo-c-K7M2" } });
  a.canal.recevoir(await a.helloTV());
  await attendre(() => a.s.etat === "appairage");
  assert.equal("empreinteTV" in a.canal.envoyes[1], false);
  assert.equal(a.canal.envoyes[1].secret, "PQ9X");
  a.canal.recevoir({ v: 1, t: "paired", nomTV: "Salon", idTV: ID_TV });
  await attendre(() => a.s.prete);
  assert.equal(a.evts.find((e) => e.t === "paired").idTV, ID_TV);
  a.s.fermer();

  const b = await preparer({ appairage, cible: { id: "ibiprojo-c-K7M2" } });
  b.canal.recevoir(await b.helloTV());
  await attendre(() => b.s.etat === "appairage");
  b.canal.recevoir({ v: 1, t: "paired", nomTV: "Salon" });
  await attendre(() => b.s.etat === "fermee");
  assert.ok(b.evts.some((e) => e.t === "fin" && e.raison === "auth"));
});

test("pair_refused → fin « refus » avec la raison", async () => {
  const a = await preparer({ appairage: { secret: "PQ9X", nom: "Tel" }, cible: { id: "ibiprojo-c-K7M2" } });
  a.canal.recevoir(await a.helloTV());
  await attendre(() => a.s.etat === "appairage");
  a.canal.recevoir({ v: 1, t: "pair_refused", raison: "trop_d_essais" });
  await attendre(() => a.s.etat === "fermee");
  assert.ok(a.evts.some((e) => e.t === "fin" && e.raison === "refus" && e.refus === "trop_d_essais"));
});

test("versions : erreur {version, vMax} et hello TV d'une autre version", async () => {
  const a = await preparer();
  a.canal.recevoir({ v: 0, t: "erreur", code: "version", vMax: 0 });
  await attendre(() => a.s.etat === "fermee");
  assert.ok(a.evts.some((e) => e.t === "version" && e.verdict === "maj_tv"));
  const b = await preparer();
  b.canal.recevoir(await b.helloTV({ v: 2 }));
  await attendre(() => b.s.etat === "fermee");
  assert.ok(b.evts.some((e) => e.t === "version" && e.verdict === "recharger"));
  assert.ok(b.evts.some((e) => e.t === "fin" && e.raison === "version"));
});

test("revoked après authentification → fin « revoked »", async () => {
  const a = await preparer();
  a.canal.recevoir(await a.helloTV());
  a.canal.recevoir({ v: 1, t: "revoked" }); // traité APRÈS la vérification du hello (file ordonnée)
  await attendre(() => a.s.etat === "fermee");
  assert.ok(a.evts.some((e) => e.t === "fin" && e.raison === "revoked"));
});

test("EXPIRE du serveur → TV injoignable ; délai de connexion dépassé → injoignable", async () => {
  const a = await preparer();
  a.signal.injecter({ type: "EXPIRE", src: ID_TV, dst: a.identite.id });
  await attendre(() => a.s.etat === "fermee");
  assert.ok(a.evts.some((e) => e.t === "fin" && e.raison === "injoignable"));
  const b = await preparer();
  await attendre(() => b.s.etat === "fermee", 3000);
  assert.ok(b.evts.some((e) => e.t === "fin" && e.raison === "injoignable"));
});

test("pilotage : fonctions du hello, messages applis / réglages transmis seulement une fois prête", async () => {
  // TV ancienne : pas de champ fonctions → aucune
  const a = await preparer();
  a.canal.recevoir(await a.helloTV());
  await attendre(() => a.s.prete);
  assert.deepEqual(a.evts.find((e) => e.t === "tv").fonctions, []);
  a.s.fermer();

  const b = await preparer();
  // Avant le hello vérifié : ignorés
  b.canal.recevoir({ v: 1, t: "apps", liste: [{ id: "x", nom: "X", favori: false, maj: 1 }] });
  b.canal.recevoir(await b.helloTV({ fonctions: ["apps", "reglages", "inconnue"] }));
  await attendre(() => b.s.prete);
  assert.deepEqual(b.evts.find((e) => e.t === "tv").fonctions, ["apps", "reglages"]);
  b.canal.recevoir({ v: 1, t: "apps", liste: [{ id: "com.a", nom: "A", favori: true, maj: 3 }, { id: 5 }] });
  b.canal.recevoir({ v: 1, t: "app_image", id: "com.a", maj: 3, forme: "icone", img: "data:image/png;base64,iVBORw0KGgo=" });
  b.canal.recevoir({ v: 1, t: "app_ouverte", id: "com.a", ok: true });
  b.canal.recevoir({ v: 1, t: "reglages", items: [{ cle: "muet", type: "interrupteur", groupe: "Son", titre: "Muet", valeur: true }] });
  await attendre(() => b.evts.some((e) => e.t === "reglages"));
  assert.equal(b.evts.filter((e) => e.t === "apps").length, 1, "liste reçue avant le hello ignorée");
  assert.deepEqual(b.evts.find((e) => e.t === "apps").liste, [{ id: "com.a", nom: "A", favori: true, maj: 3 }]);
  assert.equal(b.evts.find((e) => e.t === "app_image").forme, "icone");
  assert.deepEqual(b.evts.find((e) => e.t === "app_ouverte"), { t: "app_ouverte", id: "com.a", ok: true });
  assert.equal(b.evts.find((e) => e.t === "reglages").items[0].valeur, true);
  // Envois
  assert.ok(b.s.envoyer("apps_images", { ids: ["com.a"] }));
  assert.ok(b.s.envoyer("reglage", { cle: "muet", valeur: false }));
  assert.deepEqual(b.canal.envoyes.slice(-2), [
    { v: 1, t: "apps_images", ids: ["com.a"] }, { v: 1, t: "reglage", cle: "muet", valeur: false },
  ]);
  b.s.fermer();
});

test("envoyer sur la TV : liens et lien_ouvert transmis une fois prête ; liens_etat et lien_ouvrir envoyés", async () => {
  const { s, canal, helloTV, evts } = await preparer();
  canal.recevoir({ v: 1, t: "liens", vpn: true, seulementVpn: true, appliPages: "VYPN" });
  assert.equal(s.envoyer("lien_ouvrir", { url: "https://a.fr/" }), false, "rien avant l'authentification");
  canal.recevoir(await helloTV({ fonctions: ["apps", "liens"] }));
  await attendre(() => s.prete);
  assert.deepEqual(evts.find((e) => e.t === "tv").fonctions, ["apps", "liens"]);
  canal.recevoir({ v: 1, t: "liens", vpn: false, seulementVpn: true, appliPages: "VYPN", appliVpn: "VYPN",
    raccourci: { adresse: "http://192.168.1.42:8642/envoyer?c=K7Q2M9XD" } });
  canal.recevoir({ v: 1, t: "lien_ouvert", url: "https://a.fr/", ok: false, raison: "vpn_coupe" });
  canal.recevoir({ v: 1, t: "lien_ouvert", ok: true }); // sans url : ignoré
  await attendre(() => evts.some((e) => e.t === "lien_ouvert"));
  await new Promise((r) => setTimeout(r, 20));
  assert.equal(evts.filter((e) => e.t === "liens").length, 1, "état reçu avant le hello ignoré");
  assert.deepEqual(evts.find((e) => e.t === "liens"), {
    t: "liens", vpn: false, seulementVpn: true, appliPages: "VYPN", appliVpn: "VYPN",
    raccourci: { adresse: "http://192.168.1.42:8642/envoyer?c=K7Q2M9XD" },
  });
  assert.deepEqual(evts.filter((e) => e.t === "lien_ouvert"), [{ t: "lien_ouvert", url: "https://a.fr/", ok: false, raison: "vpn_coupe" }]);
  assert.ok(s.envoyer("liens_etat"));
  assert.ok(s.envoyer("lien_ouvrir", { url: "https://a.fr/", vpnDabord: true }));
  assert.deepEqual(canal.envoyes.slice(-2), [
    { v: 1, t: "liens_etat" }, { v: 1, t: "lien_ouvrir", url: "https://a.fr/", vpnDabord: true },
  ]);
  s.fermer();
});

test("design de la TV : annoncé dans hello, puis poussé (seulement une fois prête)", async () => {
  // TV ancienne : pas de champ design → null (thème par défaut)
  const a = await preparer();
  a.canal.recevoir(await a.helloTV());
  await attendre(() => a.s.prete);
  assert.equal(a.evts.find((e) => e.t === "tv").design, null);
  a.s.fermer();

  const b = await preparer();
  b.canal.recevoir({ v: 1, t: "design", id: "une" }); // avant le hello vérifié : ignoré
  b.canal.recevoir(await b.helloTV({ design: "galerie" }));
  await attendre(() => b.s.prete);
  assert.equal(b.evts.find((e) => e.t === "tv").design, "galerie");
  b.canal.recevoir({ v: 1, t: "design", id: "pastel" });
  b.canal.recevoir({ v: 1, t: "design" }); // sans id : ignoré
  await attendre(() => b.evts.some((e) => e.t === "design"));
  await new Promise((r) => setTimeout(r, 20));
  assert.deepEqual(b.evts.filter((e) => e.t === "design"), [{ t: "design", id: "pastel" }]);
  b.s.fermer();
});

test("design : ignoré pendant l'appairage (avant paired)", async () => {
  const a = await preparer({ appairage: { secret: "PQ9X", nom: "Tel" }, cible: { id: "ibiprojo-c-K7M2" } });
  a.canal.recevoir(await a.helloTV({ design: "studio" }));
  await attendre(() => a.s.etat === "appairage");
  assert.equal(a.evts.find((e) => e.t === "tv").design, "studio");
  a.canal.recevoir({ v: 1, t: "design", id: "verre" });
  a.canal.recevoir({ v: 1, t: "paired", nomTV: "Salon", idTV: ID_TV });
  await attendre(() => a.s.prete);
  assert.ok(!a.evts.some((e) => e.t === "design"));
  a.s.fermer();
});
