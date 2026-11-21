// Box score NBA : décodage et validation du message `nba`, champs envoyés, affichage du bandeau ;
// paris suivis (`nba.paris`, texte envoyé, valeurs affichées, bilan d'un ajout) ; combinés
// (`nba.groupes`, `groupe` des lignes, en-têtes de l'accordéon, bilan, résumé, journal).
import { test } from "node:test";
import assert from "node:assert/strict";
import "../../remote-pwa/config.js";
import {
  normaliserNba, normaliserMatch, normaliserEquipe, couleurValable, texteLisible, champsNbaAfficher,
  bandeauVisible, matchDe, equipeMenee, ligneStatut, nomsMatch, texteMatch, annonceAffichage,
  AFFICHAGES, NOMS_AFFICHAGE, ERREURS, MAX_MATCHS, affichagesProposes,
  normaliserParis, normaliserPari, texteAEnvoyer, nombreFr, valeurPari, avancementPari, matchPari, textePari,
  bilanAjout, messageAjout, resumeParis, ETATS_PARI, NOMS_ETAT_PARI, MAX_PARIS, MAX_TEXTE_PARI,
  normaliserCombine, normaliserCombines, lignesCombine, combineOuvert, compteCombine, detailCombine, texteCombine,
  resumeCombines, nouveauCombine, messageAjoutCombine, ETATS_COMBINE, MAX_COMBINES, MAX_LIGNES_COMBINES,
} from "../../remote-pwa/js/nba.js";
import { decoder } from "../../remote-pwa/js/codec.js";
import { lireFonctions, FONCTIONS } from "../../remote-pwa/js/pilotage.js";
import { journal } from "../../remote-pwa/js/journal.js";
import { readFileSync } from "node:fs";

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
  assert.deepEqual(NOMS_AFFICHAGE, { cache: "Caché", resume: "Résumé", details: "Détails", pari: "Pari" });
  assert.deepEqual(champsNbaAfficher("pari", "PHI-IND"), { affichage: "pari", id: "PHI-IND" });
  // « pari » proposé seulement si la TV annonce « nba_paris »
  assert.deepEqual(affichagesProposes(false), ["cache", "resume", "details"]);
  assert.deepEqual(affichagesProposes(true), ["cache", "resume", "details", "pari"]);
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
  assert.equal(annonceAffichage("pari"), "Mon pari affiché sur la TV");
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
  assert.deepEqual(lignes, ["[iBiTV] nba_afficher cache", "[iBiTV] nba_afficher resume", "[iBiTV] nba_afficher details",
    "[iBiTV] nba_afficher pari", "[iBiTV] nba_afficher"]);
});

// ---------- Paris suivis ----------

const PARI = "Philadelphie 76ers\nVainqueur du match\nTyrese Maxey + de 19,5\nNombre de points du joueur (paliers)";
const ligne = (o = {}) => ({ id: "p1", libelle: "T. Maxey · + de 19,5 pts", etat: "en_cours", match: "PHI-IND", valeur: 12, seuil: 19.5, ...o });

test("paris : fonction annoncée, `paris` normalisé dans `nba` (absent : pas de champ)", () => {
  assert.deepEqual(lireFonctions(["nba_paris", "nba", "apps"]), ["apps", "nba", "nba_paris"]);
  // Sans `paris` : l'état ne change pas (TV sans paris suivis)
  assert.equal(Object.hasOwn(normaliserNba(etat()), "paris"), false);
  assert.equal(Object.hasOwn(normaliserNba(etat({ paris: "p1" })), "paris"), false);
  assert.deepEqual(normaliserNba(etat({ paris: [] })).paris, []);
  const e = normaliserNba(etat({ affichage: "pari", paris: [ligne(), ligne({ id: "p2", libelle: "76ers vainqueurs", valeur: -3, seuil: undefined })] }));
  assert.equal(e.affichage, "pari");
  assert.deepEqual(e.paris, [
    { id: "p1", libelle: "T. Maxey · + de 19,5 pts", etat: "en_cours", match: "PHI-IND", valeur: 12, seuil: 19.5 },
    { id: "p2", libelle: "76ers vainqueurs", etat: "en_cours", match: "PHI-IND", valeur: -3 },
  ]);
  // Via le décodeur : champs en trop retirés
  const d = decoder(JSON.stringify({ v: 1, t: "nba", ...etat(), paris: [{ ...ligne(), cote: 1.85, mise: 10 }] }));
  assert.deepEqual(d.paris, [ligne()]);
});

