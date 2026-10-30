// The whole promo, laid out for one aspect: "vertical" (1080×1920) or "wide" (1920×1080).
// Every time comes from timeline.json, which the audio script reads too.
import React from "react";
import { AbsoluteFill, Easing, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { Audio } from "@remotion/media";
import timeline from "./timeline.json";
import { Mono, VerdictColor, nahText } from "./tokens";
import { MEANING, word } from "./nah";
import voice from "./voice.json";
import { OFFER_SCENES, PHONE_H, PHONE_W, Phone } from "./Phone";
import { StatusPill, Stop, Wordmark } from "./ui";
import { CamLayout, Grain, VerdictSlam, Vignette, camera, shakeAt } from "./fx";
import { Drive } from "./Drive";

export type Aspect = "vertical" | "wide";

const S = timeline.scenes;
const cue = timeline.offerCue;
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const outExpo = Easing.bezier(0.16, 1, 0.3, 1);

type Box = { x: number; y: number; w: number; h: number };
type Layout = CamLayout & {
  W: number;
  H: number;
  caption: Box & { size: number; align: "left" | "center" };
  chip: { x: number; y: number; align: "left" | "center"; scale: number };
  pad: number;
};

const LAYOUTS: Record<Aspect, Layout> = {
  vertical: {
    W: 1080,
    H: 1920,
    phone: { cx: 540, top: 560, scale: 1180 / PHONE_H },
    zoomQ: { x: 540, y: 1060 },
    caption: { x: 60, y: 140, w: 960, h: 380, size: 104, align: "center" },
    chip: { x: 540, y: 1760, align: "center", scale: 1.3 },
    pad: 80,
  },
  wide: {
    W: 1920,
    H: 1080,
    phone: { cx: 560, top: 70, scale: 940 / PHONE_H },
    zoomQ: { x: 500, y: 560 },
    caption: { x: 1000, y: 260, w: 860, h: 400, size: 110, align: "left" },
    chip: { x: 1000, y: 740, align: "left", scale: 1.3 },
    pad: 130,
  },
};

/** Lines that slam in one after another (big, blurred, then sharp), from `at`, and leave at `outAt`. */
const SlamLines: React.FC<{
  t: number;
  lines: React.ReactNode[];
  at: number[];
  outAt: number;
  size: number;
  align: "left" | "center";
  color?: string;
  weight?: number;
}> = ({ t, lines, at, outAt, size, align, color = Mono.ink, weight = 900 }) => {
  const out = interpolate(t, [outAt - 0.22, outAt], [0, 1], { ...clamp, easing: Easing.in(Easing.cubic) });
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: align === "center" ? "center" : "flex-start", gap: size * 0.02 }}>
      {lines.map((line, i) => {
        const p = interpolate(t, [at[i], at[i] + 0.28], [0, 1], { ...clamp, easing: outExpo });
        return (
          <div
            key={i}
            style={{
              ...nahText(size, weight, size * 1.04, -0.05, color),
              textAlign: align,
              transformOrigin: align === "center" ? "center" : "left center",
              scale: String(interpolate(p, [0, 1], [1.45, 1]) * (1 + out * 0.25)),
              opacity: p * (1 - out),
              filter: `blur(${(1 - p) * 16 + out * 10}px)`,
              textShadow: "0 6px 30px rgba(0,0,0,0.8)",
            }}
          >
            {line}
          </div>
        );
      })}
    </div>
  );
};

/**
 * Scene 1: the offer's numbers slam in over a draining 15 s ring ("$6.07", "23 min", "11.5 km"),
 * then "Worth it?", then "Don't do the math." over scrambling digits.
 */
