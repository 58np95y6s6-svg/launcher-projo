// Faux serveur PeerJS pour les tests : même protocole WebSocket que 0.peerjs.com
// (peerjs-server 1.x), sans aucune dépendance (WebSocket RFC 6455 minimal).
//
//   GET /peerjs?key=peerjs&id=<id>&token=<jeton>  (upgrade WebSocket)
//   → {"type":"OPEN"} | {"type":"ID-TAKEN","payload":{"msg":"ID is taken"}} | {"type":"ERROR",…}
//   Même id + même jeton : la connexion est reprise SANS nouvel OPEN (comme le vrai serveur).
//   OFFER / ANSWER / CANDIDATE / LEAVE / EXPIRE : relayés à « dst » avec « src » ajouté.
//   Destinataire absent : message gardé 5 s, puis {"type":"EXPIRE","src":dst,"dst":src} à l'expéditeur.
//
// Utilisable seul : node faux-peerjs.mjs [port]

import http from "node:http";
import crypto from "node:crypto";

const GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

function trame(texte) {
  const data = Buffer.from(texte, "utf8");
  let entete;
  if (data.length < 126) entete = Buffer.from([0x81, data.length]);
  else if (data.length < 65536) { entete = Buffer.alloc(4); entete[0] = 0x81; entete[1] = 126; entete.writeUInt16BE(data.length, 2); }
  else { entete = Buffer.alloc(10); entete[0] = 0x81; entete[1] = 127; entete.writeBigUInt64BE(BigInt(data.length), 2); }
  return Buffer.concat([entete, data]);
}

class Connexion {
  constructor(socket, surMessage, surFermeture) {
    this.socket = socket;
    this.tampon = Buffer.alloc(0);
    this.fragments = [];
    this.ouverte = true;
    socket.on("data", (d) => { this.tampon = Buffer.concat([this.tampon, d]); this.lire(surMessage); });
    socket.on("close", () => { this.ouverte = false; surFermeture(); });
    socket.on("error", () => {});
  }
  lire(surMessage) {
    for (;;) {
      const b = this.tampon;
      if (b.length < 2) return;
      const fin = (b[0] & 0x80) !== 0, op = b[0] & 0x0f, masque = (b[1] & 0x80) !== 0;
      let long = b[1] & 0x7f, pos = 2;
      if (long === 126) { if (b.length < 4) return; long = b.readUInt16BE(2); pos = 4; }
      else if (long === 127) { if (b.length < 10) return; long = Number(b.readBigUInt64BE(2)); pos = 10; }
      const cle = masque ? b.subarray(pos, pos + 4) : null;
      if (masque) pos += 4;
      if (b.length < pos + long) return;
      const charge = Buffer.from(b.subarray(pos, pos + long));
      if (cle) for (let i = 0; i < charge.length; i++) charge[i] ^= cle[i & 3];
      this.tampon = b.subarray(pos + long);
      if (op === 0x8) { this.fermer(); return; }
      if (op === 0x9) { this.socket.write(Buffer.concat([Buffer.from([0x8a, charge.length]), charge])); continue; }
      if (op === 0xa) continue;
      if (op === 0x1 || op === 0x0) {
        this.fragments.push(charge);
        if (fin) { const t = Buffer.concat(this.fragments).toString("utf8"); this.fragments = []; surMessage(t); }
      }
    }
  }
  envoyer(obj) { if (this.ouverte) this.socket.write(trame(JSON.stringify(obj))); }
  fermer() {
    if (!this.ouverte) return;
    this.ouverte = false;
    try { this.socket.write(Buffer.from([0x88, 0])); } catch {}
    this.socket.end();
    setTimeout(() => this.socket.destroy(), 50);
  }
  couper() { this.ouverte = false; this.socket.destroy(); } // coupure brutale (tests)
}

