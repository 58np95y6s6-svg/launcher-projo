// Test d'interopérabilité de bout en bout : PWA (Chromium, profil téléphone) ↔ fausse TV
// (second contexte Chromium) via le faux serveur PeerJS local, en WebRTC réel.
//
// appairage QR → confirmation → paired → key / text / focus_text → reconnexion → coupure
// → revoked ; code de secours ; plusieurs TV ; versions incompatibles ; TV injoignable ;
// mise à jour (bannière) ; pilotage (onglets Applis et Réglages, TV ancienne sans onglets) ;
// aucun texte saisi, secret, nom d'appli ni valeur de réglage dans les journaux.

import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { demarrerBanc, appairerParQR, appuyerDirection } from "./banc.mjs";

const MOT_DE_PASSE = "Tr3s-S3cret!";
const TEXTE = "bonjour tout le monde";
let banc;
const journaux = [];

before(async () => { banc = await demarrerBanc(); });
after(async () => {
  // Aucun texte saisi, secret ou SDP dans les journaux de toutes les pages téléphone
  const tout = journaux.flat().join("\n");
  try {
    for (const interdit of [MOT_DE_PASSE, TEXTE, "a=fingerprint", "v=0", "candidate:", "BEGIN", "Téléphone de Marie",
      "Netflix", "com.netflix", "pastel", "data:image"]) {
      assert.ok(!tout.includes(interdit), `journal : « ${interdit} » trouvé`);
    }
    assert.ok(!/ERREUR PAGE/.test(tout), tout);
  } finally {
    await banc.fermer();
  }
});

const pause = (ms) => new Promise((r) => setTimeout(r, ms));