const Hook: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  if (t > S.hook[1] + 0.1) return null;
  const wide = L.W > L.H;
  const at = timeline.hookLinesSec;
  const out = interpolate(t, [S.hook[1] - 0.2, S.hook[1]], [0, 1], clamp);
  const sh = shakeAt(t);
  const R = wide ? 420 : 470;
  const left = interpolate(t, [0, S.hook[1]], [1, 0.73], clamp); // 15 s → 11 s
  const secs = Math.ceil(15 - t);
  const nums = ["$6.07", "23 min", "11.5 km"];
  const mathP = interpolate(t, [at[4] - 0.05, at[4] + 0.1], [0, 1], clamp);
  // Digits churning behind "Don't do the math."
  let sd = Math.floor(t * 30) * 7919;
  const digit = () => String((sd = (sd * 16807 + 11) % 2147483647) % 10);
  return (
    <AbsoluteFill style={{ translate: `${sh.x}px ${sh.y}px`, scale: String(1 + out * 0.5), opacity: 1 - out, filter: `blur(${out * 14}px)` }}>
      {/* The 15 s ring and its seconds */}
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", opacity: 1 - mathP }}>
        <svg width={R * 2 + 40} height={R * 2 + 40} style={{ position: "absolute" }}>
          <circle cx={R + 20} cy={R + 20} r={R} fill="none" stroke="#1F1F1F" strokeWidth={10} />
          <circle
            cx={R + 20}
            cy={R + 20}
            r={R}
            fill="none"
            stroke="#FFFFFF"
            strokeWidth={10}
            strokeDasharray={2 * Math.PI * R}
            strokeDashoffset={2 * Math.PI * R * (1 - left)}
            transform={`rotate(-90 ${R + 20} ${R + 20})`}
            strokeLinecap="round"
          />
        </svg>
        <div style={{ position: "absolute", top: wide ? 70 : 330, ...nahText(wide ? 40 : 44, 700, 48, 0, Mono.inkMuted) }}>{secs} s</div>
      </AbsoluteFill>
      {/* The numbers, then the question */}
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", gap: 6, opacity: 1 - mathP }}>
        {nums.map((n, i) => {
          const p = interpolate(t, [at[i], at[i] + 0.12], [0, 1], { ...clamp, easing: Easing.out(Easing.cubic) });
          const dim = interpolate(t, [at[3] - 0.02, at[3] + 0.12], [1, 0.1], clamp);
          return (
            <div key={n} style={{ ...nahText(wide ? 150 : 170, 900, wide ? 150 : 172, -0.05, Mono.ink), opacity: p * dim, scale: String(interpolate(p, [0, 1], [2.2, 1])), filter: `blur(${(1 - dim) * 10}px)` }}>
              {n}
            </div>
          );
        })}
        <div
          style={{
            position: "absolute",
            ...nahText(wide ? 190 : 200, 900, 200, -0.06, Mono.ink),
            opacity: interpolate(t, [at[3], at[3] + 0.1], [0, 1], clamp),
            scale: String(interpolate(t, [at[3], at[3] + 0.16], [2.6, 1], { ...clamp, easing: Easing.out(Easing.cubic) })),
            rotate: "-4deg",
            textShadow: "0 0 60px #000, 0 0 30px #000",
          }}
        >
          Worth it?
        </div>
      </AbsoluteFill>
      {/* Don't do the math. */}
      {mathP > 0 && (
        <AbsoluteFill style={{ alignItems: "center", justifyContent: "center" }}>
          <div style={{ position: "absolute", inset: 0, display: "flex", flexWrap: "wrap", alignContent: "center", justifyContent: "center", opacity: 0.16, ...nahText(wide ? 90 : 96, 800, wide ? 100 : 110, 0, Mono.ink) }}>
            {Array.from({ length: wide ? 60 : 72 }, (_, i) => (
              <span key={i} style={{ width: wide ? 120 : 130, textAlign: "center" }}>
                {["÷", "×", "=", "$"][i % 9] ?? digit()}
              </span>
            ))}
          </div>
          <div style={{ ...nahText(wide ? 160 : 150, 900, wide ? 160 : 150, -0.055, Mono.ink), textAlign: "center", padding: `0 ${L.pad}px`, scale: String(interpolate(mathP, [0, 1], [1.8, 1])) }}>
            Don't do the math
            <span style={{ display: "inline-block", width: (wide ? 160 : 150) * 0.2, height: (wide ? 160 : 150) * 0.2, marginLeft: 8, background: Mono.stop }} />
          </div>
        </AbsoluteFill>
      )}
    </AbsoluteFill>
  );
};

/**
 * The spoken verdict: a waveform drawn from the real clip's loudness (src/voice.json, written by
 * scripts/make-audio.mjs) scrolling while it plays, and the verdict word.
 */
