// Usage : node smoke.mjs <url> <dossier-captures>
// Échoue si la page lève une exception JavaScript ou si le canvas reste vide.
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const url = process.argv[2] ?? 'http://localhost:8000/';
const outDir = process.argv[3] ?? 'screenshots';
mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch({
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
});
const page = await browser.newPage({ viewport: { width: 1000, height: 690 } });
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => {
  console.log(`[console.${m.type()}] ${m.text()}`);
  if (m.type() === 'error' && !/favicon|Failed to load resource/i.test(m.text())) errors.push(m.text());
});

await page.goto(url);
await page.waitForSelector('canvas', { timeout: 60000 });
await page.waitForTimeout(12000); // préchargement des assets + démarrage
await page.screenshot({ path: `${outDir}/1-demarrage.png` });

// Achète les 3 premières cartes de la boutique (coordonnées du jeu : 1000x690, origine en haut à gauche)
for (let i = 0; i < 3; i++) {
  await page.mouse.click(75 + i * 124, 608);
  await page.waitForTimeout(300);
}
// Glisse les 3 premiers Pokémon du banc sur la première ligne du plateau hexagonal (colonnes 2 à 4)
const HEX_W = 28 * Math.sqrt(3);
for (let i = 0; i < 3; i++) {
  await page.mouse.move(57 + i * 88, 473);
  await page.mouse.down();
  await page.mouse.move(200 + HEX_W / 2 + HEX_W * (2 + i), 58 + 28 + 42 * 4, { steps: 10 });
  await page.mouse.up();
  await page.waitForTimeout(300);
}
// Survol de l'objet de départ (panneau d'info), puis on le glisse sur le premier Pokémon placé
await page.mouse.move(751, 451);
await page.waitForTimeout(300);
await page.screenshot({ path: `${outDir}/2a-objet.png` });
await page.mouse.down();
await page.mouse.move(200 + HEX_W / 2 + HEX_W * 2, 58 + 28 + 42 * 4, { steps: 10 });
await page.mouse.up();
await page.waitForTimeout(300);
await page.mouse.click(904, 560); // Acheter XP
await page.waitForTimeout(300);
await page.mouse.move(200 + HEX_W / 2 + HEX_W * 2, 58 + 28 + 42 * 4); // survol : panneau d'info du Pokémon équipé
await page.waitForTimeout(300);
await page.screenshot({ path: `${outDir}/2-equipe.png` });

await page.mouse.click(904, 633); // COMBAT !
await page.waitForTimeout(2500);
await page.screenshot({ path: `${outDir}/3-combat.png` });
await page.waitForTimeout(15000);
await page.screenshot({ path: `${outDir}/4-resultat.png` });

// Le canvas ne doit pas être uniforme (sinon rien ne s'affiche)
const shot = await page.screenshot({ clip: { x: 200, y: 58, width: 364, height: 350 } });
await browser.close();

if (shot.length < 8000) errors.push(`Capture du plateau suspecte (${shot.length} octets) : rien ne semble dessiné.`);
if (errors.length) {
  console.error('Échec du test de fumée :\n- ' + errors.join('\n- '));
  process.exit(1);
}
console.log('Test de fumée OK');
