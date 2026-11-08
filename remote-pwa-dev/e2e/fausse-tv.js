// Fausse « TV » iBiTV pour les tests de bout en bout : implémente le côté TV du protocole
// (docs/telecommande.md) en JavaScript, INDÉPENDAMMENT du code de la PWA (aucun import),
// comme le fera l'app Android : signalisation PeerJS brute, réponse WebRTC, hello signés,
// appairage (QR et code de secours), commandes, révocation, pilotage (applis et réglages),
// Envoyer sur la TV (liens : VPN, raccourci iOS).
//
// Paramètres d'URL : ?ws=<port du faux serveur PeerJS>&nom=<nom de la TV>&vmax=<version max>
//   &fonctions=apps,reglages,liens (défaut) | aucune (TV ancienne : pas de champ fonctions, messages ignorés)
//   &design=verre (défaut) | galerie | pastel… | aucun (TV ancienne : ni `design` dans hello, ni message `design`)

const params = new URLSearchParams(location.search);
const PORT = params.get("ws");
const NOM_TV = params.get("nom") || "Salon";
const V_MAX = Number(params.get("vmax") || 1);
const V_HELLO = Number(params.get("vhello") || 1); // version annoncée dans le hello TV
const VERSION_APP = "test-1.0";
const FONCTIONS = (params.get("fonctions") ?? "apps,reglages,liens").split(",").filter((f) => f && f !== "aucune");
const AVEC_LIENS = FONCTIONS.includes("liens");
const DESIGN = params.get("design") ?? "verre";
const AVEC_DESIGN = DESIGN !== "aucun";
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
  recus: [],            // commandes reçues de téléphones authentifiés : {appareil, t, k?, s?, ids?, id?, cle?, valeur?, bouton?, url?…}
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
    } else if (PILOTAGE[m.t] && FONCTIONS.length && (AVEC_LIENS || !m.t.startsWith("lien"))) {
      const r = { appareil: ses.appareil, t: m.t };
      for (const c of ["ids", "id", "cle", "valeur", "bouton", "url", "titre", "sansVpn", "vpnDabord"]) if (m[c] !== undefined) r[c] = m[c];
      tv.recus.push(r);
      PILOTAGE[m.t](ses, m);
    } else tv.ignores.push(m.t);
  };
}

