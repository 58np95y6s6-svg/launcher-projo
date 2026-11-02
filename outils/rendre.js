// Rendu de l'aperçu « Galerie » : écran 1920×1080, puis mise en scène en perspective.
// Usage : node outils/rendre.js (depuis la racine) → sorties/galerie-ecran.jpg, sorties/galerie-scene.jpg
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

(async () => {
  const racine = path.resolve(__dirname, '..');
  const apercu = path.join(racine, 'apercu');
  const sorties = path.join(racine, 'sorties');
  fs.mkdirSync(sorties, { recursive: true });
  const b = await chromium.launch();

  const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  await p.goto('file://' + path.join(apercu, 'index.html'));
  await p.waitForFunction(() => window.pret === true);
  await p.evaluate(async () => {
    await document.fonts.ready;
    // attendre les images de fond (héros, tuiles)
    const urls = new Set();
    document.querySelectorAll('*').forEach(el => {
      const m = getComputedStyle(el).backgroundImage.match(/url\("(.+?)"\)/);
      if (m) urls.add(m[1]);
    });
    await Promise.all([...urls].map(u => new Promise(ok => { const i = new Image(); i.onload = i.onerror = ok; i.src = u; })));
  });
  await p.waitForTimeout(800);
  const ecran = path.join(sorties, 'galerie-ecran.jpg');
  await p.screenshot({ path: ecran, type: 'jpeg', quality: 90 });

  fs.copyFileSync(ecran, path.join(apercu, 'ecran.jpg'));
  const s = await b.newPage({ viewport: { width: 1600, height: 1200 } });
  await s.goto('file://' + path.join(apercu, 'scene.html'));
  await s.waitForFunction(() => window.pret === true);
  await s.waitForTimeout(400);
  await s.screenshot({ path: path.join(sorties, 'galerie-scene.jpg'), type: 'jpeg', quality: 90 });
  await b.close();
  console.log('Aperçus rendus');
})();
