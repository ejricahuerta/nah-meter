// Builds public/audio/mix.wav: music, sound effects and the spoken verdicts.
//   - Music: audio/build/music.wav, played by scripts/make-music.mjs (run it first).
//   - Sound effects: recorded (Kenney, CC0) in audio/sfx/, shaped below.
//   - Speech: the owner's voiceover lines and verdict clips, prepared by scripts/make-timeline.mjs
//     (run it first), placed where src/timeline.json says, never overlapping or cut off.
// Times come from src/timeline.json, the same file the picture uses.
import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { CANT_READ, DEFAULT_RULES, OFFERS, score } from "../src/nah.ts";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const T = JSON.parse(readFileSync(join(root, "src", "timeline.json"), "utf8"));
const SR = 48000;
const N = Math.round(T.durationSec * SR);

// ---------- buses ----------

const buses = { music: [new Float32Array(N), new Float32Array(N)], sfx: [new Float32Array(N), new Float32Array(N)], vo: [new Float32Array(N), new Float32Array(N)], verb: [new Float32Array(N), new Float32Array(N)] };

/** Adds a mono signal to a bus at `at` seconds, panned (-1 left … 1 right), with a reverb send. */
const add = (bus, at, sig, gain = 1, pan = 0, send = 0) => {
  const start = Math.round(at * SR);
  const gl = Math.cos(((pan + 1) * Math.PI) / 4) * gain;
  const gr = Math.sin(((pan + 1) * Math.PI) / 4) * gain;
  const [L, R] = buses[bus];
  const [VL, VR] = buses.verb;
  for (let i = 0; i < sig.length; i++) {
    const j = start + i;
    if (j < 0 || j >= N) continue;
    L[j] += sig[i] * gl;
    R[j] += sig[i] * gr;
    if (send) {
      VL[j] += sig[i] * gl * send;
      VR[j] += sig[i] * gr * send;
    }
  }
};

// ---------- sound effects (recorded, CC0) ----------
// Real recordings from Kenney's CC0 packs (audio/sfx/, see CREDITS.md), shaped with ffmpeg:
// pitched down for weight, reversed for swells, and given a room so they sit in the mix.

const sampleCache = new Map();

/**
 * An audio/sfx/ file as 48 kHz mono. `rate` < 1 plays it slower and lower (tape-style), `reverse`
 * flips it, `room` adds an echo tail (0…1), `lp` is a low-pass in Hz.
 */
const sample = (name, { rate = 1, reverse = false, room = 0, lp = 0 } = {}) => {
  const key = JSON.stringify([name, rate, reverse, room, lp]);
  if (sampleCache.has(key)) return sampleCache.get(key);
  const af = [`asetrate=44100*${rate}`, "aresample=48000"];
  if (lp) af.push(`lowpass=f=${lp}`);
  if (room) af.push(`apad=pad_dur=${(0.6 * room + 0.2).toFixed(2)}`, `aecho=0.8:${(0.5 * room).toFixed(2)}:43|89|157|263:${[0.5, 0.4, 0.3, 0.2].map((g) => (g * room).toFixed(2)).join("|")}`);
  if (reverse) af.push("areverse");
  const raw = execFileSync(
    "ffmpeg",
    ["-hide_banner", "-loglevel", "error", "-i", join(root, "audio", "sfx", name), "-af", af.join(","), "-f", "f32le", "-ac", "1", "-ar", String(SR), "pipe:1"],
    { maxBuffer: 64 * 1024 * 1024 },
  );
  const v = new Float32Array(raw.buffer, raw.byteOffset, raw.byteLength / 4);
  const peak = v.reduce((m, x) => Math.max(m, Math.abs(x)), 0) || 1;
  const out = v.map((x) => x / peak);
  sampleCache.set(key, out);
  return out;
};

