// Builds public/audio/mix.wav: music, sound effects and the spoken verdicts.
//   - Music and SFX: synthesised sample by sample below (no samples, no downloads).
//   - Voice: the project's own verdict clips in audio/voice/ (supplied by the project owner),
//     trimmed and levelled here. Their loudness envelopes go to src/voice.json for the speech chip.
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
const BEAT = 60 / T.bpm;

// ---------- tiny DSP kit ----------

// A seeded random, so every build sounds the same.
let seed = 12345;
const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff) * 2 - 1;

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

const buf = (sec) => new Float32Array(Math.max(1, Math.round(sec * SR)));
const mtof = (m) => 440 * 2 ** ((m - 69) / 12);

/** RBJ biquad; `freqAt(i)` may change the cutoff over time (recomputed every 32 samples). */
const biquad = (x, type, freqAt, q = 0.707) => {
  const y = new Float32Array(x.length);
  let b0, b1, b2, a1, a2, x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  const set = (f) => {
    const w = (2 * Math.PI * Math.min(f, SR * 0.45)) / SR;
    const c = Math.cos(w), s = Math.sin(w), al = s / (2 * q);
    let nb0, nb1, nb2;
    if (type === "lp") [nb0, nb1, nb2] = [(1 - c) / 2, 1 - c, (1 - c) / 2];
    else if (type === "hp") [nb0, nb1, nb2] = [(1 + c) / 2, -(1 + c), (1 + c) / 2];
    else [nb0, nb1, nb2] = [al, 0, -al]; // band-pass
    const a0 = 1 + al;
    [b0, b1, b2, a1, a2] = [nb0 / a0, nb1 / a0, nb2 / a0, (-2 * c) / a0, (1 - al) / a0];
  };
  for (let i = 0; i < x.length; i++) {
    if (i % 32 === 0) set(typeof freqAt === "function" ? freqAt(i) : freqAt);
    const v = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
    x2 = x1; x1 = x[i]; y2 = y1; y1 = v;
    y[i] = v;
  }
  return y;
};

const noise = (sec) => buf(sec).map(() => rnd());

/** A band-limited-ish saw: the first `h` harmonics. */
const saw = (phase, h = 8) => {
  let v = 0;
  for (let k = 1; k <= h; k++) v += Math.sin(phase * k) / k;
  return v * 0.6;
};

// ---------- instruments ----------

const kick = (big = false) => {
  const s = buf(0.5);
  let ph = 0;
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    const f = 46 + (big ? 140 : 110) * Math.exp(-t * 32);
    ph += (2 * Math.PI * f) / SR;
    s[i] = Math.sin(ph) * Math.exp(-t * (big ? 4.5 : 7)) + (t < 0.004 ? rnd() * 0.5 * (1 - t / 0.004) : 0);
  }
  return s;
};

const clap = () => {
  const n = biquad(noise(0.25), "bp", 1500, 1.2);
  return n.map((v, i) => {
    const t = i / SR;
    const bursts = t < 0.03 ? Math.exp(-((t % 0.01) * 300)) : 0;
    return v * (bursts * 0.8 + Math.exp(-t * 18)) * 1.4;
  });
};

const hat = (open = false) => {
  const n = biquad(noise(open ? 0.2 : 0.06), "hp", 8000, 0.9);
  return n.map((v, i) => v * Math.exp(-(i / SR) * (open ? 20 : 70)));
};

const pluck = (midi, sec = 0.25) => {
  const f = mtof(midi);
  const s = buf(sec);
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    s[i] = (Math.sin(2 * Math.PI * f * t) + 0.35 * Math.sin(4 * Math.PI * f * t) + 0.12 * saw(2 * Math.PI * f * t, 5)) * Math.exp(-t * 14) * Math.min(1, t * 400);
  }
  return biquad(s, "lp", 3200);
};

/** A soft pad chord: detuned saws through a low-pass, slow attack. */
const pad = (notes, sec, cutoff = 1400) => {
  const s = buf(sec);
  for (const m of notes) {
    for (const det of [-0.08, 0.08]) {
      const f = mtof(m + det);
      for (let i = 0; i < s.length; i++) s[i] += saw((2 * Math.PI * f * i) / SR, 7) * 0.12;
    }
  }
  const atk = 0.25 * SR, rel = 0.3 * SR;
  for (let i = 0; i < s.length; i++) s[i] *= Math.min(1, i / atk) * Math.min(1, (s.length - i) / rel);
  return biquad(s, "lp", cutoff, 0.8);
};

const sub = (midi, sec) => {
  const f = mtof(midi);
  const s = buf(sec);
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    s[i] = (Math.sin(2 * Math.PI * f * t) + 0.25 * Math.sin(4 * Math.PI * f * t)) * Math.min(1, t * 80) * Math.min(1, (sec - t) * 40);
  }
  return s;
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