async function envoyerHello(ses, fpTV, fpTel, nonceTel) {
  const nonce = b64u(crypto.getRandomValues(new Uint8Array(16)));
  const sig = b64u(await crypto.subtle.sign(SIG, clePrivee, utf8(`ibiprojo-v1|hello-tv|${fpTV}|${fpTel}|${nonceTel}|${nonce}`)));
  const hello = { v: V_HELLO, t: "hello", nonce, sig, nomTV: tv.nom, versionApp: VERSION_APP, cle: tv.cleSpki };
  if (FONCTIONS.length) hello.fonctions = FONCTIONS;
  if (AVEC_DESIGN) hello.design = R.design;
  ses.envoyer(hello);
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

// ---------- Pilotage : applis et réglages (données factices) ----------

// Applis : favoris d'abord (ordre de l'accueil), puis les autres par ordre alphabétique.
// Images dessinées ici (texte et formes simples, pas de logos) : bannière 240×135 ou icône 96×96.
const APPS = [
  { id: "com.netflix.ninja", nom: "Netflix", favori: true, forme: "banniere", fond: ["#141414", "#141414"], texte: "NETFLIX", encre: "#e50914" },
  { id: "com.amazon.amazonvideo.livingroom", nom: "Prime Video", favori: true, forme: "banniere", fond: ["#0f79af", "#00a8e1"], texte: "prime video", encre: "#fff" },
  { id: "com.disney.disneyplus", nom: "Disney+", favori: true, forme: "banniere", fond: ["#0b1e5b", "#123b9b"], texte: "Disney+", encre: "#fff" },
  { id: "fr.projo.ibitube", nom: "iBiTube", favori: true, forme: "banniere", fond: ["#0a1020", "#0a1020"], texte: ["iBi", "Tube"], encre: ["#1e90ff", "#fff"] },
  { id: "tv.arte.plus7", nom: "Arte", forme: "icone", fond: ["#ff4f00", "#ff4f00"], glyphe: "a" },
  { id: "com.canal.android.canal", nom: "CANAL+", forme: "banniere", fond: ["#000", "#000"], texte: "CANAL+", encre: "#fff" },
  { id: "fr.francetv.pluzz", nom: "france.tv", forme: "banniere", fond: ["#1f2f6b", "#3256c8"], texte: "france.tv", encre: "#fff" },
  { id: "org.xbmc.kodi", nom: "Kodi", forme: "icone", fond: ["#31a6dc", "#17608a"], glyphe: "losanges" },
  { id: "com.plexapp.android", nom: "Plex", forme: "icone", fond: ["#e5a00d", "#e5a00d"], glyphe: "P", encre: "#000" },
  { id: "com.spotify.tv.android", nom: "Spotify", forme: "banniere", fond: ["#1db954", "#1db954"], texte: "Spotify", encre: "#000" },
  { id: "tv.twitch.android.app", nom: "Twitch", forme: "icone", fond: ["#6441a5", "#6441a5"], glyphe: "t" },
  { id: "org.videolan.vlc", nom: "VLC", forme: "icone", fond: ["#ff8800", "#e85d00"], glyphe: "cone" },
].map((a, i) => ({ ...a, maj: 1730000000000 + i * 86400000 }));

function dessiner(a) {
  const [l, hh] = a.forme === "banniere" ? [240, 135] : [96, 96];
  const c = document.createElement("canvas");
  c.width = l; c.height = hh;
  const g = c.getContext("2d");
  const d = g.createLinearGradient(0, 0, l, hh);
  d.addColorStop(0, a.fond[0]); d.addColorStop(1, a.fond[1]);
  g.fillStyle = d;
  g.fillRect(0, 0, l, hh);
  g.fillStyle = a.encre || "#fff";
  g.strokeStyle = a.encre || "#fff";
  g.textAlign = "center"; g.textBaseline = "middle";
  if (a.forme === "banniere") {
    const morceaux = [a.texte].flat(), encres = [a.encre].flat();
    g.font = `700 ${morceaux.join("").length > 8 ? 30 : 36}px system-ui, sans-serif`;
    g.textAlign = "left";
    let x = (l - morceaux.reduce((s, m) => s + g.measureText(m).width, 0)) / 2;
    morceaux.forEach((m, i) => { g.fillStyle = encres[i] || encres[0]; g.fillText(m, x, hh / 2 + 2); x += g.measureText(m).width; });
  } else {
    g.lineWidth = 6; g.lineCap = "round";
    const m = l / 2;
    switch (a.glyphe) {
      case "losanges":
        g.translate(m, m); g.rotate(Math.PI / 4);
        for (const [x, y] of [[-16, -16], [4, -16], [-16, 4], [4, 4]]) g.fillRect(x, y, 14, 14);
        g.setTransform(1, 0, 0, 1, 0, 0);
        break;
      case "cone":
        g.beginPath(); g.moveTo(m, 16); g.lineTo(72, 76); g.lineTo(24, 76); g.closePath(); g.fill();
        g.fillStyle = a.fond[1]; g.fillRect(34, 46, 28, 6); g.fillRect(28, 60, 40, 6);
        break;
      default:
        g.font = "700 46px system-ui, sans-serif";
        g.fillText(a.glyphe, m, m + 3);
    }
  }
  return c.toDataURL("image/webp", 0.9);
}
const IMAGES = new Map(); // id → URL data: (dessinée à la première demande)
const image = (a) => { if (!IMAGES.has(a.id)) IMAGES.set(a.id, dessiner(a)); return IMAGES.get(a.id); };

// Réglages : état de la TV, décrit avec les types génériques
const R = {
  volume: 9, muet: false, design: AVEC_DESIGN ? DESIGN : "verre", nouveautes: true,
  ambiance_auto: true, ambiance_intensite: "normale", ambiance_couleurs: "pochette", ambiance_decalage: "100",
  maj: { etape: "a_jour", progression: undefined },
};
const VERSION_INSTALLEE = "2.8";
const VERSION_NOUVELLE = "2.9";
const DESIGNS = [
  { id: "verre", nom: "Verre" }, { id: "pastel", nom: "Pastel" }, { id: "console", nom: "Console" },
  { id: "une", nom: "À la une" }, { id: "chaines", nom: "Chaînes" }, { id: "studio", nom: "Studio" },
  { id: "galerie", nom: "Galerie" },
];

function itemsReglages() {
  const maj = { cle: "maj", type: "boutons", groupe: "Mises à jour", titre: `iBiTV ${VERSION_INSTALLEE}` };
  const rechercher = { id: "verifier", nom: "Rechercher" };
  const installer = { id: "installer", nom: `Installer la ${VERSION_NOUVELLE}`, principal: true };
  switch (R.maj.etape) {
    case "a_jour":
      maj.detail = "À jour";
      maj.boutons = [{ ...rechercher, nom: "Rechercher une mise à jour", principal: true }];
      break;
    case "verification":
      maj.detail = "Recherche d'une mise à jour…";
      maj.boutons = [{ ...rechercher, nom: "Rechercher une mise à jour", principal: true }];
      break;
    case "disponible":
      maj.titre = `iBiTV ${VERSION_INSTALLEE} → ${VERSION_NOUVELLE}`;
      maj.detail = "Nouvelle version disponible";
      maj.boutons = [rechercher, installer];
      break;
    case "telechargement":
      maj.titre = `iBiTV ${VERSION_INSTALLEE} → ${VERSION_NOUVELLE}`;
      maj.detail = `Téléchargement… ${Math.round(R.maj.progression * 100)} %`;
      maj.boutons = [rechercher, installer];
      maj.progression = R.maj.progression;
      break;
    case "confirmation":
      maj.titre = `iBiTV ${VERSION_INSTALLEE} → ${VERSION_NOUVELLE}`;
      maj.detail = "Prête : confirmez l'installation sur la TV";
      maj.boutons = [rechercher, installer];
      break;
  }
  const opt = (...l) => l.map(([id, nom]) => ({ id, nom }));
  return [
    { cle: "volume", type: "curseur", groupe: "Son", titre: "Volume", min: 0, max: 15, valeur: R.volume },
    { cle: "muet", type: "interrupteur", groupe: "Son", titre: "Couper le son", valeur: R.muet },
    { cle: "design", type: "choix", groupe: "Affichage", titre: "Design de l'accueil", valeur: R.design, options: DESIGNS },
    { cle: "nouveautes", type: "interrupteur", groupe: "Affichage", titre: "Affiches des nouveautés", detail: "Films et séries récents en fond", valeur: R.nouveautes },
    { cle: "ambiance_auto", type: "interrupteur", groupe: "Mode Ambiance", titre: "Avec les applis de musique", detail: "Néon autour de l'écran", valeur: R.ambiance_auto },
    { cle: "ambiance_intensite", type: "choix", groupe: "Mode Ambiance", titre: "Intensité", valeur: R.ambiance_intensite,
      options: opt(["douce", "Douce"], ["normale", "Normale"], ["forte", "Forte"]), actif: R.ambiance_auto },
    { cle: "ambiance_couleurs", type: "choix", groupe: "Mode Ambiance", titre: "Couleurs du néon", valeur: R.ambiance_couleurs,
      options: opt(["pochette", "Pochette"], ["arc_en_ciel", "Arc-en-ciel"]), actif: R.ambiance_auto },
    { cle: "ambiance_decalage", type: "choix", groupe: "Mode Ambiance", titre: "Synchro avec le son", valeur: R.ambiance_decalage,
      options: opt(["0", "Aucun retard"], ["100", "0,1 s (conseillé)"], ["200", "0,2 s"], ["300", "0,3 s"], ["400", "0,4 s"]), actif: R.ambiance_auto },
    ...(AVEC_LIENS ? itemsLiens() : []),
    maj,
    { cle: "systeme", type: "boutons", groupe: "Ouvrir sur la TV",
      boutons: [{ id: "wifi", nom: "Wi-Fi" }, { id: "bluetooth", nom: "Bluetooth" }, { id: "reglages", nom: "Réglages" }] },
  ];
}

tv.reglages = R;
tv.refuserApps = new Set(); // identifiants dont l'ouverture échoue (ok: false)
tv.ouvertes = [];           // applis ouvertes
tv.systeme = [];            // pages système ouvertes
tv.majPasMs = 250;          // simulation du téléchargement
tv.majArret = null;         // progression où le téléchargement s'arrête (captures)

const envoyerReglages = (ses) => ses.envoyer({ t: "reglages", items: itemsReglages() });
let poussee = null;
/** Changement côté TV : `reglages` aux téléphones abonnés, au plus toutes les 300 ms. */
function pousser() {
  if (poussee) return;
  poussee = setTimeout(() => {
    poussee = null;
    for (const s of tv.sessions) if (s.auth && s.mode === "pilotage" && s.abonne) envoyerReglages(s);
  }, 300);
}
tv.changerReglage = (cle, valeur) => { R[cle] = valeur; pousser(); };

/** Le design de l'accueil change (Réglages de la TV, ou réglage du téléphone) : `design` à CHAQUE
 *  téléphone authentifié, sans abonnement (thème de la télécommande), puis `reglages` aux abonnés. */
function changerDesign(id) {
  if (R.design === id) return;
  R.design = id;
  if (AVEC_DESIGN) tv.envoyer({ t: "design", id });
  log("design");
  pousser();
}
tv.changerDesign = changerDesign;

function lancerMaj(etape) {
  R.maj = { etape };
  if (etape === "verification") setTimeout(() => { R.maj = { etape: "disponible" }; pousser(); }, 700);
  if (etape === "telechargement") {
    R.maj.progression = 0;
    const pas = () => {
      if (R.maj.etape !== "telechargement") return;
      if (tv.majArret !== null && R.maj.progression >= tv.majArret) return;
      R.maj.progression = Math.min(1, Math.round((R.maj.progression + 0.07) * 100) / 100);
      if (R.maj.progression >= 1) R.maj = { etape: "confirmation" };
      pousser();
      setTimeout(pas, tv.majPasMs);
    };
    setTimeout(pas, tv.majPasMs);
  }
}

const PILOTAGE = {
  apps_liste(ses) {
    ses.envoyer({ t: "apps", liste: APPS.map(({ id, nom, favori = false, maj }) => ({ id, nom, favori, maj })) });
  },
  apps_images(ses, m) {
    if (!Array.isArray(m.ids)) return;
    for (const id of m.ids.slice(0, 100)) {
      const a = APPS.find((x) => x.id === id);
      if (a) ses.envoyer({ t: "app_image", id, maj: a.maj, forme: a.forme, img: image(a) });
    }
  },
  app_ouvrir(ses, m) {
    const ok = APPS.some((a) => a.id === m.id) && !tv.refuserApps.has(m.id);
    if (ok) tv.ouvertes.push(m.id);
    ses.envoyer({ t: "app_ouverte", id: m.id, ok });
  },
  reglages_liste(ses) {
    ses.abonne = true;
    envoyerReglages(ses);
  },
  reglage(ses, m) {
    const it = itemsReglages().find((i) => i.cle === m.cle);
    if (it?.cle.startsWith("liens_")) reglageLiens(it, m);
    else if (it?.type === "curseur" && Number.isFinite(m.valeur)) R[m.cle] = Math.min(it.max, Math.max(it.min, Math.round(m.valeur)));
    else if (it?.type === "interrupteur" && typeof m.valeur === "boolean") R[m.cle] = m.valeur;
    else if (it?.cle === "design" && it.options.some((o) => o.id === m.valeur)) changerDesign(m.valeur);
    else if (it?.type === "choix" && it.options.some((o) => o.id === m.valeur)) R[m.cle] = m.valeur;
    else if (it?.cle === "maj" && it.boutons.some((b) => b.id === m.bouton)) lancerMaj(m.bouton === "verifier" ? "verification" : "telechargement");
    else if (it?.cle === "systeme" && it.boutons.some((b) => b.id === m.bouton)) tv.systeme.push(m.bouton);
    // Réponse à chaque `reglage` (même refusé), puis les autres téléphones abonnés
    envoyerReglages(ses);
    for (const s of tv.sessions) if (s !== ses && s.auth && s.mode === "pilotage" && s.abonne) envoyerReglages(s);
  },

  // ---------- Envoyer sur la TV ----------
  liens_etat(ses) {
    ses.abonneLiens = true;
    ses.envoyer(etatLiens());
  },
  lien_ouvrir(ses, m) {
    const repondre = (r) => ses.envoyer({ t: "lien_ouvert", url: typeof m.url === "string" ? m.url : "", ...r });
    const url = lienValable(m.url);
    if (!url) return repondre({ ok: false, raison: "invalide" });
    const force = tv.liensForcer.shift(); // issue imposée par un test
    if (force === "silence") return;
    if (force) return repondre(force);
    const appli = appliPour(url);
    if (!appli) return repondre({ ok: false, raison: "aucune_appli" });
    if (L.seulementVpn && !L.vpn && m.sansVpn !== true) {
      if (m.vpnDabord !== true) return repondre({ ok: false, raison: "vpn_coupe" });
      if (!L.appliVpn) return repondre({ ok: false, raison: "pas_d_appli_vpn" });
      // Une seule attente à la fois : la précédente est remplacée
      if (attenteVpn) {
        clearTimeout(attenteVpn.minuterie);
        attenteVpn.ses.envoyer({ t: "lien_ouvert", url: attenteVpn.url, ok: false, raison: "remplace" });
      }
      const a = { ses, url: m.url, appli };
      a.minuterie = setTimeout(() => {
        if (attenteVpn !== a) return;
        attenteVpn = null;
        a.ses.envoyer({ t: "lien_ouvert", url: a.url, ok: false, raison: "delai_vpn" });
      }, L.attenteMs);
      attenteVpn = a;
      tv.applisVpnOuvertes++;
      log("attente-vpn");
      return repondre({ ok: false, raison: "attente_vpn" });
    }
    tv.liensOuverts.push({ url: m.url, appli, sansVpn: m.sansVpn === true });
    log("lien-ouvert");
    repondre({ ok: true, ouvertDans: appli });
  },
};

// ---------- Envoyer sur la TV : liens (docs/telecommande.md, « Envoyer sur la TV ») ----------

const L = {
  vpn: true,              // un VPN est actif sur la TV
  seulementVpn: true,     // réglage « Seulement avec le VPN » (activé par défaut)
  pages: "auto",          // réglage liens_pages
  applisPages: [{ id: "net.vypn.app", nom: "VYPN" }, { id: "com.amazon.cloud9", nom: "Silk" }],
  appliVpn: "VYPN",       // appli VPN que la TV sait ouvrir ; null : aucune
  raccourci: false,       // réglage liens_raccourci (désactivé par défaut)
  ip: "192.168.1.42",     // adresse IPv4 sur le réseau local ; null : aucune
  code: "K7Q2M9XD",       // code du raccourci
  attenteMs: 60000,       // attente du VPN (vpnDabord)
};
tv.liens = L;
tv.liensOuverts = [];      // liens ouverts : {url, appli, sansVpn}
tv.liensForcer = [];       // issues imposées aux prochains lien_ouvrir : {ok, raison?, ouvertDans?} | "silence"
tv.applisVpnOuvertes = 0;  // « Allumer le VPN » : appli VPN ouverte
let attenteVpn = null;     // lien qui attend le VPN : {ses, url, appli, minuterie}

function lienValable(url) {
  if (typeof url !== "string" || url.length > 2048) return null;
  try {
    const u = new URL(url);
    return (u.protocol === "http:" || u.protocol === "https:") && u.hostname ? u : null;
  } catch { return null; }
}

const nomAppliPages = () => (L.pages === "auto" ? L.applisPages[0] : L.applisPages.find((a) => a.id === L.pages))?.nom ?? "";

/** Appli qui ouvre le lien (règles « Ouverture sur la TV », simplifiées), ou null. */
function appliPour(u) {
  const hote = u.hostname.replace(/^(www|m)\./, "");
  if (hote === "youtube.com" || hote === "youtu.be") return "iBiTube";
  if (hote === "netflix.com") return "Netflix";
  if (/\.(mp4|m4v|mkv|webm|mov|avi|ts|m3u8|mpd)$/i.test(u.pathname)) return "VLC";
  return nomAppliPages() || null;
}

function etatLiens() {
  const e = { t: "liens", vpn: L.vpn, seulementVpn: L.seulementVpn, appliPages: nomAppliPages() };
  if (L.appliVpn) e.appliVpn = L.appliVpn;
  if (L.raccourci && L.ip) e.raccourci = { adresse: `http://${L.ip}:8642/envoyer?c=${L.code}` };
  return e;
}

let pousseeLiens = null;
/** Changement côté TV : `liens` aux téléphones abonnés, au plus toutes les 300 ms. */
function pousserLiens() {
  if (pousseeLiens) return;
  pousseeLiens = setTimeout(() => {
    pousseeLiens = null;
    for (const s of tv.sessions) if (s.auth && s.mode === "pilotage" && s.abonneLiens) s.envoyer(etatLiens());
  }, 300);
}

/** Change l'état (VPN allumé ou coupé, adresse, appli VPN…) ; un VPN qui s'allume ouvre le lien en attente. */
tv.changerLiens = (champs) => {
  Object.assign(L, champs);
  if (L.vpn && attenteVpn) {
    const a = attenteVpn;
    attenteVpn = null;
    clearTimeout(a.minuterie);
    tv.liensOuverts.push({ url: a.url, appli: a.appli, sansVpn: false });
    a.ses.envoyer({ t: "lien_ouvert", url: a.url, ok: true, ouvertDans: a.appli });
  }
  pousserLiens();
};

function itemsLiens() {
  const groupe = "Envoyer sur la TV";
  return [
    { cle: "liens_seulement_vpn", type: "interrupteur", groupe, titre: "Seulement avec le VPN", detail: "N'ouvrir les liens que si un VPN est actif", valeur: L.seulementVpn },
    { cle: "liens_pages", type: "choix", groupe, titre: "Pages web", valeur: L.pages, options: [{ id: "auto", nom: "Automatique" }, ...L.applisPages] },
    { cle: "liens_raccourci", type: "interrupteur", groupe, titre: "Bouton Partager de l'iPhone", detail: "Accepter les liens du raccourci iOS", valeur: L.raccourci },
    { cle: "liens_code", type: "boutons", groupe, titre: "Code du raccourci", detail: "L'ancien code ne marchera plus", boutons: [{ id: "nouveau", nom: "Nouveau code" }] },
  ];
}

function reglageLiens(it, m) {
  if (it.type === "interrupteur" && typeof m.valeur === "boolean") {
    if (m.cle === "liens_seulement_vpn") L.seulementVpn = m.valeur;
    if (m.cle === "liens_raccourci") L.raccourci = m.valeur;
  } else if (it.type === "choix" && it.options.some((o) => o.id === m.valeur)) L.pages = m.valeur;
  else if (m.cle === "liens_code" && m.bouton === "nouveau") {
    L.code = Array.from(crypto.getRandomValues(new Uint8Array(8)), (b) => "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[b % 32]).join("");
  } else return;
  pousserLiens();
}

(async () => {
  const paire = await crypto.subtle.generateKey(ECDSA, false, ["sign", "verify"]);
  clePrivee = paire.privateKey;
  tv.cleSpki = b64u(await crypto.subtle.exportKey("spki", paire.publicKey));
  tv.empreinte = await empreinteCle(tv.cleSpki);
  tv.signal = await signalisation(tv.id);
  tv.pret = true;
  log("prete");
})();
