// Composes the background music from src/timeline.json and renders it with FluidSynth through the
// MuseScore General soundfont (MIT; recorded drums, bass, electric piano and strings), so it
// sounds played rather than synthesised. Writes audio/build/music.wav.
//   Needs: fluidsynth, and the soundfont (Ubuntu: apt install fluidsynth musescore-general-soundfont),
//   or set SOUNDFONT to another .sf2/.sf3.
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const T = JSON.parse(readFileSync(join(root, "src", "timeline.json"), "utf8"));
const SOUNDFONT = process.env.SOUNDFONT ?? "/usr/share/sounds/sf3/MuseScore_General_Full.sf3";
const PPQ = 480;
const BEAT = 60 / T.bpm;
const S = T.scenes;

// A seeded random, so every build plays the same.
let seed = 777;
const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
const vary = (v, spread = 10) => Math.max(1, Math.min(127, Math.round(v + (rnd() - 0.5) * spread)));

// ---------- a tiny Standard MIDI File writer ----------

/** Seconds → ticks (the tempo is constant). A few ms of human looseness unless `tight`. */
const tick = (sec, tight = false) => Math.max(0, Math.round(((sec + (tight ? 0 : (rnd() - 0.5) * 0.012)) / BEAT) * PPQ));

class Track {
  constructor(name) {
    this.name = name;
    this.events = []; // [tick, order, bytes]
  }
  raw(t, bytes, order = 1) {
    this.events.push([t, order, bytes]);
  }
  program(ch, bank, prog) {
    this.raw(0, [0xb0 | ch, 0, bank], 0);
    this.raw(0, [0xc0 | ch, prog], 0);
  }
  cc(ch, at, num, val) {
    this.raw(tick(at, true), [0xb0 | ch, num, Math.round(val)], 0);
  }
  note(ch, at, dur, pitch, vel, tight = false) {
    const t0 = tick(at, tight);
    this.raw(t0, [0x90 | ch, pitch, vel], 2);
    this.raw(Math.max(t0 + 1, tick(at + dur, true)), [0x80 | ch, pitch, 0], 1);
  }
  bytes() {
    const vlq = (n) => {
      const out = [n & 0x7f];
      while ((n >>= 7)) out.unshift((n & 0x7f) | 0x80);
      return out;
    };
    const ev = [...this.events].sort((a, b) => a[0] - b[0] || a[1] - b[1]);
    const data = [];
    const name = [...Buffer.from(this.name)];
    data.push(0, 0xff, 0x03, name.length, ...name);
    let last = 0;
    for (const [t, , b] of ev) {
      data.push(...vlq(t - last), ...b);
      last = t;
    }
    data.push(0, 0xff, 0x2f, 0);
    const len = data.length;
    return Buffer.from([0x4d, 0x54, 0x72, 0x6b, (len >> 24) & 255, (len >> 16) & 255, (len >> 8) & 255, len & 255, ...data]);
  }
}

const writeMidi = (path, tracks) => {
  const tempo = Math.round(BEAT * 1e6);
  const meta = new Track("tempo");
  meta.raw(0, [0xff, 0x51, 0x03, (tempo >> 16) & 255, (tempo >> 8) & 255, tempo & 255], 0);
  const all = [meta, ...tracks];
  const head = Buffer.from([0x4d, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 1, 0, all.length, PPQ >> 8, PPQ & 255]);
  writeFileSync(path, Buffer.concat([head, ...all.map((t) => t.bytes())]));
};

// ---------- the arrangement ----------

const DR = 9, BASS = 0, EP = 1, STR = 2, PNO = 3;
const drums = new Track("drums"), bass = new Track("bass"), ep = new Track("electric piano"), strings = new Track("strings"), piano = new Track("piano");
drums.program(DR, 128, 16); // Power kit: punchy, recorded
bass.program(BASS, 0, 33); // Fingered bass
ep.program(EP, 0, 4); // Tine electric piano
strings.program(STR, 0, 49); // Strings, slow attack
piano.program(PNO, 0, 0); // Grand piano
for (const [tr, ch, vol, rev] of [[drums, DR, 112, 30], [bass, BASS, 108, 10], [ep, EP, 92, 60], [strings, STR, 78, 90], [piano, PNO, 96, 70]]) {
  tr.cc(ch, 0, 7, vol);
  tr.cc(ch, 0, 91, rev);
  tr.cc(ch, 0, 11, 127);
}

const K = 36, SN = 38, CLAP = 39, HH = 42, PHH = 44, OHH = 46, CRASH = 49, RIDE = 51, TOM_L = 45, TOM_H = 48;

// Am F C G, one chord a bar (2 s at 120 bpm): root (bass), voicing (EP), string pad.
const CHORDS = [
  { root: 45, ep: [57, 60, 64, 67], pad: [45, 52, 57, 60] }, // Am7
  { root: 41, ep: [57, 60, 64, 65], pad: [41, 48, 53, 57] }, // Fmaj7
  { root: 48, ep: [55, 60, 64, 67], pad: [48, 55, 60, 64] }, // C
  { root: 43, ep: [55, 59, 62, 67], pad: [43, 50, 55, 59] }, // G
];

const start = T.musicInSec;
const breakdown = [S.promise[0], S.promise[1] - 1.0];
const end = S.end[0];
const bar = 4 * BEAT;