test("appairage par QR code, commandes, champ texte, reconnexion, coupure, révocation", async () => {
  const tv = await banc.tv({ nom: "Salon" });
  const tel = await banc.telephone("android");
  journaux.push(tel.journal);
  let page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();

  // --- Appairage : QR → nom → « Confirmez sur la TV » → confirmation → appairée
  const { fragment } = await tv.demarrerAppairage();
  const secret = fragment.split(".")[1];
  await page.goto(banc.urlPWA + fragment);
  await page.locator("#ecran-appairage").waitFor();
  assert.equal(new URL(page.url()).hash, "", "le secret est retiré de l'adresse");
  await page.fill("#champ-nom", "Téléphone de Marie");
  await page.click("#form-nom button[type=submit]");
  await page.locator("#attente-titre", { hasText: "Confirmez sur la TV" }).waitFor();
  const demande = await tv.attendreDemande();
  assert.equal(demande.nom, "Téléphone de Marie");
  assert.match(demande.appareil, /^tel-[0-9a-f]{32}$/);
  // Avant confirmation, les commandes ne passent pas
  await page.evaluate(() => {}); // (aucune touche disponible sur cet écran)
  await tv.confirmer(true);
  await page.locator("#resultat-titre", { hasText: "Appairée" }).waitFor();
  await page.click("#btn-resultat");
  await page.locator('#voyant[data-etat="connecte"]').waitFor();
  assert.equal(await page.textContent("#nom-tv"), "Salon");
  assert.equal(await page.textContent("#etat-tv"), "Connectée");

  // --- Touches
  for (const k of ["up", "right", "down", "left"]) await appuyerDirection(page, k);
  await page.click(".dpad-ok");
  for (const k of ["back", "home", "menu", "vol-", "mute", "vol+", "playpause"]) await page.click(`.touche[data-k="${k}"]`);
  await tv.attendreRecu((r) => r.k === "playpause");
  assert.deepEqual((await tv.recus()).map((r) => r.k),
    ["up", "right", "down", "left", "ok", "back", "home", "menu", "vol-", "mute", "vol+", "playpause"]);
  assert.ok((await tv.recus()).every((r) => r.appareil === demande.appareil));

  // --- Pavé tactile : glisser à droite (2 pas) puis toucher (OK)
  await tv.viderRecus();
  await page.click("#btn-mode-nav");
  const p = await page.locator("#pave").boundingBox();
  await page.mouse.move(p.x + 60, p.y + p.height / 2);
  await page.mouse.down();
  await page.mouse.move(p.x + 170, p.y + p.height / 2, { steps: 10 });
  await page.mouse.up();
  await page.mouse.click(p.x + p.width / 2, p.y + p.height / 2);
  await tv.attendreRecu((r) => r.k === "ok");
  assert.deepEqual((await tv.recus()).map((r) => r.k), ["right", "right", "ok"]);
  await page.click("#btn-mode-nav");

  // --- Champ mot de passe : focus_text → masqué, saisie envoyée en direct, Entrée → submit
  await tv.viderRecus();
  await tv.envoyer({ t: "focus_text", type: "motdepasse", s: "", action: "go" });
  await page.locator("#saisie.actif").waitFor();
  assert.equal(await page.getAttribute("#champ-texte", "type"), "password");
  assert.equal(await page.getAttribute("#champ-texte", "enterkeyhint"), "go");
  assert.ok(await page.isVisible("#btn-afficher"));
  await page.click("#champ-texte");
  await page.keyboard.type(MOT_DE_PASSE, { delay: 15 });
  await tv.attendreRecu((r) => r.t === "text" && r.s === "Tr3s-S3cret!");
  await page.click("#btn-afficher");
  assert.equal(await page.getAttribute("#champ-texte", "type"), "text");
  assert.equal(await page.evaluate(() => document.activeElement.id), "champ-texte", "le focus reste dans le champ");
  await page.keyboard.press("Enter");
  await tv.attendreRecu((r) => r.t === "submit");
  const textes = (await tv.recus()).filter((r) => r.t === "text");
  assert.equal(textes.length, MOT_DE_PASSE.length, "un message text par frappe");
  assert.equal(textes.at(-1).curseur, MOT_DE_PASSE.length);
  await tv.envoyer({ t: "focus_lost" });
  await page.waitForFunction(() => !document.querySelector("#saisie").classList.contains("actif"));
  assert.equal(await page.inputValue("#champ-texte"), "");
  assert.equal(await page.getAttribute("#champ-texte", "type"), "text");
  assert.ok(await page.isHidden("#btn-afficher"));

  // --- Champ de recherche pré-rempli
  await tv.envoyer({ t: "focus_text", type: "recherche", s: "abc", action: "search" });
  await page.locator("#saisie.actif").waitFor();
  assert.equal(await page.inputValue("#champ-texte"), "abc");
  assert.equal(await page.getAttribute("#champ-texte", "type"), "search");
  await page.fill("#champ-texte", TEXTE);
  await tv.attendreRecu((r) => r.t === "text" && r.s === "bonjour tout le monde");
  await tv.envoyer({ t: "focus_lost" });

  // --- Reconnexion à la réouverture (identité et TV en IndexedDB)
  await page.close();
  await tv.viderRecus();
  page = await tel.ouvrir();
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  assert.ok(await page.isVisible("#ecran-telecommande"));
  await page.click('.touche[data-k="home"]');
  await tv.attendreRecu((r) => r.k === "home");

  // --- Coupure du réseau local : reconnexion automatique
  await tv.eval(() => window.tv.couper());
  await page.waitForFunction(() => document.querySelector("#voyant").dataset.etat !== "connecte");
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 20000 });
  await tv.viderRecus();
  await page.click('.touche[data-k="back"]');
  await tv.attendreRecu((r) => r.k === "back");

  // --- Révocation pendant la session
  await tv.eval((id) => window.tv.revoquer(id), demande.appareil);
  await page.locator("#alerte-titre", { hasText: "Accès retiré" }).waitFor();
  await page.click("#alerte-ok");
  await page.locator("#ecran-accueil").waitFor();
  const restantes = await page.evaluate(() => new Promise((ok) => {
    const r = indexedDB.open("ibiprojo-telecommande");
    r.onsuccess = () => { const g = r.result.transaction("tvs").objectStore("tvs").getAll(); g.onsuccess = () => ok(g.result.length); };
  }));
  assert.equal(restantes, 0);
  assert.ok(!(await tv.eval(() => window.tv.ignores.includes("key"))), "aucune commande envoyée sans authentification");
  assert.ok(!journaux.flat().join("\n").includes(secret), "secret d'appairage absent des journaux");
  await tel.fermer();
  await tv.fermer();
});

