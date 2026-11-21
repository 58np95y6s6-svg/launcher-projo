// Tests de bout en bout des combinés (box score NBA, « nba_paris_groupes ») : PWA (Chromium, profil
// téléphone) ↔ fausse TV via le faux serveur PeerJS local, en WebRTC réel.
// Un combiné par collage (tickets Winamax réels), accordéon qui suit `ouvert` de la TV,
// `nba_paris_ouvrir`, bilan (« Combiné 3 ajouté · … », « Déjà ajouté »), « Effacer ce combiné » et
// « Tout effacer » avec confirmation, résumé du bandeau (« Mon pari · 3 combinés ») sans décalage,
// petit écran, texte agrandi, zones sûres de l'iPhone, contrastes dans les 8 thèmes ; TV sans
// « nba_paris_groupes » : liste à plat, comme avant ; journaux : noms de messages seulement.

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { demarrerBanc, appairerParQR } from "./banc.mjs";

let banc;
const journaux = [];

before(async () => { banc = await demarrerBanc(); });
after(async () => {
  // Aucun texte de pari, cote, id ou nom de combiné, joueur ni équipe dans les journaux
  const tout = journaux.flat().join("\n");
  try {
    for (const interdit of ["Téléphone de Marie", "MYMATCH", "Maxey", "76ers", "76ERS", "Bucks", "BUCKS", "paliers", "handicap",
      "229,5", "24,5", "3,10", "5,10", "7,85", "Combiné", "combiné", " g1", " g2", " g3", "PHI-MIL", "Wembanyama"]) {
      assert.ok(!tout.includes(interdit), `journal : « ${interdit} » trouvé`);
    }
    assert.ok(!/ERREUR PAGE/.test(tout), tout);
  } finally {
    await banc.fermer();
  }
});

const pause = (ms) => new Promise((r) => setTimeout(r, ms));
const recusDe = async (tv, t) => (await tv.recus()).filter((r) => r.t === t);
const AVEC_PARIS = ["apps", "reglages", "liens", "nba", "nba_paris"];
const AVEC_COMBINES = [...AVEC_PARIS, "nba_paris_groupes"];
const DESIGNS = ["galerie", "verre", "pastel", "console", "une", "chaines", "studio", "nba"];

// Tickets réels de l'utilisateur (Winamax, texte copié depuis la capture du ticket)
const MYMATCH = `MYMATCH (0/2)
3,10
Écart de points (handicap) Philadelphia 76ers -12.5
Nombre de points Plus de 229,5`;
const MAXEY = `PHILADELPHIA 76ERS - MILWAUKEE BUCKS
COTE
5,10
Nombre de points du joueur (paliers) - Tyrese Maxey :
Plus de 24,5
Nombre de rebonds du joueur (paliers) - Tyrese Maxey :
Plus de 2,5`;
// … et un ticket Betclic (sélection puis type de pari), avec une ligne non suivie, la cote et la mise
const BETCLIC = `Philadelphie 76ers
Vainqueur du match
Tyrese Maxey + de 19,5
Nombre de points du joueur (paliers)
Kentavious Caldwell-Pope + de 9,5
Nombre de points du joueur (paliers)
Ivica Zubac + de 2,5
Nombre de passes décisives du joueur (paliers)
Victor Wembanyama
Premier panier du match
Cote totale 7,85
Mise 10,00 €`;

/** Colle un texte dans « Mon pari » et l'envoie ; attend que la TV l'ait reçu. */
async function coller(page, tv, texte) {
  const n = (await recusDe(tv, "nba_paris_ajouter")).length;
  await page.fill("#champ-pari", texte);
  await page.click("#btn-pari-ajouter");
  await tv.page.waitForFunction((k) => window.tv.recus.filter((r) => r.t === "nba_paris_ajouter").length > k, n);
}

