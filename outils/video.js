// Vidéo du carrousel « Galerie » : ▶ ▶ ▶ puis ◀ ◀, à 30 images/s.
// Les transitions CSS sont figées puis avancées image par image (Web Animations), pour une vidéo
// fluide même sur une machine lente. Usage : node outils/video.js → sorties/galerie-animation.mp4
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const FPS = 30;
const LARGEUR = 1280, HAUTEUR = 720;

(async () => {
  const racine = path.resolve(__dirname, '..');
  const sorties = path.join(racine, 'sorties');
  const images = path.join(sorties, 'images-video');
  fs.rmSync(images, { recursive: true, force: true });
  fs.mkdirSync(images, { recursive: true });

  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: LARGEUR, height: HAUTEUR } });
  await p.goto('file://' + path.join(racine, 'apercu', 'index.html') + '?focus=suivant');
  await p.waitForFunction(() => window.pret === true);
  await p.evaluate(async () => {
    await document.fonts.ready;
    const urls = new Set();
    document.querySelectorAll('*').forEach(el => {
      const m = getComputedStyle(el).backgroundImage.match(/url\("(.+?)"\)/);
      if (m) urls.add(m[1]);
    });
    await Promise.all([...urls].map(u => new Promise(ok => { const i = new Image(); i.onload = i.onerror = ok; i.src = u; })));
  });
  await p.waitForTimeout(800);

  let n = 0;
  let derniere = null;
  const cliche = async () => {
    const f = path.join(images, `f${String(n++).padStart(5, '0')}.jpg`);
    await p.screenshot({ path: f, type: 'jpeg', quality: 90 });
    derniere = f;
  };
  // Image fixe : on recopie la dernière image
  const tenir = async secondes => {
    if (!derniere) await cliche();
    for (let k = 0; k < Math.round(secondes * FPS); k++) {
      fs.copyFileSync(derniere, path.join(images, `f${String(n++).padStart(5, '0')}.jpg`));
    }
  };
  // Un mouvement : déclenche, fige les transitions, puis les avance image par image
  const mouvement = async (code, secondes) => {
    await p.evaluate(code);
    await p.evaluate(() => document.getAnimations().forEach(a => a.pause()));
    const total = Math.round(secondes * FPS);
    for (let k = 1; k <= total; k++) {
      await p.evaluate(t => document.getAnimations().forEach(a => { a.currentTime = t; }), (k * 1000) / FPS);
      await cliche();
    }
    await p.evaluate(() => document.getAnimations().forEach(a => a.finish()));
  };

  await cliche();
  await tenir(1.2);
  for (let k = 0; k < 3; k++) {
    await mouvement('suivant()', 1.1);
    await tenir(1.4);
  }
  await p.evaluate(() => focusSur('precedent'));
  await cliche();
  await tenir(0.5);
  for (let k = 0; k < 2; k++) {
    await mouvement('precedent()', 1.1);
    await tenir(1.4);
  }
  await b.close();

  const mp4 = path.join(sorties, 'galerie-animation.mp4');
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-framerate', String(FPS), '-i', path.join(images, 'f%05d.jpg'),
    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '21', '-preset', 'slow', '-movflags', '+faststart', mp4]);
  fs.rmSync(images, { recursive: true, force: true });
  console.log('Vidéo :', path.basename(mp4), n, 'images');
})();