// Intro: strings swell on Am under the hook, a piano low A on each line, a snare roll into the drop.
for (const n of [45, 52, 57]) strings.note(STR, 0, start + 0.1, n, 70, true);
for (let i = 0; i <= 24; i++) strings.cc(STR, (i / 24) * start, 11, 40 + (87 * i) / 24);
T.hookLinesSec.forEach((s, i) => piano.note(PNO, s, 1.2, i === 2 ? 45 : 33, vary(88), true));
for (let t = start - 1.0, i = 0; t < start - 0.02; i++) {
  const step = t < start - 0.5 ? BEAT / 4 : BEAT / 8;
  drums.note(DR, t, 0.05, SN, vary(40 + (i * 80) / 24), true);
  t += step;
}

// The groove.
for (let b0 = start, k = 0; b0 < end - 0.01; b0 += bar, k++) {
  const ch = CHORDS[k % 4];
  const inBreak = b0 >= breakdown[0] - 0.01 && b0 < breakdown[1];
  const lift = b0 >= S.rules[0]; // rides and open hats from the rules on
  // Strings: a quiet bed (louder in the breakdown).
  for (const n of ch.pad) strings.note(STR, b0, bar - 0.05, n, inBreak ? 64 : 46);
  // Electric piano: on 1 and the "and" of 2, then a lighter push on the "and" of 3.
  const hits = inBreak ? [[0, 3.8, 70]] : [[0, 1.4, 78], [1.5, 0.45, 66], [2.5, 1.4, 70]];
  for (const [beat, len, v] of hits) for (const n of ch.ep) ep.note(EP, b0 + beat * BEAT, len * BEAT, n, vary(v, 8));
  // Bass: root with an octave pickup, following the kick.
  if (inBreak) bass.note(BASS, b0, bar - 0.05, ch.root - 12, 80);
  else {
    bass.note(BASS, b0, 1.4 * BEAT, ch.root - 12, vary(100, 6));
    bass.note(BASS, b0 + 1.5 * BEAT, 0.45 * BEAT, ch.root - 12, vary(84, 6));
    bass.note(BASS, b0 + 2.5 * BEAT, 0.9 * BEAT, ch.root - 12, vary(92, 6));
    bass.note(BASS, b0 + 3.5 * BEAT, 0.45 * BEAT, ch.root, vary(80, 6));
  }
  if (inBreak) {
    drums.note(DR, b0, 0.1, PHH, vary(40));
    continue;
  }
  // Drums: kick on 1, the "and" of 2 and 3; snare (with a clap) on 2 and 4; 16th hats.
  for (const beat of [0, 1.5, 2]) drums.note(DR, b0 + beat * BEAT, 0.1, K, vary(beat === 0 ? 118 : 104, 8));
  for (const beat of [1, 3]) {
    drums.note(DR, b0 + beat * BEAT, 0.1, SN, vary(112, 8));
    drums.note(DR, b0 + beat * BEAT, 0.1, CLAP, vary(70, 10));
  }
  for (let q = 0; q < 16; q++) {
    const accent = q % 4 === 2 ? 86 : q % 2 === 0 ? 68 : 44;
    const open = q === 14 && (k % 2 === 1 || lift);
    drums.note(DR, b0 + (q * BEAT) / 4, 0.08, open ? OHH : lift && q % 2 === 0 ? RIDE : HH, vary(open ? 80 : accent, 12));
  }
  // A small fill at the end of every fourth bar.
  if (k % 4 === 3) [TOM_H, TOM_H, TOM_L, TOM_L].forEach((n, i) => drums.note(DR, b0 + (3 + i / 4) * BEAT, 0.1, n, vary(90 + i * 6)));
}

// Crashes on the drop, each verdict landing and the strict flip.
drums.note(DR, start, 1, CRASH, 118, true);
for (const name of ["nah", "bet", "meh", "grey"]) drums.note(DR, S[name][0] + T.offerCue.verdictInSec, 1, CRASH, 100, true);
drums.note(DR, T.rulesFlipSec, 1, CRASH, 96, true);

// Out of the breakdown: a snare roll into the end hit.
for (let t = breakdown[1], i = 0; t < end - 0.02; i++) {
  drums.note(DR, t, 0.05, SN, vary(50 + i * 4), true);
  t += t < end - 0.5 ? BEAT / 4 : BEAT / 8;
}

// The end: crash, kick and a big Am on piano, strings and bass, ringing out.
drums.note(DR, end, 2, CRASH, 124, true);
drums.note(DR, end, 0.2, K, 124, true);
for (const n of [33, 45, 52, 57, 60, 64, 69]) piano.note(PNO, end, T.durationSec - end - 0.2, n, n < 40 ? 100 : 84, true);
for (const n of [45, 52, 57, 60, 64]) strings.note(STR, end, T.durationSec - end - 0.2, n, 80, true);
bass.note(BASS, end, 3, 33, 104, true);
for (let i = 0; i <= 20; i++) strings.cc(STR, end + 1.5 + (i / 20) * (T.durationSec - end - 1.7), 11, 127 - (127 * i) / 20);

// ---------- render ----------

if (!existsSync(SOUNDFONT)) {
  console.error(`No soundfont at ${SOUNDFONT}. Install one (apt install musescore-general-soundfont) or set SOUNDFONT.`);
  process.exit(1);
}
const buildDir = join(root, "audio", "build");
mkdirSync(buildDir, { recursive: true });
const mid = join(buildDir, "music.mid");
const wav = join(buildDir, "music.wav");
writeMidi(mid, [drums, bass, ep, strings, piano]);
execFileSync("fluidsynth", ["-ni", "-q", "-g", "0.6", "-r", "48000", "-R", "1", "-C", "1", "-F", wav, SOUNDFONT, mid], { stdio: ["ignore", "ignore", "inherit"] });
console.log(`wrote ${wav}`);