/** En-têtes des combinés : [id, état, nom, détail, « 2/5 », ouvert (aria-expanded et corps visible)]. */
const combines = (page) => page.$$eval("#combines-pari > .combine", (l) => l.map((c) => {
  const t = c.querySelector(".combine-tete");
  const compte = t.querySelector(".combine-compte");
  const ouvert = t.getAttribute("aria-expanded") === "true";
  if (ouvert === c.querySelector(".combine-corps").hidden) throw new Error("aria-expanded et corps en désaccord");
  return [c.dataset.id, t.dataset.etat, t.querySelector(".combine-nom").textContent, t.querySelector(".combine-detail").textContent,
    compte.hidden ? "" : compte.textContent, ouvert];
}));
const ouvert = (page) => page.$$eval('#combines-pari .combine-tete[aria-expanded="true"]', (l) => l.map((t) => t.closest(".combine").dataset.id));
const attendreOuvert = (page, id, timeout = 5000) => page.waitForFunction((x) =>
  [...document.querySelectorAll('#combines-pari .combine-tete[aria-expanded="true"]')].map((t) => t.closest(".combine").dataset.id).join() === x, id, { timeout });
/** Lignes d'un combiné : [état, libellé, valeur affichée]. */
const lignesDe = (page, id) => page.$$eval(`#combines-pari .combine[data-id="${id}"] .pari`, (l) => l.map((li) => [
  li.dataset.etat, li.querySelector(".pari-libelle").textContent,
  li.querySelector(".pari-valeur").hidden ? "" : li.querySelector(".pari-valeur").textContent,
]));
const tete = (id) => `#combines-pari .combine[data-id="${id}"] .combine-tete`;
/** Fin des animations (entrée d'une feuille, changement d'onglet) ; « DIRECT » clignote sans fin. */
const finAnimations = (page) => page.waitForFunction(() => document.getAnimations()
  .every((a) => a.playState !== "running" || a.effect?.getTiming().iterations === Infinity));
const focusDe = (page) => page.evaluate(() => {
  const a = document.activeElement;
  return a?.closest(".combine") ? `${a.closest(".combine").dataset.id}:${a.dataset.role}` : a?.id;
});

