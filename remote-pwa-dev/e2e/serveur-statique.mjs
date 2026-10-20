// Serveur statique de test :
//   /launcher-projo/…  → remote-pwa/ (même chemin que sur GitHub Pages)
//   /launcher-projo/config.js → configuration de TEST (faux PeerJS local, sans STUN, debug=false)
//   /tv/…              → la fausse TV (remote-pwa-dev/e2e/)
//
// La version de l'app servie peut être changée (test de la bannière de mise à jour).

import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ICI = path.dirname(fileURLToPath(import.meta.url));
const PWA = path.resolve(ICI, "../../remote-pwa");
const TYPES = {
  ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8", ".json": "application/json", ".webmanifest": "application/manifest+json",
  ".png": "image/png", ".svg": "image/svg+xml",
};

export function configTest({ portPeerJS, versionApp = "1.0.0-test", versionProtocole = 1 }) {
  return `globalThis.IBIPROJO_CONFIG = Object.freeze(${JSON.stringify({
    urlBase: "http://127.0.0.1/launcher-projo/",
    versionApp,
    versionProtocole,
    versionProtocoleMin: 1,
    peerjs: { hote: "127.0.0.1", port: portPeerJS, chemin: "/", securise: false, cle: "peerjs", intervalleHeartbeatMs: 5000, delaiOpenMs: 1500 },
    iceServers: [],
    delais: { connexion: 8000, ping: 15000, pongsManquesMax: 2, reconnexion: [500, 1000, 2000, 3000] },
    debug: false,
  })});\n`;
}

export function demarrerServeurStatique({ portPeerJS }) {
  const etat = { versionApp: "1.0.0-test", versionProtocole: 1 };
  const serveur = http.createServer(async (req, res) => {
    const url = new URL(req.url, "http://x");
    let fichier;
    if (url.pathname === "/launcher-projo/config.js") {
      res.writeHead(200, { "content-type": TYPES[".js"], "cache-control": "no-store" });
      return res.end(configTest({ portPeerJS, ...etat }));
    }
    if (url.pathname === "/launcher-projo") { res.writeHead(301, { location: "/launcher-projo/" }); return res.end(); }
    if (url.pathname.startsWith("/launcher-projo/")) {
      const rel = decodeURIComponent(url.pathname.slice("/launcher-projo/".length)) || "index.html";
      fichier = path.resolve(PWA, rel);
      if (!fichier.startsWith(PWA)) { res.writeHead(403); return res.end(); }
    } else if (url.pathname.startsWith("/tv/")) {
      fichier = path.resolve(ICI, decodeURIComponent(url.pathname.slice(4)) || "fausse-tv.html");
      if (!fichier.startsWith(ICI)) { res.writeHead(403); return res.end(); }
    } else { res.writeHead(404); return res.end(); }
    try {
      const contenu = await readFile(fichier);
      res.writeHead(200, { "content-type": TYPES[path.extname(fichier)] || "application/octet-stream", "cache-control": "no-cache" });
      res.end(contenu);
    } catch {
      res.writeHead(404); res.end();
    }
  });
  return new Promise((ok) => serveur.listen(0, "127.0.0.1", () => ok({
    port: serveur.address().port,
    etat,
    fermer: () => new Promise((r) => { serveur.closeAllConnections?.(); serveur.close(() => r()); }),
  })));
}
