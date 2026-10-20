// Service worker : interface disponible hors ligne, mise à jour signalée par une bannière.
// La version vient de config.js : la changer suffit à publier une nouvelle version.
importScripts("config.js");

const VERSION = self.IBIPROJO_CONFIG.versionApp;
const CACHE = `ibiprojo-telecommande-${VERSION}`;
const FICHIERS = [
  "./",
  "index.html",
  "styles.css",
  "config.js",
  "manifest.webmanifest",
  "js/app.js",
  "js/appairage.js",
  "js/codec.js",
  "js/controleur.js",
  "js/crypto.js",
  "js/installation.js",
  "js/journal.js",
  "js/session.js",
  "js/signalisation.js",
  "js/stockage.js",
  "icones/icone.svg",
  "icones/icone-192.png",
  "icones/icone-512.png",
  "icones/icone-maskable-192.png",
  "icones/icone-maskable-512.png",
  "icones/apple-touch-icon.png",
];

self.addEventListener("install", (e) => {
  // Pas de skipWaiting automatique : la page affiche « Nouvelle version — Recharger ».
  e.waitUntil(
    caches.open(CACHE).then((c) => c.addAll(FICHIERS.map((f) => new Request(f, { cache: "reload" })))),
  );
});

self.addEventListener("activate", (e) => {
  e.waitUntil((async () => {
    for (const nom of await caches.keys()) {
      if (nom.startsWith("ibiprojo-telecommande-") && nom !== CACHE) await caches.delete(nom);
    }
    await self.clients.claim();
  })());
});

self.addEventListener("message", (e) => {
  if (e.data && e.data.type === "SKIP_WAITING") self.skipWaiting();
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  // Navigation (y compris avec #pair=…, le fragment n'est jamais transmis) : la page en cache.
  if (req.mode === "navigate") {
    e.respondWith(
      caches.match("index.html", { cacheName: CACHE }).then((r) => r || fetch(req)),
    );
    return;
  }
  e.respondWith(
    caches.match(req, { cacheName: CACHE, ignoreSearch: true }).then((r) => r || fetch(req)),
  );
});
