// Render out/story-NN.png (1080×1920) and out/post-NN.png (1080×1350) from data/offers.out.json.
// Run scripts/build.py first. Usage: node render.mjs [ids…]
import { createRequire } from "node:module";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

// Playwright and its Chromium come preinstalled (global); a local install would look for another browser.
const { chromium } = createRequire(import.meta.url)("playwright");
const here = path.dirname(fileURLToPath(import.meta.url));
const data = JSON.parse(fs.readFileSync(path.join(here, "data/offers.out.json"), "utf8"));
const only = process.argv.slice(2);
const out = path.join(here, "out");
fs.mkdirSync(out, { recursive: true });

const browser = await chromium.launch();
for (const [fmt, [W, H]] of Object.entries(data.formats)) {
  const ctx = await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 1080 / W });
  const page = await ctx.newPage();
  page.on("pageerror", (e) => { console.error(e); process.exitCode = 1; });
  await page.goto(pathToFileURL(path.join(here, "template.html")).href);
  for (const o of data.offers) {
    if (only.length && !only.includes(o.id)) continue;
    await page.evaluate(([o, fmt, formats]) => window.draw(o, fmt, formats), [o, fmt, data.formats]);
    await page.evaluate(() => document.fonts.ready);
    const file = path.join(out, `${fmt}-${o.id}.png`);
    await page.locator("#screen").screenshot({ path: file });
    console.log(file);
  }
  await ctx.close();
}
await browser.close();
