// Captures de chaque écran, en profil iPhone (390×844) et Android (412×915) :
// télécommande dans les 8 thèmes (un par design de la TV), feuille du clavier, onglets, alertes,
// box score NBA (bandeau, formats, choix du match, erreurs, petit écran)…
// Usage : node remote-pwa-dev/outils/captures.mjs [dossier de sortie] [iphone,android]

import { mkdir } from "node:fs/promises";
import path from "node:path";
import { demarrerBanc, appairerParQR } from "../e2e/banc.mjs";

const SORTIE = path.resolve(process.argv[2] || "captures-pwa");
await mkdir(SORTIE, { recursive: true });
const pause = (ms) => new Promise((r) => setTimeout(r, ms));
const DESIGNS = ["galerie", "verre", "pastel", "console", "une", "chaines", "studio", "nba"];
const banc = await demarrerBanc();

async function serie(modele) {
  const salon = await banc.tv({ nom: "Salon", design: "galerie", fonctions: ["apps", "reglages", "liens", "nba"] });
  const chambre = await banc.tv({ nom: "Chambre", design: "une" });
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
  const theme = (t) => page.waitForFunction((x) => document.documentElement.dataset.theme === x, t);
  const cacherToast = () => page.waitForFunction(() => !document.querySelector("#toast").classList.contains("visible"), null, { timeout: 8000 });

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
  await theme("galerie");
  await capture("telecommande");

  // La TV attend une recherche : la feuille du clavier s'ouvre seule
  await salon.envoyer({ t: "focus_text", type: "recherche", s: "", action: "search" });
  await page.locator("#saisie.actif").waitFor();
  await pause(600);
  await page.click("#champ-texte");
  await page.keyboard.type("dune");
  await capture("clavier-recherche");
  await salon.envoyer({ t: "focus_text", type: "motdepasse", s: "", action: "go" });
  await page.locator("#btn-afficher:not([hidden])").waitFor();
  await pause(600);
  await capture("clavier-mot-de-passe-invite");
  await page.click("#champ-texte");
  await page.keyboard.type("Tr3s-S3cret!");
  await page.click("#btn-afficher");
  await capture("clavier-mot-de-passe-affiche");
  await salon.envoyer({ t: "focus_lost" });
  await page.locator("#feuille-clavier").waitFor({ state: "hidden" });
  // « Aa » : texte libre
  await page.click("#btn-clavier");
  await page.locator("#feuille-clavier:not([hidden])").waitFor();
  await capture("clavier-libre");
  await page.click("#btn-fermer-clavier");

  // Onglet « Applis »
  await page.click("#onglet-applis");
  await page.waitForFunction(() => document.querySelectorAll(".tuile img").length >= 12);
  await capture("applis");
  await page.click('.tuile[data-id="com.amazon.amazonvideo.livingroom"]');
  await page.locator("#toast.visible").waitFor();
  await capture("applis-ouverture");

  // Onglet « Envoyer » : lien collé, envoyé, VPN coupé, bouton Partager (raccourci iOS)
  await tel.ctx.grantPermissions(["clipboard-read", "clipboard-write"], { origin: banc.base });
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
  // Modifier les récents : « Retirer » sur chaque lien, « Tout effacer » sous la liste
  await page.click("#btn-recents-modifier");
  await page.locator("#btn-recents-effacer").evaluate((e) => e.scrollIntoView({ block: "center" }));
  await capture("envoyer-modifier");
  await page.click("#btn-recents-modifier");
  await page.locator("#envoyer-defile").evaluate((e) => { e.scrollTop = 0; });
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

  // Onglet « Réglages »
  await page.click("#onglet-reglages");
  await page.locator('.reglage[data-cle="volume"]').waitFor();
  await cacherToast();
  await capture("reglages");
  await page.locator('.reglage[data-cle="design"]').evaluate((e) => e.scrollIntoView({ block: "start" }));
  await capture("reglages-design");
  await page.locator('.reglage[data-cle="nba_decalage"]').evaluate((e) => e.scrollIntoView({ block: "center" }));
  await capture("reglages-box-score-nba");
  // Mise à jour en cours : progression et rappel « touche OK » (téléchargement arrêté à 42 %)
  await salon.eval(() => { window.tv.majArret = 0.42; });
  await page.click('.reglage[data-cle="maj"] .btn-reglage');
  const installer = page.locator('.reglage[data-cle="maj"] .btn-reglage', { hasText: "Installer" });
  await installer.waitFor();
  await installer.click();
  await page.locator('.reglage[data-cle="maj"] .reglage-detail', { hasText: "42 %" }).waitFor();
  await page.locator('.reglage[data-cle="maj"]').evaluate((e) => e.scrollIntoView({ block: "center" }));
  await capture("reglages-mise-a-jour");

  // Thème qui suit la TV : le design choisi dans Réglages, puis les 8 designs sur la télécommande
  await page.locator("#reglages-defile").evaluate((e) => { e.scrollTop = 0; });
  await salon.design("une");
  await theme("une");
  await capture("reglages-theme-une");
  await page.click("#onglet-applis");
  await salon.design("console");
  await theme("console");
  await capture("applis-theme-console");
  await page.click("#onglet-envoyer");
  await salon.design("pastel");
  await theme("pastel");
  await capture("envoyer-theme-pastel");
  // NBA : les quatre onglets et le choix du design
  await salon.design("nba");
  await theme("nba");
  await capture("envoyer-theme-nba");
  await page.click("#onglet-applis");
  await capture("applis-theme-nba");
  await page.click("#onglet-reglages");
  await page.locator("#reglages-defile").evaluate((e) => { e.scrollTop = 0; });
  await capture("reglages-theme-nba");
  await page.locator('.reglage[data-cle="design"]').evaluate((e) => e.scrollIntoView({ block: "start" }));
  await capture("reglages-design-nba");
  await page.locator("#reglages-defile").evaluate((e) => { e.scrollTop = 0; });
  await page.click("#onglet-telecommande");
  for (const d of DESIGNS) {
    await salon.design(d);
    await theme(d);
    await capture(`telecommande-${d}`);
  }
  await salon.design("studio");
  await theme("studio");
  await salon.envoyer({ t: "focus_text", type: "recherche", s: "", action: "search" });
  await page.locator("#saisie.actif").waitFor();
  await pause(600);
  await capture("clavier-studio");
  await salon.envoyer({ t: "focus_lost" });
  await salon.design("verre");
  await theme("verre");
  await salon.envoyer({ t: "focus_text", type: "texte", s: "Salut", action: "done" });
  await page.locator("#saisie.actif").waitFor();
  await pause(600);
  await capture("clavier-verre");
  await salon.envoyer({ t: "focus_lost" });
  await salon.design("nba");
  await theme("nba");
  await salon.envoyer({ t: "focus_text", type: "recherche", s: "", action: "search" });
  await page.locator("#saisie.actif").waitFor();
  await pause(600);
  await capture("clavier-nba");
  await salon.envoyer({ t: "focus_lost" });
  await salon.design("galerie");
  await theme("galerie");

  // Box score NBA : bandeau en haut de la télécommande (deux matchs en cours)
  const nbaAffichage = async (nom, valeur) => {
    await page.click(`.segment:has-text("${nom}")`);
    await salon.page.waitForFunction((v) => window.tv.nba.affichage === v, valeur);
  };
  await page.locator("#feuille-clavier").waitFor({ state: "hidden" });
  await salon.nba({ scenario: "deux" });
  await page.locator("#bandeau-nba:not([hidden])").waitFor();
  await capture("nba-cache");
  await nbaAffichage("Résumé", "resume");
  await capture("nba-resume");
  await nbaAffichage("Détails", "details");
  await capture("nba-details");
  await page.click("#btn-nba-matchs");
  await page.locator("#feuille-nba:not([hidden])").waitFor();
  await page.evaluate(() => document.activeElement?.blur());
  await capture("nba-changer-de-match");
  await page.click('.match-choix[data-id="GSW-DEN"]');
  await page.locator("#nba-badge-ext", { hasText: "GSW" }).waitFor();
  await capture("nba-autre-match");
  await salon.nba({ erreur: "accessibilite" });
  await page.locator("#nba-erreur:not([hidden])").waitFor();
  await capture("nba-erreur-accessibilite");
  await salon.nba({ erreur: "source" });
  await page.locator("#nba-erreur-texte", { hasText: "indisponibles" }).waitFor();
  await capture("nba-erreur-source");
  await salon.nba({ erreur: null, scenario: "termine", affichage: "resume" });
  await page.locator("#nba-direct.termine").waitFor();
  await capture("nba-termine");
  await salon.nba({ scenario: "un", affichage: "cache" });
  await page.locator("#btn-nba-matchs").waitFor({ state: "hidden" });
  await capture("nba-un-match");
  // Dans chaque thème (deux matchs, résumé affiché), puis la feuille en Verre
  await salon.nba({ scenario: "deux", affichage: "resume" });
  await page.locator("#btn-nba-matchs:not([hidden])").waitFor();
  for (const d of DESIGNS) {
    await salon.design(d);
    await theme(d);
    await capture(`nba-${d}`);
  }
  await salon.design("verre");
  await theme("verre");
  await page.click("#btn-nba-matchs");
  await page.locator("#feuille-nba:not([hidden])").waitFor();
  await page.evaluate(() => document.activeElement?.blur());
  await capture("nba-changer-de-match-verre");
  await page.click("#btn-nba-fermer");
  await salon.nba({ erreur: "accessibilite" });
  await page.locator("#nba-erreur:not([hidden])").waitFor();
  await capture("nba-erreur-verre");
  await salon.design("nba");
  await theme("nba");
  await capture("nba-erreur-nba");
  await salon.nba({ erreur: null });
  await page.locator("#nba-erreur").waitFor({ state: "hidden" });
  await page.click("#btn-nba-matchs");
  await page.locator("#feuille-nba:not([hidden])").waitFor();
  await page.evaluate(() => document.activeElement?.blur());
  await capture("nba-changer-de-match-nba");
  await page.click("#btn-nba-fermer");
  await salon.nba({ scenario: "termine" });
  await page.locator("#nba-direct.termine").waitFor();
  await capture("nba-termine-nba");
  await salon.nba({ scenario: "deux" });
  await salon.nba({ erreur: null });
  await salon.design("galerie");
  await theme("galerie");
  // Petit écran (iPhone SE, 375 × 667) : le pavé rétrécit, les touches restent à l'écran
  const taille = page.viewportSize();
  await page.setViewportSize({ width: 375, height: 667 });
  await capture("nba-petit-ecran");
  await page.setViewportSize(taille);

  // Texte agrandi (Dynamic Type, ici simulé) : la télécommande et Réglages restent utilisables
  await page.evaluate(() => { document.documentElement.style.fontSize = "23px"; });
  await capture("texte-agrandi-telecommande");
  await page.click("#onglet-reglages");
  await page.locator('.reglage[data-cle="volume"]').waitFor();
  await capture("texte-agrandi-reglages");
  await page.evaluate(() => { document.documentElement.style.fontSize = ""; });
  await page.click("#onglet-telecommande");

  // Seconde TV (design « À la une »), puis liste des TV
  await page.click("#btn-tvs");
  await page.click("#btn-ajouter-tv");
  await appairerParQR(page, chambre, "Téléphone de Marie");
  await theme("une");
  await page.click("#btn-tvs");
  await page.locator("#feuille-tvs").waitFor();
  await capture("mes-tv");
  await page.click('.tv-choix:has-text("Salon")');
  await theme("galerie");
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
  const { fragment: f3 } = await tv3.demarrerAppairage();
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

  // Téléphone en mode sombre, sans TV : thème par défaut sombre (Verre)
  const sombre = await banc.telephone(modele, { sombre: true });
  page = await sombre.ouvrir();
  await page.locator("#ecran-accueil").waitFor();
  await theme("verre");
  await capture("accueil-mode-sombre");
  await sombre.fermer();
}

try {
  for (const modele of (process.argv[3] || "iphone,android").split(",")) await serie(modele);
} finally {
  await banc.fermer();
}
