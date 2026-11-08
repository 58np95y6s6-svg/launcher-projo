// Box score NBA : décodage et validation du message `nba`, champs envoyés, affichage du bandeau.
import { test } from "node:test";
import assert from "node:assert/strict";
import "../../remote-pwa/config.js";
import {
  normaliserNba, normaliserMatch, normaliserEquipe, couleurValable, texteLisible, champsNbaAfficher,
  bandeauVisible, matchDe, equipeMenee, ligneStatut, nomsMatch, texteMatch, annonceAffichage,
  AFFICHAGES, NOMS_AFFICHAGE, ERREURS, MAX_MATCHS,
} from "../../remote-pwa/js/nba.js";
import { decoder } from "../../remote-pwa/js/codec.js";
import { lireFonctions } from "../../remote-pwa/js/pilotage.js";
import { journal } from "../../remote-pwa/js/journal.js";

const equipe = (code, score, couleur = "#552583", texte = "#FDB927", nom = code === "LAL" ? "Lakers" : "Celtics") =>
  ({ code, nom, score, couleur, texte });
const match = (id = "LAL-BOS", o = {}) => {
  const [ext, dom] = id.split("-");
  return { id, ext: equipe(ext, 98), dom: equipe(dom, 102, "#007A33", "#FFFFFF"), statut: "4e QT · 2:31", direct: true, ...o };
};
const etat = (o = {}) => ({ matchs: [match()], choisi: "LAL-BOS", affichage: "resume", decalage: 30, ...o });

test("nba : message complet normalisé", () => {
  assert.deepEqual(normaliserNba(etat({ erreur: "source" })), {
    matchs: [{
      id: "LAL-BOS",
      ext: { code: "LAL", nom: "Lakers", score: 98, couleur: "#552583", texte: "#fdb927" },
      dom: { code: "BOS", nom: "Celtics", score: 102, couleur: "#007a33", texte: "#ffffff" },
      statut: "4e QT · 2:31", direct: true,
    }],
    choisi: "LAL-BOS", affichage: "resume", decalage: 30, erreur: "source",
  });
  // Aucun match : choisi null, format caché
  assert.deepEqual(normaliserNba({ matchs: [], choisi: null, affichage: "cache", decalage: 0 }),
    { matchs: [], choisi: null, affichage: "cache", decalage: 0 });
  // Via le décodeur : champs en trop retirés
  const d = decoder(JSON.stringify({ v: 1, t: "nba", ...etat(), x: "<b>", matchs: [{ ...match(), y: 1 }] }));
  assert.equal(d.t, "nba");
  assert.equal(d.x, undefined);
  assert.equal(d.matchs[0].y, undefined);
  assert.equal(d.matchs[0].ext.nom, "Lakers");
});

test("nba : couleurs des badges validées (#rrggbb seulement), lettres déduites du fond", () => {
  for (const c of ["#552583", "#FFFFFF", "#00aaFF"]) assert.equal(couleurValable(c), c.toLowerCase());
  for (const c of ["red", "#fff", "#12345g", "#1234567", "552583", "#552583 ", "url(x)", "#000;background:url(x)",
    "rgb(0,0,0)", "var(--x)", "", null, 0x552583]) assert.equal(couleurValable(c), null, String(c));
  // Fond invalide : badge neutre (couleurs du thème)
  assert.deepEqual(normaliserEquipe(equipe("LAL", 1, "violet", "#FDB927")), { code: "LAL", nom: "Lakers", score: 1, couleur: null, texte: null });
  assert.deepEqual(normaliserEquipe(equipe("LAL", 1, "#552583;color:red", "#FDB927")).couleur, null);
  // Lettres invalides : blanc sur fond foncé, presque noir sur fond clair
  assert.equal(normaliserEquipe(equipe("LAL", 1, "#552583", "jaune")).texte, "#ffffff");
  assert.equal(normaliserEquipe(equipe("LAL", 1, "#FDB927", null)).texte, "#111111");
  assert.equal(texteLisible("#000000"), "#ffffff");
  assert.equal(texteLisible("#ffffff"), "#111111");
  assert.equal(texteLisible("blanc"), null);
});