test("combinés : un combiné par collage, accordéon qui suit la TV, nba_paris_ouvrir, bilan, effacer ce combiné, tout effacer", async () => {
  const tv = await banc.tv({ nom: "Salon", fonctions: AVEC_COMBINES, design: "nba" });
  await tv.nba({ scenario: "combines" });
  const tel = await banc.telephone("iphone");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  await page.locator("#btn-nba-pari:not([hidden])").waitFor();
  const hauteurBandeau = () => page.$eval("#bandeau-nba", (e) => e.getBoundingClientRect().height);
  const hauteurAvant = await hauteurBandeau();

  // Feuille vide : ni liste à plat ni combinés
  await page.click("#btn-nba-pari");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  assert.equal(await page.textContent("#pari-vide"), "Aucun combiné pour l'instant.");
  assert.ok(await page.isHidden("#combines-pari"));
  assert.ok(await page.isHidden("#liste-pari"));
  assert.ok(await page.isHidden("#btn-pari-effacer"));

  // 1er collage (ticket « MyMatch ») : Combiné 1, ouvert, sans cote lue
  await coller(page, tv, MYMATCH);
  assert.deepEqual((await recusDe(tv, "nba_paris_ajouter")).map((r) => r.texte), [MYMATCH]);
  await page.locator("#pari-retour", { hasText: "Combiné 1 ajouté · 2 lignes suivies" }).waitFor();
  assert.equal(await page.textContent("#pari-retour"), "Combiné 1 ajouté · 2 lignes suivies");
  assert.deepEqual(await combines(page), [["g1", "en_cours", "Combiné 1", "En cours", "0/2", true]]);
  assert.deepEqual(await lignesDe(page, "g1"), [
    ["en_cours", "76ers · handicap −12,5", "14 / 12,5"],
    ["en_cours", "Total du match · + de 229,5 pts", "142 / 229,5"],
  ]);
  assert.ok(await page.isHidden("#liste-pari"));
  assert.ok(await page.isVisible("#btn-pari-effacer"));

  // 2e et 3e collages : chaque combiné nouveau s'ouvre, les autres se replient ; cote lue
  await coller(page, tv, MAXEY);
  await page.locator("#pari-retour", { hasText: "Combiné 2 ajouté · 2 lignes suivies" }).waitFor();
  await coller(page, tv, BETCLIC);
  await page.locator("#pari-retour", { hasText: "Combiné 3 ajouté · 4 lignes suivies · 1 non suivie" }).waitFor();
  assert.deepEqual(await combines(page), [
    ["g1", "en_cours", "Combiné 1", "En cours", "0/2", false],
    ["g2", "en_cours", "Combiné 2", "En cours · cote 5,10", "0/2", false],
    ["g3", "en_cours", "Combiné 3", "En cours · cote 7,85 · 1 non suivie", "1/4", true],
  ]);
  assert.deepEqual(await lignesDe(page, "g2"), [
    ["en_cours", "T. Maxey · + de 24,5 pts", "12 / 24,5"],
    ["en_cours", "T. Maxey · + de 2,5 rebonds", "2 / 2,5"],
  ]);
  assert.deepEqual(await lignesDe(page, "g3"), [
    ["en_cours", "76ers vainqueurs", "+14"],
    ["en_cours", "T. Maxey · + de 19,5 pts", "12 / 19,5"],
    ["gagne", "K. Caldwell-Pope · + de 9,5 pts", "11 / 9,5"],
    ["en_cours", "I. Zubac · + de 2,5 passes", "1 / 2,5"],
    ["non_suivi", "Victor Wembanyama · Premier panier du match", ""],
  ]);
  assert.deepEqual(await page.locator('#combines-pari .combine[data-id="g3"] .pari-detail').allTextContents(),
    ["En cours · PHI – MIL", "En cours · PHI – MIL", "Gagné · MEM – LAC", "En cours · MEM – LAC", "Non suivi"]);
  // Accordéon accessible : titres, boutons aria-expanded / aria-controls, régions nommées ; l'ouvert ne se replie pas
  const aria = await page.$$eval("#combines-pari .combine-tete", (l) => l.map((t) => {
    const corps = document.getElementById(t.getAttribute("aria-controls"));
    return [t.parentElement.tagName, t.getAttribute("aria-expanded"), t.getAttribute("aria-disabled"),
      corps?.getAttribute("role"), corps?.getAttribute("aria-labelledby") === t.id, t.getAttribute("aria-label")];
  }));
  assert.deepEqual(aria, [
    ["H3", "false", null, "region", true, "Combiné 1, en cours, 0 ligne gagnée sur 2"],
    ["H3", "false", null, "region", true, "Combiné 2, en cours, cote 5,10, 0 ligne gagnée sur 2"],
    ["H3", "true", "true", "region", true, "Combiné 3, en cours, cote 7,85, 1 ligne gagnée sur 4, 1 non suivie"],
  ]);
  assert.equal(await page.getAttribute("#combines-pari", "aria-label"), "Combinés");
  // Dans l'ouvert : ses lignes, puis « Effacer ce combiné » ; « Tout effacer » en bas
  assert.equal(await page.$eval('.combine[data-id="g3"] .combine-corps', (c) => c.lastElementChild.textContent), "Effacer ce combiné");
  assert.equal(await page.locator(".btn-effacer-combine:visible").count(), 1);
  assert.equal(await page.$eval("#combines-pari", (l) => l.nextElementSibling.nextElementSibling.id), "btn-pari-effacer");
  // « Mon pari » : nombre de combinés ; le bandeau ne bouge pas
  assert.equal(await page.textContent("#nba-pari-compte"), "3");
  assert.equal(await page.getAttribute("#btn-nba-pari", "aria-label"), "Mon pari : 3 combinés");
  assert.equal(await hauteurBandeau(), hauteurAvant);
  assert.equal((await recusDe(tv, "nba_paris_ouvrir")).length, 0, "un ajout n'envoie pas d'ouverture");

  // Même ticket une seconde fois : la TV n'ajoute rien → « Déjà ajouté »
  await coller(page, tv, MAXEY);
  await page.locator("#pari-retour", { hasText: "Lecture du pari…" }).waitFor();
  await page.locator("#pari-retour", { hasText: "Déjà ajouté" }).waitFor({ timeout: 8000 });
  assert.equal((await combines(page)).length, 3);
  assert.deepEqual(await ouvert(page), ["g3"]);

  // En-tête touché : nba_paris_ouvrir, ce combiné s'ouvre et replie les autres ; la TV suit ; focus gardé
  await page.click(tete("g1"));
  assert.deepEqual(await ouvert(page), ["g1"], "ouvert tout de suite");
  await tv.attendreRecu((r) => r.t === "nba_paris_ouvrir" && r.groupe === "g1");
  await tv.page.waitForFunction(() => window.tv.paris.ouvert === "g1");
  assert.equal(await focusDe(page), "g1:tete");
  assert.ok(await page.isVisible('.combine[data-id="g1"] .btn-effacer-combine'));
  assert.ok(await page.isHidden('.combine[data-id="g3"] .combine-corps'));
  // L'ouvert touché à nouveau (aria-disabled : il ne se replie pas) : rien n'est envoyé
  await page.click(tete("g1"), { force: true });
  await pause(300);
  assert.equal((await recusDe(tv, "nba_paris_ouvrir")).length, 1);
  assert.deepEqual(await ouvert(page), ["g1"]);

  // La TV change de combiné ouvert (◀ ▶ de sa télécommande) : l'accordéon suit, le focus ne bouge pas
  await tv.eval(() => { window.tv.paris.ouvert = "g2"; window.tv.changerNba({}); });
  await attendreOuvert(page, "g2");
  assert.equal(await focusDe(page), "g1:tete");

  // La TV ne confirme pas (1,5 s) : retour à son combiné ouvert
  await tv.eval(() => { window.tv.parisSourde = true; });
  await page.click(tete("g3"));
  assert.deepEqual(await ouvert(page), ["g3"]);
  await tv.attendreRecu((r) => r.t === "nba_paris_ouvrir" && r.groupe === "g3");
  await attendreOuvert(page, "g2", 4000);
  await tv.eval(() => { window.tv.parisSourde = false; });

  // Clavier : flèches haut / bas, Début, Fin d'un en-tête à l'autre ; Entrée ouvre
  await page.focus(tete("g1"));
  await page.keyboard.press("ArrowDown");
  assert.equal(await focusDe(page), "g2:tete");
  await page.keyboard.press("End");
  assert.equal(await focusDe(page), "g3:tete");
  await page.keyboard.press("ArrowDown");
  assert.equal(await focusDe(page), "g3:tete");
  await page.keyboard.press("Home");
  assert.equal(await focusDe(page), "g1:tete");
  await page.keyboard.press("Enter");
  await tv.page.waitForFunction(() => window.tv.paris.ouvert === "g1");
  await attendreOuvert(page, "g1");

  // Les valeurs suivent la TV, combinés repliés compris : rebonds de Maxey (2/2), fin de MEM-LAC (perdu)
  await tv.statNba("tyrese maxey", "rebonds", 3);
  await page.locator(`${tete("g2")} .combine-compte`, { hasText: "1/2" }).waitFor();
  assert.deepEqual((await lignesDe(page, "g2"))[1], ["gagne", "T. Maxey · + de 2,5 rebonds", "3 / 2,5"]);
  await tv.eval(() => { window.tv.nba.matchs.find((x) => x.id === "MEM-LAC").direct = false; window.tv.changerNba({}); });
  await page.locator(`${tete("g3")}[data-etat="perdu"]`).waitFor();
  assert.equal(await page.textContent(`${tete("g3")} .combine-detail`), "Perdu · cote 7,85 · 1 non suivie");
  assert.equal(await page.getAttribute(`${tete("g3")} .pari-etat use`, "href"), "#i-croix");
  assert.equal(await page.getAttribute(tete("g3"), "aria-label"), "Combiné 3, perdu, cote 7,85, 1 ligne gagnée sur 4, 1 non suivie");

  // « Effacer ce combiné » : confirmation (Annuler : rien n'est envoyé, focus rendu), puis {groupe}
  await page.click(tete("g2"));
  await tv.page.waitForFunction(() => window.tv.paris.ouvert === "g2");
  await page.click('.combine[data-id="g2"] .btn-effacer-combine');
  await page.locator("#alerte:not([hidden])").waitFor();
  assert.equal(await page.textContent("#alerte-titre"), "Effacer ce combiné ?");
  assert.equal(await page.textContent("#alerte-texte"), "Combiné 2 (2 lignes) sera retiré de la TV.");
  await page.keyboard.press("Escape"); // l'alerte reste au-dessus : la feuille ne se ferme pas
  assert.ok(await page.isVisible("#feuille-pari"));
  await page.click("#alerte-annuler");
  assert.equal(await focusDe(page), "g2:effacer");
  assert.equal((await recusDe(tv, "nba_paris_effacer")).length, 0);
  await page.click('.combine[data-id="g2"] .btn-effacer-combine');
  await page.click("#alerte-ok");
  await tv.attendreRecu((r) => r.t === "nba_paris_effacer" && r.groupe === "g2");
  await page.locator('#combines-pari .combine[data-id="g2"]').waitFor({ state: "detached" });
  assert.equal(await page.textContent("#pari-retour"), "Combiné 2 effacé");
  assert.deepEqual((await combines(page)).map(([id]) => id), ["g1", "g3"]);
  // Focus rendu au combiné voisin ; la TV ouvre le plus récent
  assert.equal(await focusDe(page), "g3:tete");
  await attendreOuvert(page, "g3");
  assert.equal(await page.textContent("#nba-pari-compte"), "2");

  // « Tout effacer » : tous les combinés (sans groupe), après confirmation
  await page.click("#btn-pari-effacer");
  await page.locator("#alerte:not([hidden])").waitFor();
  assert.equal(await page.textContent("#alerte-titre"), "Effacer ton pari ?");
  assert.equal(await page.textContent("#alerte-texte"), "Les 2 combinés seront retirés de la TV.");
  await page.click("#alerte-ok");
  await tv.attendreRecu((r) => r.t === "nba_paris_effacer" && r.groupe === undefined);
  await page.locator("#pari-vide:not([hidden])").waitFor();
  assert.ok(await page.isHidden("#combines-pari"));
  assert.equal(await page.textContent("#pari-retour"), "Combinés effacés");
  assert.equal(await page.evaluate(() => document.activeElement.id), "titre-pari");
  await page.locator("#nba-pari-compte").waitFor({ state: "hidden" });
  assert.equal(await hauteurBandeau(), hauteurAvant);

  // Journaux : les seuls noms de messages (jamais un texte, une cote, un id ni un nom de combiné)
  for (const l of ["[iBiTV] nba_paris_ajouter", "[iBiTV] nba_paris_ouvrir", "[iBiTV] nba_paris_effacer"]) assert.ok(tel.journal.includes(l), l);
  assert.ok(!tel.journal.some((l) => /nba_paris/.test(l) && !/^\[iBiTV\] nba_paris_(ajouter|effacer|ouvrir)$/.test(l)), tel.journal.join("\n"));
  await tel.fermer();
  await tv.fermer();
});

