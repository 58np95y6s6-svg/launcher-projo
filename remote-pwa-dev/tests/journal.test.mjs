// Le journal n'écrit que des types d'événements : jamais de texte saisi, de secret ni de SDP.
import { test } from "node:test";
import assert from "node:assert/strict";
import "../../remote-pwa/config.js";
import { journal } from "../../remote-pwa/js/journal.js";

function capturer(fn) {
  const lignes = [];
  const orig = { info: console.info, debug: console.debug, log: console.log };
  console.info = console.debug = console.log = (...a) => lignes.push(a.join(" "));
  try { fn(); } finally { Object.assign(console, orig); }
  return lignes;
}

test("événements connus seulement, codes courts seulement", () => {
  const l = capturer(() => {
    journal("connexion", "tv");
    journal("appairage_refuse", "trop_d_essais");
    journal("texte_saisi", "motdepasse123");            // événement inconnu : rien
    journal("connexion", "Mot de passe secret !");       // code refusé : événement seul
    journal("connexion", "v=0\r\na=fingerprint:sha-256 AA");
    journal("connexion", { s: "secret" });
  });
  assert.deepEqual(l, ["[iBiProjo] connexion tv", "[iBiProjo] appairage_refuse trop_d_essais", "[iBiProjo] connexion", "[iBiProjo] connexion", "[iBiProjo] connexion"]);
});

test("aucun console.log dans le code de la PWA (hors journal.js)", async () => {
  const { readdir, readFile } = await import("node:fs/promises");
  const dossier = new URL("../../remote-pwa/js/", import.meta.url);
  for (const f of await readdir(dossier)) {
    if (f === "journal.js") continue;
    const src = await readFile(new URL(f, dossier), "utf8");
    assert.doesNotMatch(src, /console\.(log|info|debug|warn|error)/, f);
  }
});