export function demarrerFauxPeerJS({ port = 0, cle = "peerjs", expirationMs = 5000, journal = () => {} } = {}) {
  const clients = new Map(); // id → { jeton, conn }
  const attente = new Map(); // id → [{ msg, t }]
  const stats = { messages: [] }; // types relayés (pour les vérifications), jamais le contenu

  const serveur = http.createServer((req, res) => {
    // Petite API de test : liste des clients connectés
    if (req.url === "/__clients") { res.writeHead(200, { "content-type": "application/json" }); return res.end(JSON.stringify([...clients.keys()])); }
    res.writeHead(404); res.end();
  });

  serveur.on("upgrade", (req, socket) => {
    const url = new URL(req.url, "http://x");
    if (!url.pathname.endsWith("/peerjs")) { socket.destroy(); return; }
    const accept = crypto.createHash("sha1").update(req.headers["sec-websocket-key"] + GUID).digest("base64");
    socket.write(
      "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
      `Sec-WebSocket-Accept: ${accept}\r\n\r\n`);
    const id = url.searchParams.get("id");
    const jeton = url.searchParams.get("token");
    let conn;
    const surFermeture = () => {
      const c = clients.get(id);
      if (c && c.conn === conn) { clients.delete(id); journal(`fermeture ${id}`); }
    };
    conn = new Connexion(socket, (texte) => surMessage(id, conn, texte), surFermeture);
    if (url.searchParams.get("key") !== cle) {
      conn.envoyer({ type: "ERROR", payload: { msg: "Invalid key provided" } });
      return conn.fermer();
    }
    if (!id || !jeton || !/^[A-Za-z0-9]+(?:[ _-][A-Za-z0-9]+)*$/.test(id)) {
      conn.envoyer({ type: "ERROR", payload: { msg: "No id, token, or key supplied to websocket server" } });
      return conn.fermer();
    }
    const existant = clients.get(id);
    if (existant) {
      if (existant.jeton !== jeton) {
        conn.envoyer({ type: "ID-TAKEN", payload: { msg: "ID is taken" } });
        return conn.fermer();
      }
      // Reprise : même jeton → on remplace la socket, sans OPEN (comportement de peerjs-server)
      const ancien = existant.conn;
      existant.conn = conn;
      ancien.couper();
      journal(`reprise ${id}`);
    } else {
      clients.set(id, { jeton, conn });
      conn.envoyer({ type: "OPEN" });
      journal(`ouverture ${id}`);
    }
    for (const { msg } of attente.get(id) || []) conn.envoyer(msg);
    attente.delete(id);
  });

  function surMessage(id, conn, texte) {
    let m;
    try { m = JSON.parse(texte); } catch { return; }
    if (clients.get(id)?.conn !== conn) return;
    if (m.type === "HEARTBEAT") return;
    if (!["OFFER", "ANSWER", "CANDIDATE", "LEAVE", "EXPIRE"].includes(m.type) || !m.dst) return;
    const msg = { type: m.type, src: id, dst: m.dst, payload: m.payload };
    stats.messages.push({ type: m.type, src: id, dst: m.dst });
    const dest = clients.get(m.dst);
    if (dest) dest.conn.envoyer(msg);
    else if (m.type !== "LEAVE" && m.type !== "EXPIRE") {
      if (!attente.has(m.dst)) attente.set(m.dst, []);
      attente.get(m.dst).push({ msg, t: Date.now() });
    }
  }

  const nettoyage = setInterval(() => {
    const maintenant = Date.now();
    for (const [dst, liste] of attente) {
      const restants = [];
      for (const e of liste) {
        if (maintenant - e.t < expirationMs) { restants.push(e); continue; }
        clients.get(e.msg.src)?.conn.envoyer({ type: "EXPIRE", src: dst, dst: e.msg.src });
      }
      if (restants.length) attente.set(dst, restants); else attente.delete(dst);
    }
  }, 250);

  return new Promise((resoudre) => {
    serveur.listen(port, "127.0.0.1", () => {
      resoudre({
        port: serveur.address().port,
        clients,
        stats,
        /** Coupe brutalement la connexion d'un client (simulation de perte réseau). */
        couper(id) { clients.get(id)?.conn.couper(); clients.delete(id); },
        fermer() {
          clearInterval(nettoyage);
          for (const { conn } of clients.values()) conn.couper();
          return new Promise((r) => serveur.close(() => r()));
        },
      });
    });
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const s = await demarrerFauxPeerJS({ port: Number(process.argv[2] || 9000), journal: console.log });
  console.log(`Faux serveur PeerJS sur ws://127.0.0.1:${s.port}/peerjs`);
}