test("code de secours, plusieurs TV, choix de la TV, révocation hors connexion, oubli", async () => {
  const salon = await banc.tv({ nom: "Salon" });
  const chambre = await banc.tv({ nom: "Chambre" });
  const tel = await banc.telephone("iphone");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();

  // Code invalide
  await page.fill("#champ-code", "ABCD-EFG0");
  await page.click("#form-code button[type=submit]");
  await page.locator("#erreur-code").waitFor();

  // Salon par code de secours
  const { code } = await salon.demarrerAppairage();
  await page.fill("#champ-code", code.toLowerCase());
  await page.click("#form-code button[type=submit]");
  await page.locator("#appairage-source", { hasText: code }).waitFor();
  await page.fill("#champ-nom", "iPhone de Paul");
  await page.click("#form-nom button[type=submit]");
  assert.equal((await salon.attendreDemande()).nom, "iPhone de Paul");
  await salon.confirmer(true);
  await page.locator("#resultat-titre", { hasText: "Appairée" }).waitFor();
  await page.click("#btn-resultat");
  await page.locator('#voyant[data-etat="connecte"]').waitFor();

  // Chambre par QR (TV ajoutée depuis la liste)
  await page.click("#btn-tvs");
  await page.click("#btn-ajouter-tv");
  await page.locator("#ecran-accueil").waitFor();
  assert.ok(await page.isVisible("#btn-accueil-retour"));
  await appairerParQR(page, chambre, "iPhone de Paul");
  assert.equal(await page.textContent("#nom-tv"), "Chambre");
  await page.click('.touche[data-k="mute"]');
  await chambre.attendreRecu((r) => r.k === "mute");

  // Choix de la TV pilotée
  await page.click("#btn-tvs");
  assert.equal(await page.locator(".tv-choix").count(), 2);
  await page.click('.tv-choix:has-text("Salon")');
  await page.locator("#nom-tv", { hasText: "Salon" }).waitFor();
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  await salon.viderRecus();
  await page.click('.touche[data-k="vol+"]');
  await salon.attendreRecu((r) => r.k === "vol+");
  // Après l'appairage par code, la reconnexion se fait sur l'identifiant stable
  const offres = banc.peerjs.stats.messages.filter((m) => m.type === "OFFER" && m.dst === salon.id);
  assert.ok(offres.length >= 1);

  // Révocation pendant que le téléphone est fermé : à la réouverture, hello puis revoked
  await page.close();
  await salon.eval(() => window.tv.appareils.clear());
  const page2 = await tel.ouvrir();
  await page2.locator("#alerte-titre", { hasText: "Accès retiré" }).waitFor({ timeout: 15000 });
  await page2.click("#alerte-ok");
  await page2.locator("#nom-tv", { hasText: "Chambre" }).waitFor();
  await page2.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });

  // Oublier la dernière TV → écran d'accueil
  await page2.click("#btn-tvs");
  assert.equal(await page2.locator(".tv-choix").count(), 1);
  await page2.click(".tv-oublier");
  await page2.locator("#alerte-titre", { hasText: "Oublier" }).waitFor();
  await page2.click("#alerte-ok");
  await page2.locator("#ecran-accueil").waitFor();
  await tel.fermer();
  await salon.fermer();
  await chambre.fermer();
});

test("appairage refusé sur la TV, puis TV injoignable", async () => {
  const tv = await banc.tv();
  const tel = await banc.telephone("android");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  const { fragment } = await tv.demarrerAppairage();
  await page.goto(banc.urlPWA + fragment);
  await page.fill("#champ-nom", "Téléphone de Marie");
  await page.click("#form-nom button[type=submit]");
  await tv.attendreDemande();
  await tv.confirmer(false);
  await page.locator("#resultat-titre", { hasText: "Appairage refusé" }).waitFor();

  // QR d'une TV éteinte : le serveur renvoie EXPIRE → « TV injoignable »
  await page.goto(banc.urlPWA + "#pair=ibiprojo-00000000000000000000000000000000.BwcHBwcHBwcHBwcHBwcHBw.edzppNhI05JFkML9N6NsPQ");
  await page.fill("#champ-nom", "Téléphone de Marie");
  await page.click("#form-nom button[type=submit]");
  await page.locator("#resultat-titre", { hasText: "TV injoignable" }).waitFor({ timeout: 15000 });
  assert.ok(await page.isVisible("#btn-resultat-2"));
  await tel.fermer();
  await tv.fermer();
});