const SpeechChip: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  const active = OFFER_SCENES.find(({ scene }) => t >= scene[0] + cue.speechInSec - 0.1 && t < scene[1]);
  if (!active) return null;
  const local = t - active.scene[0] - cue.speechInSec;
  const inP = spring({ frame: Math.max(0, (local + 0.1) * fps), fps, config: { damping: 11, stiffness: 160 } });
  const out = interpolate(t, [active.scene[1] - 0.25, active.scene[1]], [0, 1], clamp);
  const clip = (voice as Record<string, { durationSec: number; envelope: number[] }>)[active.e.verdict];
  const level = (k: number) => clip?.envelope[Math.floor(local * fps) - k] ?? 0;
  const s = L.chip.scale;
  return (
    <div
      style={{
        position: "absolute",
        left: L.chip.x,
        top: L.chip.y,
        translate: `${L.chip.align === "center" ? "-50%" : "0"} ${(1 - inP) * 40}px`,
        scale: String(0.7 + 0.3 * inP),
        opacity: Math.min(1, inP * 2) * (1 - out),
        height: 64 * s,
        borderRadius: 32 * s,
        background: "#1F1F1F",
        border: `2px solid ${VerdictColor[active.e.verdict]}`,
        boxShadow: `0 10px 40px rgba(0,0,0,0.7), 0 0 30px ${VerdictColor[active.e.verdict]}44`,
        padding: `0 ${28 * s}px 0 ${20 * s}px`,
        display: "flex",
        alignItems: "center",
        gap: 16 * s,
        whiteSpace: "nowrap",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 4 * s, height: 30 * s }}>
        {Array.from({ length: 16 }, (_, i) => {
          // Newest on the right; each bar is one frame of the clip's loudness.
          const h = 0.14 + 0.86 * level(15 - i);
          return <div key={i} style={{ width: 4 * s, height: `${h * 100}%`, borderRadius: 2 * s, background: i > 11 ? VerdictColor[active.e.verdict] : Mono.ink }} />;
        })}
      </div>
      <div style={nahText(30 * s, 900, 34 * s, -0.04, VerdictColor[active.e.verdict])}>“{word(active.e.verdict)}”</div>
    </div>
  );
};

/** Captions beside (wide) or above (vertical) the phone, one per scene. */
const Captions: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const c = L.caption;
  const items: { from: number; to: number; lines: React.ReactNode[]; extra?: React.ReactNode }[] = [
    { from: S.shift[0] + 0.2, to: S.shift[1], lines: ["On shift."], extra: <StatusPill text="Reading offers" scale={1.7} /> },
    ...OFFER_SCENES.map(({ scene, e }) => ({
      from: scene[0] + cue.verdictInSec + 0.6,
      to: scene[1],
      lines: MEANING[e.verdict].split(/(?<=[?,]) /), // "Can't read it?" / "It won't guess."
    })),
    { from: S.rules[0] + 0.4, to: timeline.rulesFlipSec - 0.15, lines: ["Your rules.", "Your call."] },
    // Strict on $/km suits busy times, when traffic stretches the minutes but not the distance.
    { from: timeline.rulesFlipSec + 0.15, to: S.rules[1], lines: ["Traffic?", "Go strict on $/km."] },
  ];
  return (
    <>
      {items.map((it, i) => {
        if (t < it.from - 0.1 || t > it.to + 0.1) return null;
        const extraIn = spring({ frame: Math.max(0, (t - it.from - 0.3) * 30), fps: 30, config: { damping: 12 } });
        const extraOut = interpolate(t, [it.to - 0.25, it.to], [0, 1], clamp);
        return (
          <div
            key={i}
            style={{
              position: "absolute",
              left: c.x,
              top: c.y,
              width: c.w,
              height: c.h,
              display: "flex",
              flexDirection: "column",
              justifyContent: "center",
              alignItems: c.align === "center" ? "center" : "flex-start",
              gap: 36,
            }}
          >
            <SlamLines
              t={t}
              lines={it.lines}
              at={it.lines.map((_, j) => it.from + j * 0.3)}
              outAt={it.to}
              size={it.lines.length > 1 ? c.size * 1.05 : c.size}
              align={c.align}
            />
            {it.extra && <div style={{ opacity: extraIn * (1 - extraOut), scale: String(0.6 + 0.4 * extraIn) }}>{it.extra}</div>}
          </div>
        );
      })}
    </>
  );
};

/**
 * The night drive and the phone in its dash mount, under the camera. The phone sits tilted in 3D,
 * squares up as the camera moves in, and leaves after the rules.
 */
