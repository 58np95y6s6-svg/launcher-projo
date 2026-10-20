// Fausse « TV » iBiProjo pour les tests de bout en bout : implémente le côté TV du protocole
// (docs/telecommande.md) en JavaScript, INDÉPENDAMMENT du code de la PWA (aucun import),
// comme le fera l'app Android : signalisation PeerJS brute, réponse WebRTC, hello signés,
// appairage (QR et code de secours), commandes, révocation.
//
// Paramètres d'URL : ?ws=<port du faux serveur PeerJS>&nom=<nom de la TV>&vmax=<version max>

const params = new URLSearchParams(location.search);
const PORT = params.get("ws");
const NOM_TV = params.get("nom") || "Salon";
const V_MAX = Number(params.get("vmax") || 1);
const V_HELLO = Number(params.get("vhello") || 1); // version annoncée dans le hello TV
const VERSION_APP = "test-1.0";
const ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

// ---------- Encodages ----------
const b64u = (u8) => btoa(String.fromCharCode(...new Uint8Array(u8))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const deB64u = (s) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));
const hex = (n) => Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) => b.toString(16).padStart(2, "0")).join("");
const utf8 = (s) => new TextEncoder().encode(s);
const ECDSA = { name: "ECDSA", namedCurve: "P-256" };
const SIG = { name: "ECDSA", hash: "SHA-256" };
const empreinteCle = async (spki) => b64u(new Uint8Array(await crypto.subtle.digest("SHA-256", deB64u(spki))).slice(0, 16));
function fpDtls(sdp) {
  const l = [...sdp.matchAll(/^a=fingerprint:(\S+)\s+([0-9A-Fa-f:]+)\s*$/gm)];
  const x = l.find((m) => m[1].toLowerCase() === "sha-256") || l[0];
  return x ? `${x[1].toLowerCase()} ${x[2].toUpperCase()}` : null;
}

// ---------- État de la TV (exposé aux tests) ----------
const tv = (window.tv = {
  pret: false,
  id: "ibiprojo-" + hex(16),
  nom: NOM_TV,
  cleSpki: null,
  empreinte: null,
  appareils: new Map(), // id appareil → { nom, cle }
  recus: [],            // commandes reçues de téléphones authentifiés : {appareil, t, k?, s?}
  ignores: [],          // types des messages ignorés (non authentifiés / non appairés)
  evenements: [],       // types d'événements (pas de contenu)
  demande: null,        // demande d'appairage en attente de confirmation {nom, appareil}
  sessions: new Set(),
  appairage: null,      // { secret, code, expire, utilise, essais: [] }
});
let clePrivee;

const log = (e) => { tv.evenements.push(e); document.getElementById("etat").textContent = tv.evenements.join("\n"); };

// ---------- Signalisation PeerJS ----------
function signalisation(id) {
  return new Promise((ok, ko) => {
    const jeton = hex(8);
    const ws = new WebSocket(`ws://127.0.0.1:${PORT}/peerjs?key=peerjs&id=${encodeURIComponent(id)}&token=${jeton}`);
    const s = { id, ws, envoyer: (type, dst, payload) => ws.send(JSON.stringify({ type, dst, payload })) };
    const hb = setInterval(() => ws.readyState === 1 && ws.send('{"type":"HEARTBEAT"}'), 5000);
    ws.onmessage = (ev) => {
      const m = JSON.parse(ev.data);
      if (m.type === "OPEN") ok(s);
      else if (m.type === "ID-TAKEN" || m.type === "ERROR") ko(new Error(m.type));
      else if (m.type === "OFFER") repondre(s, m);
      else if (m.type === "CANDIDATE") candidatDistant(m);
    };
    ws.onclose = () => clearInterval(hb);
  });
}

const pcs = new Map(); // connectionId → { pc, enAttente: [] }

async function candidatDistant(m) {
  const e = pcs.get(m.payload?.connectionId);
  if (!e) return;
  if (e.pc.remoteDescription) await e.pc.addIceCandidate(m.payload.candidate).catch(() => {});
  else e.enAttente.push(m.payload.candidate);
}

