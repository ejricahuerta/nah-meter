// Camera moves and impact effects: punch-in zooms, shake, motion blur, the full-screen verdict
// word, flash, burst lines, colour glow and film grain. Times come from timeline.json.
import React from "react";
import { Easing, interpolate, spring } from "remotion";
import timeline from "./timeline.json";
import { Verdict, word } from "./nah";
import { VerdictColor, nahText } from "./tokens";
import { BEZEL, OFFER_SCENES, PHONE_H, PHONE_W } from "./Phone";

const S = timeline.scenes;
const cue = timeline.offerCue;
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const smooth = Easing.bezier(0.65, 0, 0.35, 1);

export type CamLayout = {
  phone: { cx: number; top: number; scale: number };
  /** Where a zoomed-in focus point sits on the canvas. */
  zoomQ: { x: number; y: number };
};

/** A camera pose: zoom, the focus point on the phone screen (dp), and where it lands on the canvas. */
type Pose = { z: number; fx: number; fy: number; qx: number; qy: number };

// Points on the phone screen (dp, from the screen's top-left).
const SCREEN_CENTER = { x: 206, y: 446 };
const OFFER_CENTER = { x: 206, y: 782 };
const VERDICT_CENTER = { x: 206, y: 611 };
const BOTH_CARDS = { x: 206, y: 690 };
const RULES_FOCUS = { x: 206, y: 350 };

const phoneToCanvas = (L: CamLayout, p: { x: number; y: number }) => ({
  x: L.phone.cx - (PHONE_W * L.phone.scale) / 2 + (BEZEL + p.x) * L.phone.scale,
  y: L.phone.top + (BEZEL + p.y) * L.phone.scale,
});

const identity = (L: CamLayout): Pose => {
  const c = phoneToCanvas(L, SCREEN_CENTER);
  return { z: 1, fx: SCREEN_CENTER.x, fy: SCREEN_CENTER.y, qx: c.x, qy: c.y };
};
const zoomOn = (L: CamLayout, z: number, p: { x: number; y: number }, qy = 0): Pose => ({
  z,
  fx: p.x,
  fy: p.y,
  qx: L.zoomQ.x,
  qy: L.zoomQ.y + qy,
});

const mix = (a: Pose, b: Pose, k: number): Pose => ({
  z: a.z + (b.z - a.z) * k,
  fx: a.fx + (b.fx - a.fx) * k,
  fy: a.fy + (b.fy - a.fy) * k,
  qx: a.qx + (b.qx - a.qx) * k,
  qy: a.qy + (b.qy - a.qy) * k,
});

/** Keyframes: from `at`, move to `pose` over `dur` (eased), or with a spring that overshoots. */
type Key = { at: number; dur: number; pose: Pose; spring?: boolean };

const run = (keys: Key[], t: number, start: Pose, fps: number): Pose => {
  let pose = start;
  for (const k of keys) {
    if (t < k.at) break;
    const p = k.spring
      ? spring({ frame: (t - k.at) * fps, fps, config: { damping: 12, stiffness: 120, mass: 0.9 } })
      : interpolate(t, [k.at, k.at + k.dur], [0, 1], { ...clamp, easing: smooth });
    pose = mix(pose, k.pose, p);
  }
  return pose;
};

const cameraKeys = (L: CamLayout): Key[] => {
  const keys: Key[] = [
    // The shift: start wide on the night drive, then push in on the phone.
    { at: S.shift[0] + 0.15, dur: 1.75, pose: { ...identity(L), z: 1.05 } },
  ];
  for (const { scene } of OFFER_SCENES) {
    const [s, e] = scene;
    keys.push(
      { at: s - 0.05, dur: 0.3, pose: identity(L) },
      { at: s + cue.offerInSec, dur: cue.verdictInSec - cue.offerInSec, pose: zoomOn(L, 1.3, OFFER_CENTER, 120) },
      { at: s + cue.verdictInSec, dur: 0, pose: zoomOn(L, 1.95, VERDICT_CENTER), spring: true },
      { at: s + cue.verdictInSec + 1.5, dur: e - s - cue.verdictInSec - 1.9, pose: zoomOn(L, 1.5, BOTH_CARDS, 80) },
      { at: e - 0.35, dur: 0.35, pose: identity(L) },
    );
  }
  keys.push(
    { at: S.rules[0] + 0.5, dur: 1.1, pose: zoomOn(L, 1.7, RULES_FOCUS, 40) },
    { at: S.rules[1] - 0.6, dur: 0.5, pose: identity(L) },
  );
  return keys;
};

