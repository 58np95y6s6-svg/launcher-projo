// Tests de bout en bout des paris suivis (box score NBA, « nba_paris ») : PWA (Chromium, profil
// téléphone) ↔ fausse TV via le faux serveur PeerJS local, en WebRTC réel.
// Segment Pari, « Mon pari » : collage, lignes, bilan, tout effacer ; valeurs reçues ; avant le
// match depuis Réglages, bandeau réduit ; liste à plat (TV sans « nba_paris_groupes » : les
// combinés sont dans combines.test.mjs) ; aucun texte de pari, score ni équipe dans les journaux.

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { demarrerBanc, appairerParQR } from "./banc.mjs";

let banc;
const journaux = [];

before(async () => { banc = await demarrerBanc(); });
after(async () => {
  // Aucun texte de pari, joueur, équipe, match ni score dans les journaux des pages téléphone
  const tout = journaux.flat().join("\n");
  try {
    for (const interdit of ["Téléphone de Marie", "Lakers", "Celtics", "LAL-BOS", "4e QT",
      "Maxey", "Caldwell", "Zubac", "Wembanyama", "76ers", "Vainqueur", "paliers", "19,5", "PHI-IND", "7,85"]) {
      assert.ok(!tout.includes(interdit), `journal : « ${interdit} » trouvé`);
    }
    assert.ok(!/ERREUR PAGE/.test(tout), tout);
  } finally {
    await banc.fermer();
  }
});

const pause = (ms) => new Promise((r) => setTimeout(r, ms));
const recusDe = async (tv, t) => (await tv.recus()).filter((r) => r.t === t);
const AVEC_NBA = ["apps", "reglages", "liens", "nba"];
const coche = (page) => page.$eval('input[name="nba-affichage"]:checked', (e) => e.value).catch(() => null);

// ---------- Paris suivis (liste à plat) ----------

const AVEC_PARIS = [...AVEC_NBA, "nba_paris"];
// Le pari réel de l'utilisateur (texte copié depuis la capture du ticket), plus une ligne d'un type
// non suivi, une cote et une mise (ignorées)
const PARI = `Philadelphie 76ers
Vainqueur du match
Tyrese Maxey + de 19,5
Nombre de points du joueur (paliers)
Kentavious Caldwell-Pope + de 9,5
Nombre de points du joueur (paliers)
Ivica Zubac + de 2,5
Nombre de passes décisives du joueur (paliers)`;
const PARI_COMPLET = `${PARI}\nVictor Wembanyama\nPremier panier du match\nCote totale 7,85\nMise 10,00 €`;
/** Lignes de la feuille : [état, libellé, valeur affichée]. */
const lignesPari = (page) => page.$$eval("#liste-pari .pari", (l) => l.map((li) => [
  li.dataset.etat, li.querySelector(".pari-libelle").textContent,
  li.querySelector(".pari-valeur").hidden ? "" : li.querySelector(".pari-valeur").textContent,
]));