const PhoneStage: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  if (t < S.shift[0] || t > S.rules[1] + 0.6) return null;
  const rise = spring({ frame: Math.max(0, (t - S.shift[0]) * fps), fps, config: { damping: 14, stiffness: 120 } });
  const out = interpolate(t, [S.rules[1] - 0.2, S.rules[1] + 0.35], [0, 1], { ...clamp, easing: Easing.in(Easing.cubic) });
  const cam = camera(L, t, fps);
  const p = L.phone;
  const tilt = 1 - interpolate(cam.zoom, [1, 1.35], [0, 1], clamp);
  const glare = ((t * 0.35) % 1.6) - 0.3; // a slow light across the glass
  return (
    <>
      <div style={{ position: "absolute", inset: 0, opacity: 1 - out }}>
        <Drive t={t} W={L.W} H={L.H} zoom={cam.zoom} />
      </div>
      <AbsoluteFill style={{ transformOrigin: "0 0", transform: cam.transform, filter: cam.blur > 0.4 ? `blur(${cam.blur}px)` : undefined }}>
        <div
          style={{
            position: "absolute",
            left: p.cx - (PHONE_W * p.scale) / 2,
            top: p.top,
            width: PHONE_W * p.scale,
            height: PHONE_H * p.scale,
            translate: `0 ${(1 - rise) * L.H * 0.7 + out * L.H}px`,
            transform: `perspective(2400px) rotateY(${-11 * tilt + (1 - rise) * 20}deg) rotateX(${6 * tilt}deg) rotateZ(${-out * 10}deg)`,
          }}
        >
          <div style={{ transformOrigin: "top left", scale: String(p.scale) }}>
            <Phone t={t} />
          </div>
          {/* Glass glare */}
          <div
            style={{
              position: "absolute",
              inset: 0,
              borderRadius: 62 * p.scale,
              background: `linear-gradient(115deg, transparent ${glare * 100 - 12}%, rgba(255,255,255,0.10) ${glare * 100}%, transparent ${glare * 100 + 12}%)`,
              pointerEvents: "none",
            }}
          />
          {/* Mount arms */}
          {[-1, 1].map((sd) => (
            <div
              key={sd}
              style={{
                position: "absolute",
                top: "55%",
                [sd < 0 ? "left" : "right"]: -18 * p.scale,
                width: 26 * p.scale,
                height: 120 * p.scale,
                borderRadius: 8 * p.scale,
                background: "linear-gradient(90deg, #1a1a1a, #2b2b2b, #111)",
              }}
            />
          ))}
        </div>
      </AbsoluteFill>
    </>
  );
};

/** Darkens the top of a vertical frame while the camera is in close, so captions read over the phone. */
const CaptionShade: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  if (L.W > L.H || t < S.shift[0] || t > S.rules[1]) return null;
  const z = camera(L, t, fps).zoom;
  return (
    <div
      style={{
        position: "absolute",
        left: 0,
        right: 0,
        top: 0,
        height: 760,
        background: "linear-gradient(rgba(0,0,0,0.92) 55%, rgba(0,0,0,0))",
        opacity: interpolate(z, [1.05, 1.3], [0, 1], clamp),
      }}
    />
  );
};

/**
 * Scene 8: the promises, one at a time and full screen. Each slams in with the red square and
 * knocks the last one up and away; "You decide." lands last, biggest.
 */
const Promises: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  if (t < S.promise[0] || t > S.promise[1] + 0.1) return null;
  const { fps } = useVideoConfig();
  const at = timeline.promiseLinesSec;
  const wide = L.W > L.H;
  const lines = ["It never taps anything for you.", "No internet permission.", "Nothing leaves your phone.", "No account.", "You decide"];
  const out = interpolate(t, [S.promise[1] - 0.25, S.promise[1]], [0, 1], clamp);
  const sh = shakeAt(t);
  return (
    <AbsoluteFill style={{ opacity: 1 - out, translate: `${sh.x}px ${sh.y}px` }}>
      {lines.map((line, i) => {
        const last = i === lines.length - 1;
        const next = at[i + 1] ?? Infinity;
        if (t < at[i] - 0.05 || t > next + 0.35) return null;
        const inP = spring({ frame: Math.max(0, (t - at[i]) * fps), fps, config: { damping: 13, stiffness: 170 } });
        const leave = Number.isFinite(next) ? interpolate(t, [next, next + 0.3], [0, 1], { ...clamp, easing: Easing.in(Easing.cubic) }) : 0;
        const size = (last ? 2 : 1) * (wide ? 110 : 112);
        return (
          <AbsoluteFill
            key={i}
            style={{
              alignItems: "center",
              justifyContent: "center",
              padding: `0 ${L.pad}px`,
              opacity: Math.min(1, inP * 1.5) * (1 - leave),
              translate: `0 ${-leave * L.H * 0.35}px`,
              scale: String(interpolate(inP, [0, 1], [2.3, 1]) * (1 - leave * 0.3)),
              filter: `blur(${(1 - Math.min(1, inP)) * 18 + leave * 12}px)`,
            }}
          >
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: size * 0.35 }}>
              {!last && <Stop size={size * 0.42} style={{ rotate: `${(1 - Math.min(1, inP)) * 90}deg` }} />}
              <div style={{ ...nahText(size, 900, size * 1.05, -0.05, Mono.ink), textAlign: "center", maxWidth: wide ? 1500 : 940 }}>
                {line}
                {last && <span style={{ display: "inline-block", width: size * 0.2, height: size * 0.2, marginLeft: size * 0.05, background: Mono.stop }} />}
              </div>
            </div>
          </AbsoluteFill>
        );
      })}
    </AbsoluteFill>
  );
};