test("versions incompatibles : « Mettez à jour iBiTV sur la TV » / « Rechargez la télécommande »", async () => {
  const ancienne = await banc.tv({ vmax: 0 });
  const tel = await banc.telephone("android");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  let { fragment } = await ancienne.demarrerAppairage();
  await page.goto(banc.urlPWA + fragment);
  await page.fill("#champ-nom", "Tel");
  await page.click("#form-nom button[type=submit]");
  await page.locator("#alerte-titre", { hasText: "Mettez à jour iBiTV sur la TV" }).waitFor({ timeout: 15000 });
  await page.click("#alerte-ok");

  const recente = await banc.tv({ vhello: 2 });
  ({ fragment } = await recente.demarrerAppairage());
  await page.goto(banc.urlPWA + fragment);
  await page.fill("#champ-nom", "Tel");
  await page.click("#form-nom button[type=submit]");
  await page.locator("#alerte-titre", { hasText: "Rechargez la télécommande" }).waitFor({ timeout: 15000 });
  await page.click("#alerte-annuler");
  await tel.fermer();
  await ancienne.fermer();
  await recente.fermer();
});

test("service worker : hors ligne, puis nouvelle version → bannière « Recharger »", async () => {
  const tel = await banc.telephone("android");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.waitForFunction(async () => (await navigator.serviceWorker.ready).active?.state === "activated");
  await page.reload();
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null);
  // Hors ligne : l'interface se charge depuis le cache
  await tel.ctx.setOffline(true);
  await page.reload();
  await page.locator("#ecran-accueil").waitFor();
  await tel.ctx.setOffline(false);
  // Nouvelle version publiée
  banc.web.etat.versionApp = "1.0.1-test";
  await page.evaluate(async () => (await navigator.serviceWorker.getRegistration()).update());
  await page.locator("#banniere-maj").waitFor({ timeout: 15000 });
  await Promise.all([page.waitForEvent("load"), page.click("#btn-recharger")]);
  await page.locator("#ecran-accueil").waitFor();
  assert.ok(await page.isHidden("#banniere-maj"));
  const version = await page.evaluate(() => globalThis.IBITV_CONFIG.versionApp);
  assert.equal(version, "1.0.1-test");
  banc.web.etat.versionApp = "1.0.0-test";
  await tel.fermer();
});

// ---------- Pilotage : applis et réglages ----------

const recusDe = async (tv, t) => (await tv.recus()).filter((r) => r.t === t);

test("TV ancienne (sans « fonctions ») : télécommande seule, sans onglets ni erreur", async () => {
  const tv = await banc.tv({ nom: "Ancienne", fonctions: null });
  const tel = await banc.telephone("android");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  await page.click('.touche[data-k="home"]');
  await tv.attendreRecu((r) => r.k === "home");
  assert.ok(await page.isHidden("#onglets"));
  assert.ok(await page.isVisible("#btn-mode-nav"));
  assert.equal(await page.evaluate(() => document.querySelector("#ecran-telecommande").classList.contains("avec-onglets")), false);
  // Rien du pilotage n'a été envoyé à cette TV
  assert.deepEqual(await tv.eval(() => window.tv.ignores), []);
  assert.deepEqual((await tv.recus()).map((r) => r.t), ["key"]);
  await tel.fermer();
  await tv.fermer();
});