test("paris suivis : segment Pari, « Mon pari », collage, lignes et états, bilan, tout effacer", async () => {
  const tv = await banc.tv({ nom: "Salon", fonctions: AVEC_PARIS, design: "nba" });
  await tv.nba({ scenario: "paris" });
  const tel = await banc.telephone("iphone");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  await page.locator("#bandeau-nba:not([hidden])").waitFor();

  // « nba_paris » annoncé : 4e segment Pari et « Mon pari » (sans pastille : aucune ligne)
  assert.deepEqual(await page.locator(".segment:not([hidden])").allTextContents(), ["Caché", "Résumé", "Détails", "Pari"]);
  assert.equal(await page.textContent("#btn-nba-pari"), "Mon pari");
  assert.equal(await page.getAttribute("#btn-nba-pari", "aria-label"), "Mon pari");
  assert.ok(await page.isHidden("#nba-pari-compte"));
  // Cibles ≥ 48 px, rien ne déborde ; pavé et touches à l'écran, même en iPhone SE
  await page.evaluate(() => document.fonts.ready);
  const mesures = () => page.evaluate(() => {
    const r = (s) => document.querySelector(s).getBoundingClientRect();
    const p = document.querySelector("#panneau-telecommande");
    return {
      cibles: [...document.querySelectorAll("#btn-nba-matchs, #btn-nba-pari, .segment:not([hidden])")].map((e) => Math.min(e.getBoundingClientRect().width, e.getBoundingClientRect().height)),
      bandeau: r("#bandeau-nba").height, large: p.scrollWidth <= innerWidth, pave: r("#pave").height,
      bas: r(".rangee-son").bottom, onglets: r("#onglets").top, defile: p.scrollHeight - p.clientHeight,
    };
  });
  const m = await mesures();
  assert.ok(m.cibles.length === 6 && m.cibles.every((x) => x >= 47.5), `cibles : ${m.cibles}`);
  assert.ok(m.large && m.bas <= m.onglets && m.defile === 0 && m.pave >= 250, JSON.stringify(m));
  await page.setViewportSize({ width: 375, height: 667 });
  const se = await mesures();
  assert.ok(se.large && se.pave >= 150 && se.bas <= se.onglets && se.defile === 0, JSON.stringify(se));
  await page.setViewportSize({ width: 390, height: 844 });

  // Pari touché sans aucune ligne : nba_afficher pari (avec le match affiché), et la feuille s'ouvre
  await page.click('.segment:has-text("Pari")');
  await tv.attendreRecu((r) => r.t === "nba_afficher" && r.affichage === "pari" && r.id === "PHI-IND");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  assert.equal(await coche(page), "pari");
  await tv.page.waitForFunction(() => window.tv.nba.affichage === "pari");
  await page.locator("#nba-annonce", { hasText: "Mon pari affiché sur la TV" }).waitFor();
  assert.ok(tel.journal.includes("[iBiTV] nba_afficher pari"));
  assert.equal(await page.textContent("#titre-pari"), "Mon pari");
  assert.equal(await page.evaluate(() => document.activeElement.id), "titre-pari", "focus sur le titre de la feuille");
  assert.equal(await page.textContent("#pari-vide"), "Aucune ligne suivie pour l'instant.");
  assert.ok(await page.isHidden("#btn-pari-effacer"));
  assert.equal(await page.textContent('label[for="champ-pari"]'), "Colle ton pari");
  assert.equal(await page.getAttribute("#champ-pari", "placeholder"), "Colle ici le texte de ton pari");
  assert.equal(await page.textContent("#pari-aide"), "Sur la capture du ticket : appui long sur le texte → Tout sélectionner → Copier, puis colle ici.");
  assert.equal(await page.getAttribute("#champ-pari", "aria-describedby"), "pari-aide");
  assert.equal(await page.getAttribute("#feuille-pari .feuille", "role"), "dialog");
  // « Ajouter » : désactivé tant que le champ est vide (ou blanc)
  assert.ok(await page.isDisabled("#btn-pari-ajouter"));
  await page.fill("#champ-pari", "  \n  ");
  assert.ok(await page.isDisabled("#btn-pari-ajouter"));

  // Collage, Ajouter : le texte part vers la TV, le champ se vide, les lignes et le bilan arrivent
  await page.fill("#champ-pari", `\n${PARI_COMPLET}\n\n`);
  assert.ok(await page.isEnabled("#btn-pari-ajouter"));
  await page.click("#btn-pari-ajouter");
  await tv.attendreRecu((r) => r.t === "nba_paris_ajouter");
  assert.deepEqual((await recusDe(tv, "nba_paris_ajouter")).map((r) => r.texte), [PARI_COMPLET]);
  assert.equal(await page.inputValue("#champ-pari"), "");
  assert.ok(await page.isDisabled("#btn-pari-ajouter"));
  await page.locator("#pari-retour", { hasText: "4 lignes suivies · 1 non suivie" }).waitFor();
  assert.deepEqual(await lignesPari(page), [
    ["en_cours", "76ers vainqueurs", "+4"],
    ["en_cours", "T. Maxey · + de 19,5 pts", "12 / 19,5"],
    ["gagne", "K. Caldwell-Pope · + de 9,5 pts", "11 / 9,5"],
    ["en_cours", "I. Zubac · + de 2,5 passes", "1 / 2,5"],
    ["non_suivi", "Victor Wembanyama · Premier panier du match", ""],
  ]);
  assert.deepEqual(await page.locator("#liste-pari .pari-detail").allTextContents(),
    ["En cours · PHI – IND", "En cours · PHI – IND", "Gagné · MEM – LAC", "En cours · MEM – LAC", "Non suivi"]);
  assert.deepEqual(await page.locator("#liste-pari .invisible-lecteur").allTextContents(), [
    "76ers vainqueurs : en cours, mène de 4 points",
    "T. Maxey · + de 19,5 pts : en cours, 12 sur 19,5",
    "K. Caldwell-Pope · + de 9,5 pts : gagné, 11 sur 9,5",
    "I. Zubac · + de 2,5 passes : en cours, 1 sur 2,5",
    "Victor Wembanyama · Premier panier du match : non suivi",
  ]);
  // États : ✓ gagné, tiret non suivi (grisé), point en cours ; avancement vers le seuil
  assert.equal(await page.locator('.pari[data-etat="gagne"] .pari-etat use').getAttribute("href"), "#i-ok");
  assert.equal(await page.locator('.pari[data-etat="non_suivi"] .pari-etat use').getAttribute("href"), "#i-moins");
  assert.equal(await page.locator('.pari[data-etat="en_cours"] .pari-etat use').count(), 0);
  const gris = await page.evaluate(() => ["non_suivi", "en_cours"].map((e) => getComputedStyle(document.querySelector(`.pari[data-etat="${e}"] .pari-libelle`)).color));
  assert.notEqual(gris[0], gris[1], "ligne non suivie grisée");
  assert.equal(await page.$eval('.pari[data-id="p2"] .pari-barre > span', (e) => e.style.getPropertyValue("--p")), String(12 / 19.5));
  assert.ok(await page.isHidden('.pari[data-id="p1"] .pari-barre'), "vainqueur : pas de barre");
  // « Mon pari » : nombre de lignes ; « Tout effacer » apparaît
  assert.equal(await page.textContent("#nba-pari-compte"), "5");
  assert.equal(await page.getAttribute("#btn-nba-pari", "aria-label"), "Mon pari : 5 lignes suivies");
  assert.ok(await page.isVisible("#btn-pari-effacer"));

  // Les valeurs suivent la TV, feuille ouverte : Maxey passe le seuil, le match MEM-LAC se termine
  await tv.statNba("tyrese maxey", "points", 21);
  await page.locator('.pari[data-id="p2"][data-etat="gagne"] .pari-valeur', { hasText: "21 / 19,5" }).waitFor();
  await tv.eval(() => { window.tv.nba.matchs.find((x) => x.id === "MEM-LAC").direct = false; window.tv.changerNba({}); });
  await page.locator('.pari[data-id="p4"][data-etat="perdu"]').waitFor();
  assert.equal(await page.locator('.pari[data-etat="perdu"] .pari-etat use').getAttribute("href"), "#i-croix");
  assert.equal(await page.textContent('.pari[data-id="p4"] .pari-detail'), "Perdu · MEM – LAC");

  // Même texte une seconde fois : la TV ne l'ajoute pas deux fois → « Aucune nouvelle ligne »
  await page.fill("#champ-pari", PARI);
  await page.click("#btn-pari-ajouter");
  await page.locator("#pari-retour", { hasText: "Lecture du pari…" }).waitFor();
  await page.locator("#pari-retour", { hasText: "Aucune nouvelle ligne" }).waitFor({ timeout: 8000 });
  assert.equal((await lignesPari(page)).length, 5);
  // 4000 caractères au plus envoyés
  await page.evaluate(() => {
    const c = document.querySelector("#champ-pari");
    c.value = "x".repeat(5000);
    c.dispatchEvent(new Event("input"));
  });
  await page.click("#btn-pari-ajouter");
  await tv.page.waitForFunction(() => window.tv.recus.filter((r) => r.t === "nba_paris_ajouter").length === 3);
  assert.equal((await recusDe(tv, "nba_paris_ajouter")).at(-1).texte.length, 4000);

  // Échap ferme la feuille : focus rendu à « Mon pari » ; Pari avec des lignes : la feuille ne s'ouvre pas
  await page.keyboard.press("Escape");
  assert.ok(await page.isHidden("#feuille-pari"));
  assert.equal(await page.evaluate(() => document.activeElement.id), "btn-nba-pari");
  await page.click('.segment:has-text("Résumé")');
  await tv.attendreRecu((r) => r.t === "nba_afficher" && r.affichage === "resume");
  await page.click('.segment:has-text("Pari")');
  await tv.page.waitForFunction(() => window.tv.nba.affichage === "pari");
  await pause(300);
  assert.ok(await page.isHidden("#feuille-pari"));

  // Tout effacer : confirmation (Annuler : rien n'est envoyé), puis nba_paris_effacer
  await page.click("#btn-nba-pari");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  await page.click("#btn-pari-effacer");
  await page.locator("#alerte:not([hidden])").waitFor();
  assert.equal(await page.textContent("#alerte-titre"), "Effacer ton pari ?");
  assert.equal(await page.textContent("#alerte-texte"), "Les 5 lignes suivies seront retirées de la TV.");
  await page.keyboard.press("Escape"); // l'alerte reste au-dessus : la feuille ne se ferme pas
  assert.ok(await page.isVisible("#feuille-pari"));
  await page.click("#alerte-annuler");
  assert.equal((await recusDe(tv, "nba_paris_effacer")).length, 0);
  assert.equal((await lignesPari(page)).length, 5);
  await page.click("#btn-pari-effacer");
  await page.click("#alerte-ok");
  await tv.attendreRecu((r) => r.t === "nba_paris_effacer");
  await page.locator("#pari-vide:not([hidden])").waitFor();
  assert.ok(await page.isHidden("#liste-pari"));
  assert.ok(await page.isHidden("#btn-pari-effacer"));
  assert.equal(await page.textContent("#pari-retour"), "Lignes effacées");
  await page.locator("#nba-pari-compte").waitFor({ state: "hidden" });
  await page.click("#btn-pari-fermer");
  assert.equal(await page.evaluate(() => document.activeElement.id), "btn-nba-pari");

  // Journaux : les seuls événements, jamais le texte collé ni une ligne
  for (const l of ["[iBiTV] nba_paris_ajouter", "[iBiTV] nba_paris_effacer", "[iBiTV] nba_afficher pari"]) assert.ok(tel.journal.includes(l), l);
  assert.ok(!tel.journal.some((l) => /nba_paris/.test(l) && !/^\[iBiTV\] nba_paris_(ajouter|effacer)$/.test(l)), tel.journal.join("\n"));
  const tout = tel.journal.join("\n");
  for (const x of ["Maxey", "Zubac", "Vainqueur", "19,5", "Wembanyama", "xxxx"]) assert.ok(!tout.includes(x), x);
  await tel.fermer();
  await tv.fermer();
});