test("combinés : TV sans « nba_paris_groupes » : liste à plat comme avant (`groupes` ignorés), autre TV avec combinés", async () => {
  const plate = await banc.tv({ nom: "Salon", fonctions: AVEC_PARIS, design: "galerie" });
  const chambre = await banc.tv({ nom: "Chambre", fonctions: AVEC_COMBINES, design: "galerie" });
  await plate.nba({ scenario: "combines" });
  const tel = await banc.telephone("android");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, plate);
  await page.locator("#btn-nba-pari:not([hidden])").waitFor();

  // Les deux tickets Winamax : lignes à plat, bilan en lignes, pastille en lignes
  await page.click("#btn-nba-pari");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  assert.equal(await page.textContent("#pari-vide"), "Aucune ligne suivie pour l'instant.");
  await coller(page, plate, MYMATCH);
  await page.locator("#pari-retour", { hasText: "2 lignes suivies" }).waitFor();
  await coller(page, plate, MAXEY);
  await page.locator("#pari-retour", { hasText: "2 lignes suivies" }).waitFor();
  await page.locator("#liste-pari .pari >> nth=3").waitFor();
  assert.deepEqual(await page.locator("#liste-pari .pari-libelle").allTextContents(), [
    "76ers · handicap −12,5", "Total du match · + de 229,5 pts", "T. Maxey · + de 24,5 pts", "T. Maxey · + de 2,5 rebonds",
  ]);
  assert.ok(await page.isHidden("#combines-pari"));
  assert.equal(await page.locator("#combines-pari > *").count(), 0);
  assert.equal(await page.textContent("#nba-pari-compte"), "4");
  assert.equal(await page.getAttribute("#btn-nba-pari", "aria-label"), "Mon pari : 4 lignes suivies");

  // Une TV sans combinés qui enverrait quand même `groupes` : ignorés, la liste reste à plat
  const lignes = await plate.eval(() => window.tv.paris.lignes.map((p) => ({ id: p.id, libelle: p.libelle, etat: "attente", groupe: "g1" })));
  await plate.envoyer({
    t: "nba", matchs: await plate.eval(() => window.tv.nba.matchs), choisi: "PHI-MIL", affichage: "cache", decalage: 0,
    paris: lignes, groupes: [{ id: "g1", nom: "Combiné 1", etat: "attente", gagnees: 0, suivies: 4, nonSuivies: 0, ouvert: true }],
  });
  await page.locator('#liste-pari .pari[data-etat="attente"] >> nth=3').waitFor();
  assert.ok(await page.isHidden("#combines-pari"));
  assert.equal(await page.textContent("#nba-pari-compte"), "4");

  // Tout effacer : en lignes, sans groupe ; jamais d'ouverture envoyée
  await page.click("#btn-pari-effacer");
  assert.equal(await page.textContent("#alerte-texte"), "Les 4 lignes suivies seront retirées de la TV.");
  await page.click("#alerte-ok");
  await plate.attendreRecu((r) => r.t === "nba_paris_effacer");
  assert.deepEqual((await recusDe(plate, "nba_paris_effacer")).map((r) => r.groupe), [undefined]);
  assert.equal(await page.textContent("#pari-retour"), "Lignes effacées");
  assert.equal((await recusDe(plate, "nba_paris_ouvrir")).length, 0);
  await page.click("#btn-pari-fermer");

  // Autre TV, avec combinés : accordéon ; retour à la première : liste à plat, rien de l'autre gardé
  await page.click("#btn-tvs");
  await page.click("#btn-ajouter-tv");
  await appairerParQR(page, chambre);
  await page.click("#onglet-reglages");
  await page.click("#btn-reglages-pari");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  assert.equal(await page.textContent("#pari-vide"), "Aucun combiné pour l'instant.");
  await coller(page, chambre, MYMATCH);
  await page.locator("#pari-retour", { hasText: "Combiné 1 ajouté · 2 lignes suivies" }).waitFor();
  assert.deepEqual((await combines(page)).map(([id, etat, nom, , , o]) => [id, etat, nom, o]), [["g1", "attente", "Combiné 1", true]]);
  await page.keyboard.press("Escape");
  assert.equal(await page.textContent("#reglage-pari-detail"), "Mon pari : 1 combiné");
  await page.click("#pastille-tv");
  await page.click('.tv-choix:has-text("Salon")');
  await page.locator('#pastille-voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  await page.click("#onglet-reglages");
  await page.locator("#reglage-pari-detail", { hasText: "Colle ton pari" }).waitFor();
  await page.click("#btn-reglages-pari");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  assert.equal(await page.textContent("#pari-vide"), "Aucune ligne suivie pour l'instant.");
  assert.equal(await page.locator("#combines-pari > *").count(), 0);
  await tel.fermer();
  await plate.fermer();
  await chambre.fermer();
});

