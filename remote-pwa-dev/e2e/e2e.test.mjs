// Test d'interopérabilité de bout en bout : PWA (Chromium, profil téléphone) ↔ fausse TV
// (second contexte Chromium) via le faux serveur PeerJS local, en WebRTC réel.
//
// appairage QR → confirmation → paired → key (pavé : glisser, flèches, OK) / text / focus_text
// (feuille du clavier, « Aa ») → reconnexion → coupure → revoked ; code de secours ; plusieurs TV ;
// thème qui suit le design de la TV ; versions incompatibles ; TV injoignable ;
// mise à jour (bannière) ; pilotage (onglets Applis et Réglages, TV ancienne sans onglets) ;
// Envoyer sur la TV (liens, VPN coupé, raccourci iOS) ;
// aucun texte saisi, secret, nom d'appli, valeur de réglage ni lien dans les journaux.

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
      "Netflix", "com.netflix", "pastel", "data:image",
      "arte.tv", "youtu", "exemple.fr", "192.168.1", "K7Q2M9XD", "envoyer?c="]) {
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
  assert.equal(await page.textContent("#etat-tv"), "Connectée · design Verre");

  // --- Touches : flèches (tout leur secteur du pavé), OK, puis les touches du bas
  for (const k of ["up", "right", "down", "left"]) await appuyerDirection(page, k);
  await page.click(".pave-ok");
  for (const k of ["back", "home", "menu", "vol-", "mute", "vol+", "playpause"]) await page.click(`#panneau-telecommande .rangee [data-k="${k}"]`);
  await tv.attendreRecu((r) => r.k === "playpause");
  assert.deepEqual((await tv.recus()).map((r) => r.k),
    ["up", "right", "down", "left", "ok", "back", "home", "menu", "vol-", "mute", "vol+", "playpause"]);
  assert.ok((await tv.recus()).every((r) => r.appareil === demande.appareil));

  // --- Glisser sur le pavé : à droite (2 pas), puis toucher OK
  await tv.viderRecus();
  const p = await page.locator("#pave").boundingBox();
  await page.mouse.move(p.x + 60, p.y + p.height / 2);
  await page.mouse.down();
  await page.mouse.move(p.x + 170, p.y + p.height / 2, { steps: 10 });
  await page.mouse.up();
  await page.mouse.click(p.x + p.width / 2, p.y + p.height / 2);
  await tv.attendreRecu((r) => r.k === "ok");
  assert.deepEqual((await tv.recus()).map((r) => r.k), ["right", "right", "ok"]);
  // Un glissé qui part de OK (vers le haut, 3 pas) ne valide pas
  await tv.viderRecus();
  await page.mouse.move(p.x + p.width / 2, p.y + p.height / 2 + 30);
  await page.mouse.down();
  await page.mouse.move(p.x + p.width / 2 + 8, p.y + p.height / 2 - 120, { steps: 12 });
  await page.mouse.up();
  await tv.attendreRecu((r) => r.k === "up");
  await pause(300);
  assert.deepEqual((await tv.recus()).map((r) => r.k), ["up", "up", "up"]);
  // Flèche maintenue : répétition (comme le volume), rien de plus au relâcher
  await tv.viderRecus();
  await page.mouse.move(p.x + p.width / 2, p.y + p.height - 22);
  await page.mouse.down();
  await pause(800);
  await page.mouse.up();
  await pause(250);
  const repetes = (await tv.recus()).map((r) => r.k);
  assert.ok(repetes.length >= 3 && repetes.every((k) => k === "down"), `répétition : ${repetes}`);

  // --- La TV attend un mot de passe : la feuille du clavier s'ouvre seule (masqué, saisie en direct, Entrée → submit)
  await tv.viderRecus();
  assert.ok(await page.isHidden("#feuille-clavier"), "pas de champ permanent sur la télécommande");
  await tv.envoyer({ t: "focus_text", type: "motdepasse", s: "", action: "go" });
  await page.locator("#saisie.actif").waitFor();
  await page.locator("#feuille-clavier:not([hidden])").waitFor();
  assert.equal(await page.textContent("#saisie-aide"), "La TV attend un mot de passe");
  assert.equal(await page.textContent("#btn-entree"), "Valider sur la TV");
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
  await page.locator("#feuille-clavier").waitFor({ state: "hidden" });
  assert.equal(await page.inputValue("#champ-texte"), "");
  assert.equal(await page.getAttribute("#champ-texte", "type"), "text");
  assert.ok(await page.isHidden("#btn-afficher"));

  // --- Champ de recherche pré-rempli : « Rechercher sur la TV » envoie submit
  await tv.viderRecus();
  await tv.envoyer({ t: "focus_text", type: "recherche", s: "abc", action: "search" });
  await page.locator("#saisie.actif").waitFor();
  assert.equal(await page.textContent("#saisie-aide"), "La TV attend une recherche");
  assert.equal(await page.textContent("#btn-entree"), "Rechercher sur la TV");
  assert.equal(await page.inputValue("#champ-texte"), "abc");
  assert.equal(await page.getAttribute("#champ-texte", "type"), "search");
  assert.equal(await page.getAttribute("#champ-texte", "enterkeyhint"), "search");
  await page.fill("#champ-texte", TEXTE);
  await tv.attendreRecu((r) => r.t === "text" && r.s === "bonjour tout le monde");
  await page.click("#btn-entree");
  await tv.attendreRecu((r) => r.t === "submit");
  // Fermée à la main : la TV attend toujours, « Aa » la rouvre telle quelle
  await page.click("#btn-fermer-clavier");
  assert.ok(await page.isHidden("#feuille-clavier"));
  await page.click("#btn-clavier");
  assert.equal(await page.inputValue("#champ-texte"), "bonjour tout le monde");
  assert.equal(await page.textContent("#saisie-aide"), "La TV attend une recherche");
  await tv.envoyer({ t: "focus_lost" });
  await page.locator("#feuille-clavier").waitFor({ state: "hidden" });

  // --- « Aa » : texte libre, envoyé de la même façon ; Échap ferme la feuille
  await tv.viderRecus();
  await page.click("#btn-clavier");
  await page.locator("#feuille-clavier:not([hidden])").waitFor();
  assert.equal(await page.textContent("#saisie-aide"), "Écrire sur la TV");
  assert.equal(await page.evaluate(() => document.activeElement.id), "champ-texte");
  await page.keyboard.type("ok");
  await page.click("#btn-entree");
  await tv.attendreRecu((r) => r.t === "submit");
  assert.deepEqual((await tv.recus()).map((r) => [r.t, r.s]), [["text", "o"], ["text", "ok"], ["submit", undefined]]);
  await page.keyboard.press("Escape");
  assert.ok(await page.isHidden("#feuille-clavier"));

  // --- Reconnexion à la réouverture (identité et TV en IndexedDB)
  await page.close();
  await tv.viderRecus();
  page = await tel.ouvrir();
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  assert.ok(await page.isVisible("#ecran-telecommande"));
  await page.click('[data-k="home"]');
  await tv.attendreRecu((r) => r.k === "home");

  // --- Coupure du réseau local : reconnexion automatique
  await tv.eval(() => window.tv.couper());
  await page.waitForFunction(() => document.querySelector("#voyant").dataset.etat !== "connecte");
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 20000 });
  await tv.viderRecus();
  await page.click('[data-k="back"]');
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
  await page.click('[data-k="mute"]');
  await chambre.attendreRecu((r) => r.k === "mute");

  // Choix de la TV pilotée
  await page.click("#btn-tvs");
  assert.equal(await page.locator(".tv-choix").count(), 2);
  await page.click('.tv-choix:has-text("Salon")');
  await page.locator("#nom-tv", { hasText: "Salon" }).waitFor();
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  await salon.viderRecus();
  await page.click('[data-k="vol+"]');
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