test("paris : lignes invalides écartées, champs facultatifs vérifiés, 20 lignes au plus", () => {
  for (const p of [null, "p1", {}, ligne({ id: undefined }), ligne({ id: "" }), ligne({ id: 3 }), ligne({ id: "x".repeat(65) }),
    ligne({ libelle: undefined }), ligne({ libelle: " \n " }), ligne({ libelle: 12 })]) assert.equal(normaliserPari(p), null, JSON.stringify(p));
  // État inconnu : « attente » (ligne montrée sans conclure) ; tous les états connus gardés
  assert.equal(normaliserPari(ligne({ etat: "rembourse" })).etat, "attente");
  assert.equal(normaliserPari(ligne({ etat: undefined })).etat, "attente");
  for (const x of ETATS_PARI) assert.equal(normaliserPari(ligne({ etat: x })).etat, x);
  // Match, valeur, seuil invalides : ignorés ; libellé nettoyé et borné
  const p = normaliserPari(ligne({ match: "PHI IND", valeur: "12", seuil: Infinity, libelle: "  T. Maxey\n· + de 19,5 pts\u0007 " }));
  assert.deepEqual(p, { id: "p1", libelle: "T. Maxey · + de 19,5 pts", etat: "en_cours" });
  assert.equal(normaliserPari(ligne({ valeur: NaN })).valeur, undefined);
  assert.equal(normaliserPari(ligne({ valeur: 1e9 })).valeur, undefined);
  assert.equal(normaliserPari(ligne({ libelle: "x".repeat(500) })).libelle.length, 160);
  // Doublons écartés, 20 au plus, ordre d'ajout gardé
  const liste = Array.from({ length: 25 }, (_, i) => ligne({ id: `p${i}` }));
  assert.equal(MAX_PARIS, 20);
  assert.deepEqual(normaliserParis([ligne(), ligne(), {}, ...liste]).map((x) => x.id), ["p1", ...liste.slice(0, 26).map((x) => x.id).filter((x) => x !== "p1")].slice(0, 20));
  assert.equal(normaliserParis(null), null);
  assert.equal(normaliserParis({}), null);
});

test("paris : texte envoyé (fins de ligne, blancs, 4000 caractères au plus)", () => {
  assert.equal(texteAEnvoyer(`  \r\n${PARI.replace(/\n/g, "\r\n")}\r\n\r\n  `), PARI);
  for (const x of ["", "  \n\t ", null, undefined, 12]) assert.equal(texteAEnvoyer(x), "", String(x));
  assert.equal(MAX_TEXTE_PARI, 4000);
  assert.equal(texteAEnvoyer("a".repeat(5000)).length, 4000);
  // Pas de caractère coupé en deux à la limite (émoji : deux unités UTF-16)
  const t = texteAEnvoyer("a".repeat(3999) + "🏀🏀");
  assert.equal(t, "a".repeat(3999));
  assert.ok(texteAEnvoyer("a".repeat(3998) + "🏀🏀").endsWith("🏀"));
});