/** A deterministic wobble in −1…1. */
const wobble = (t: number, k: number) => Math.sin(t * 61.3 + k * 1.7) * 0.6 + Math.sin(t * 97.1 + k * 4.1) * 0.4;

/** How hard the screen shakes at `t`: hard on nah., a nudge on the others, a tap per rule step. */
export const shakeAt = (t: number) => {
  let a = 0;
  for (const { scene, e } of OFFER_SCENES) {
    const u = t - scene[0] - cue.verdictInSec;
    if (u >= 0 && u < 0.5) a = Math.max(a, (e.verdict === "RED" ? 26 : 10) * Math.exp(-u * 9));
  }
  for (const s of [...timeline.rulesStepsSec, timeline.rulesFlipSec]) {
    const u = t - s;
    if (u >= 0 && u < 0.25) a = Math.max(a, 6 * Math.exp(-u * 14));
  }
  for (const s of timeline.hookLinesSec) {
    const u = t - s;
    if (u >= 0 && u < 0.3) a = Math.max(a, 14 * Math.exp(-u * 12));
  }
  const u = t - timeline.endCue.stopSec;
  if (u >= 0 && u < 0.4) a = Math.max(a, 16 * Math.exp(-u * 10));
  return { x: wobble(t, 1) * a, y: wobble(t, 2) * a };
};

const poseAt = (L: CamLayout, t: number, fps: number) => {
  const p = run(cameraKeys(L), t, { ...identity(L), z: 0.66 }, fps);
  // A small extra punch on each rule step.
  const bump = [...timeline.rulesStepsSec, timeline.rulesFlipSec].reduce((b, s) => b + (t >= s ? 0.07 * Math.exp(-(t - s) * 9) : 0), 0);
  return { ...p, z: p.z + bump };
};

/** The CSS transform for the camera at `t`, with motion blur from how fast it moves. */
export const camera = (L: CamLayout, t: number, fps: number) => {
  const a = poseAt(L, t, fps);
  const b = poseAt(L, t - 1 / fps, fps);
  const P = phoneToCanvas(L, { x: a.fx, y: a.fy });
  const Pb = phoneToCanvas(L, { x: b.fx, y: b.fy });
  const tx = a.qx - P.x * a.z;
  const ty = a.qy - P.y * a.z;
  const txb = b.qx - Pb.x * b.z;
  const tyb = b.qy - Pb.y * b.z;
  const speed = Math.hypot(tx - txb, ty - tyb) + Math.abs(a.z - b.z) * 900;
  const sh = shakeAt(t);
  return {
    transform: `translate(${tx + sh.x}px, ${ty + sh.y}px) scale(${a.z})`,
    // Only fast moves (whips, punches) blur; slow pushes stay sharp.
    blur: Math.min(10, Math.max(0, speed - 60) / 15),
    zoom: a.z,
  };
};

// ---------- the verdict impact ----------

/** The scene whose verdict is landing (or held) at `t`, with the time since it landed. */
export const impactAt = (t: number) => {
  for (const o of OFFER_SCENES) {
    const u = t - o.scene[0] - cue.verdictInSec;
    if (u >= -0.05 && t < o.scene[1]) return { ...o, u };
  }
  return null;
};

/**
 * The verdict lands: the screen bursts open in the verdict's colour from the card (an iris), the
 * word is stamped huge in black, burst lines fly out, then the colour closes back into the card.
 * `cx`, `cy` is where the card sits on the canvas.
 */
