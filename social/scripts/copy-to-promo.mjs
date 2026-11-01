// Hand the phone layers to the promo's showcase cut (promo/src/Showcase.tsx).
// Run after `node render.mjs --layers 05 01 03 06`. The images go to promo/public/showcase/
// (generated, like the promo's audio); where things sit goes to promo/src/showcase.json.
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const layers = path.join(here, "..", "layers");
const promo = path.join(here, "..", "..", "promo");
const pub = path.join(promo, "public", "showcase");
fs.mkdirSync(pub, { recursive: true });

const index = {};
for (const f of fs.readdirSync(layers).sort()) {
  if (f.endsWith(".png")) fs.copyFileSync(path.join(layers, f), path.join(pub, f));
  if (f.endsWith(".json")) {
    const info = JSON.parse(fs.readFileSync(path.join(layers, f), "utf8"));
    index[info.id] = info;
  }
}
fs.writeFileSync(path.join(promo, "src", "showcase.json"), JSON.stringify(index, null, 2) + "\n");
console.log(`${Object.keys(index).length} photos → ${path.relative(process.cwd(), pub)} and promo/src/showcase.json`);
