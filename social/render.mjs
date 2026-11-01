// Render the images from data/offers.out.json (run scripts/build.py first).
//   node render.mjs [ids…]            out/story-NN.png (1080×1920) and out/post-NN.png (1080×1350)
//   node render.mjs --layers [--scale=2] [ids…]  layers/NN-screen.png, NN-label.png, NN-verdict.png, NN.json:
//                                     a phone screen without its verdict card or timer fill, the
//                                     button's word alone, the verdict card alone, and where things
//                                     are, for the showcase video (promo/src/Showcase.tsx)
import { createRequire } from "node:module";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

// Playwright and its Chromium come preinstalled (global); a local install would look for another browser.
const { chromium } = createRequire(import.meta.url)("playwright");
const here = path.dirname(fileURLToPath(import.meta.url));
const data = JSON.parse(fs.readFileSync(path.join(here, "data/offers.out.json"), "utf8"));
const args = process.argv.slice(2);
const layers = args.includes("--layers");
// --scale=2 renders the pixels twice as dense (2160 px wide, for a 4K video); positions in
// NN.json stay in 1080-wide units either way.
const density = Number(args.find((a) => a.startsWith("--scale="))?.split("=")[1] ?? 1);
const only = args.filter((a) => !a.startsWith("--"));
const out = path.join(here, layers ? "layers" : "out");
fs.mkdirSync(out, { recursive: true });

const browser = await chromium.launch();
const formats = layers ? ["phone"] : ["story", "post"];
for (const fmt of formats) {
  const [W, H] = data.formats[fmt];
  const ctx = await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: (1080 / W) * density });
  const page = await ctx.newPage();
  page.on("pageerror", (e) => { console.error(e); process.exitCode = 1; });
  await page.goto(pathToFileURL(path.join(here, "template.html")).href);
  const draw = (o, layer) => page.evaluate(([o, fmt, formats, layer]) => window.draw(o, fmt, formats, layer), [o, fmt, data.formats, layer]);
  const shoot = async (file, clear = false) => {
    await page.evaluate(() => document.fonts.ready);
    await page.locator("#screen").screenshot({ path: file, omitBackground: clear });
    console.log(file);
  };
  for (const o of data.offers) {
    if (only.length && !only.includes(o.id)) continue;
    if (!layers) {
      await draw(o);
      await shoot(path.join(out, `${fmt}-${o.id}.png`));
      continue;
    }
    const info = await draw(o, "screen");
    await shoot(path.join(out, `${o.id}-screen.png`));
    await draw(o, "label");
    await shoot(path.join(out, `${o.id}-label.png`), true);
    await draw(o, "verdict");
    await shoot(path.join(out, `${o.id}-verdict.png`), true);
    const scale = 1080 / W;
    const px = (r) => r.map((v) => Math.round(v * scale));
    fs.writeFileSync(
      path.join(out, `${o.id}.json`),
      JSON.stringify({ id: o.id, width: 1080, height: Math.round(H * scale), verdict: px(info.verdict), button: px(info.button), exclusive: info.exclusive, timer: info.timer, word: o.word }, null, 2),
    );
  }
  await ctx.close();
}
await browser.close();