/** Several sounds layered: [signal, start (s), gain]. */
const layer = (...parts) => {
  const len = Math.max(...parts.map(([sig, at]) => Math.round(at * SR) + sig.length));
  const out = new Float32Array(len);
  for (const [sig, at, g] of parts) {
    const st = Math.round(at * SR);
    for (let i = 0; i < sig.length; i++) out[st + i] += sig[i] * g;
  }
  return out;
};

/** A swish: a real card slide or shove, slowed down into a whoosh. Longer `sec` is slower and deeper. */
const whoosh = (sec = 0.5) =>
  sample(sec > 0.5 ? "card-shove-1.ogg" : "card-slide-1.ogg", { rate: sec > 0.5 ? 0.55 : 0.7, room: 0.3 });

/** A swell into a hit: a slowed, reverberant shove played backwards. */
const riser = (sec) => {
  const s = sample("card-shove-2.ogg", { rate: 0.5, room: 1, reverse: true });
  return s.subarray(Math.max(0, s.length - Math.round(sec * SR)));
};

/** A clock tick (two alternating recordings), or a UI click when `click` is set. */
const tick = (alt = 0) => sample(alt % 2 ? "tick_002.ogg" : "tick_001.ogg");
const click = (alt = 0) => sample(alt % 2 ? "click_002.ogg" : "click_001.ogg");

/** A dull body hit, for slams. */
const thump = (heavy = false) => sample(heavy ? "impactPunch_heavy_000.ogg" : "impactPunch_medium_000.ogg", { rate: 0.85 });

/** A deep, cinematic boom. */
const boom = (long = false) => sample(long ? "lowFrequency_explosion_000.ogg" : "lowFrequency_explosion_001.ogg", { lp: 900 });

/** "nah.": a heavy punch and a low metal hit over a boom. */
const nahHit = (big = false) =>
  layer(
    [thump(true), 0, 1],
    [sample("impactMetal_heavy_000.ogg", { rate: 0.6, room: 0.5 }), 0.01, 0.55],
    [boom(big), 0, big ? 0.9 : 0.7],
    ...(big ? [[sample("impactBell_heavy_000.ogg", { rate: 0.5, room: 0.8, lp: 2500 }), 0.02, 0.35]] : []),
  );

/** "bet!": a stack of chips (money landing) and a bright confirmation. */
const betHit = () =>
  layer(
    [sample("chips-stack-1.ogg"), 0, 0.9],
    [sample("chips-collide-1.ogg"), 0.07, 0.6],
    [sample("confirmation_002.ogg", { room: 0.3 }), 0.02, 0.45],
    [sample("impactSoft_heavy_000.ogg", { rate: 0.8 }), 0, 0.6],
  );

/** "meh.": two knocks on wood. */
const mehHit = () =>
  layer(
    [sample("impactWood_medium_000.ogg", { room: 0.2 }), 0, 1],
    [sample("impactWood_medium_001.ogg", { rate: 0.9, room: 0.2 }), 0.14, 0.8],
    [sample("impactSoft_medium_000.ogg", { rate: 0.8 }), 0, 0.4],
  );

/** "can't read.": a digital stutter and a scratch. */
const greyHit = () =>
  layer(
    [sample("glitch_001.ogg"), 0, 0.9],
    [sample("glitch_002.ogg"), 0.05, 0.8],
    [sample("glitch_004.ogg"), 0.09, 0.8],
    [sample("glitch_002.ogg", { rate: 0.8 }), 0.15, 0.7],
    [sample("scratch_001.ogg", { room: 0.3 }), 0.06, 0.6],
    [sample("impactSoft_medium_000.ogg", { rate: 0.7 }), 0, 0.5],
  );

// ---------- speech (prepared by scripts/make-timeline.mjs) ----------

/** A voiceover line or verdict clip from audio/build/speech/, as 48 kHz mono. */
const speechFile = (id) => {
  const raw = execFileSync(
    "ffmpeg",
    ["-hide_banner", "-loglevel", "error", "-i", join(root, "audio", "build", "speech", `${id}.wav`), "-f", "f32le", "-ac", "1", "-ar", String(SR), "pipe:1"],
    { maxBuffer: 64 * 1024 * 1024 },
  );
  return new Float32Array(raw.buffer.slice(raw.byteOffset, raw.byteOffset + raw.byteLength));
};