// ---------- the voice (the project's verdict clips) ----------

const CLIPS = { RED: "verdict_nah.mp3", GREEN: "verdict_bet.mp3", YELLOW: "verdict_meh.mp3", GREY: "verdict_cant_read.mp3" };

/** A clip as 48 kHz mono, silence trimmed from both ends, peak-normalised. */
const voice = (verdict) => {
  const raw = execFileSync(
    "ffmpeg",
    ["-hide_banner", "-loglevel", "error", "-i", join(root, "audio", "voice", CLIPS[verdict]),
      "-af", "silenceremove=start_periods=1:start_threshold=-40dB:start_silence=0.02,areverse,silenceremove=start_periods=1:start_threshold=-40dB:start_silence=0.05,areverse,highpass=f=80",
      "-f", "f32le", "-ac", "1", "-ar", String(SR), "pipe:1"],
    { maxBuffer: 64 * 1024 * 1024 },
  );
  const v = new Float32Array(raw.buffer, raw.byteOffset, raw.byteLength / 4);
  const peak = v.reduce((m, x) => Math.max(m, Math.abs(x)), 0) || 1;
  return v.map((x) => (x / peak) * 0.95);
};

/** Loudness per video frame (0…1), for the speech chip's waveform. */
const envelope = (v) => {
  const step = SR / T.fps;
  const out = [];
  for (let i = 0; i < v.length; i += step) {
    let sum = 0;
    const end = Math.min(v.length, i + step);
    for (let j = Math.floor(i); j < end; j++) sum += v[j] * v[j];
    out.push(Math.sqrt(sum / Math.max(1, end - i)));
  }
  const max = Math.max(...out) || 1;
  return out.map((x) => Math.round((x / max) * 100) / 100);
};
const voiceMeta = {};

// ---------- the score ----------

const S = T.scenes;

// Scene 1: clock ticks, a slam per line, a low drone and a riser into the drop.
for (let k = 0; k < 6; k++) add("sfx", k * 0.5, tick(k), 0.45, k % 2 ? 0.3 : -0.3, 0.1);
for (const s of T.hookLinesSec) {
  add("sfx", s, thump(), 0.8, 0, 0.15);
  add("sfx", s, boom(), 0.25);
}
{
  const d = buf(3.0);
  for (let i = 0; i < d.length; i++) {
    const t = i / SR;
    d[i] = (Math.sin(2 * Math.PI * 55 * t) + 0.3 * Math.sin(2 * Math.PI * 110.3 * t)) * Math.min(1, t / 0.8) * 0.35;
  }
  add("music", 0, d, 0.6);
}
add("sfx", S.shift[0] - 1.5, riser(1.5), 0.35, 0, 0.3);

// Music, 120 bpm in A minor: Am F C G, one chord a bar.
const CHORDS = [
  { root: 33, notes: [57, 60, 64], arp: [69, 72, 76, 72] },
  { root: 29, notes: [53, 57, 60], arp: [65, 69, 72, 69] },
  { root: 36, notes: [55, 60, 64], arp: [67, 72, 76, 72] },
  { root: 31, notes: [55, 59, 62], arp: [67, 71, 74, 71] },
];
const musicStart = T.musicInSec;
const breakdown = [S.promise[0], S.promise[1] - 1.0]; // no kick or clap here
const musicEnd = S.end[0];
const kicks = [];
for (let bar = 0; musicStart + bar * 4 * BEAT < musicEnd; bar++) {
  const b0 = musicStart + bar * 4 * BEAT;
  const ch = CHORDS[bar % 4];
  const inBreak = b0 >= breakdown[0] && b0 < breakdown[1];
  add("music", b0, pad(ch.notes, 4 * BEAT + 0.3, inBreak ? 2600 : 1500), 0.55, 0, 0.5);
  if (!inBreak) add("music", b0, sub(ch.root, 4 * BEAT - 0.02), 0.5);
  for (let beat = 0; beat < 4; beat++) {
    const tb = b0 + beat * BEAT;
    if (tb >= musicEnd) break;
    const quiet = tb >= breakdown[0] && tb < breakdown[1];
    if (!quiet) {
      add("music", tb, kick(), 0.85);
      kicks.push(tb);
      if (beat % 2 === 1) add("music", tb, clap(), 0.28, 0.1, 0.25);
    }
    add("music", tb + BEAT / 2, hat(beat === 3), 0.16, 0.35);
    add("music", tb + BEAT / 4, hat(), 0.07, -0.35);
    add("music", tb + (3 * BEAT) / 4, hat(), 0.07, -0.35);
    for (let q = 0; q < 4; q++) add("music", tb + (q * BEAT) / 4, pluck(ch.arp[(beat * 4 + q) % 4]), quiet ? 0.07 : 0.035, (q % 2 ? 0.4 : -0.4), 0.35);
  }
}
add("sfx", breakdown[1], riser(S.promise[1] - breakdown[1]), 0.3, 0, 0.3);