test("nba : champs manquants ou invalides → match écarté, valeurs par défaut", () => {
  for (const m of [
    null, "LAL-BOS", {}, { ...match(), id: undefined }, { ...match(), id: "LAL BOS" }, { ...match(), id: "LAL-BOS-NYK" },
    { ...match(), id: "<b>-x" }, { ...match(), ext: undefined }, { ...match(), dom: "BOS" },
    { ...match(), ext: { ...equipe("LAL", 98), code: undefined } }, { ...match(), ext: { ...equipe("LAL", 98), code: "L A" } },
    { ...match(), ext: { ...equipe("LAL", 98), score: undefined } }, { ...match(), ext: { ...equipe("LAL", 98), score: "98" } },
    { ...match(), dom: { ...equipe("BOS", 102), score: -1 } }, { ...match(), dom: { ...equipe("BOS", 102), score: 9.5 } },
  ]) assert.equal(normaliserMatch(m), null, JSON.stringify(m));
  // Nom absent : le code ; statut absent : vide ; direct seulement si vrai ; code en capitales
  const m = normaliserMatch({ id: "LAL-BOS", ext: { code: "lal", score: 0 }, dom: { code: "BOS", nom: "  Celtics\n de Boston ", score: 0 }, direct: "oui" });
  assert.deepEqual(m, {
    id: "LAL-BOS", statut: "", direct: false,
    ext: { code: "LAL", nom: "LAL", score: 0, couleur: null, texte: null },
    dom: { code: "BOS", nom: "Celtics de Boston", score: 0, couleur: null, texte: null },
  });
  // Message sans liste de matchs : ignoré
  for (const x of [null, {}, { matchs: "LAL-BOS" }, { matchs: null, affichage: "resume" }]) assert.equal(normaliserNba(x), null);
  for (const brut of ['{"v":1,"t":"nba"}', '{"v":1,"t":"nba","matchs":{}}']) assert.equal(decoder(brut), null, brut);
  // Matchs invalides écartés, doublons aussi ; décalage invalide → 0 ; erreur inconnue ignorée
  const e = normaliserNba({ matchs: [{}, match(), match(), match("GSW-DEN")], choisi: "GSW-DEN", affichage: "details", decalage: -5, erreur: "panne" });
  assert.deepEqual(e.matchs.map((x) => x.id), ["LAL-BOS", "GSW-DEN"]);
  assert.equal(e.choisi, "GSW-DEN");
  assert.equal(e.decalage, 0);
  assert.equal(e.erreur, undefined);
  for (const d of [15.5, "30", 4000, null]) assert.equal(normaliserNba(etat({ decalage: d })).decalage, 0, String(d));
  for (const x of ["toString", "constructor", "__proto__", 1]) assert.equal(normaliserNba(etat({ erreur: x })).erreur, undefined, String(x));
  assert.equal(normaliserNba(etat({ erreur: "accessibilite" })).erreur, "accessibilite");
});

test("nba : 15 matchs au plus", () => {
  const codes = Array.from({ length: 20 }, (_, i) => `T${String.fromCharCode(65 + i)}`);
  const matchs = codes.map((c) => match(`${c}A-${c}B`));
  const e = normaliserNba({ matchs, choisi: "TTA-TTB", affichage: "cache", decalage: 0 });
  assert.equal(MAX_MATCHS, 15);
  assert.equal(e.matchs.length, 15);
  assert.deepEqual(e.matchs.map((x) => x.id), matchs.slice(0, 15).map((x) => x.id));
  // Le choisi au-delà du 15e n'est pas dans la liste : premier match en cours
  assert.equal(e.choisi, "TAA-TAB");
});

test("nba : affichage inconnu → null (aucun segment coché) ; choisi hors liste → premier match en cours", () => {
  for (const a of ["plein", "", "CACHE", null, undefined, 1]) assert.equal(normaliserNba(etat({ affichage: a })).affichage, null, String(a));
  for (const a of AFFICHAGES) assert.equal(normaliserNba(etat({ affichage: a })).affichage, a);
  const fini = match("GSW-DEN", { direct: false, statut: "Terminé" });
  assert.equal(normaliserNba(etat({ matchs: [fini, match()], choisi: "NYK-MIA" })).choisi, "LAL-BOS");
  assert.equal(normaliserNba(etat({ matchs: [fini], choisi: undefined })).choisi, "GSW-DEN");
  assert.equal(normaliserNba(etat({ matchs: [], choisi: "LAL-BOS" })).choisi, null);
});

