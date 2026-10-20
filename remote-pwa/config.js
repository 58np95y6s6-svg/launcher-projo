// Configuration de la télécommande iBiProjo : TOUTES les adresses et versions sont ici.
// Script « classique » : chargé par index.html (balise <script>), par le service worker
// (importScripts) et par les tests Node (import). Il ne contient aucun secret.
globalThis.IBIPROJO_CONFIG = Object.freeze({
  // Adresse publique de la PWA (GitHub Pages). Le QR code de la TV pointe ici.
  urlBase: "https://58np95y6s6-svg.github.io/launcher-projo/",

  // Version de l'interface : la changer déclenche la mise à jour du service worker
  // (bannière « Nouvelle version — Recharger »).
  versionApp: "1.0.0",

  // Version du protocole télécommande (docs/telecommande.md) et plage acceptée.
  versionProtocole: 1,
  versionProtocoleMin: 1,

  // Serveur de mise en relation PeerJS (protocole WebSocket parlé directement).
  // URL : ws(s)://hote:port{chemin}peerjs?key=…&id=…&token=…
  peerjs: Object.freeze({
    hote: "0.peerjs.com",
    port: 443,
    chemin: "/",
    securise: true,
    cle: "peerjs",
    intervalleHeartbeatMs: 5000,
    // Le serveur n'envoie pas OPEN quand on reprend un identifiant avec le même jeton :
    // passé ce délai sans OPEN ni erreur, la connexion est considérée comme ouverte.
    delaiOpenMs: 2500,
  }),

  // Serveurs STUN (pas de TURN : téléphone et TV sont sur le même réseau).
  iceServers: Object.freeze([
    Object.freeze({ urls: ["stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"] }),
  ]),

  // Délais (ms)
  delais: Object.freeze({
    connexion: 20000,      // OFFER → hello vérifié
    ping: 15000,           // intervalle des ping
    pongsManquesMax: 2,    // au-delà : connexion perdue
    reconnexion: Object.freeze([1000, 2000, 5000, 10000, 20000, 30000]),
  }),

  // Journal détaillé (types d'événements seulement, même activé). Faux en production.
  debug: false,
});