test("paris suivis : toutes les valeurs reçues (attente, écart négatif), autre TV sans paris", async () => {
  const salon = await banc.tv({ nom: "Salon", fonctions: AVEC_PARIS, design: "galerie" });
  const chambre = await banc.tv({ nom: "Chambre", fonctions: AVEC_NBA });
  await salon.nba({ scenario: "un" });
  await chambre.nba({ scenario: "un" });
  const tel = await banc.telephone("android");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, salon);
  await page.locator("#btn-nba-pari:not([hidden])").waitFor();
  // État envoyé tel quel par la TV : lignes en attente, vainqueur mené, ligne invalide écartée
  await salon.envoyer({
    t: "nba", matchs: await salon.eval(() => window.tv.nba.matchs), choisi: "LAL-BOS", affichage: "pari", decalage: 0,
    paris: [
      { id: "a", libelle: "Lakers vainqueurs", etat: "en_cours", match: "LAL-BOS", valeur: -4 },
      { id: "b", libelle: "L. James · + de 7,5 passes", etat: "attente", seuil: 7.5 },
      { id: "c", libelle: "Total du match · - de 221,5 pts", etat: "perdu", match: "LAL-BOS", valeur: 230, seuil: 221.5 },
      { id: "d", etat: "gagne" },
    ],
  });
  await page.locator("#nba-pari-compte", { hasText: "3" }).waitFor();
  assert.equal(await coche(page), "pari");
  await page.click("#btn-nba-pari");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  assert.deepEqual(await lignesPari(page), [
    ["en_cours", "Lakers vainqueurs", "\u22124"],
    ["attente", "L. James · + de 7,5 passes", ""],
    ["perdu", "Total du match · - de 221,5 pts", "230 / 221,5"],
  ]);
  assert.equal(await page.textContent('.pari[data-id="b"] .pari-detail'), "En attente");
  assert.equal(await page.textContent('.pari[data-id="a"] .invisible-lecteur'), "Lakers vainqueurs : en cours, mené de 4 points");
  // Cibles de la feuille ≥ 48 px
  const petites = await page.$$eval("#feuille-pari button:not([hidden]), #champ-pari", (l) => l
    .filter((b) => b.getClientRects().length)
    .filter((b) => { const r = b.getBoundingClientRect(); return r.width < 47.5 || r.height < 47.5; }).map((b) => b.id));
  assert.deepEqual(petites, []);
  await page.click("#btn-pari-fermer");

  // Autre TV sans « nba_paris » : ni segment Pari ni « Mon pari », rien de l'autre TV gardé
  await page.click("#btn-tvs");
  await page.click("#btn-ajouter-tv");
  await appairerParQR(page, chambre);
  await page.locator("#bandeau-nba:not([hidden])").waitFor();
  assert.ok(await page.isHidden("#btn-nba-pari"));
  assert.ok(await page.isHidden('.segment:has(input[value="pari"])'));
  assert.deepEqual(await page.locator(".segment:not([hidden])").allTextContents(), ["Caché", "Résumé", "Détails"]);
  // Une TV sans paris qui enverrait quand même `paris` ou « pari » : ignorés
  await chambre.envoyer({ t: "nba", matchs: await chambre.eval(() => window.tv.nba.matchs), choisi: "LAL-BOS", affichage: "pari", decalage: 0, paris: [{ id: "x", libelle: "Lakers vainqueurs", etat: "gagne" }] });
  await pause(400);
  assert.equal(await coche(page), null);
  assert.ok(await page.isHidden("#btn-nba-pari"));
  assert.ok(await page.isHidden("#feuille-pari"));
  // … ni « Mon pari… » dans Réglages
  await page.click("#onglet-reglages");
  await page.locator('.reglage[data-cle="nba_decalage"]').waitFor();
  assert.ok(await page.isHidden("#reglage-pari"));
  await tel.fermer();
  await salon.fermer();
  await chambre.fermer();
});