test("paris : valeurs (virgule française), écart du vainqueur, avancement, lecteur d'écran", () => {
  assert.equal(nombreFr(19.5), "19,5");
  assert.equal(nombreFr(12), "12");
  assert.equal(nombreFr(0.25), "0,3");
  assert.equal(nombreFr(-3), "\u22123");
  assert.equal(nombreFr(-0.01), "0");
  assert.equal(valeurPari(ligne()), "12 / 19,5");
  assert.equal(valeurPari(ligne({ valeur: 21.5, seuil: 221.5 })), "21,5 / 221,5");
  assert.equal(valeurPari({ libelle: "76ers vainqueurs", etat: "en_cours", valeur: 2 }), "+2");
  assert.equal(valeurPari({ libelle: "76ers vainqueurs", etat: "en_cours", valeur: -3 }), "\u22123");
  assert.equal(valeurPari({ libelle: "76ers vainqueurs", etat: "en_cours", valeur: 0 }), "0");
  assert.equal(valeurPari({ libelle: "Maxey", etat: "attente", seuil: 19.5 }), "");
  assert.equal(avancementPari(ligne()), 12 / 19.5);
  assert.equal(avancementPari(ligne({ valeur: 30 })), 1);
  assert.equal(avancementPari(ligne({ valeur: -2 })), 0);
  assert.equal(avancementPari(ligne({ seuil: undefined })), null);
  assert.equal(avancementPari(ligne({ seuil: 0 })), null);
  assert.equal(matchPari(ligne()), "PHI – IND");
  assert.equal(matchPari({}), "");
  assert.equal(textePari(ligne()), "T. Maxey · + de 19,5 pts : en cours, 12 sur 19,5");
  assert.equal(textePari({ libelle: "76ers vainqueurs", etat: "gagne", valeur: 4 }), "76ers vainqueurs : gagné, mène de 4 points");
  assert.equal(textePari({ libelle: "76ers vainqueurs", etat: "en_cours", valeur: -1 }), "76ers vainqueurs : en cours, mené de 1 point");
  assert.equal(textePari({ libelle: "76ers vainqueurs", etat: "en_cours", valeur: 0 }), "76ers vainqueurs : en cours, à égalité");
  assert.equal(textePari({ libelle: "Premier panier", etat: "non_suivi" }), "Premier panier : non suivi");
  assert.deepEqual(NOMS_ETAT_PARI, { attente: "En attente", en_cours: "En cours", gagne: "Gagné", perdu: "Perdu", non_suivi: "Non suivi" });
});

test("paris : bilan d'un ajout (lignes nouvelles, suivies ou non)", () => {
  const paris = [ligne(), ligne({ id: "p2" }), ligne({ id: "p3", etat: "gagne" }), ligne({ id: "p4", etat: "non_suivi" })];
  assert.deepEqual(bilanAjout(new Set(), paris), { suivies: 3, nonSuivies: 1 });
  assert.deepEqual(bilanAjout(new Set(["p1", "p2"]), paris), { suivies: 1, nonSuivies: 1 });
  assert.deepEqual(bilanAjout(new Set(["p1", "p2", "p3", "p4"]), paris), { suivies: 0, nonSuivies: 0 });
  assert.equal(messageAjout({ suivies: 3, nonSuivies: 1 }), "3 lignes suivies · 1 non suivie");
  assert.equal(messageAjout({ suivies: 1, nonSuivies: 0 }), "1 ligne suivie");
  assert.equal(messageAjout({ suivies: 4, nonSuivies: 0 }), "4 lignes suivies");
  assert.equal(messageAjout({ suivies: 0, nonSuivies: 2 }), "Aucune ligne suivie · 2 non suivies");
  assert.equal(messageAjout({ suivies: 0, nonSuivies: 0 }), "Aucune nouvelle ligne");
});

test("paris : résumé des lignes (bandeau sans match, Réglages)", () => {
  const l = (...etats) => etats.map((etat, i) => ligne({ id: `p${i}`, etat }));
  assert.equal(resumeParis([]), "");
  assert.equal(resumeParis(l("attente", "attente", "attente", "attente", "non_suivi")), "4 lignes en attente");
  assert.equal(resumeParis(l("attente")), "1 ligne en attente");
  assert.equal(resumeParis(l("gagne")), "1 ligne gagnée");
  assert.equal(resumeParis(l("perdu", "perdu")), "2 lignes perdues");
  assert.equal(resumeParis(l("en_cours", "gagne", "attente", "gagne", "perdu")), "2 gagnées · 1 perdue · 1 en cours · 1 en attente");
  assert.equal(resumeParis(l("non_suivi")), "1 ligne non suivie");
  assert.equal(resumeParis(l("non_suivi", "non_suivi")), "2 lignes non suivies");
});