/** Scene 9: the wordmark zooms in, the square lands with a shockwave, then the tagline word by word. */
const End: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  const e = timeline.endCue;
  if (t < S.end[0]) return null;
  const wide = L.W > L.H;
  const wIn = interpolate(t, [e.wordSec - 0.1, e.wordSec + 0.35], [0, 1], { ...clamp, easing: outExpo });
  const stop = spring({ frame: Math.max(0, (t - e.stopSec) * fps), fps, config: { damping: 8, stiffness: 180 } });
  const wave = interpolate(t, [e.stopSec, e.stopSec + 0.6], [0, 1], clamp);
  const fade = interpolate(t, [e.fadeOutSec[1] - 0.6, e.fadeOutSec[1]], [1, 0], clamp);
  const drift = interpolate(t, [e.wordSec, timeline.durationSec], [1, 1.06]);
  const sh = shakeAt(t);
  const size = wide ? 200 : 180;
  const tagWords = ["Bad", "offer?", "Just", "say", "nah"];
  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", gap: wide ? 50 : 70, opacity: fade, translate: `${sh.x}px ${sh.y}px`, scale: String(drift) }}>
      <div style={{ position: "relative", opacity: wIn, scale: String(interpolate(wIn, [0, 1], [3.2, 1])), filter: `blur(${(1 - wIn) * 24}px)` }}>
        <Wordmark size={size} stopScale={t < e.stopSec ? 0 : stop} stopDrop={(1 - Math.min(1, stop)) * -160}>
          {wave > 0 && wave < 1 && (
            <div
              style={{
                position: "absolute",
                inset: 0,
                border: `${6 * (1 - wave) + 1}px solid ${Mono.stop}`,
                scale: String(1 + wave * 14),
                opacity: 1 - wave,
              }}
            />
          )}
        </Wordmark>
      </div>
      <div style={{ display: "flex", gap: (wide ? 72 : 76) * 0.28, alignItems: "baseline", flexWrap: "wrap", justifyContent: "center", padding: "0 60px" }}>
        {tagWords.map((w, i) => {
          const p = interpolate(t, [e.taglineSec + i * 0.12, e.taglineSec + i * 0.12 + 0.25], [0, 1], { ...clamp, easing: outExpo });
          const s = wide ? 72 : 76;
          return (
            <span key={i} style={{ ...nahText(s, 900, s * 1.05, -0.06, Mono.ink), opacity: p, display: "inline-block", scale: String(interpolate(p, [0, 1], [1.6, 1])), filter: `blur(${(1 - p) * 10}px)` }}>
              {w}
              {i === tagWords.length - 1 && <span style={{ display: "inline-block", width: s * 0.2, height: s * 0.2, marginLeft: s * 0.04, background: Mono.stop }} />}
            </span>
          );
        })}
      </div>
      <div
        style={{
          position: "absolute",
          bottom: wide ? 70 : 160,
          opacity: interpolate(t, [e.taglineSec + 0.8, e.taglineSec + 1.2], [0, 1], clamp),
          ...nahText(34, 600, 40, 0, Mono.inkMuted),
        }}
      >
        For Android
      </div>
    </AbsoluteFill>
  );
};

export const Promo: React.FC<{ aspect: Aspect; withAudio?: boolean }> = ({ aspect, withAudio = true }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const t = frame / fps;
  const L = LAYOUTS[aspect];
  return (
    <AbsoluteFill style={{ background: Mono.ground, overflow: "hidden" }}>
      <Hook t={t} L={L} />
      <PhoneStage t={t} L={L} />
      <CaptionShade t={t} L={L} />
      <Captions t={t} L={L} />
      <SpeechChip t={t} L={L} />
      <Promises t={t} L={L} />
      <End t={t} L={L} />
      <VerdictSlam t={t} W={L.W} H={L.H} cx={L.zoomQ.x} cy={L.zoomQ.y} />
      <Vignette />
      <Grain frame={frame} W={L.W} H={L.H} />
      {withAudio && <Audio src={staticFile("audio/mix.wav")} />}
    </AbsoluteFill>
  );
};