/** Contraste WCAG du texte d'un élément sur son fond réel (couleurs calculées, transparences composées). */
const contrastes = (page, selecteur) => page.$$eval(selecteur, (l) => {
  const lire = (c) => {
    let m = c.match(/^rgba?\(([^)]+)\)$/);
    if (m) { const [r, g, b, a = 1] = m[1].split(",").map(Number); return [r, g, b, a]; }
    m = c.match(/^color\(srgb ([\d.]+) ([\d.]+) ([\d.]+)(?: \/ ([\d.]+))?\)$/);
    if (m) return [m[1] * 255, m[2] * 255, m[3] * 255, m[4] === undefined ? 1 : Number(m[4])];
    throw new Error(`couleur illisible : ${c}`);
  };
  const sur = (c, f) => [0, 1, 2].map((i) => c[i] * c[3] + f[i] * (1 - c[3])).concat(1);
  const lum = (c) => c.slice(0, 3).map((v) => v / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
    .reduce((s, v, i) => s + v * [0.2126, 0.7152, 0.0722][i], 0);
  const fond = (el) => {
    const couches = [];
    for (let e = el; e; e = e.parentElement) {
      const c = lire(getComputedStyle(e).backgroundColor);
      if (c[3] > 0) couches.push(c);
      if (c[3] >= 1) break;
    }
    return couches.reduceRight((f, c) => sur(c, f), [255, 255, 255, 1]);
  };
  return l.filter((e) => e.getClientRects().length && e.textContent.trim()).map((e) => {
    const f = fond(e);
    const t = sur(lire(getComputedStyle(e).color), f);
    const [x, y] = [lum(t), lum(f)].sort((a, b) => b - a);
    return [`${e.className} « ${e.textContent.trim().slice(0, 24)} »`, (x + 0.05) / (y + 0.05)];
  });
});