async function repondre(s, m) {
  const { sdp, connectionId } = m.payload;
  const pc = new RTCPeerConnection({ iceServers: [] });
  const e = { pc, enAttente: [] };
  pcs.set(connectionId, e);
  pc.onicecandidate = (ev) => {
    if (ev.candidate && ev.candidate.candidate) {
      s.envoyer("CANDIDATE", m.src, {
        candidate: { candidate: ev.candidate.candidate, sdpMid: ev.candidate.sdpMid, sdpMLineIndex: ev.candidate.sdpMLineIndex },
        type: "data", connectionId,
      });
    }
  };
  pc.ondatachannel = (ev) => {
    if (ev.channel.label !== "telecommande") return ev.channel.close();
    session(pc, ev.channel, s.id);
  };
  await pc.setRemoteDescription(sdp);
  for (const c of e.enAttente.splice(0)) await pc.addIceCandidate(c).catch(() => {});
  const reponse = await pc.createAnswer();
  await pc.setLocalDescription(reponse);
  s.envoyer("ANSWER", m.src, { sdp: { type: "answer", sdp: pc.localDescription.sdp }, type: "data", connectionId });
  log("offre");
}

// ---------- Session DataChannel ----------
function session(pc, canal, viaId) {
  const ses = { pc, canal, appareil: null, auth: false, mode: null, cle: null, nom: null };
  tv.sessions.add(ses);
  const envoyer = (o) => canal.readyState === "open" && canal.send(JSON.stringify({ v: 1, ...o }));
  ses.envoyer = envoyer;
  canal.onclose = () => { tv.sessions.delete(ses); log("fermeture"); };
  canal.onmessage = async (ev) => {
    let m;
    try { m = JSON.parse(ev.data); } catch { return; }
    if (typeof m.t !== "string") return;

    if (!ses.auth) {
      if (m.t !== "hello") { tv.ignores.push(m.t); return; }
      if (!Number.isInteger(m.v) || m.v > V_MAX) {
        envoyer({ t: "erreur", code: "version", vMax: V_MAX });
        log("version");
        return setTimeout(() => canal.close(), 200);
      }
      const fpTel = fpDtls(pc.remoteDescription.sdp);
      const fpTV = fpDtls(pc.localDescription.sdp);
      const connu = tv.appareils.get(m.appareil);
      let cleTel = connu?.cle;
      if (!connu) {
        if (!m.cle) {
          // Appareil inconnu sans clé : il a été révoqué. Hello signé puis « revoked ».
          await envoyerHello(ses, fpTV, fpTel, m.nonce);
          envoyer({ t: "revoked" });
          log("revoque-inconnu");
          return setTimeout(() => canal.close(), 300);
        }
        cleTel = m.cle;
        ses.mode = "appairage";
      }
      const cle = await crypto.subtle.importKey("spki", deB64u(cleTel), ECDSA, false, ["verify"]);
      const ok = await crypto.subtle.verify(SIG, cle, deB64u(m.sig), utf8(`ibiprojo-v1|hello|${fpTel}|${fpTV}|${m.nonce}`));
      if (!ok) { log("hello-invalide"); return canal.close(); }
      ses.appareil = m.appareil;
      ses.cle = cleTel;
      ses.auth = true;
      ses.mode ??= "pilotage";
      await envoyerHello(ses, fpTV, fpTel, m.nonce);
      log(ses.mode === "appairage" ? "hello-appairage" : "hello");
      return;
    }

    if (m.t === "ping") return envoyer({ t: "pong", n: m.n });

    if (ses.mode === "appairage") {
      if (m.t !== "pair_request") { tv.ignores.push(m.t); return; }
      return demandeAppairage(ses, m, viaId);
    }
    if (["key", "text", "submit"].includes(m.t)) {
      const r = { appareil: ses.appareil, t: m.t };
      if (m.k !== undefined) r.k = m.k;
      if (m.s !== undefined) r.s = m.s;
      if (m.curseur !== undefined) r.curseur = m.curseur;
      tv.recus.push(r);
    } else tv.ignores.push(m.t);
  };
}