test("nba_afficher : format, et le match affiché (sauf pour cacher)", () => {
  assert.deepEqual(champsNbaAfficher("resume", "LAL-BOS"), { affichage: "resume", id: "LAL-BOS" });
  assert.deepEqual(champsNbaAfficher("details", "GSW-DEN"), { affichage: "details", id: "GSW-DEN" });
  assert.deepEqual(champsNbaAfficher("cache", "LAL-BOS"), { affichage: "cache" });
  assert.deepEqual(champsNbaAfficher("resume", null), { affichage: "resume" });
  assert.deepEqual(champsNbaAfficher("resume", "pas un id"), { affichage: "resume" });
  assert.deepEqual(NOMS_AFFICHAGE, { cache: "Caché", resume: "Résumé", details: "Détails" });
});

test("bandeau : visible avec un match ou un box score affiché ; score, statut, textes", () => {
  assert.equal(bandeauVisible(null), false);
  assert.equal(bandeauVisible(normaliserNba({ matchs: [], affichage: "cache" })), false);
  assert.equal(bandeauVisible(normaliserNba({ matchs: [], affichage: "resume" })), true);
  assert.equal(bandeauVisible(normaliserNba({ matchs: [], affichage: "futur" })), true);
  assert.equal(bandeauVisible(normaliserNba(etat({ affichage: "cache" }))), true);

  const e = normaliserNba(etat({ matchs: [match(), match("GSW-DEN")], choisi: "GSW-DEN" }));
  assert.equal(matchDe(e).id, "GSW-DEN");
  assert.equal(matchDe(e, "LAL-BOS").id, "LAL-BOS");
  assert.equal(matchDe(e, "NYK-MIA"), null);
  const m = matchDe(e, "LAL-BOS");
  assert.equal(equipeMenee(m), "ext");
  assert.equal(equipeMenee({ ext: { score: 3 }, dom: { score: 1 } }), "dom");
  assert.equal(equipeMenee({ ext: { score: 1 }, dom: { score: 1 } }), null);
  assert.deepEqual(ligneStatut(m), { direct: true, etiquette: "DIRECT", statut: "4e QT · 2:31" });
  assert.deepEqual(ligneStatut({ ...m, direct: false, statut: "Terminé" }), { direct: false, etiquette: "TERMINÉ", statut: "" });
  assert.deepEqual(ligneStatut({ ...m, direct: false, statut: "Terminé après prol." }), { direct: false, etiquette: "TERMINÉ", statut: "Terminé après prol." });
  assert.equal(nomsMatch(m), "Lakers – Celtics");
  assert.equal(texteMatch(m), "Lakers – Celtics, 98 à 102, en direct, 4e QT · 2:31");
  assert.equal(texteMatch({ ...m, direct: false, statut: "Terminé" }), "Lakers – Celtics, 98 à 102, terminé");
  assert.equal(annonceAffichage("resume"), "Box score en résumé sur la TV");
  assert.equal(annonceAffichage("cache"), "Box score caché sur la TV");
  assert.equal(annonceAffichage(null), "");
  assert.equal(ERREURS.accessibilite, "Active l'accessibilité d'iBiTV sur la TV");
  assert.equal(ERREURS.source, "Scores NBA indisponibles pour l'instant");
});

test("nba : fonction annoncée, journal limité au format", () => {
  assert.deepEqual(lireFonctions(["nba", "apps"]), ["apps", "nba"]);
  assert.deepEqual(lireFonctions(["apps", "reglages", "liens", "nba"]), ["apps", "reglages", "liens", "nba"]);
  const lignes = [];
  const orig = console.info;
  console.info = (l) => lignes.push(l);
  try {
    for (const a of AFFICHAGES) journal("nba_afficher", a);
    journal("nba_afficher", "Lakers 98 – 102 Celtics");
    journal("nba_score", "98");
  } finally { console.info = orig; }
  assert.deepEqual(lignes, ["[iBiTV] nba_afficher cache", "[iBiTV] nba_afficher resume", "[iBiTV] nba_afficher details", "[iBiTV] nba_afficher"]);
});