// ---------- Thème : la télécommande suit le design de la TV ----------

const themeDe = (page) => page.evaluate(() => document.documentElement.dataset.theme);
const couleurBarre = (page) => page.getAttribute('meta[name="theme-color"]', "content");

test("thème : défaut sans TV, design du hello, changement en direct, gardé par TV, appliqué avant la connexion", async () => {
  // Sans TV : Galerie (clair) ; téléphone en mode sombre : Verre
  const sombre = await banc.telephone("iphone", { sombre: true });
  journaux.push(sombre.journal);
  const pageSombre = await sombre.ouvrir();
  await pageSombre.locator("#ecran-accueil").waitFor();
  assert.equal(await themeDe(pageSombre), "verre");
  assert.equal(await couleurBarre(pageSombre), "#241a4c");
  // TV sans design (ancienne) : thème par défaut, ici sombre
  const ancienne = await banc.tv({ nom: "Ancienne", design: null });
  await appairerParQR(pageSombre, ancienne);
  assert.equal(await themeDe(pageSombre), "verre");
  assert.equal(await pageSombre.textContent("#etat-tv"), "Connectée");
  await sombre.fermer();

  const salon = await banc.tv({ nom: "Salon", design: "console" });
  const bureau = await banc.tv({ nom: "Bureau", design: null });
  const tel = await banc.telephone("android");
  journaux.push(tel.journal);
  let page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  assert.equal(await themeDe(page), "galerie");
  assert.equal(await couleurBarre(page), "#F4F5F7");

  // Design annoncé dans le hello
  await appairerParQR(page, salon);
  assert.equal(await themeDe(page), "console");
  assert.equal(await couleurBarre(page), "#250c15");
  assert.equal(await page.textContent("#etat-tv"), "Connectée · design Console");
  // Le design change sur la TV : poussé à la télécommande, sans abonnement (aucun onglet ouvert)
  assert.equal((await salon.recus()).length, 0);
  await salon.design("pastel");
  await page.waitForFunction(() => document.documentElement.dataset.theme === "pastel");
  assert.equal(await page.textContent("#etat-tv"), "Connectée · design Pastel");
  assert.equal(await couleurBarre(page), "#f8e6f2");
  // Sur les autres onglets aussi
  await page.click("#onglet-reglages");
  await salon.design("une");
  await page.waitForFunction(() => document.documentElement.dataset.theme === "une");
  await page.click("#onglet-telecommande");

  // Seconde TV sans design : thème par défaut ; retour à la première : son design, gardé
  await page.click("#btn-tvs");
  await page.click("#btn-ajouter-tv");
  await appairerParQR(page, bureau);
  assert.equal(await themeDe(page), "galerie");
  assert.equal(await page.textContent("#etat-tv"), "Connectée");
  await page.click("#btn-tvs");
  assert.match(await page.textContent('.tv-choix:has-text("Salon") .tv-detail'), /design À la une/);
  await page.click('.tv-choix:has-text("Salon")');
  await page.waitForFunction(() => document.documentElement.dataset.theme === "une");
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });

  // Réouverture, TV éteinte : le design gardé est appliqué dès l'ouverture, avant toute connexion
  await page.close();
  await salon.fermer();
  page = await tel.ouvrir();
  assert.equal(await themeDe(page), "une", "appliqué avant le premier affichage (theme-initial.js)");
  await page.locator("#ecran-telecommande").waitFor();
  assert.equal(await themeDe(page), "une");
  assert.notEqual(await page.getAttribute("#voyant", "data-etat"), "connecte");
  // L'autre TV choisie : son thème (par défaut), même hors connexion
  await page.click("#btn-tvs");
  await page.click('.tv-choix:has-text("Bureau")');
  await page.waitForFunction(() => document.documentElement.dataset.theme === "galerie");
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  await page.close();
  page = await tel.ouvrir();
  assert.equal(await themeDe(page), "galerie", "thème par défaut : rien de retenu");
  await tel.fermer();
  await bureau.fermer();
  await ancienne.fermer();
});