// ---------- the score ----------

const S = T.scenes;

// Music: played by scripts/make-music.mjs (recorded instruments, rendered by FluidSynth), stereo.
{
  const raw = execFileSync(
    "ffmpeg",
    ["-hide_banner", "-loglevel", "error", "-i", join(root, "audio", "build", "music.wav"), "-f", "f32le", "-ac", "2", "-ar", String(SR), "pipe:1"],
    { maxBuffer: 256 * 1024 * 1024 },
  );
  const m = new Float32Array(raw.buffer, raw.byteOffset, raw.byteLength / 4);
  let peak = 0;
  for (const x of m) peak = Math.max(peak, Math.abs(x));
  const [L, R] = buses.music;
  for (let j = 0; j < N && 2 * j + 1 < m.length; j++) {
    L[j] = m[2 * j] / (peak || 1);
    R[j] = m[2 * j + 1] / (peak || 1);
  }
}

// The question: the offer's timer ticking every 0.5 s until the answer lands; a swell into it.
for (let t = 0, k = 0; t < T.musicInSec - 0.05; t += 0.5, k++) add("sfx", t, tick(k), 0.45, k % 2 ? 0.3 : -0.3, 0.1);
for (const s of T.hookLinesSec) add("sfx", s, thump(), 0.45, 0, 0.15);
add("sfx", T.musicInSec - 1.0, riser(1.0), 0.4, 0, 0.3);

// Offers: a swish and a ping as the card comes up, a swell into the verdict and its sound.
const HITS = { RED: nahHit, GREEN: betHit, YELLOW: mehHit, GREY: greyHit };
for (const o of T.offers) {
  const offer = OFFERS[o.offer];
  const ev = offer ? score(offer, DEFAULT_RULES) : CANT_READ;
  add("sfx", o.offerInSec - 0.1, whoosh(0.45), 0.5, -0.2, 0.15);
  add("sfx", o.offerInSec, sample("drop_002.ogg", { rate: 1.2 }), 0.3, 0.1, 0.2);
  add("sfx", o.verdictInSec - 0.3, riser(0.3), 0.3, 0, 0.1);
  add("sfx", o.verdictInSec, HITS[ev.verdict](o.timer), 0.75, 0, 0.2);
}

// Speech: every voiceover line and verdict clip, in full, where the timeline put it.
const speechTimes = [];
let lastEnd = 0;
for (const { id, at } of [...T.speech].sort((a, b) => a.at - b.at)) {
  const v = speechFile(id);
  const end = at + v.length / SR;
  if (at < lastEnd - 0.01 || end > T.durationSec) throw new Error(`${id} at ${at} s would overlap or be cut off`);
  add("vo", at, v, 1, 0, 0.06);
  speechTimes.push([at, end]);
  lastEnd = end;
  console.log(`speech: ${id} ${at.toFixed(2)}–${end.toFixed(2)} s`);
}

// Shot changes: a soft swish into the black shots and back.
for (const name of ["money", "rules", "trust", "end"]) add("sfx", S[name][0] - 0.12, whoosh(0.45), 0.35, 0.3, 0.15);
// Rules: a click per step. Trust: a soft drop per line.
T.rulesStepsSec.forEach((s, i) => add("sfx", s - 0.02, click(i), 0.55, 0.2));
T.trustLinesSec.forEach((s, i) => add("sfx", s, sample(i % 2 ? "drop_002.ogg" : "drop_001.ogg"), 0.4, 0, 0.15));

// The end: a swell, the red square landing.
add("sfx", T.endCue.stopSec - 0.6, riser(0.6), 0.3, 0, 0.3);
add("sfx", T.endCue.stopSec, nahHit(true), 0.7, 0, 0.3);

