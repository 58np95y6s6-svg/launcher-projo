// Captures de chaque écran, en profil iPhone (390×844) et Android (412×915).
// Usage : node remote-pwa-dev/outils/captures.mjs [dossier de sortie]

import { mkdir } from "node:fs/promises";
import path from "node:path";
import { demarrerBanc, appairerParQR } from "../e2e/banc.mjs";

const SORTIE = path.resolve(process.argv[2] || "captures-pwa");
await mkdir(SORTIE, { recursive: true });
const pause = (ms) => new Promise((r) => setTimeout(r, ms));
const banc = await demarrerBanc();

async function serie(modele) {
  const salon = await banc.tv({ nom: "Salon" });
  const chambre = await banc.tv({ nom: "Chambre" });
  const tel = await banc.telephone(modele);
  let page = await tel.ouvrir();
  let n = 0;
  const capture = async (nom) => {
    await pause(350); // fin des animations d'entrée
    const f = path.join(SORTIE, `${modele}-${String(++n).padStart(2, "0")}-${nom}.png`);
    await page.screenshot({ path: f });
    console.log(f);
  };
  const inviterInstallation = () => page.evaluate(() => {
    if (/iPhone/.test(navigator.userAgent)) return;
    const e = new Event("beforeinstallprompt", { cancelable: true });
    e.prompt = async () => {};
    e.userChoice = Promise.resolve({ outcome: "dismissed" });
    dispatchEvent(e);
  });

  await page.locator("#ecran-accueil").waitFor();
  await inviterInstallation();
  await capture("accueil");

  const { fragment } = await salon.demarrerAppairage();
  await page.goto(banc.urlPWA + fragment);
  await page.locator("#ecran-appairage").waitFor();
  await inviterInstallation();
  await page.fill("#champ-nom", "Téléphone de Marie");
  await capture("appairage-nom");

  await page.click("#form-nom button[type=submit]");
  await page.locator("#attente-titre", { hasText: "Confirmez" }).waitFor();
  await salon.attendreDemande();
  await capture("attente-confirmation");

  await salon.confirmer(true);
  await page.locator("#resultat-titre", { hasText: "Appairée" }).waitFor();
  await capture("appairee");

  await page.click("#btn-resultat");
  await page.locator('#voyant[data-etat="connecte"]').waitFor();
  await capture("telecommande");

  await page.click("#btn-mode-nav");
  await capture("pave-tactile");
  await page.click("#btn-mode-nav");

  await salon.envoyer({ t: "focus_text", type: "motdepasse", s: "", action: "go" });
  await page.locator("#saisie.actif").waitFor();
  await pause(600);
  await capture("focus-text-invite");
  await page.click("#champ-texte");
  await page.keyboard.type("Tr3s-S3cret!");
  await capture("mot-de-passe-masque");
  await page.click("#btn-afficher");
  await capture("mot-de-passe-affiche");
  await salon.envoyer({ t: "focus_lost" });
  await page.waitForFunction(() => !document.querySelector("#saisie").classList.contains("actif"));

  // Onglets « Applis » et « Réglages » (fonctions annoncées par la TV)
  await page.click("#onglet-applis");
  await page.waitForFunction(() => document.querySelectorAll(".tuile img").length >= 12);
  await capture("applis");
  await page.click('.tuile[data-id="com.amazon.amazonvideo.livingroom"]');
  await page.locator("#toast.visible").waitFor();
  await capture("applis-ouverture");

  // Onglet « Envoyer » : lien collé, envoyé, VPN coupé, bouton Partager (raccourci iOS)
  await tel.ctx.grantPermissions(["clipboard-read", "clipboard-write"], { origin: banc.base });
  const cacherToast = () => page.waitForFunction(() => !document.querySelector("#toast").classList.contains("visible"), null, { timeout: 8000 });
  const envoyerLien = async (lien) => {
    await page.fill("#champ-lien", lien);
    await page.click("#btn-envoyer");
    await page.locator("#toast.visible", { hasText: "Envoyé" }).waitFor();
  };
  await page.click("#onglet-envoyer");
  await page.locator("#etat-vpn:not([hidden])").waitFor();
  await envoyerLien("http://nas.local/films/conference-keynote.mp4");
  await envoyerLien("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
  await cacherToast();
  await page.fill("#champ-lien", "https://www.arte.tv/fr/videos/RC-014035/les-grands-documentaires/");
  await page.evaluate(() => { const c = document.querySelector("#champ-lien"); c.setSelectionRange(0, 0); c.blur(); c.scrollLeft = 0; });
  await capture("envoyer");
  await page.click("#btn-envoyer");
  await page.locator("#toast.visible", { hasText: "Envoyé" }).waitFor();
  await capture("envoyer-envoye");
  await cacherToast();
  await salon.liens({ vpn: false });
  await page.locator('#etat-vpn[data-etat="coupe"]').waitFor();
  await page.click('.recent:has-text("Les grands documentaires") .btn-ligne');
  await page.locator("#feuille-vpn:not([hidden])").waitFor();
  await capture("envoyer-vpn-coupe");
  await page.click("#btn-vpn-annuler");
  await salon.liens({ vpn: true });
  await page.click("#btn-raccourci");
  await page.locator("#guide-activer:not([hidden])").waitFor();
  await capture("envoyer-bouton-partager-desactive");
  await page.click("#btn-activer");
  await page.locator("#guide-adresse:not([hidden])").waitFor();
  await page.click("#btn-copier");
  await cacherToast();
  await salon.liens({ ip: "192.168.1.57" });
  await page.locator("#guide-bandeau:not([hidden])").waitFor();
  await capture("envoyer-bouton-partager");
  await page.locator(".adresse").evaluate((e) => e.scrollIntoView({ block: "center" }));
  await capture("envoyer-bouton-partager-adresse");
  await page.click("#btn-guide-retour");
  await capture("envoyer-adresse-changee");
  await page.click("#onglet-reglages");
  await page.locator('.reglage[data-cle="volume"]').waitFor();
  await page.waitForFunction(() => !document.querySelector("#toast").classList.contains("visible"));
  await capture("reglages");
  // Mise à jour en cours : progression et rappel « touche OK » (téléchargement arrêté à 42 %)
  await salon.eval(() => { window.tv.majArret = 0.42; });
  await page.click('.reglage[data-cle="maj"] .btn-reglage');
  const installer = page.locator('.reglage[data-cle="maj"] .btn-reglage', { hasText: "Installer" });
  await installer.waitFor();
  await installer.click();
  await page.locator('.reglage[data-cle="maj"] .reglage-detail', { hasText: "42 %" }).waitFor();
  await page.locator('.reglage[data-cle="maj"]').evaluate((e) => e.scrollIntoView({ block: "center" }));
  await capture("reglages-mise-a-jour");
  await page.click("#onglet-telecommande");

  // Seconde TV, puis liste des TV
  await page.click("#btn-tvs");
  await page.click("#btn-ajouter-tv");
  await appairerParQR(page, chambre, "Téléphone de Marie");
  await page.click("#btn-tvs");
  await page.locator("#feuille-tvs").waitFor();
  await capture("mes-tv");
  await page.click('.tv-choix:has-text("Salon")');
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });

  // Box injoignable : la TV « Salon » s'éteint
  await salon.fermer();
  await page.locator('#voyant[data-etat="injoignable"]').waitFor({ timeout: 20000 });
  await capture("box-injoignable");

  // Retour sur « Chambre », puis révocation
  await page.click("#btn-tvs");
  await page.click('.tv-choix:has-text("Chambre")');
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
  const appareil = await chambre.eval(() => [...window.tv.appareils.keys()][0]);
  await chambre.eval((id) => window.tv.revoquer(id), appareil);
  await page.locator("#alerte-titre", { hasText: "Accès retiré" }).waitFor();
  await capture("revoquee");
  await page.click("#alerte-ok");

  // Appairage refusé
  const tv3 = await banc.tv({ nom: "Bureau" });
  ({ fragment: f3 } = await tv3.demarrerAppairage());
  await page.goto(banc.urlPWA + f3);
  await page.fill("#champ-nom", "Téléphone de Marie");
  await page.click("#form-nom button[type=submit]");
  await tv3.attendreDemande();
  await tv3.confirmer(false);
  await page.locator("#resultat-titre", { hasText: "refusé" }).waitFor();
  await capture("appairage-refuse");

  // Version incompatible
  const ancienne = await banc.tv({ nom: "Vieille TV", vmax: 0 });
  const { fragment: f4 } = await ancienne.demarrerAppairage();
  await page.goto(banc.urlPWA + f4);
  await page.fill("#champ-nom", "Téléphone de Marie");
  await page.click("#form-nom button[type=submit]");
  await page.locator("#alerte-titre", { hasText: "Mettez à jour" }).waitFor({ timeout: 15000 });
  await capture("version-incompatible");
  await page.click("#alerte-ok");

  // Bannière de mise à jour
  await page.goto(banc.urlPWA);
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null, null, { timeout: 15000 }).catch(async () => {
    await page.reload();
    await page.waitForFunction(() => navigator.serviceWorker.controller !== null);
  });
  banc.web.etat.versionApp = `maj-${modele}`;
  await page.evaluate(async () => (await navigator.serviceWorker.getRegistration()).update());
  await page.locator("#banniere-maj").waitFor({ timeout: 15000 });
  await capture("banniere-mise-a-jour");
  banc.web.etat.versionApp = "1.0.0-test";

  await tel.fermer();
  for (const t of [chambre, tv3, ancienne]) await t.fermer();
}
let f3;

try {
  await serie("iphone");
  await serie("android");
} finally {
  await banc.fermer();
}