async function envoyerHello(ses, fpTV, fpTel, nonceTel) {
  const nonce = b64u(crypto.getRandomValues(new Uint8Array(16)));
  const sig = b64u(await crypto.subtle.sign(SIG, clePrivee, utf8(`ibiprojo-v1|hello-tv|${fpTV}|${fpTel}|${nonceTel}|${nonce}`)));
  ses.envoyer({ v: V_HELLO, t: "hello", nonce, sig, nomTV: tv.nom, versionApp: VERSION_APP, cle: tv.cleSpki });
}

function demandeAppairage(ses, m, viaId) {
  const a = tv.appairage;
  const refuser = (raison) => { ses.envoyer({ t: "pair_refused", raison }); log("refus-" + raison); };
  if (!a) return refuser("invalide");
  const maintenant = Date.now();
  a.essais = a.essais.filter((t) => maintenant - t < 60000);
  if (a.bloqueJusqua && maintenant < a.bloqueJusqua) return refuser("trop_d_essais");
  const attendu = viaId === tv.id ? a.secret : a.code.slice(4);
  if (a.utilise || m.secret !== attendu) {
    a.essais.push(maintenant);
    if (a.essais.length >= 5) a.bloqueJusqua = maintenant + 60000;
    return refuser("invalide");
  }
  if (maintenant > a.expire) return refuser("expire");
  if (m.empreinteTV && m.empreinteTV !== tv.empreinte) return refuser("invalide");
  if (m.cle !== ses.cle) return refuser("invalide");
  tv.demande = { ses, nom: m.nom, appareil: ses.appareil };
  log("demande");
}

/** Réponse de l'utilisateur à « Autoriser le téléphone de … ? ». */
tv.confirmer = (accepter) => {
  const d = tv.demande;
  if (!d) return false;
  tv.demande = null;
  if (!accepter) { d.ses.envoyer({ t: "pair_refused", raison: "refuse" }); log("refus-refuse"); return true; }
  tv.appareils.set(d.appareil, { nom: d.nom, cle: d.ses.cle });
  tv.appairage.utilise = true;
  d.ses.mode = "pilotage";
  d.ses.envoyer({ t: "paired", nomTV: tv.nom, idTV: tv.id });
  log("appaire");
  return true;
};

/** Lance l'appairage : renvoie le fragment du QR code et le code de secours. */
tv.demarrerAppairage = async () => {
  const secret = b64u(crypto.getRandomValues(new Uint8Array(16)));
  const r = crypto.getRandomValues(new Uint8Array(8));
  const code = Array.from(r, (b) => ALPHABET[b % ALPHABET.length]).join("");
  tv.appairage = { secret, code, expire: Date.now() + 5 * 60000, utilise: false, essais: [] };
  if (!tv.signalCode) tv.signalCode = await signalisation(`ibiprojo-c-${code.slice(0, 4)}`);
  return { fragment: `#pair=${tv.id}.${secret}.${tv.empreinte}`, code: `${code.slice(0, 4)}-${code.slice(4)}` };
};

/** Envoie un message à toutes les sessions de pilotage authentifiées. */
tv.envoyer = (o) => {
  let n = 0;
  for (const s of tv.sessions) if (s.auth && s.mode === "pilotage") { s.envoyer(o); n++; }
  return n;
};

tv.revoquer = (appareil) => {
  tv.appareils.delete(appareil);
  for (const s of tv.sessions) if (s.appareil === appareil) { s.envoyer({ t: "revoked" }); setTimeout(() => s.canal.close(), 200); }
  log("revocation");
};

/** Coupe toutes les connexions WebRTC (simulation de perte du réseau local). */
tv.couper = () => { for (const s of tv.sessions) { s.pc.close(); } tv.sessions.clear(); log("coupure"); };

(async () => {
  const paire = await crypto.subtle.generateKey(ECDSA, false, ["sign", "verify"]);
  clePrivee = paire.privateKey;
  tv.cleSpki = b64u(await crypto.subtle.exportKey("spki", paire.publicKey));
  tv.empreinte = await empreinteCle(tv.cleSpki);
  tv.signal = await signalisation(tv.id);
  tv.pret = true;
  log("prete");
})();