test("paris : journal sans texte de pari", () => {
  const lignes = [];
  const orig = console.info;
  console.info = (l) => lignes.push(l);
  try {
    journal("nba_paris_ajouter");
    journal("nba_paris_ajouter", PARI);
    journal("nba_paris_effacer");
    journal("nba_paris_effacer", "Tyrese Maxey");
  } finally { console.info = orig; }
  assert.deepEqual(lignes, ["[iBiTV] nba_paris_ajouter", "[iBiTV] nba_paris_ajouter", "[iBiTV] nba_paris_effacer", "[iBiTV] nba_paris_effacer"]);
});

// ---------- Combinés ----------

const combine = (o = {}) => ({ id: "g1", nom: "Combiné 1", cote: "5,10", etat: "en_cours", gagnees: 2, suivies: 5, nonSuivies: 1, ouvert: true, ...o });

test("combinés : fonction annoncée, `groupes` et `groupe` des lignes normalisés (absents : liste à plat)", () => {
  assert.ok(FONCTIONS.includes("nba_paris_groupes"));
  assert.deepEqual(lireFonctions(["nba_paris_groupes", "nba_paris", "nba"]), ["nba", "nba_paris", "nba_paris_groupes"]);
  // Sans `groupes` : ni champ, ni groupe perdu sur les lignes
  assert.equal(Object.hasOwn(normaliserNba(etat({ paris: [] })), "groupes"), false);
  assert.equal(Object.hasOwn(normaliserNba(etat({ paris: [], groupes: "g1" })), "groupes"), false);
  const e = normaliserNba(etat({
    affichage: "pari",
    paris: [ligne({ groupe: "g1" }), ligne({ id: "p2", groupe: "g2", etat: "gagne" }), ligne({ id: "p3", groupe: 7 })],
    groupes: [combine(), combine({ id: "g2", nom: "Combiné 2", cote: undefined, etat: "gagne", gagnees: 1, suivies: 1, nonSuivies: 0, ouvert: false })],
  }));
  assert.deepEqual(e.paris.map((p) => p.groupe), ["g1", "g2", undefined]);
  assert.deepEqual(e.groupes, [
    { id: "g1", nom: "Combiné 1", cote: "5,10", etat: "en_cours", gagnees: 2, suivies: 5, nonSuivies: 1, ouvert: true },
    { id: "g2", nom: "Combiné 2", etat: "gagne", gagnees: 1, suivies: 1, nonSuivies: 0, ouvert: false },
  ]);
  // Via le décodeur : champs en trop retirés (mise, texte du ticket)
  const d = decoder(JSON.stringify({ v: 1, t: "nba", ...etat(), paris: [{ ...ligne(), groupe: "g1" }], groupes: [{ ...combine(), mise: 10, texte: "MYMATCH" }] }));
  assert.deepEqual(d.groupes, [combine()]);
  assert.equal(d.paris[0].groupe, "g1");
});

