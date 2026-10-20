// Test d'interopérabilité de bout en bout : PWA (Chromium, profil téléphone) ↔ fausse TV
// (second contexte Chromium) via le faux serveur PeerJS local, en WebRTC réel.
//
// appairage QR → confirmation → paired → key / text / focus_text → reconnexion → coupure
// → revoked ; code de secours ; plusieurs TV ; versions incompatibles ; TV injoignable ;
// mise à jour (bannière) ; aucun texte saisi ni secret dans les journaux.

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
    for (const interdit of [MOT_DE_PASSE, TEXTE, "a=fingerprint", "v=0", "candidate:", "BEGIN", "Téléphone de Marie"]) {
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

test("versions incompatibles : « Mettez à jour iBiProjo sur la TV » / « Rechargez la télécommande »", async () => {
  const ancienne = await banc.tv({ vmax: 0 });
  const tel = await banc.telephone("android");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  let { fragment } = await ancienne.demarrerAppairage();
  await page.goto(banc.urlPWA + fragment);
  await page.fill("#champ-nom", "Tel");
  await page.click("#form-nom button[type=submit]");
  await page.locator("#alerte-titre", { hasText: "Mettez à jour iBiProjo sur la TV" }).waitFor({ timeout: 15000 });
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
  const version = await page.evaluate(() => globalThis.IBIPROJO_CONFIG.versionApp);
  assert.equal(version, "1.0.1-test");
  banc.web.etat.versionApp = "1.0.0-test";
  await tel.fermer();
});
