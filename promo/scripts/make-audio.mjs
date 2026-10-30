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

// ---------- sound effects ----------

/** Filtered noise sweeping up then down, panned across. */
const whoosh = (sec = 0.5, lo = 350, hi = 3200) => {
  const n = noise(sec);
  const len = n.length;
  const f = biquad(n, "bp", (i) => lo + (hi - lo) * Math.sin((Math.PI * i) / len) ** 1.5, 1.6);
  return f.map((v, i) => v * Math.sin((Math.PI * i) / len) ** 2 * 2.2);
};

const tick = (freq = 1800, sec = 0.035) => {
  const s = buf(sec);
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    s[i] = (Math.sin(2 * Math.PI * freq * t) * 0.7 + rnd() * 0.3) * Math.exp(-t * 140);
  }
  return s;
};

const thump = (sec = 0.35, from = 120, to = 45) => {
  const s = buf(sec);
  let ph = 0;
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    ph += (2 * Math.PI * (to + (from - to) * Math.exp(-t * 25))) / SR;
    s[i] = Math.sin(ph) * Math.exp(-t * 9);
  }
  return s;
};

/** The "nah" sting: two falling synth notes, "na-ah", with a low thud under them. */
const nahSting = (big = false) => {
  const s = buf(big ? 1.4 : 0.6);
  const notes = [ [0, 0.11, 64], [0.1, big ? 1.2 : 0.45, 60] ];
  for (const [at, dur, m] of notes) {
    const st = Math.round(at * SR);
    let ph = 0;
    for (let i = 0; i < dur * SR && st + i < s.length; i++) {
      const t = i / SR;
      const bend = m === 60 ? -0.6 * Math.min(1, t / 0.35) : 0;
      ph += (2 * Math.PI * mtof(m + bend - (big ? 12 : 0))) / SR;
      const env = Math.min(1, t * 300) * Math.exp(-t * (big ? 2.2 : 5));
      s[st + i] += (saw(ph, 10) + 0.5 * saw(ph * 2.003, 6)) * env * 0.55;
    }
  }
  const out = biquad(s, "lp", (i) => 900 + 2600 * Math.exp(-(i / SR) * 6), 1.1);
  const th = thump(0.4, 140, 42);
  for (let i = 0; i < th.length; i++) out[i] += th[i] * 0.9;
  return out;
};

/** "bet!": a bright two-note pop going up. */
const betPop = () => {
  const s = buf(0.5);
  for (const [at, m] of [[0, 84], [0.075, 91]]) {
    const st = Math.round(at * SR);
    for (let i = 0; st + i < s.length; i++) {
      const t = i / SR;
      const f = mtof(m) * (1 + 0.04 * Math.exp(-t * 60));
      s[st + i] += (Math.sin(2 * Math.PI * f * t) + 0.3 * Math.sin(4 * Math.PI * f * t)) * Math.exp(-t * 11) * Math.min(1, t * 600) * 0.6;
    }
  }
  const th = thump(0.2, 220, 90);
  for (let i = 0; i < th.length; i++) s[i] += th[i] * 0.4;
  return s;
};

/** "meh.": a neutral wood-block double tick. */
const mehTick = () => {
  const s = buf(0.4);
  for (const [at, f] of [[0, 880], [0.13, 740]]) {
    const st = Math.round(at * SR);
    const n = biquad(noise(0.08), "bp", f * 2, 3);
    for (let i = 0; i < 0.2 * SR; i++) {
      const t = i / SR;
      s[st + i] += (Math.sin(2 * Math.PI * f * t) * Math.exp(-t * 32) + (n[i] ?? 0) * Math.exp(-t * 80) * 0.5) * 0.8;
    }
  }
  return s;
};

/** "can't read.": a soft, muffled blip. */
const greyBlip = () => {
  const s = buf(0.35);
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    s[i] = Math.sin(2 * Math.PI * (330 - 40 * t) * t) * Math.exp(-t * 12) * Math.min(1, t * 200) + rnd() * 0.08 * Math.exp(-t * 10);
  }
  return biquad(s, "lp", 900);
};