test("combinés : avant le match (Réglages, bandeau réduit « Mon pari · 3 combinés » sans décalage), petit écran, texte agrandi, zones sûres, 8 thèmes", async () => {
  const tv = await banc.tv({ nom: "Salon", fonctions: AVEC_COMBINES, design: "galerie" });
  const tel = await banc.telephone("iphone");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  await tv.attendreRecu((r) => r.t === "nba_etat");

  // Réglages → « Mon pari… » : un combiné collé avant le match, en attente
  await page.click("#onglet-reglages");
  await page.locator("#reglage-pari:not([hidden])").waitFor();
  assert.equal(await page.textContent("#reglage-pari-detail"), "Colle ton pari, même avant le match : la TV suit ses lignes en direct");
  await page.click("#btn-reglages-pari");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  await coller(page, tv, MAXEY);
  await page.locator("#pari-retour", { hasText: "Combiné 1 ajouté · 2 lignes suivies" }).waitFor();
  assert.deepEqual(await combines(page), [["g1", "attente", "Combiné 1", "En attente · cote 5,10", "0/2", true]]);
  await page.keyboard.press("Escape");
  assert.equal(await page.evaluate(() => document.activeElement.id), "btn-reglages-pari");
  assert.equal(await page.textContent("#reglage-pari-detail"), "Mon pari : 1 combiné");

  // Télécommande : bandeau réduit, résumé en combinés ; il ne bouge pas quand des combinés s'ajoutent
  await page.click("#onglet-telecommande");
  await page.locator("#bandeau-nba.reduit").waitFor();
  assert.equal(await page.textContent("#nba-pari-resume"), "Mon pari · 1 combiné");
  // Position et hauteur du bandeau, une fois les animations (changement d'onglet, feuille) finies
  const boite = async () => {
    await finAnimations(page);
    return page.$eval("#bandeau-nba", (e) => { const r = e.getBoundingClientRect(); return [r.top, r.height]; });
  };
  const reduit = await boite();
  await page.click("#btn-nba-pari");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  await coller(page, tv, MYMATCH);
  await page.locator("#pari-retour", { hasText: "Combiné 2 ajouté" }).waitFor();
  await coller(page, tv, BETCLIC);
  await page.locator("#pari-retour", { hasText: "Combiné 3 ajouté · 4 lignes suivies · 1 non suivie" }).waitFor();
  await page.click("#btn-pari-fermer");
  await page.locator("#nba-pari-resume", { hasText: "Mon pari · 3 combinés" }).waitFor();
  assert.equal(await page.textContent("#nba-pari-compte"), "3");
  assert.deepEqual(await boite(), reduit, "bandeau réduit sans décalage");
  await page.click("#onglet-reglages");
  assert.equal(await page.textContent("#reglage-pari-detail"), "Mon pari : 3 combinés");
  await page.click("#onglet-telecommande");

  // Le match commence : bandeau complet, résumé caché ; il ne bouge plus quand les valeurs changent
  await tv.nba({ scenario: "combines" });
  await page.locator("#nba-segments:not([hidden])").waitFor();
  assert.ok(await page.isHidden("#nba-pari-resume"));
  const complet = await boite();
  await tv.statNba("tyrese maxey", "rebonds", 3);
  await tv.eval(() => { window.tv.nba.matchs.find((x) => x.id === "MEM-LAC").direct = false; window.tv.changerNba({}); });
  await page.locator("#nba-pari-compte", { hasText: "3" }).waitFor();
  await pause(400);
  assert.deepEqual(await boite(), complet, "bandeau complet sans décalage");

  // Petit écran (360 px) et texte agrandi : rien ne déborde, cibles ≥ 48 px
  await page.click("#btn-nba-pari");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  await page.setViewportSize({ width: 360, height: 640 });
  await page.evaluate(() => { document.documentElement.style.fontSize = "24px"; });
  const mesures = () => page.evaluate(() => {
    const f = document.querySelector(".feuille-pari");
    const tetes = [...document.querySelectorAll(".combine-tete")];
    const boutons = [...document.querySelectorAll("#feuille-pari button")].filter((b) => b.getClientRects().length);
    return {
      large: f.scrollWidth <= f.clientWidth && document.documentElement.scrollWidth <= innerWidth,
      tetes: tetes.filter((t) => t.scrollWidth > t.clientWidth + 1 || t.getBoundingClientRect().right > innerWidth).length,
      petits: boutons.filter((b) => { const r = b.getBoundingClientRect(); return r.width < 47.5 || r.height < 47.5; }).map((b) => b.className || b.id),
      noms: tetes.map((t) => getComputedStyle(t.querySelector(".combine-nom")).fontSize),
    };
  });
  await finAnimations(page);
  const m = await mesures();
  assert.ok(m.large && m.tetes === 0, JSON.stringify(m));
  assert.deepEqual(m.petits, []);
  assert.ok(m.noms.every((t) => parseFloat(t) >= 23.5), `texte agrandi : ${m.noms}`);

  // Zones sûres de l'iPhone (encoche, barre d'accueil) : la feuille reste dedans, jusqu'en bas
  await page.evaluate(() => {
    document.documentElement.style.fontSize = "";
    document.documentElement.style.setProperty("--haut", "47px");
    document.documentElement.style.setProperty("--bas", "34px");
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await finAnimations(page);
  await page.$eval(".feuille-pari", (f) => { f.scrollTop = f.scrollHeight; });
  const zones = await page.evaluate(() => ({
    haut: document.querySelector(".feuille-pari").getBoundingClientRect().top,
    ajouter: document.querySelector("#btn-pari-ajouter").getBoundingClientRect().bottom,
    bas: innerHeight,
  }));
  assert.ok(zones.haut >= 47 && zones.ajouter <= zones.bas - 34, JSON.stringify(zones));
  await page.evaluate(() => {
    document.documentElement.style.removeProperty("--haut");
    document.documentElement.style.removeProperty("--bas");
  });
  await page.$eval(".feuille-pari", (f) => { f.scrollTop = 0; });

  // Les 8 thèmes : textes des combinés et de leurs lignes lisibles (WCAG AA, 4,5:1)
  const selecteur = "#feuille-pari :is(.combine-nom, .combine-detail, .combine-compte, .pari-libelle, .pari-detail, .pari-valeur, .btn-effacer-combine, #btn-pari-effacer)";
  for (const d of DESIGNS) {
    await tv.design(d);
    await page.waitForFunction((x) => document.documentElement.dataset.theme === x, d);
    const c = await contrastes(page, selecteur);
    assert.ok(c.length >= 10, `${d} : ${c.length} textes mesurés`);
    const faibles = c.filter(([, x]) => x < 4.5).map(([n, x]) => `${n} : ${x.toFixed(2)}`);
    assert.deepEqual(faibles, [], `${d} : ${faibles.join(", ")}`);
  }
  await tel.fermer();
  await tv.fermer();
});