test("accessibilité : noms des boutons, cibles ≥ 48 px (touches ≥ 56 px), texte en rem, animations et contraste", async () => {
  const tv = await banc.tv({ nom: "Salon", design: "galerie" });
  const tel = await banc.telephone("iphone");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  /** Boutons visibles de l'écran : sans nom accessible, ou plus petits que `min` px. */
  const verifier = (selecteur, min = 48) => page.$$eval(selecteur, (l, min) => l
    .filter((b) => b.getClientRects().length && getComputedStyle(b).visibility !== "hidden")
    .flatMap((b) => {
      const r = b.getBoundingClientRect();
      const par = b.getAttribute("aria-labelledby")?.split(" ").map((i) => document.getElementById(i)?.textContent).join(" ");
      const nom = (b.getAttribute("aria-label") || par || b.textContent || b.closest("label")?.textContent || "").trim();
      const id = b.id || b.dataset.k || b.dataset.cle || b.className;
      return [...(!nom ? [`${id} : sans nom`] : []), ...(r.width < min - 0.5 || r.height < min - 0.5 ? [`${id} : ${Math.round(r.width)}×${Math.round(r.height)}`] : [])];
    }), min);
  assert.deepEqual(await verifier("#ecran-telecommande button:not(.pave-dir)"), []);
  assert.deepEqual(await verifier(".rangee-touches button, .touche-lecture, .pave-ok, #btn-tvs, #btn-clavier", 56), []);
  // Flèches : tout un secteur du pavé
  assert.deepEqual(await verifier(".pave-dir", 100), []);
  await page.click("#btn-clavier");
  assert.deepEqual(await verifier("#feuille-clavier button, #champ-texte"), []);
  await page.keyboard.press("Escape");
  await page.click("#onglet-reglages");
  await page.locator('.reglage[data-cle="volume"]').waitFor();
  assert.deepEqual(await verifier("#panneau-reglages .btn-reglage, #panneau-reglages .option, #panneau-reglages .reglage-ligne, #panneau-reglages input[type=range], #pastille-tv"), []);
  await page.click("#onglet-envoyer");
  await page.locator("#etat-vpn:not([hidden])").waitFor();
  assert.deepEqual(await verifier("#panneau-envoyer button:not([hidden]), #champ-lien"), []);
  await page.click("#onglet-telecommande");

  // Texte en rem : il suit la taille du texte du téléphone (iOS : Dynamic Type sur <html>)
  const tailles = () => page.evaluate(() => ["#nom-tv", ".touche span", ".pave-indice"].map((s) => parseFloat(getComputedStyle(document.querySelector(s)).fontSize)));
  const avant = await tailles();
  await page.evaluate(() => { document.documentElement.style.fontSize = "24px"; });
  const apres = await tailles();
  apres.forEach((t, i) => assert.ok(Math.abs(t / avant[i] - 1.5) < 0.02, `${t} / ${avant[i]}`));
  // … et rien ne déborde horizontalement
  assert.ok(await page.evaluate(() => document.querySelector("#panneau-telecommande").scrollWidth <= innerWidth));
  await page.evaluate(() => { document.documentElement.style.fontSize = ""; });

  // Animations réduites, contraste renforcé
  await page.emulateMedia({ reducedMotion: "reduce" });
  assert.ok(parseFloat(await page.evaluate(() => getComputedStyle(document.querySelector("#ecran-telecommande")).animationDuration)) < 0.001);
  assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector(".pave-ok")).transitionDuration), "0s");
  await page.emulateMedia({ contrast: "more" });
  const [texte, texte2] = await page.evaluate(() => {
    const st = getComputedStyle(document.querySelector(".pave-indice"));
    return [getComputedStyle(document.querySelector("#nom-tv")).color, st.color];
  });
  assert.equal(texte2, texte, "texte secondaire plein en contraste renforcé");
  await tel.fermer();
  await tv.fermer();
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