/** Rising filtered noise into a downbeat. */
const riser = (sec) => {
  const n = noise(sec);
  const len = n.length;
  return biquad(n, "bp", (i) => 300 + 5000 * (i / len) ** 2, 2).map((v, i) => v * (i / len) ** 2 * 1.6);
};

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
for (let k = 0; k < 6; k++) add("sfx", k * 0.5, tick(k % 2 ? 1400 : 1800), 0.35, k % 2 ? 0.3 : -0.3);
for (const s of T.hookLinesSec) {
  add("sfx", s, thump(0.35, 160, 50), 0.7);
  add("sfx", s, tick(2400, 0.02), 0.3);
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
    for (let q = 0; q < 4; q++) add("music", tb + (q * BEAT) / 4, pluck(ch.arp[(beat * 4 + q) % 4]), quiet ? 0.16 : 0.1, (q % 2 ? 0.4 : -0.4), 0.35);
  }
}
add("sfx", breakdown[1], riser(S.promise[1] - breakdown[1]), 0.3, 0, 0.3);

// Ending: a big hit with the last chord, the "nah" sting as the square lands, a long Am.
add("music", S.end[0], kick(true), 0.9);
add("music", S.end[0], pad([45, 57, 60, 64, 69], T.durationSec - S.end[0], 1800), 0.7, 0, 0.6);
add("music", S.end[0], sub(33, 3.2), 0.5);
add("sfx", T.endCue.wordSec, whoosh(0.5, 200, 1800), 0.25);
add("sfx", T.endCue.stopSec, nahSting(true), 0.55, 0, 0.35);
add("sfx", T.endCue.stopSec, kick(true), 0.7);
add("sfx", T.endCue.wordSec - 0.35, riser(0.35), 0.3, 0, 0.2);
add("sfx", T.endCue.taglineSec, tick(2000, 0.03), 0.25);

// Transitions.
add("sfx", S.shift[0] - 0.1, whoosh(0.6, 200, 2000), 0.35, 0, 0.2);
add("sfx", S.rules[0] - 0.1, whoosh(0.45, 400, 3600), 0.3, 0.4, 0.2);
add("sfx", S.rules[1] - 0.15, whoosh(0.55, 250, 2200), 0.3, -0.3, 0.2);

// Offers: a whoosh as the card comes up, a sound per verdict, then the spoken line.
const offerScenes = [
  [S.nah, score(OFFERS.nah, DEFAULT_RULES), nahSting],
  [S.bet, score(OFFERS.bet, DEFAULT_RULES), betPop],
  [S.meh, score(OFFERS.meh, DEFAULT_RULES), mehTick],
  [S.grey, CANT_READ, greyBlip],
];
const speechTimes = [];
for (const [[s, e], ev, sound] of offerScenes) {
  add("sfx", s + T.offerCue.offerInSec - 0.12, whoosh(0.45), 0.4, -0.2, 0.15);
  add("sfx", s + T.offerCue.verdictInSec, sound(), sound === greyBlip ? 0.5 : 0.6, 0, 0.25);
  // The camera punch: a short riser into the hit, a sub boom on it, and a whip when the scene ends.
  add("sfx", s + T.offerCue.verdictInSec - 0.3, riser(0.3), 0.3, 0, 0.2);
  add("sfx", s + T.offerCue.verdictInSec, kick(true), sound === nahSting ? 0.75 : 0.45);
  add("sfx", s + T.offerCue.verdictInSec, biquad(noise(0.25), "lp", 2500).map((v, i) => v * Math.exp(-(i / SR) * 18)), 0.25, 0, 0.3);
  add("sfx", e - 0.4, whoosh(0.4, 500, 4500), 0.35, 0.3, 0.15);
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
  add("sfx", s - 0.02, tick(1500 + i * 150, 0.03), 0.45, 0.2);
  add("sfx", s, thump(0.12, 300, 160), 0.2);
});

// Strict flips to $/km: a switch click and a small whoosh.
add("sfx", T.rulesFlipSec - 0.02, tick(2600, 0.025), 0.5, 0.2);
add("sfx", T.rulesFlipSec, whoosh(0.3, 800, 5000), 0.25, 0.3);
add("sfx", T.rulesFlipSec, thump(0.2, 220, 90), 0.35);

// Promises: a soft tick and thump per line.
T.promiseLinesSec.forEach((s, i) => {
  add("sfx", s, tick(1100 + i * 80, 0.05), 0.3);
  add("sfx", s, thump(0.25, 140, 60), i === 3 ? 0.6 : 0.35);
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