test("applis : onglet annoncé, grille, images demandées une seule fois (cache), ouverture", async () => {
  const tv = await banc.tv({ nom: "Salon" });
  const tel = await banc.telephone("iphone");
  journaux.push(tel.journal);
  let page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  await page.locator("#onglets").waitFor();
  assert.ok(await page.isVisible("#btn-tvs"));
  assert.deepEqual(await page.locator(".onglet:visible").allInnerTexts(), ["Télécommande", "Applis", "Réglages"]);
  assert.equal(await page.getAttribute("#onglet-telecommande", "aria-selected"), "true");
  assert.equal((await tv.recus()).length, 0, "rien n'est demandé tant qu'aucun onglet n'est ouvert");

  await page.click("#onglet-applis");
  assert.equal(await page.getAttribute("#onglet-applis", "aria-selected"), "true");
  assert.ok(await page.isHidden("#btn-mode-nav"));
  assert.ok(await page.isHidden("#btn-tvs"));
  assert.equal(await page.textContent("#pastille-nom"), "Salon · iBiTV");
  assert.equal(await page.getAttribute("#pastille-voyant", "data-etat"), "connecte");
  await page.locator(".tuile[data-id]").nth(11).waitFor();
  await page.waitForFunction(() => document.querySelectorAll(".tuile img").length === 12);
  const ids = await page.$$eval(".tuile[data-id]", (l) => l.map((e) => e.dataset.id));
  assert.equal(ids.length, 12);
  assert.deepEqual(ids.slice(0, 4), ["com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "fr.projo.ibitube"]);
  assert.equal(await page.locator(".tuile-favori:not([hidden])").count(), 4);
  assert.equal(await page.getAttribute('.tuile[data-id="com.netflix.ninja"]', "data-forme"), "banniere");
  assert.equal(await page.getAttribute('.tuile[data-id="org.videolan.vlc"]', "data-forme"), "icone");
  assert.equal((await recusDe(tv, "apps_liste")).length, 1);
  const demandes = await recusDe(tv, "apps_images");
  assert.equal(demandes.length, 1);
  assert.deepEqual([...demandes[0].ids].sort(), [...ids].sort());

  // Ouvrir une appli : app_ouvrir, puis confirmation discrète
  await page.click('.tuile[data-id="com.netflix.ninja"]');
  await tv.attendreRecu((r) => r.t === "app_ouvrir" && r.id === "com.netflix.ninja");
  await page.locator("#toast.visible", { hasText: "Netflix s'ouvre sur la TV" }).waitFor();
  assert.deepEqual(await tv.eval(() => window.tv.ouvertes), ["com.netflix.ninja"]);
  // Refus de la TV (ok: false) : message d'erreur
  await tv.eval(() => window.tv.refuserApps.add("org.videolan.vlc"));
  await page.click('.tuile[data-id="org.videolan.vlc"]');
  await page.locator("#toast.visible.erreur", { hasText: "Impossible d'ouvrir VLC" }).waitFor();

  // Retour sur l'onglet : liste redemandée, mais aucune image
  await page.click("#onglet-telecommande");
  await page.click("#onglet-applis");
  await tv.attendreRecu(() => true);
  await page.waitForFunction(() => document.querySelectorAll(".tuile img").length === 12);
  await tv.page.waitForFunction(() => window.tv.recus.filter((r) => r.t === "apps_liste").length === 2);
  await pause(400);
  assert.equal((await recusDe(tv, "apps_images")).length, 1);

  // Réouverture de l'appli : liste et images affichées depuis le cache, aucune image redemandée
  await page.close();
  page = await tel.ouvrir();
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  await page.click("#onglet-applis");
  await page.waitForFunction(() => document.querySelectorAll(".tuile img").length === 12);
  await tv.page.waitForFunction(() => window.tv.recus.filter((r) => r.t === "apps_liste").length === 3);
  await pause(500);
  assert.equal((await recusDe(tv, "apps_images")).length, 1, "images gardées dans IndexedDB");

  // Un champ texte s'ouvre sur la TV : retour automatique à la télécommande
  await tv.envoyer({ t: "focus_text", type: "recherche", s: "", action: "search" });
  await page.locator("#saisie.actif").waitFor();
  assert.equal(await page.getAttribute("#onglet-telecommande", "aria-selected"), "true");
  assert.ok(await page.isHidden("#panneau-applis"));
  assert.equal(await page.evaluate(() => document.querySelector("#panneau-telecommande").hidden), false);
  await tel.fermer();
  await tv.fermer();
});

test("réglages : rendu générique par groupe, design, interrupteurs, curseur limité, mise à jour, envois poussés", async () => {
  const tv = await banc.tv({ nom: "Salon" });
  const tel = await banc.telephone("android");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  await page.click("#onglet-reglages");
  await page.locator('.reglage[data-cle="volume"]').waitFor();
  await tv.attendreRecu((r) => r.t === "reglages_liste");
  assert.deepEqual(await page.locator(".groupe-titre").allTextContents(),
    ["Son", "Affichage", "Mode Ambiance", "Mises à jour", "Ouvrir sur la TV"]);
  assert.deepEqual(await page.$$eval(".reglage[data-cle]", (l) => l.map((e) => `${e.dataset.cle}:${e.dataset.type}`)), [
    "volume:curseur", "muet:interrupteur", "design:choix", "nouveautes:interrupteur", "ambiance_auto:interrupteur",
    "ambiance_intensite:choix", "ambiance_couleurs:choix", "ambiance_decalage:choix", "maj:boutons", "systeme:boutons",
  ]);
  assert.equal(await page.inputValue('.reglage[data-cle="volume"] input[type=range]'), "9");
  assert.equal(await page.textContent('.reglage[data-cle="volume"] .reglage-valeur'), "60 %");
  assert.equal(await page.locator('.reglage[data-cle="design"] .options-designs .option').count(), 6);
  assert.equal(await page.locator('.reglage[data-cle="ambiance_decalage"] .options-pastilles .option').count(), 5);
  assert.equal(await page.textContent('.reglage[data-cle="design"] .option.choisie .option-nom'), "Verre");

  // Design : une carte → reglage {cle: "design", valeur: "pastel"}
  await page.click('.reglage[data-cle="design"] .option:has-text("Pastel")');
  await tv.attendreRecu((r) => r.t === "reglage" && r.cle === "design" && r.valeur === "pastel");
  assert.equal(await tv.eval(() => window.tv.reglages.design), "pastel");
  assert.equal(await page.textContent('.reglage[data-cle="design"] .option.choisie .option-nom'), "Pastel");

  // Interrupteur (toute la ligne est touchable)
  await page.click('.reglage[data-cle="muet"] .reglage-titre');
  await tv.attendreRecu((r) => r.t === "reglage" && r.cle === "muet" && r.valeur === true);
  assert.ok(await page.isChecked('.reglage[data-cle="muet"] input'));
  // Mode Ambiance coupé : la TV grise ses réglages (actif: false)
  await page.click('.reglage[data-cle="ambiance_auto"] input');
  await tv.attendreRecu((r) => r.t === "reglage" && r.cle === "ambiance_auto" && r.valeur === false);
  await page.locator('.reglage[data-cle="ambiance_intensite"].inactif').waitFor();
  assert.ok(await page.isDisabled('.reglage[data-cle="ambiance_intensite"] input >> nth=0'));

  // Curseur : glissé de 9 à 0 en ~600 ms → envois limités (≤ 1 / 150 ms), valeur finale au relâcher
  await tv.viderRecus();
  const piste = await page.locator('.reglage[data-cle="volume"] input[type=range]').boundingBox();
  const xDe = (v) => piste.x + 12 + (v / 15) * (piste.width - 24);
  const y = piste.y + piste.height / 2;
  await page.evaluate(() => { window.__curseur = document.querySelector('.reglage[data-cle="volume"] input'); });
  await page.mouse.move(xDe(9), y);
  await page.mouse.down();
  const t0 = Date.now();
  for (let v = 8.5; v >= -0.5; v -= 0.5) { await page.mouse.move(xDe(v), y); await pause(32); }
  // Changements venus de la TV pendant le glissé (valeur, et structure : nouveaux boutons de mise à jour) :
  // le curseur reste sous le doigt, la reconstruction attend le relâcher
  await tv.eval(() => { window.tv.reglages.maj = { etape: "disponible" }; window.tv.changerReglage("volume", 12); });
  for (let i = 0; i < 6; i++) { await page.mouse.move(xDe(0) - i, y); await pause(70); }
  assert.equal(await page.inputValue('.reglage[data-cle="volume"] input[type=range]'), "0");
  assert.ok(await page.evaluate(() => document.querySelector('.reglage[data-cle="volume"] input') === window.__curseur));
  assert.equal(await page.locator('.reglage[data-cle="maj"] .btn-reglage', { hasText: "Installer" }).count(), 0);
  const duree = Date.now() - t0;
  await page.mouse.up();
  await tv.attendreRecu((r) => r.t === "reglage" && r.cle === "volume" && r.valeur === 0);
  const volumes = (await recusDe(tv, "reglage")).filter((r) => r.cle === "volume").map((r) => r.valeur);
  assert.ok(volumes.length >= 3, `envois pendant le glissé : ${volumes}`);
  // au plus 1 envoi / 150 ms pendant le glissé, + la valeur finale (renvoyée : la TV l'a changée entre-temps)
  assert.ok(volumes.length <= Math.ceil(duree / 150) + 2, `${volumes.length} envois en ${duree} ms`);
  assert.equal(volumes.at(-1), 0);
  await tv.page.waitForFunction(() => window.tv.reglages.volume === 0);
  await page.locator('.reglage[data-cle="maj"] .btn-reglage', { hasText: "Installer" }).waitFor();
  await pause(400);
  assert.equal(await page.inputValue('.reglage[data-cle="volume"] input[type=range]'), "0");

  // Changement venu de la TV : mis à jour sur place, sans faire sauter le défilement
  await page.evaluate(() => {
    const d = document.querySelector("#reglages-defile");
    d.scrollTop = 180;
    window.__volume = document.querySelector('.reglage[data-cle="volume"]');
  });
  await tv.eval(() => window.tv.changerReglage("volume", 11));
  await page.waitForFunction(() => document.querySelector('.reglage[data-cle="volume"] input').value === "11");
  assert.equal(await page.textContent('.reglage[data-cle="volume"] .reglage-valeur'), "73 %");
  assert.equal(await page.evaluate(() => document.querySelector("#reglages-defile").scrollTop), 180);
  assert.ok(await page.evaluate(() => document.querySelector('.reglage[data-cle="volume"]') === window.__volume), "élément conservé");

  // Mise à jour : « Rechercher » → bouton installer → note « touche OK » → progression
  await page.click('.reglage[data-cle="maj"] .btn-reglage:has-text("Rechercher")');
  await tv.attendreRecu((r) => r.t === "reglage" && r.cle === "maj" && r.bouton === "verifier");
  const installer = page.locator('.reglage[data-cle="maj"] .btn-reglage.principal', { hasText: "Installer" });
  await installer.waitFor();
  assert.ok(await page.isHidden('.reglage[data-cle="maj"] .reglage-note'));
  await installer.click();
  await tv.attendreRecu((r) => r.t === "reglage" && r.cle === "maj" && r.bouton === "installer");
  await page.locator('.reglage[data-cle="maj"] .reglage-note', { hasText: "appuyez sur OK" }).waitFor();
  await page.locator('.reglage[data-cle="maj"] .progression:not([hidden])').waitFor();
  await page.locator('.reglage[data-cle="maj"] .reglage-detail', { hasText: "confirmez" }).waitFor({ timeout: 10000 });
  assert.ok(await page.isHidden('.reglage[data-cle="maj"] .progression'));

  // Boutons d'un réglage quelconque
  await page.click('.reglage[data-cle="systeme"] .btn-reglage:has-text("Wi-Fi")');
  await tv.attendreRecu((r) => r.t === "reglage" && r.cle === "systeme" && r.bouton === "wifi");

  // Reconnexion : l'abonnement est renouvelé (onglet ouvert)
  const avant = (await recusDe(tv, "reglages_liste")).length;
  await tv.eval(() => window.tv.couper());
  await page.waitForFunction(() => document.querySelector("#pastille-voyant").dataset.etat !== "connecte");
  await page.locator('#pastille-voyant[data-etat="connecte"]').waitFor({ timeout: 20000 });
  await tv.page.waitForFunction((n) => window.tv.recus.filter((r) => r.t === "reglages_liste").length > n, avant);
  await tel.fermer();
  await tv.fermer();
});
