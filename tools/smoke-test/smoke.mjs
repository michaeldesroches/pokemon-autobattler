// Usage : node smoke.mjs <url> <dossier-captures>
// Échoue si la page lève une exception JavaScript ou si le plateau reste vide.
// Les coordonnées reprennent la mise en page de GameView (1280×720, origine en haut à gauche).
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const url = process.argv[2] ?? 'http://localhost:8000/';
const outDir = process.argv[3] ?? 'screenshots';
mkdirSync(outDir, { recursive: true });

// ---- géométrie du plateau en perspective (cf. GameView)
const HW = 84, SQUASH = 0.56, BOARD_CX = 560, BOARD_Y0 = 150;
const persp = (r) => 0.78 + 0.32 * r / 7;
const rowY = [BOARD_Y0];
for (let r = 1; r < 8; r++) rowY.push(rowY[r - 1] + HW * 1.1547 * 0.75 * SQUASH * (persp(r - 1) + persp(r)) / 2);
const cell = (col, row) => [BOARD_CX + (col + (row % 2 ? 0.5 : 0) - 3.25) * HW * persp(row), rowY[row]];
const shopCard = (i) => [213 + i * 140 + 67, 568 + 40];
const benchSlot = (i) => [211 + i * 88 + 41, 480 + 38];
const invSlot = (i) => [12 + (i % 5) * 36 + 16, 480 + Math.floor(i / 5) * 38 + 17];
const BTN_XP = [1185, 590], BTN_COMBAT = [1098, 687];

const browser = await chromium.launch({
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'],
});
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => {
  console.log(`[console.${m.type()}] ${m.text()}`);
  if (m.type() === 'error' && !/favicon|Failed to load resource|AudioContext|play\(\)/i.test(m.text())) errors.push(m.text());
});

await page.goto(url);
await page.waitForSelector('canvas', { timeout: 60000 });
await page.waitForTimeout(12000); // préchargement des assets + démarrage
await page.screenshot({ path: `${outDir}/1-demarrage.png` });

// Achète les 3 premières cartes de la boutique
for (let i = 0; i < 3; i++) {
  await page.mouse.click(...shopCard(i));
  await page.waitForTimeout(300);
}
// Glisse les 3 premiers Pokémon du banc sur la première ligne du camp (ligne 4, colonnes 2 à 4)
for (let i = 0; i < 3; i++) {
  await page.mouse.move(...benchSlot(i));
  await page.mouse.down();
  await page.mouse.move(...cell(2 + i, 4), { steps: 10 });
  await page.mouse.up();
  await page.waitForTimeout(300);
}
// Survol de l'objet de départ, puis on le glisse sur le premier Pokémon placé
await page.mouse.move(...invSlot(0));
await page.waitForTimeout(300);
await page.screenshot({ path: `${outDir}/2a-objet.png` });
await page.mouse.down();
await page.mouse.move(...cell(2, 4), { steps: 10 });
await page.mouse.up();
await page.waitForTimeout(300);
await page.mouse.click(...BTN_XP);
await page.waitForTimeout(300);
await page.mouse.move(...cell(2, 4));
await page.waitForTimeout(300);
await page.screenshot({ path: `${outDir}/2-equipe.png` });

await page.mouse.click(...BTN_COMBAT);
await page.waitForTimeout(2500);
await page.screenshot({ path: `${outDir}/3-combat.png` });
await page.waitForTimeout(4000);
await page.screenshot({ path: `${outDir}/3b-combat.png` });
await page.waitForTimeout(12000);
await page.screenshot({ path: `${outDir}/4-resultat.png` });

const shot = await page.screenshot({ clip: { x: 204, y: 50, width: 712, height: 412 } });
await browser.close();

if (shot.length < 8000) errors.push(`Capture du plateau suspecte (${shot.length} octets) : rien ne semble dessiné.`);
if (errors.length) {
  console.error('Échec du test de fumée :\n- ' + errors.join('\n- '));
  process.exit(1);
}
console.log('Test de fumée OK');