test("combinés : invalides écartés, valeurs par défaut, un seul ouvert, 10 combinés et 40 lignes au plus", () => {
  for (const g of [null, "g1", {}, combine({ id: "" }), combine({ id: 3 }), combine({ id: "x".repeat(65) })]) assert.equal(normaliserCombine(g), null, JSON.stringify(g));
  // Nom absent : « Combiné » ; état inconnu : attente ; cote illisible ignorée ; compteurs invalides : 0
  assert.deepEqual(normaliserCombine({ id: "g9", etat: "rembourse", cote: "<b>5</b>", gagnees: 9, suivies: 3, nonSuivies: -1, ouvert: "oui" }),
    { id: "g9", nom: "Combiné", etat: "attente", gagnees: 3, suivies: 3, nonSuivies: 0, ouvert: false });
  assert.deepEqual(normaliserCombine(combine({ nom: "  Combiné\n3 ", cote: " 10,06 ", gagnees: 1.5, suivies: 41 })),
    { id: "g1", nom: "Combiné 3", cote: "10,06", etat: "en_cours", gagnees: 0, suivies: 0, nonSuivies: 1, ouvert: true });
  for (const c of ["5.10", "3,10", "12"]) assert.equal(normaliserCombine(combine({ cote: c })).cote, c);
  for (const c of ["", "cote", "5,10 €", "1,2345", 5.1]) assert.equal(normaliserCombine(combine({ cote: c })).cote, undefined, String(c));
  for (const x of ETATS_COMBINE) assert.equal(normaliserCombine(combine({ etat: x })).etat, x);
  assert.equal(normaliserCombine(combine({ etat: "non_suivi" })).etat, "attente");
  // Doublons écartés, un seul ouvert (le premier), 10 au plus
  const liste = Array.from({ length: 12 }, (_, i) => combine({ id: `g${i}`, ouvert: i >= 3 }));
  assert.equal(MAX_COMBINES, 10);
  const n = normaliserCombines([combine({ id: "g0", ouvert: false }), ...liste]);
  assert.deepEqual(n.map((g) => g.id), liste.slice(0, 10).map((g) => g.id));
  assert.deepEqual(n.filter((g) => g.ouvert).map((g) => g.id), ["g3"]);
  assert.equal(normaliserCombines(null), null);
  assert.deepEqual(normaliserCombines([]), []);
  // 40 lignes au plus avec des combinés ; 20 sans (liste à plat inchangée)
  assert.equal(MAX_LIGNES_COMBINES, 40);
  const lignes = Array.from({ length: 45 }, (_, i) => ligne({ id: `p${i}`, groupe: "g1" }));
  assert.equal(normaliserNba(etat({ paris: lignes, groupes: [combine()] })).paris.length, 40);
  assert.equal(normaliserNba(etat({ paris: lignes })).paris.length, 20);
  assert.equal(normaliserNba(etat({ paris: lignes, groupes: "g1" })).paris.length, 20);
});

test("combinés : lignes d'un combiné, ouvert, en-tête (« 2/5 », cote, non suivies) et lecteur d'écran", () => {
  const paris = [ligne({ groupe: "g1" }), ligne({ id: "p2", groupe: "g2" }), ligne({ id: "p3", groupe: "g1" }), ligne({ id: "p4" })];
  assert.deepEqual(lignesCombine(paris, "g1").map((p) => p.id), ["p1", "p3"]);
  assert.deepEqual(lignesCombine(paris, "g3"), []);
  assert.equal(combineOuvert([combine({ ouvert: false }), combine({ id: "g2", ouvert: true })]), "g2");
  assert.equal(combineOuvert([combine({ ouvert: false })]), null);
  assert.equal(combineOuvert([]), null);
  assert.equal(compteCombine(combine()), "2/5");
  assert.equal(compteCombine(combine({ gagnees: 0, suivies: 0 })), "");
  assert.equal(detailCombine(combine()), "En cours · cote 5,10 · 1 non suivie");
  assert.equal(detailCombine(combine({ etat: "gagne", cote: undefined, nonSuivies: 0 })), "Gagné");
  assert.equal(detailCombine(combine({ etat: "perdu", nonSuivies: 3 })), "Perdu · cote 5,10 · 3 non suivies");
  assert.equal(detailCombine(combine({ etat: "attente", cote: undefined })), "En attente · 1 non suivie");
  assert.equal(texteCombine(combine()), "Combiné 1, en cours, cote 5,10, 2 lignes gagnées sur 5, 1 non suivie");
  assert.equal(texteCombine(combine({ gagnees: 1, suivies: 2, nonSuivies: 0, cote: undefined, etat: "attente" })), "Combiné 1, en attente, 1 ligne gagnée sur 2");
  assert.equal(texteCombine(combine({ gagnees: 0, suivies: 4, nonSuivies: 0 })), "Combiné 1, en cours, cote 5,10, 0 ligne gagnée sur 4");
  assert.equal(texteCombine(combine({ gagnees: 0, suivies: 0, nonSuivies: 2, etat: "attente" })), "Combiné 1, en attente, cote 5,10, 2 non suivies");
});