// Ending: a big hit with the last chord, the "nah" sting as the square lands, a long Am.
add("music", S.end[0], kick(true), 0.9);
add("music", S.end[0], pad([45, 57, 60, 64, 69], T.durationSec - S.end[0], 1800), 0.7, 0, 0.6);
add("music", S.end[0], sub(33, 3.2), 0.5);
add("sfx", T.endCue.wordSec - 0.05, whoosh(0.8), 0.45, 0, 0.2);
add("sfx", T.endCue.stopSec, nahHit(true), 0.9, 0, 0.3);
add("sfx", T.endCue.wordSec - 0.5, riser(0.5), 0.45, 0, 0.2);
add("sfx", T.endCue.taglineSec, click(), 0.4);

// Transitions.
add("sfx", S.shift[0] - 0.1, whoosh(0.8), 0.55, 0, 0.2);
add("sfx", S.rules[0] - 0.1, whoosh(0.45), 0.5, 0.4, 0.2);
add("sfx", S.rules[1] - 0.15, whoosh(0.8), 0.55, -0.3, 0.2);

// Offers: a whoosh as the card comes up, a sound per verdict, then the spoken line.
const offerScenes = [
  [S.nah, score(OFFERS.nah, DEFAULT_RULES), nahHit],
  [S.bet, score(OFFERS.bet, DEFAULT_RULES), betHit],
  [S.meh, score(OFFERS.meh, DEFAULT_RULES), mehHit],
  [S.grey, CANT_READ, greyHit],
];
const speechTimes = [];
for (const [[s, e], ev, sound] of offerScenes) {
  add("sfx", s + T.offerCue.offerInSec - 0.1, whoosh(0.45), 0.6, -0.2, 0.15);
  add("sfx", s + T.offerCue.verdictInSec, sound(), 0.85, 0, 0.2);
  // The camera punch: a swell into the hit, a boom under it (nah. has its own), a whoosh out.
  add("sfx", s + T.offerCue.verdictInSec - 0.35, riser(0.35), 0.4, 0, 0.1);
  if (sound === betHit || sound === mehHit) add("sfx", s + T.offerCue.verdictInSec, boom(), 0.35);
  add("sfx", e - 0.4, whoosh(0.8), 0.5, 0.3, 0.15);
  const v = voice(ev.verdict);
  const at = s + T.offerCue.speechInSec;
  add("vo", at, v, 0.9, 0, 0.1);
  speechTimes.push([at, at + v.length / SR]);
  voiceMeta[ev.verdict] = { durationSec: Math.round((v.length / SR) * 100) / 100, envelope: envelope(v) };
  if (at + v.length / SR > e) console.warn(`warning: the ${CLIPS[ev.verdict]} clip runs past its scene`);
  console.log(`voice: ${CLIPS[ev.verdict]} at ${at.toFixed(2)} s, ${(v.length / SR).toFixed(2)} s`);
}

// Rules: a UI tick per step, a touch higher each time.
T.rulesStepsSec.forEach((s, i) => {
  add("sfx", s - 0.02, click(i), 0.6, 0.2);
});

// Strict flips to $/km: a switch click and a small whoosh.
add("sfx", T.rulesFlipSec - 0.03, sample("switch_001.ogg"), 0.7, 0.2);
add("sfx", T.rulesFlipSec, whoosh(0.45), 0.35, 0.3);

// Promises: a soft tick and thump per line.
T.promiseLinesSec.forEach((s, i) => {
  add("sfx", s, sample(i % 2 ? "drop_002.ogg" : "drop_001.ogg"), 0.5, 0, 0.15);
  add("sfx", s, thump(i === 3), i === 3 ? 0.8 : 0.5, 0, 0.15);
});

// ---------- mix ----------

// Sidechain the music to the kicks, and duck it under the voice.
const duck = new Float32Array(N).fill(1);
for (const k of kicks) {
  const st = Math.round(k * SR);
  for (let i = 0; i < 0.25 * SR && st + i < N; i++) duck[st + i] = Math.min(duck[st + i], 1 - 0.45 * Math.exp(-(i / SR) * 14));
}
for (const [a, b] of speechTimes) {
  for (let j = Math.round((a - 0.08) * SR); j < Math.round((b + 0.25) * SR) && j < N; j++) {
    const edge = Math.min((j / SR - (a - 0.08)) / 0.08, (b + 0.25 - j / SR) / 0.2, 1);
    duck[j] *= 1 - 0.5 * Math.max(0, edge);
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
    const v = m + buses.sfx[c][j] * 0.8 + buses.vo[c][j] + (c ? verbR[j] : verbL[j]);
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
writeFileSync(join(root, "src", "voice.json"), JSON.stringify(voiceMeta) + "\n");
console.log("wrote src/voice.json");