test("TV ancienne (sans « fonctions » ni « design ») : télécommande seule, thème par défaut, sans erreur", async () => {
  const tv = await banc.tv({ nom: "Ancienne", fonctions: null, design: null });
  const tel = await banc.telephone("android");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  await page.click('[data-k="home"]');
  await tv.attendreRecu((r) => r.k === "home");
  assert.ok(await page.isHidden("#onglets"));
  assert.ok(await page.isVisible("#btn-clavier"));
  assert.equal(await page.getAttribute("html", "data-theme"), "galerie");
  assert.equal(await page.textContent("#etat-tv"), "Connectée");
  assert.equal(await page.evaluate(() => document.querySelector("#ecran-telecommande").classList.contains("avec-onglets")), false);
  // Rien du pilotage n'a été envoyé à cette TV
  assert.deepEqual(await tv.eval(() => window.tv.ignores), []);
  assert.deepEqual((await tv.recus()).map((r) => r.t), ["key"]);
  await tel.fermer();
  await tv.fermer();
});

test("applis : onglet annoncé, grille, images demandées une seule fois (cache), ouverture", async () => {
  // TV qui n'annonce pas « liens » : pas d'onglet « Envoyer »
  const tv = await banc.tv({ nom: "Salon", fonctions: ["apps", "reglages"] });
  const tel = await banc.telephone("iphone");
  journaux.push(tel.journal);
  let page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  await page.locator("#onglets").waitFor();
  assert.ok(await page.isVisible("#btn-tvs"));
  assert.deepEqual(await page.locator(".onglet:visible").allInnerTexts(), ["Télécommande", "Applis", "Réglages"]);
  assert.ok(await page.isHidden("#onglet-envoyer"));
  assert.equal(await page.getAttribute("#onglet-telecommande", "aria-selected"), "true");
  assert.equal((await tv.recus()).length, 0, "rien n'est demandé tant qu'aucun onglet n'est ouvert");

  await page.click("#onglet-applis");
  assert.equal(await page.getAttribute("#onglet-applis", "aria-selected"), "true");
  assert.ok(await page.isHidden("#btn-clavier"));
  assert.ok(await page.isHidden("#btn-tvs"));
  assert.equal(await page.textContent("#pastille-nom"), "Salon");
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
  assert.ok(await page.isVisible("#feuille-clavier"));
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
  assert.deepEqual(await page.locator("#reglages-liste .groupe-titre").allTextContents(),
    ["Son", "Affichage", "Mode Ambiance", "Envoyer sur la TV", "Mises à jour", "Ouvrir sur la TV"]);
  assert.deepEqual(await page.$$eval(".reglage[data-cle]", (l) => l.map((e) => `${e.dataset.cle}:${e.dataset.type}`)), [
    "volume:curseur", "muet:interrupteur", "design:choix", "nouveautes:interrupteur", "ambiance_auto:interrupteur",
    "ambiance_intensite:choix", "ambiance_couleurs:choix", "ambiance_decalage:choix",
    "liens_seulement_vpn:interrupteur", "liens_pages:choix", "liens_raccourci:interrupteur", "liens_code:boutons",
    "maj:boutons", "systeme:boutons",
  ]);
  assert.equal(await page.inputValue('.reglage[data-cle="volume"] input[type=range]'), "9");
  assert.equal(await page.textContent('.reglage[data-cle="volume"] .reglage-valeur'), "60 %");
  assert.equal(await page.locator('.reglage[data-cle="design"] .options-designs .option').count(), 7);
  assert.equal(await page.locator('.reglage[data-cle="design"] .apercu-design[data-id="galerie"]').count(), 1);
  assert.equal(await page.locator('.reglage[data-cle="ambiance_decalage"] .options-pastilles .option').count(), 5);
  assert.equal(await page.textContent('.reglage[data-cle="design"] .option.choisie .option-nom'), "Verre");

  // Design : une carte → reglage {cle: "design", valeur: "pastel"}
  await page.click('.reglage[data-cle="design"] .option:has-text("Pastel")');
  await tv.attendreRecu((r) => r.t === "reglage" && r.cle === "design" && r.valeur === "pastel");
  assert.equal(await tv.eval(() => window.tv.reglages.design), "pastel");
  assert.equal(await page.textContent('.reglage[data-cle="design"] .option.choisie .option-nom'), "Pastel");
  // La TV pousse son nouveau design : la télécommande prend son style
  await page.waitForFunction(() => document.documentElement.dataset.theme === "pastel");

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

// ---------- Envoyer sur la TV ----------

const ARTE = "https://arte.tv/fr/videos/RC-014035/les-grands-documentaires/";
const YOUTUBE = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
const ADRESSE = "http://192.168.1.42:8642/envoyer?c=K7Q2M9XD";
/** Efface la confirmation affichée : la suivante est forcément nouvelle. */
const viderToast = (page) => page.evaluate(() => {
  document.querySelector("#toast").classList.remove("visible", "erreur");
  document.querySelector("#toast-texte").textContent = "";
});
const toast = (page, texte, { erreur = false, timeout = 8000 } = {}) =>
  page.locator(erreur ? "#toast.visible.erreur" : "#toast.visible:not(.erreur)", { hasText: texte }).waitFor({ timeout });
const titresRecents = (page) => page.locator(".recent .recent-titre").allTextContents();
const renvoyer = async (page, texte) => { await viderToast(page); await page.click(`.recent:has-text("${texte}") .btn-ligne`); };

test("envoyer : envoi, récents, VPN coupé (feuille : annuler, sans VPN, allumer), refus, sans réponse, reconnexion", async () => {
  const tv = await banc.tv({ nom: "Salon" });
  const tel = await banc.telephone("iphone");
  journaux.push(tel.journal);
  let page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  assert.deepEqual(await page.locator(".onglet:visible").allInnerTexts(), ["Télécommande", "Applis", "Envoyer", "Réglages"]);
  assert.equal((await tv.recus()).length, 0, "rien n'est demandé tant qu'aucun onglet n'est ouvert");

  // Ouverture de l'onglet : liens_etat, ligne d'état du VPN
  await page.click("#onglet-envoyer");
  await tv.attendreRecu((r) => r.t === "liens_etat");
  await page.locator("#etat-vpn-texte", { hasText: "VPN de la TV actif · les pages s'ouvrent dans VYPN" }).waitFor();
  assert.equal(await page.getAttribute("#etat-vpn", "data-etat"), "actif");
  assert.equal(await page.textContent("#pastille-nom"), "Salon");
  assert.ok(await page.isDisabled("#btn-envoyer"));
  assert.ok(await page.isVisible(".recents-vide"));

  // Bouton actif seulement pour un lien ; domaine sans schéma complété en https://
  await page.fill("#champ-lien", "bonjour");
  assert.ok(await page.isDisabled("#btn-envoyer"));
  await page.fill("#champ-lien", "arte.tv/fr/videos/RC-014035/les-grands-documentaires/");
  assert.ok(await page.isEnabled("#btn-envoyer"));
  await page.click("#btn-envoyer");
  await toast(page, "Envoyé sur Salon · ouverture dans VYPN");
  assert.deepEqual(await recusDe(tv, "lien_ouvrir"), [{ appareil: (await recusDe(tv, "liens_etat"))[0].appareil, t: "lien_ouvrir", url: ARTE }]);
  assert.equal(await page.inputValue("#champ-lien"), "", "champ vidé");
  assert.deepEqual(await titresRecents(page), ["Les grands documentaires"]);
  assert.equal(await page.textContent(".recent .recent-detail"), "arte.tv · ouvert dans VYPN · à l'instant");

  // YouTube (touche Entrée) : ouvert dans iBiTube, en tête des récents
  await viderToast(page);
  await page.fill("#champ-lien", YOUTUBE);
  await page.press("#champ-lien", "Enter");
  await toast(page, "Envoyé sur Salon · ouverture dans iBiTube");
  assert.deepEqual(await titresRecents(page), ["youtube.com/watch?v=dQw4w9WgXcQ", "Les grands documentaires"]);
  // Renvoyer : le lien remonte en tête, sans doublon
  await renvoyer(page, "Les grands documentaires");
  await toast(page, "ouverture dans VYPN");
  assert.deepEqual(await titresRecents(page), ["Les grands documentaires", "youtube.com/watch?v=dQw4w9WgXcQ"]);
  assert.equal((await recusDe(tv, "lien_ouvrir")).length, 3);

  // Le VPN de la TV se coupe : état poussé par la TV
  await tv.liens({ vpn: false });
  await page.locator("#etat-vpn-texte", { hasText: "VPN de la TV coupé · la TV demandera de l'allumer" }).waitFor();
  assert.equal(await page.getAttribute("#etat-vpn", "data-etat"), "coupe");
  // vpn_coupe → feuille ; « Annuler » n'envoie rien de plus
  await renvoyer(page, "youtube.com");
  await page.locator("#feuille-vpn:not([hidden])").waitFor();
  assert.equal(await page.textContent("#titre-vpn"), "Le VPN de la TV est coupé");
  assert.equal(await page.textContent("#btn-vpn-allumer"), "Allumer le VPN (VYPN) et ouvrir");
  assert.equal(await page.textContent("#lien-vpn"), "youtube.com/watch?v=dQw4w9WgXcQ");
  const n = (await recusDe(tv, "lien_ouvrir")).length;
  await page.click("#btn-vpn-annuler");
  assert.ok(await page.isHidden("#feuille-vpn"));
  await pause(400);
  assert.equal((await recusDe(tv, "lien_ouvrir")).length, n);
  // « Ouvrir sans VPN » : renvoi avec sansVpn
  await renvoyer(page, "youtube.com");
  await page.locator("#feuille-vpn:not([hidden])").waitFor();
  await page.click("#btn-vpn-sans");
  await tv.attendreRecu((r) => r.t === "lien_ouvrir" && r.url === "https://www.youtube.com/watch?v=dQw4w9WgXcQ" && r.sansVpn === true);
  await toast(page, "Envoyé sur Salon · ouverture dans iBiTube");
  assert.ok(await page.isHidden("#feuille-vpn"));
  assert.deepEqual((await tv.eval(() => window.tv.liensOuverts)).at(-1), { url: YOUTUBE, appli: "iBiTube", sansVpn: true });
  // « Allumer le VPN et ouvrir » : vpnDabord → attente_vpn, puis ouverture quand le VPN s'allume
  await renvoyer(page, "Les grands documentaires");
  await page.locator("#feuille-vpn:not([hidden])").waitFor();
  await page.click("#btn-vpn-allumer");
  await tv.attendreRecu((r) => r.t === "lien_ouvrir" && r.url === "https://arte.tv/fr/videos/RC-014035/les-grands-documentaires/" && r.vpnDabord === true);
  await toast(page, "Allume le VPN sur la TV : le lien s'ouvrira ensuite");
  assert.equal(await tv.eval(() => window.tv.applisVpnOuvertes), 1);
  await page.locator('.recent.en-cours:has-text("Les grands documentaires")').waitFor();
  await viderToast(page);
  await tv.liens({ vpn: true });
  await toast(page, "Envoyé sur Salon · ouverture dans VYPN");
  assert.equal(await page.locator(".recent.en-cours").count(), 0);
  await page.locator("#etat-vpn-texte", { hasText: "VPN de la TV actif" }).waitFor();

  // Une seule attente à la fois (remplace), puis délai dépassé (delai_vpn)
  await tv.liens({ vpn: false, attenteMs: 1500 });
  await page.locator('#etat-vpn[data-etat="coupe"]').waitFor();
  for (const titre of ["Les grands documentaires", "youtube.com"]) {
    await renvoyer(page, titre);
    await page.locator("#feuille-vpn:not([hidden])").waitFor();
    await page.click("#btn-vpn-allumer");
    await toast(page, "Allume le VPN sur la TV");
  }
  await viderToast(page);
  await toast(page, "Le VPN de la TV ne s'est pas allumé à temps", { erreur: true });
  assert.ok(tel.journal.includes("[iBiTV] lien_refuse remplace"));

  // Sans appli VPN connue : pas de « Allumer le VPN »
  await tv.liens({ appliVpn: null, attenteMs: 60000 });
  await pause(500);
  await renvoyer(page, "youtube.com");
  await page.locator("#feuille-vpn:not([hidden])").waitFor();
  assert.ok(await page.isHidden("#btn-vpn-allumer"));
  await page.keyboard.press("Escape");
  assert.ok(await page.isHidden("#feuille-vpn"));
  await tv.liens({ vpn: true, appliVpn: "VYPN" });

  // Refus de la TV : message clair, champ gardé, rien dans les récents
  await tv.eval(() => window.tv.liensForcer.push({ ok: false, raison: "aucune_appli" }));
  await viderToast(page);
  await page.fill("#champ-lien", "https://exemple.fr/page");
  await page.click("#btn-envoyer");
  await toast(page, "Aucune appli de la TV ne sait ouvrir ce lien", { erreur: true });
  assert.equal(await page.inputValue("#champ-lien"), "https://exemple.fr/page");
  // Pas de réponse en 6 s : erreur
  await tv.eval(() => window.tv.liensForcer.push("silence"));
  await viderToast(page);
  await page.click("#btn-envoyer");
  await page.locator("#btn-envoyer.en-cours").waitFor();
  await toast(page, "La TV n'a pas confirmé l'envoi du lien", { erreur: true, timeout: 9000 });
  assert.ok(await page.isEnabled("#btn-envoyer"));
  assert.equal(await page.locator(".recent").count(), 2);
  assert.ok(tel.journal.includes("[iBiTV] lien_refuse sans_reponse"));

  // Réouverture : récents gardés sur le téléphone, liens_etat redemandé
  await page.close();
  page = await tel.ouvrir();
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  const avant = (await recusDe(tv, "liens_etat")).length;
  await page.click("#onglet-envoyer");
  await tv.page.waitForFunction((n) => window.tv.recus.filter((r) => r.t === "liens_etat").length > n, avant);
  assert.deepEqual(await titresRecents(page), ["Les grands documentaires", "youtube.com/watch?v=dQw4w9WgXcQ"]);
  // Reconnexion pendant que l'onglet est ouvert : liens_etat renvoyé (abonnement par connexion)
  const avant2 = (await recusDe(tv, "liens_etat")).length;
  await tv.eval(() => window.tv.couper());
  await page.waitForFunction(() => document.querySelector("#pastille-voyant").dataset.etat !== "connecte");
  await page.locator('#pastille-voyant[data-etat="connecte"]').waitFor({ timeout: 20000 });
  await tv.page.waitForFunction((n) => window.tv.recus.filter((r) => r.t === "liens_etat").length > n, avant2);
  // Un champ texte sur la TV ramène sur la télécommande
  await tv.envoyer({ t: "focus_text", type: "url", s: "", action: "go" });
  await page.locator("#saisie.actif").waitFor();
  assert.ok(await page.isHidden("#panneau-envoyer"));
  assert.equal(tel.journal.filter((l) => l === "[iBiTV] lien_envoye").length, 5, "un par lien ouvert");
  await tel.fermer();
  await tv.fermer();
});

test("envoyer : effacer les récents (retirer un lien, tout effacer avec confirmation, gardé à la réouverture)", async () => {
  const tv = await banc.tv({ nom: "Salon" });
  const tel = await banc.telephone("iphone");
  journaux.push(tel.journal);
  let page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  await page.click("#onglet-envoyer");
  await tv.attendreRecu((r) => r.t === "liens_etat");
  // Aucun récent : pas de « Modifier »
  assert.ok(await page.isHidden("#btn-recents-modifier"));
  for (const url of [ARTE, YOUTUBE, "https://exemple.fr/videos/le-grand-film"]) {
    await viderToast(page);
    await page.fill("#champ-lien", url);
    await page.click("#btn-envoyer");
    await toast(page, "Envoyé sur Salon");
  }
  assert.deepEqual(await titresRecents(page), ["Le grand film", "youtube.com/watch?v=dQw4w9WgXcQ", "Les grands documentaires"]);
  // « Modifier » : « Retirer » à la place de « Renvoyer », « Tout effacer » sous la liste
  await page.click("#btn-recents-modifier");
  assert.equal(await page.textContent("#btn-recents-modifier"), "OK");
  assert.equal(await page.getAttribute("#btn-recents-modifier", "aria-pressed"), "true");
  assert.equal(await page.locator(".recent .btn-retirer").count(), 3);
  assert.equal(await page.locator(".recent .btn-ligne:not(.btn-retirer)").count(), 0);
  assert.ok(await page.isVisible("#btn-recents-effacer"));
  const envois = (await recusDe(tv, "lien_ouvrir")).length;
  await page.click('.recent:has-text("youtube.com") .btn-retirer');
  assert.deepEqual(await titresRecents(page), ["Le grand film", "Les grands documentaires"]);
  assert.equal((await recusDe(tv, "lien_ouvrir")).length, envois, "retirer n'envoie rien à la TV");
  // « OK » : retour aux boutons « Renvoyer »
  await page.click("#btn-recents-modifier");
  assert.equal(await page.textContent("#btn-recents-modifier"), "Modifier");
  assert.ok(await page.isHidden("#btn-recents-effacer"));
  assert.equal(await page.locator(".recent .btn-retirer").count(), 0);
  // Réouverture : le lien retiré ne revient pas (écriture sur le téléphone terminée)
  await pause(300);
  await page.close();
  page = await tel.ouvrir();
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  await page.click("#onglet-envoyer");
  await page.locator(".recent").first().waitFor();
  assert.deepEqual(await titresRecents(page), ["Le grand film", "Les grands documentaires"]);
  // « Tout effacer » : confirmation ; Annuler ne change rien, Effacer vide la liste
  await page.click("#btn-recents-modifier");
  await page.click("#btn-recents-effacer");
  await page.locator("#alerte:not([hidden])").waitFor();
  assert.equal(await page.textContent("#alerte-titre"), "Effacer les liens récents ?");
  assert.equal(await page.textContent("#alerte-texte"), "Les 2 liens envoyés seront retirés de ce téléphone.");
  await page.click("#alerte-annuler");
  assert.equal(await page.locator(".recent").count(), 2);
  await page.click("#btn-recents-effacer");
  await page.locator("#alerte:not([hidden])").waitFor();
  await viderToast(page);
  await page.click("#alerte-ok");
  await toast(page, "Liens récents effacés");
  assert.ok(await page.isVisible(".recents-vide"));
  assert.ok(await page.isHidden("#btn-recents-modifier"));
  assert.ok(await page.isHidden("#btn-recents-effacer"));
  await page.close();
  page = await tel.ouvrir();
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  await page.click("#onglet-envoyer");
  await tv.attendreRecu((r) => r.t === "liens_etat");
  await pause(300);
  assert.ok(await page.isVisible(".recents-vide"), "liste vide après réouverture");
  await tel.fermer();
  await tv.fermer();
});

test("envoyer : coller (presse-papiers), bouton Partager (activer, copier, adresse changée)", async () => {
  const tv = await banc.tv({ nom: "Salon" });
  const tel = await banc.telephone("iphone");
  journaux.push(tel.journal);
  const page = await tel.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await appairerParQR(page, tv);
  await page.click("#onglet-envoyer");
  await page.locator("#etat-vpn:not([hidden])").waitFor();

  // Presse-papiers refusé : invitation à coller à la main
  await page.click("#btn-coller");
  await page.locator("#envoyer-aide", { hasText: "colle le lien dans le champ" }).waitFor();
  // Accordé : le texte partagé est collé, son lien envoyé
  await tel.ctx.grantPermissions(["clipboard-read", "clipboard-write"], { origin: banc.base });
  await page.evaluate(() => navigator.clipboard.writeText("Regarde ça : https://youtu.be/dQw4w9WgXcQ"));
  await page.click("#btn-coller");
  await page.waitForFunction(() => document.querySelector("#champ-lien").value.includes("youtu.be"));
  assert.ok(await page.isHidden("#envoyer-aide"));
  assert.ok(await page.isEnabled("#btn-envoyer"));
  await page.click("#btn-envoyer");
  await tv.attendreRecu((r) => r.t === "lien_ouvrir" && r.url === "https://youtu.be/dQw4w9WgXcQ");
  await toast(page, "ouverture dans iBiTube");
  await page.evaluate(() => navigator.clipboard.writeText("bonjour"));
  await page.click("#btn-coller");
  await page.locator("#envoyer-aide", { hasText: "ne contient pas de lien" }).waitFor();
  assert.ok(await page.isDisabled("#btn-envoyer"));

  // « Depuis Safari » → écran « Bouton Partager » ; envoi depuis l'iPhone désactivé sur la TV
  await page.click("#btn-raccourci");
  assert.ok(await page.isVisible("#envoyer-guide"));
  assert.ok(await page.isHidden("#envoyer-principal"));
  assert.equal(await page.locator(".etapes-raccourci li").count(), 4);
  assert.equal(await page.textContent("#guide-nom-tv"), "Salon");
  await page.locator("#guide-activer:not([hidden])").waitFor();
  assert.ok(await page.isHidden("#guide-adresse"));
  await viderToast(page);
  await page.click("#btn-activer");
  await tv.attendreRecu((r) => r.t === "reglage" && r.cle === "liens_raccourci" && r.valeur === true);
  await page.locator("#guide-adresse-texte", { hasText: ADRESSE }).waitFor();
  await toast(page, "Envoi depuis l'iPhone activé sur la TV");
  assert.ok(await page.isHidden("#guide-activer"));
  assert.ok(await page.isHidden("#guide-bandeau"), "jamais copiée : pas de bandeau");

  // Copier : presse-papiers, adresse retenue pour cette TV
  await page.click("#btn-copier");
  await toast(page, "Adresse copiée");
  assert.equal(await page.evaluate(() => navigator.clipboard.readText()), ADRESSE);
  assert.equal(await page.textContent("#btn-copier"), "Copiée");
  // L'adresse change (box redémarrée) : bandeau, ici et sur la carte « Depuis Safari »
  await tv.liens({ ip: "192.168.1.57" });
  await page.locator("#guide-bandeau:not([hidden])", { hasText: "L'adresse de la TV a changé : mets à jour ton raccourci" }).waitFor();
  await page.click("#btn-guide-retour");
  assert.ok(await page.isVisible("#envoyer-principal"));
  assert.equal(await page.textContent("#raccourci-detail"), "L'adresse de la TV a changé : mets à jour ton raccourci");
  // Onglet touché de nouveau depuis « Bouton Partager » : retour à l'onglet
  await page.click("#btn-raccourci");
  await page.click("#onglet-envoyer");
  assert.ok(await page.isVisible("#envoyer-principal"));
  // Nouvelle adresse copiée : plus de bandeau, même après réouverture
  await page.click("#btn-raccourci");
  await page.click("#btn-copier");
  await page.locator("#guide-bandeau").waitFor({ state: "hidden" });
  assert.equal(await page.evaluate(() => navigator.clipboard.readText()), ADRESSE.replace(".42", ".57"));
  await page.reload();
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  await page.click("#onglet-envoyer");
  await page.locator("#etat-vpn:not([hidden])").waitFor();
  assert.equal(await page.textContent("#raccourci-detail"), "Envoie la page que tu regardes, sans quitter Safari");
  // Nouveau code sur la TV : l'adresse change aussi
  await tv.liens({ code: "ABCD2345" });
  await page.locator("#raccourci-detail", { hasText: "L'adresse de la TV a changé" }).waitFor();

  // TV sans adresse sur le réseau local : l'activation n'aboutit pas, message clair
  await tv.liens({ raccourci: false, ip: null });
  await page.click("#btn-raccourci");
  await page.locator("#guide-activer:not([hidden])").waitFor();
  await viderToast(page);
  await page.click("#btn-activer");
  assert.equal(await page.textContent("#btn-activer"), "Activation…");
  await toast(page, "La TV n'a pas d'adresse sur le réseau local", { erreur: true, timeout: 9000 });
  assert.equal(await page.textContent("#btn-activer"), "Activer sur la TV");
  await tel.fermer();
  await tv.fermer();
});

