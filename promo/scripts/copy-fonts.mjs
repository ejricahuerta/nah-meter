// Copies Geist from the app (app/src/main/res/font) into public/fonts, so the fonts aren't
// duplicated in the repo. public/fonts is gitignored.
import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const from = join(root, "..", "app", "src", "main", "res", "font");
const to = join(root, "public", "fonts");
mkdirSync(to, { recursive: true });
for (const w of [400, 500, 600, 700, 800, 900]) copyFileSync(join(from, `geist_${w}.ttf`), join(to, `geist_${w}.ttf`));
console.log(`Geist copied to ${to}`);