test("combinés : bilan d'un ajout (« Combiné 3 ajouté · … », « Déjà ajouté ») et résumé du bandeau", () => {
  const groupes = [combine({ ouvert: false }), combine({ id: "g2", nom: "Combiné 2", ouvert: false }), combine({ id: "g3", nom: "Combiné 3", suivies: 5, nonSuivies: 0 })];
  assert.equal(nouveauCombine(new Set(["g1", "g2"]), groupes).id, "g3");
  assert.equal(nouveauCombine(new Set(["g1"]), groupes).id, "g3", "le plus récent");
  assert.equal(nouveauCombine(new Set(["g1", "g2", "g3"]), groupes), null);
  assert.equal(messageAjoutCombine(groupes[2]), "Combiné 3 ajouté · 5 lignes suivies");
  assert.equal(messageAjoutCombine(combine({ nom: "Combiné 3", suivies: 4, nonSuivies: 1 })), "Combiné 3 ajouté · 4 lignes suivies · 1 non suivie");
  assert.equal(messageAjoutCombine(combine({ nom: "Combiné 1", suivies: 1, nonSuivies: 2 })), "Combiné 1 ajouté · 1 ligne suivie · 2 non suivies");
  assert.equal(messageAjoutCombine(combine({ nom: "Combiné 4", suivies: 0, nonSuivies: 2 })), "Combiné 4 ajouté · aucune ligne suivie · 2 non suivies");
  assert.equal(messageAjoutCombine(null), "Déjà ajouté");
  assert.equal(resumeCombines([]), "");
  assert.equal(resumeCombines(groupes.slice(0, 1)), "1 combiné");
  assert.equal(resumeCombines(groupes), "3 combinés");
});

test("combinés : journal sans id de combiné ni texte", () => {
  const lignes = [];
  const orig = console.info;
  console.info = (l) => lignes.push(l);
  try {
    journal("nba_paris_ouvrir");
    journal("nba_paris_effacer");
    journal("nba_paris_ouvrir", "Combiné 2");
    journal("nba_paris_combine", "g1");
  } finally { console.info = orig; }
  assert.deepEqual(lignes, ["[iBiTV] nba_paris_ouvrir", "[iBiTV] nba_paris_effacer", "[iBiTV] nba_paris_ouvrir"]);
  // La PWA ne passe jamais d'id (« g1 » serait un code court accepté) : seuls les noms de messages
  const sources = ["paris-nba.js", "combines-nba.js", "bandeau-nba.js"].map((f) => readFileSync(new URL(`../../remote-pwa/js/${f}`, import.meta.url), "utf8")).join("\n");
  const appels = sources.match(/journal\([^)]*\)/g);
  assert.deepEqual([...new Set(appels)].sort(), ['journal("nba_afficher", affichage)', 'journal("nba_paris_ajouter")', 'journal("nba_paris_effacer")', 'journal("nba_paris_ouvrir")']);
});

test("combinés : nouveau module dans le cache du service worker, version 1.7.0", () => {
  const sw = readFileSync(new URL("../../remote-pwa/sw.js", import.meta.url), "utf8");
  for (const f of ["js/combines-nba.js", "js/paris-nba.js", "js/bandeau-nba.js", "js/nba.js"]) assert.ok(sw.includes(`"${f}"`), f);
  assert.equal(globalThis.IBITV_CONFIG.versionApp, "1.7.0");
});
