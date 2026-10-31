// Builds the video's timeline from its audio, so every line and clip plays in full:
//   1. Splits the owner's voiceover (audio/voice/voiceover.mp3, ElevenLabs) into its ten script
//      lines, shortening long pauses inside each line.
//   2. Trims the verdict clips (audio/voice/verdict_*.mp3).
//   3. Lays the shots out back to back, each as long as its audio plus small gaps.
// Writes the processed speech to audio/build/speech/*.wav, src/timeline.json (read by the picture,
// the music and the mix) and src/voice.json (loudness, for the waveform).
import { execFileSync } from "node:child_process";
import { mkdirSync, unlinkSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const SR = 48000;
const FPS = 30;
const out = join(root, "audio", "build", "speech");
mkdirSync(out, { recursive: true });

const decode = (file, af = "anull") => {
  const raw = execFileSync(
    "ffmpeg",
    ["-hide_banner", "-loglevel", "error", "-i", file, "-af", af, "-f", "f32le", "-ac", "1", "-ar", String(SR), "pipe:1"],
    { maxBuffer: 256 * 1024 * 1024 },
  );
  return new Float32Array(raw.buffer.slice(raw.byteOffset, raw.byteOffset + raw.byteLength));
};

const writeWav = (path, v) => {
  const pcm = new Int16Array(v.length);
  for (let i = 0; i < v.length; i++) pcm[i] = Math.max(-1, Math.min(1, v[i])) * 32767;
  const h = Buffer.alloc(44);
  h.write("RIFF", 0); h.writeUInt32LE(36 + pcm.byteLength, 4); h.write("WAVE", 8);
  h.write("fmt ", 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(1, 22);
  h.writeUInt32LE(SR, 24); h.writeUInt32LE(SR * 2, 28); h.writeUInt16LE(2, 32); h.writeUInt16LE(16, 34);
  h.write("data", 36); h.writeUInt32LE(pcm.byteLength, 40);
  writeFileSync(path, Buffer.concat([h, Buffer.from(pcm.buffer)]));
};

/** Speech stretches in `v`: [start, end] in seconds, split at silences of at least `minGap` s. */
const segments = (v, threshold = 0.0126, minGap = 0.35) => {
  const win = Math.round(SR * 0.01);
  const segs = [];
  let start = -1, quiet = 0;
  for (let i = 0; i + win <= v.length; i += win) {
    let sum = 0;
    for (let j = i; j < i + win; j++) sum += v[j] * v[j];
    const loud = Math.sqrt(sum / win) > threshold; // about -38 dB
    if (loud) {
      if (start < 0) start = i;
      quiet = 0;
    } else if (start >= 0 && ++quiet * 0.01 >= minGap) {
      segs.push([start / SR, (i - (quiet - 1) * win) / SR]);
      start = -1;
      quiet = 0;
    }
  }
  if (start >= 0) segs.push([start / SR, v.length / SR]);
  return segs;
};

const slice = (v, a, b) => v.slice(Math.max(0, Math.round(a * SR)), Math.min(v.length, Math.round(b * SR)));
const peakNormalise = (v, to = 0.95) => {
  let p = 0;
  for (const x of v) p = Math.max(p, Math.abs(x));
  return v.map((x) => (x / (p || 1)) * to);
};

/** Loudness per video frame (0…1). */
const envelope = (v) => {
  const step = SR / FPS;
  const e = [];
  for (let i = 0; i < v.length; i += step) {
    let sum = 0;
    const end = Math.min(v.length, i + step);
    for (let j = Math.floor(i); j < end; j++) sum += v[j] * v[j];
    e.push(Math.sqrt(sum / Math.max(1, end - i)));
  }
  const max = Math.max(...e) || 1;
  return e.map((x) => Math.round((x / max) * 100) / 100);
};

// ---------- 1. the voiceover, line by line ----------

// How many speech stretches each script line has (its sentences, split at the pauses the voice took).
const LINES = [
  { id: "vo_01", segs: 4, text: "Delivery driver? New offer. Fifteen seconds. Is it worth it?" },
  { id: "vo_02", segs: 2, text: "nah meter does the math for you, and answers in one word." },
  { id: "vo_03", segs: 1, text: "Bet. It pays your rate." },
  { id: "vo_04", segs: 3, text: "Meh. It pays the hour, but it's a long drive." },
  { id: "vo_05", segs: 2, text: "And nah? Not worth your time." },
  { id: "vo_06", segs: 1, text: "Skip the bad offers, and earn more every hour." },
  { id: "vo_07", segs: 1, text: "It says it out loud, so your eyes stay on the road." },
  { id: "vo_08", segs: 1, text: "You set the rules: your dollars an hour, your dollars a kilometre." },
  { id: "vo_09", segs: 2, text: "It never taps anything. You decide. No internet. No account." },
  { id: "vo_10", segs: 2, text: "Bad offer? Just say nah." },
];
const PAD_IN = 0.04, PAD_OUT = 0.12, INNER_GAP = 0.25;

// 10% faster than recorded: the voice is calm, and the video should stay under about 45 s.
const VO_TEMPO = 1.1;
// The lines are found at the recorded speed (the pauses are what tell them apart), then sped up.
const vo = peakNormalise(decode(join(root, "audio", "voice", "voiceover.mp3"), "highpass=f=70"));
const found = segments(vo);
const expected = LINES.reduce((n, l) => n + l.segs, 0);
if (found.length !== expected) {
  console.error(`The voiceover has ${found.length} speech stretches; the script expects ${expected}:`);
  console.error(found.map(([a, b]) => `${a.toFixed(2)}–${b.toFixed(2)}`).join(" "));
  process.exit(1);
}

const speech = {}; // id → { len, marks: start of each sentence within the line }
let k = 0;
for (const line of LINES) {
  const parts = found.slice(k, k + line.segs);
  k += line.segs;
  const pieces = [];
  const marks = [];
  let t = 0;
  parts.forEach(([a, b], i) => {
    const piece = slice(vo, a - PAD_IN, b + PAD_OUT);
    marks.push(Math.round(t * 100) / 100);
    pieces.push(piece);
    t += piece.length / SR;
    if (i < parts.length - 1) {
      pieces.push(new Float32Array(Math.round(INNER_GAP * SR)));
      t += INNER_GAP;
    }
  });
  const v = new Float32Array(pieces.reduce((n, p) => n + p.length, 0));
  let o = 0;
  for (const p of pieces) (v.set(p, o), (o += p.length));
  const tmp = join(out, `${line.id}.raw.wav`);
  writeWav(tmp, v);
  const fast = decode(tmp, `atempo=${VO_TEMPO}`);
  unlinkSync(tmp);
  writeWav(join(out, `${line.id}.wav`), fast);
  speech[line.id] = { len: fast.length / SR, marks: marks.map((m) => Math.round((m / VO_TEMPO) * 100) / 100), env: envelope(fast) };
  console.log(`${line.id}  ${speech[line.id].len.toFixed(2)} s  "${line.text}"`);
}

// ---------- 2. the verdict clips ----------

const CLIPS = { nah: "verdict_nah.mp3", bet: "verdict_bet.mp3", meh: "verdict_meh.mp3" };
for (const [id, file] of Object.entries(CLIPS)) {
  // Trim silence from both ends, gently at the end so the last word's tail stays.
  const v = peakNormalise(
    decode(
      join(root, "audio", "voice", file),
      "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.03,areverse,silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.15,areverse,highpass=f=80",
    ),
  );
  writeWav(join(out, `clip_${id}.wav`), v);
  speech[`clip_${id}`] = { len: v.length / SR, marks: [0], env: envelope(v) };
  console.log(`clip_${id}  ${(v.length / SR).toFixed(2)} s`);
}

// ---------- 3. the shots ----------

const r2 = (x) => Math.round(x * 100) / 100;
const len = (id) => speech[id].len;
const placed = []; // { id, at }
const say = (id, at) => (placed.push({ id, at: r2(at) }), at + len(id));
const scenes = {};
const offers = [];
let t = 0;

// Hook: the offer card arrives on "New offer", "Worth it?" on its line.
const hookVo = 0.15;
const hookEnd = say("vo_01", hookVo);
const newOfferSec = r2(hookVo + speech.vo_01.marks[1]);
const worthSec = r2(hookVo + speech.vo_01.marks[3]);
scenes.hook = [0, r2(hookEnd + 0.2)];

// Answer: the verdict lands, its clip plays; then "nah.meter does the math".
const v1 = scenes.hook[1];
const clip1End = say("clip_nah", v1 + 0.15);
scenes.answer = [v1, r2(clip1End + 0.15)];
const whatEnd = say("vo_02", scenes.answer[1]);
scenes.what = [scenes.answer[1], r2(whatEnd + 0.25)];
offers.push({ id: "hero", offer: "hero", from: 0, until: scenes.what[1], offerInSec: newOfferSec, verdictInSec: v1, timer: true });

// bet! and meh.: a new offer, its verdict, its clip, then the line that explains it.
t = scenes.what[1];
for (const [id, line] of [["bet", "vo_03"], ["meh", "vo_04"]]) {
  const verdict = t + 0.35;
  const clipEnd = say(`clip_${id}`, verdict + 0.15);
  const lineEnd = say(line, clipEnd + 0.15);
  scenes[id] = [r2(t), r2(lineEnd + 0.25)];
  offers.push({ id, offer: id, from: r2(t), until: scenes[id][1], offerInSec: r2(t), verdictInSec: r2(verdict), timer: false });
  t = scenes[id][1];
}

// nah.: a new offer and its verdict; the line says what it means (no clip: its spoken rate is the
// hero offer's, not this one's).
{
  const verdict = t + 0.35;
  const lineEnd = say("vo_05", verdict + 0.25);
  scenes.nah = [r2(t), r2(lineEnd + 0.25)];
  offers.push({ id: "nah", offer: "nah", from: r2(t), until: scenes.nah[1], offerInSec: r2(t), verdictInSec: r2(verdict), timer: false });
  t = scenes.nah[1];
}

// The benefits, the rules, the trust lines: one shot per line.
let rulesSteps, trustLines, roadVo;
for (const [name, line, lead] of [["money", "vo_06", 0.2], ["road", "vo_07", 0.2], ["rules", "vo_08", 0.35], ["trust", "vo_09", 0.2]]) {
  const at = t + lead;
  const end = say(line, at);
  scenes[name] = [r2(t), r2(end + 0.25)];
  if (name === "rules") rulesSteps = [r2(at + len(line) * 0.3), r2(at + len(line) * 0.62)];
  if (name === "trust") trustLines = [r2(at), r2(at + speech.vo_09.marks[1])];
  if (name === "road") roadVo = r2(at);
  t = scenes[name][1];
}

// The end: the wordmark, the square, then "Bad offer? Just say nah." spoken and shown.
const endStart = t;
const tagAt = endStart + 0.7;
const endVo = say("vo_10", tagAt);
const duration = r2(endVo + 1.2);
scenes.end = [r2(endStart), duration];

const timeline = {
  fps: FPS,
  durationSec: duration,
  bpm: 120,
  musicInSec: v1,
  scenes,
  offers,
  speech: placed,
  hookLinesSec: [newOfferSec, worthSec],
  roadVoSec: roadVo,
  rulesStepsSec: rulesSteps,
  trustLinesSec: trustLines,
  endCue: { wordSec: r2(endStart + 0.1), stopSec: r2(endStart + 0.45), taglineSec: r2(tagAt), fadeOutSec: [r2(duration - 0.6), duration] },
};
writeFileSync(join(root, "src", "timeline.json"), JSON.stringify(timeline, null, 2) + "\n");
writeFileSync(join(root, "src", "voice.json"), JSON.stringify({ vo_07: speech.vo_07.env }) + "\n");
console.log(`timeline: ${duration} s, ${placed.length} speech items`);
