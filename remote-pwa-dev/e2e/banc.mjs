// Banc de test commun (tests de bout en bout et captures) : faux PeerJS, serveur statique,
// Chromium, fausse TV et téléphone.

import { chromium } from "playwright";
import { demarrerFauxPeerJS } from "./faux-peerjs.mjs";
import { demarrerServeurStatique } from "./serveur-statique.mjs";

export const TELEPHONES = {
  iphone: {
    viewport: { width: 390, height: 844 }, deviceScaleFactor: 3, isMobile: true, hasTouch: true,
    userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
  },
  android: {
    viewport: { width: 412, height: 915 }, deviceScaleFactor: 2.625, isMobile: true, hasTouch: true,
    userAgent: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
  },
};

export async function demarrerBanc() {
  const peerjs = await demarrerFauxPeerJS({ expirationMs: 2500 });
  const web = await demarrerServeurStatique({ portPeerJS: peerjs.port });
  const navigateur = await chromium.launch({
    args: [
      // Candidats ICE en adresse IP (pas de .local mDNS, non résolu dans un conteneur)
      "--disable-features=WebRtcHideLocalIpsWithMdns",
    ],
  });
  const base = `http://127.0.0.1:${web.port}`;
  const banc = {
    peerjs, web, navigateur, base,
    urlPWA: `${base}/launcher-projo/`,

    async tv({ nom = "Salon", vmax = 1, vhello = 1 } = {}) {
      const ctx = await navigateur.newContext();
      const page = await ctx.newPage();
      await page.goto(`${base}/tv/fausse-tv.html?ws=${peerjs.port}&nom=${encodeURIComponent(nom)}&vmax=${vmax}&vhello=${vhello}`);
      await page.waitForFunction(() => window.tv && window.tv.pret);
      const tv = {
        page, ctx,
        id: await page.evaluate(() => window.tv.id),
        eval: (fn, arg) => page.evaluate(fn, arg),
        demarrerAppairage: () => page.evaluate(() => window.tv.demarrerAppairage()),
        attendreDemande: async () => {
          await page.waitForFunction(() => window.tv.demande !== null, null, { timeout: 15000 });
          return page.evaluate(() => ({ nom: window.tv.demande.nom, appareil: window.tv.demande.appareil }));
        },
        confirmer: (ok = true) => page.evaluate((o) => window.tv.confirmer(o), ok),
        envoyer: (msg) => page.evaluate((m) => window.tv.envoyer(m), msg),
        recus: () => page.evaluate(() => window.tv.recus),
        viderRecus: () => page.evaluate(() => { window.tv.recus.length = 0; }),
        attendreRecu: (pred, timeout = 8000) => page.waitForFunction(
          (src) => window.tv.recus.some(new Function("r", `return (${src})(r)`)), pred.toString(), { timeout }),
        fermer: () => ctx.close(),
      };
      return tv;
    },

    /** Contexte « téléphone » : le stockage (IndexedDB) survit tant que le contexte vit. */
    async telephone(modele = "iphone") {
      const ctx = await navigateur.newContext({ ...TELEPHONES[modele], locale: "fr-FR" });
      const journal = [];
      const ouvrir = async (suffixe = "") => {
        const page = await ctx.newPage();
        page.on("console", (m) => journal.push(m.text()));
        page.on("pageerror", (e) => journal.push("ERREUR PAGE: " + e.message));
        await page.goto(banc.urlPWA + suffixe);
        return page;
      };
      return { ctx, journal, ouvrir, fermer: () => ctx.close() };
    },

    async fermer() {
      await navigateur.close();
      await web.fermer();
      await peerjs.fermer();
    },
  };
  return banc;
}

/** Appairage complet par QR code depuis une page téléphone. */
export async function appairerParQR(page, tv, nom = "Téléphone de Marie") {
  const { fragment } = await tv.demarrerAppairage();
  await page.goto(page.url().split("#")[0] + fragment);
  await page.locator("#ecran-appairage").waitFor();
  await page.fill("#champ-nom", nom);
  await page.click("#form-nom button[type=submit]");
  await tv.attendreDemande();
  await tv.confirmer(true);
  await page.locator("#resultat-titre", { hasText: "Appairée" }).waitFor();
  await page.click("#btn-resultat");
  await page.locator('#voyant[data-etat="connecte"]').waitFor({ timeout: 15000 });
}

/** Clic sur un secteur du pavé directionnel (le centre de chaque secteur est le bouton OK). */
export async function appuyerDirection(page, k) {
  const boite = await page.locator("#dpad").boundingBox();
  const m = 22;
  const pos = {
    up: { x: boite.width / 2, y: m }, down: { x: boite.width / 2, y: boite.height - m },
    left: { x: m, y: boite.height / 2 }, right: { x: boite.width - m, y: boite.height / 2 },
  }[k];
  await page.mouse.click(boite.x + pos.x, boite.y + pos.y);
}