export const VerdictSlam: React.FC<{ t: number; W: number; H: number; cx: number; cy: number }> = ({ t, W, H, cx, cy }) => {
  const hit = impactAt(t);
  if (!hit || hit.u < 0 || hit.u > 0.78) return null;
  const { u, e } = hit;
  const v: Verdict = e.verdict;
  const color = VerdictColor[v];
  const far = Math.hypot(Math.max(cx, W - cx), Math.max(cy, H - cy));
  const open = interpolate(u, [0, 0.12], [0, 1], { ...clamp, easing: Easing.out(Easing.cubic) });
  const close = interpolate(u, [0.55, 0.78], [0, 1], { ...clamp, easing: Easing.in(Easing.cubic) });
  const radius = far * open * (1 - close);
  // As big as fits, about 0.55em a character in Geist Black at this tracking.
  const size = Math.min(Math.min(W, H) * 0.56, (W * 0.9) / (word(v).length * 0.55));
  const stamp = interpolate(u, [0.03, 0.15, 0.22], [2.6, 0.94, 1], { ...clamp, easing: Easing.out(Easing.cubic) });
  const rays = interpolate(u, [0.05, 0.5], [0, 1], clamp);
  const flash = interpolate(u, [0, 0.02, 0.08], [0, 0.9, 0], clamp);
  return (
    <div style={{ position: "absolute", inset: 0, pointerEvents: "none" }}>
      <div style={{ position: "absolute", inset: 0, background: color, clipPath: `circle(${radius}px at ${cx}px ${cy}px)` }}>
        {/* Halftone dots, for print-like texture */}
        <div style={{ position: "absolute", inset: 0, opacity: 0.18, backgroundImage: "radial-gradient(#000 1.2px, transparent 1.6px)", backgroundSize: "14px 14px" }} />
        <svg width={W} height={H} style={{ position: "absolute", inset: 0, opacity: (1 - rays) * 0.9 }}>
          {Array.from({ length: 22 }, (_, i) => {
            const ang = (i / 22) * Math.PI * 2 + 0.3;
            const r0 = Math.min(W, H) * (0.3 + rays * 0.5);
            const r1 = r0 + Math.min(W, H) * (0.1 + (i % 3) * 0.05);
            return (
              <line key={i} x1={W / 2 + Math.cos(ang) * r0} y1={H / 2 + Math.sin(ang) * r0} x2={W / 2 + Math.cos(ang) * r1} y2={H / 2 + Math.sin(ang) * r1} stroke="#000" strokeWidth={i % 2 ? 8 : 16} />
            );
          })}
        </svg>
        <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
          <div style={{ ...nahText(size, 900, size, -0.07, "#000"), scale: String(stamp), rotate: "-5deg", whiteSpace: "nowrap" }}>{word(v)}</div>
        </div>
      </div>
      <div style={{ position: "absolute", inset: 0, background: `rgba(255,255,255,${flash})` }} />
    </div>
  );
};

/** A soft glow in the verdict's colour behind the phone while a verdict is up, pulsing on the beat. */
export const VerdictGlow: React.FC<{ t: number; x: number; y: number; r: number }> = ({ t, x, y, r }) => {
  const hit = impactAt(t);
  if (!hit || hit.u < 0) return null;
  const beat = 60 / timeline.bpm;
  const pulse = 0.75 + 0.25 * Math.exp(-((t - timeline.musicInSec) % beat) * 6);
  const end = hit.scene[1];
  const a = interpolate(t, [hit.scene[0] + cue.verdictInSec, hit.scene[0] + cue.verdictInSec + 0.3, end - 0.35, end], [0, 1, 1, 0], clamp) * pulse;
  const c = VerdictColor[hit.e.verdict];
  return (
    <div
      style={{
        position: "absolute",
        left: x - r,
        top: y - r,
        width: r * 2,
        height: r * 2,
        borderRadius: "50%",
        background: `radial-gradient(circle, ${c}66 0%, ${c}22 40%, transparent 70%)`,
        opacity: a,
      }}
    />
  );
};

/** Film grain: fine moving noise over everything. */
export const Grain: React.FC<{ frame: number; W: number; H: number }> = ({ frame, W, H }) => (
  <svg width={W} height={H} style={{ position: "absolute", inset: 0, opacity: 0.09, mixBlendMode: "overlay", pointerEvents: "none" }}>
    <filter id="grain">
      <feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves={2} seed={frame % 12} stitchTiles="stitch" />
      <feColorMatrix type="saturate" values="0" />
    </filter>
    <rect width={W} height={H} filter="url(#grain)" />
  </svg>
);

/** Dark at the edges, so the eye goes to the middle. */
export const Vignette: React.FC = () => (
  <div style={{ position: "absolute", inset: 0, background: "radial-gradient(ellipse at center, transparent 55%, rgba(0,0,0,0.65) 100%)", pointerEvents: "none" }} />
);

export { PHONE_H };
