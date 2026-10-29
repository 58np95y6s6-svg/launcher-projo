// Photographie chaque page HTML (un SVG) en PNG transparent, à la taille exacte en pixels.
const { chromium } = require('playwright');
const fs = require('fs'); const path = require('path');
(async () => {
  const liste = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
  const b = await chromium.launch();
  for (const e of liste) {
    const p = await b.newPage({ viewport: { width: e.l, height: e.h }, deviceScaleFactor: 1 });
    await p.goto('file://' + e.page);
    fs.mkdirSync(path.dirname(e.png), { recursive: true });
    await p.screenshot({ path: e.png, omitBackground: true, clip: { x: 0, y: 0, width: e.l, height: e.h } });
    await p.close();
  }
  await b.close();
  console.log('PNG :', liste.length);
})();