test("paris suivis : pari collé avant le match depuis Réglages, bandeau réduit, puis le match commence", async () => {
  const tv = await banc.tv({ nom: "Salon", fonctions: AVEC_PARIS, design: "galerie" });
  const tel = await banc.telephone("iphone");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  // Aucun match en cours, aucune ligne : pas de bandeau
  await tv.attendreRecu((r) => r.t === "nba_etat");
  await pause(400);
  assert.ok(await page.isHidden("#bandeau-nba"));

  // Réglages, groupe « Box score NBA » : « Mon pari… » juste après le décalage
  await page.click("#onglet-reglages");
  await page.locator('.reglage[data-cle="nba_decalage"]').waitFor();
  assert.ok(await page.isVisible("#reglage-pari"));
  assert.equal(await page.$eval("#reglage-pari", (e) => e.previousElementSibling?.dataset.cle), "nba_decalage");
  assert.equal(await page.$eval("#reglage-pari", (e) => e.closest(".groupe").querySelector(".groupe-titre").textContent), "Box score NBA");
  assert.equal(await page.textContent("#reglage-pari .reglage-titre"), "Paris suivis");
  assert.equal(await page.textContent("#reglage-pari-detail"), "Colle ton pari, même avant le match : la TV suit ses lignes en direct");
  assert.equal(await page.textContent("#btn-reglages-pari"), "Mon pari…");
  const r = await page.locator("#btn-reglages-pari").boundingBox();
  assert.ok(r.height >= 47.5 && r.width >= 47.5);

  // Collage sans match : les lignes sont « en attente »
  await page.click("#btn-reglages-pari");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  await page.fill("#champ-pari", PARI);
  await page.click("#btn-pari-ajouter");
  await tv.attendreRecu((x) => x.t === "nba_paris_ajouter");
  assert.deepEqual((await recusDe(tv, "nba_paris_ajouter")).map((x) => x.texte), [PARI]);
  await page.locator("#pari-retour", { hasText: "4 lignes suivies" }).waitFor();
  assert.deepEqual((await lignesPari(page)).map(([etat]) => etat), ["attente", "attente", "attente", "attente"]);
  assert.deepEqual(await page.locator("#liste-pari .pari-detail").allTextContents(), ["En attente", "En attente", "En attente", "En attente"]);
  // Fermer : focus rendu au bouton de Réglages, qui résume les lignes
  await page.keyboard.press("Escape");
  assert.equal(await page.evaluate(() => document.activeElement.id), "btn-reglages-pari");
  assert.equal(await page.textContent("#reglage-pari-detail"), "Mon pari : 4 lignes en attente");
  assert.equal((await recusDe(tv, "nba_afficher")).length, 0, "Réglages : aucun format envoyé");

  // Télécommande : bandeau réduit (aucun match, le résumé et « Mon pari »), sans formats
  await page.click("#onglet-telecommande");
  await page.locator("#bandeau-nba:not([hidden])").waitFor();
  assert.ok(await page.locator("#bandeau-nba.reduit").count() === 1);
  assert.equal(await page.textContent("#nba-aucun"), "Aucun match NBA en cours");
  assert.equal(await page.textContent("#nba-pari-resume"), "Mon pari · 4 lignes en attente");
  assert.ok(await page.isHidden("#nba-segments"));
  assert.ok(await page.isHidden("#btn-nba-matchs"));
  assert.ok(await page.isHidden(".nba-score"));
  assert.ok(await page.isVisible("#btn-nba-pari"));
  assert.equal(await page.textContent("#nba-pari-compte"), "4");
  const reduit = await page.evaluate(() => {
    const p = document.querySelector("#panneau-telecommande");
    return { defile: p.scrollHeight - p.clientHeight, large: p.scrollWidth <= innerWidth };
  });
  assert.ok(reduit.defile === 0 && reduit.large, JSON.stringify(reduit));
  // « Mon pari » du bandeau : la même feuille
  await page.click("#btn-nba-pari");
  await page.locator("#feuille-pari:not([hidden])").waitFor();
  assert.equal((await lignesPari(page)).length, 4);
  await page.click("#btn-pari-fermer");
  assert.equal(await page.evaluate(() => document.activeElement.id), "btn-nba-pari");

  // Le match commence : bandeau complet (score, formats), lignes en cours
  await tv.nba({ scenario: "paris" });
  await page.locator("#nba-segments:not([hidden])").waitFor();
  assert.ok(await page.locator("#bandeau-nba.reduit").count() === 0);
  assert.ok(await page.isHidden("#nba-pari-resume"));
  assert.ok(await page.isHidden("#nba-aucun"));
  assert.deepEqual(await page.locator(".nba-score > span").allTextContents(), ["PHI", "61", "–", "57", "IND"]);
  assert.deepEqual(await page.locator(".segment:not([hidden])").allTextContents(), ["Caché", "Résumé", "Détails", "Pari"]);
  await page.click('.segment:has-text("Pari")');
  await tv.attendreRecu((x) => x.t === "nba_afficher" && x.affichage === "pari" && x.id === "PHI-IND");
  assert.ok(await page.isHidden("#feuille-pari"), "des lignes : la feuille ne s'ouvre pas");

  // Fin des matchs, box score caché : le bandeau réduit revient ; lignes effacées : plus de bandeau
  await tv.nba({ scenario: "aucun", affichage: "cache" });
  await page.locator("#bandeau-nba.reduit").waitFor();
  await page.click("#btn-nba-pari");
  await page.click("#btn-pari-effacer");
  await page.click("#alerte-ok");
  await tv.attendreRecu((x) => x.t === "nba_paris_effacer");
  await page.locator("#bandeau-nba").waitFor({ state: "hidden" });
  await page.locator("#feuille-pari").waitFor({ state: "visible" });
  await page.click("#btn-pari-fermer");
  await page.click("#onglet-reglages");
  assert.equal(await page.textContent("#reglage-pari-detail"), "Colle ton pari, même avant le match : la TV suit ses lignes en direct");
  await tel.fermer();
  await tv.fermer();
});