// ---------- mix ----------

// Duck the music about 10 dB (and the effects a little) under every line and clip.
const duck = new Float32Array(N).fill(1);
for (const [a, b] of speechTimes) {
  for (let j = Math.max(0, Math.round((a - 0.12) * SR)); j < Math.round((b + 0.3) * SR) && j < N; j++) {
    const edge = Math.min((j / SR - (a - 0.12)) / 0.12, (b + 0.3 - j / SR) / 0.25, 1);
    duck[j] = Math.min(duck[j], 1 - 0.68 * Math.max(0, edge));
  }
}
// Fade everything out at the end.
const [f0, f1] = T.endCue.fadeOutSec;
const fade = (j) => Math.min(1, Math.max(0, (f1 - j / SR) / (f1 - f0)));

// A small Freeverb-style room on the send bus.
const reverb = (x) => {
  const combs = [1557, 1617, 1491, 1422].map((d) => ({ d, b: new Float32Array(d), i: 0, f: 0 }));
  const aps = [556, 441].map((d) => ({ d, b: new Float32Array(d), i: 0 }));
  const y = new Float32Array(x.length);
  for (let n = 0; n < x.length; n++) {
    let s = 0;
    for (const c of combs) {
      const o = c.b[c.i];
      c.f = o * 0.7 + c.f * 0.3;
      c.b[c.i] = x[n] * 0.3 + c.f * 0.82;
      c.i = (c.i + 1) % c.d;
      s += o;
    }
    for (const a of aps) {
      const o = a.b[a.i];
      a.b[a.i] = s + o * 0.5;
      a.i = (a.i + 1) % a.d;
      s = o - s * 0.5;
    }
    y[n] = s * 0.25;
  }
  return y;
};
const verbL = reverb(buses.verb[0]);
const verbR = reverb(buses.verb[1].map((v, i) => (i > 23 ? buses.verb[1][i - 23] : 0)));

const out = new Int16Array(N * 2);
for (let j = 0; j < N; j++) {
  for (let c = 0; c < 2; c++) {
    const m = buses.music[c][j] * duck[j] * 0.7;
    const fx = buses.sfx[c][j] * 0.8 * (0.55 + 0.45 * duck[j]);
    const v = m + fx + buses.vo[c][j] + (c ? verbR[j] : verbL[j]);
    const soft = Math.tanh(v * fade(j) * 0.9);
    out[j * 2 + c] = Math.round(soft * 32000);
  }
}

// ---------- write ----------

const writeWav = (path, pcm) => {
  const h = Buffer.alloc(44);
  h.write("RIFF", 0); h.writeUInt32LE(36 + pcm.byteLength, 4); h.write("WAVE", 8);
  h.write("fmt ", 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(2, 22);
  h.writeUInt32LE(SR, 24); h.writeUInt32LE(SR * 4, 28); h.writeUInt16LE(4, 32); h.writeUInt16LE(16, 34);
  h.write("data", 36); h.writeUInt32LE(pcm.byteLength, 40);
  writeFileSync(path, Buffer.concat([h, Buffer.from(pcm.buffer)]));
};

const buildDir = join(root, "audio", "build");
const pubDir = join(root, "public", "audio");
mkdirSync(buildDir, { recursive: true });
mkdirSync(pubDir, { recursive: true });
const rawPath = join(buildDir, "mix-raw.wav");
writeWav(rawPath, out);
// Loudness for social video: about -14 LUFS, peaks under -1.5 dBTP.
execFileSync("ffmpeg", ["-hide_banner", "-loglevel", "error", "-y", "-i", rawPath, "-af", "loudnorm=I=-14:TP=-1.5:LRA=11", "-ar", String(SR), "-c:a", "pcm_s16le", join(pubDir, "mix.wav")]);
console.log(`wrote ${join(pubDir, "mix.wav")} (${T.durationSec} s)`);
