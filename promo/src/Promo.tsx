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
import { Stop, Wordmark } from "./ui";
import { CamLayout, Grain, VerdictSlam, Vignette, camera, shakeAt } from "./fx";
import { Drive } from "./Drive";

export type Aspect = "vertical" | "wide";

const S = timeline.scenes;
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const outExpo = Easing.bezier(0.16, 1, 0.3, 1);

type Box = { x: number; y: number; w: number; h: number };
type Layout = CamLayout & {
  W: number;
  H: number;
  caption: Box & { size: number; align: "left" | "center" };
  chip: { x: number; y: number; align: "left" | "center"; scale: number };
  /** Where the maths the courier would have to do floats, in the pain scene. */
  math: { x: number; y: number; size: number; align: "left" | "center" };
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
    math: { x: 540, y: 640, size: 112, align: "center" },
    pad: 80,
  },
  wide: {
    W: 1920,
    H: 1080,
    phone: { cx: 560, top: 70, scale: 940 / PHONE_H },
    zoomQ: { x: 500, y: 560 },
    caption: { x: 1000, y: 260, w: 860, h: 400, size: 110, align: "left" },
    chip: { x: 1000, y: 740, align: "left", scale: 1.3 },
    math: { x: 1000, y: 640, size: 96, align: "left" },
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
 * Scene 2 (the pain): the maths the courier would have to do in 15 seconds, while driving, floats
 * over the offer term by term; the answer won't settle. It shatters when the verdict lands.
 */
const MathOverlay: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const [p0, p1] = S.pain;
  if (t < p0 || t > p1 + 0.2) return null;
  const m = L.math;
  const terms = ["$6.07", "÷ 23 min", "× 60", "="];
  const termAt = (i: number) => p0 + 0.2 + i * 0.4;
  const out = interpolate(t, [p1 - 0.05, p1 + 0.15], [0, 1], clamp);
  // The answer keeps flickering: nobody can work it out this fast.
  let sd = Math.floor(t * 15) * 7919 + 3;
  const dg = () => String((sd = (sd * 16807 + 11) % 2147483647) % 10);
  const answerOn = t > termAt(4) - 0.1;
  const jitter = (k: number) => Math.sin(t * 23 + k * 2.1) * 3;
  return (
    <div
      style={{
        position: "absolute",
        left: m.align === "center" ? 0 : m.x,
        right: m.align === "center" ? 0 : undefined,
        top: m.y,
        display: "flex",
        flexWrap: "wrap",
        justifyContent: m.align === "center" ? "center" : "flex-start",
        alignItems: "baseline",
        gap: `0 ${m.size * 0.28}px`,
        padding: `0 ${L.pad}px`,
        opacity: 1 - out,
        scale: String(1 + out * 0.4),
        filter: `blur(${out * 16}px)`,
      }}
    >
      {terms.map((term, i) => {
        const p = interpolate(t, [termAt(i), termAt(i) + 0.12], [0, 1], { ...clamp, easing: Easing.out(Easing.cubic) });
        return (
          <span key={term} style={{ ...nahText(m.size, 900, m.size * 1.1, -0.04, Mono.ink), opacity: p, scale: String(interpolate(p, [0, 1], [1.8, 1])), translate: `${jitter(i)}px ${jitter(i + 5)}px`, textShadow: "0 4px 30px #000, 0 0 12px #000" }}>
            {term}
          </span>
        );
      })}
      {answerOn && (
        <span style={{ ...nahText(m.size, 900, m.size * 1.1, -0.04, Mono.inkMuted), textShadow: "0 4px 30px #000" }}>
          ${dg()}
          {dg()}.{dg()}?
        </span>
      )}
    </div>
  );
};

/**
 * The spoken verdict: a waveform drawn from the real clip's loudness (src/voice.json, written by
 * scripts/make-audio.mjs) scrolling while it plays, and the verdict word.
 */
const SpeechChip: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  const active = OFFER_SCENES.find((o) => t >= o.speechIn - 0.1 && t < o.scene[1]);
  if (!active) return null;
  const local = t - active.speechIn;
  const inP = spring({ frame: Math.max(0, (local + 0.1) * fps), fps, config: { damping: 11, stiffness: 160 } });
  const out = interpolate(t, [active.scene[1] - 0.25, active.scene[1]], [0, 1], clamp);
  const clip = (voice as Record<string, { durationSec: number; envelope: number[] }>)[active.id];
  const level = (k: number) => clip?.envelope[Math.floor(local * fps) - k] ?? 0;
  // Bigger when the voice is the point of the scene ("It says it out loud").
  const s = L.chip.scale * (active.id === "voice" ? 1.45 : 1);
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

/** Captions beside (wide) or above (vertical) the phone: what each moment means, in plain words. */
const Captions: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const c = L.caption;
  const wide = L.W > L.H;
  const sub = (text: string) => (
    <div style={{ ...nahText(wide ? 44 : 46, 600, wide ? 56 : 58, -0.01, "#D4D4D4"), textAlign: c.align, maxWidth: c.w, textShadow: "0 4px 24px #000" }}>{text}</div>
  );
  const verdictLine = (o: (typeof OFFER_SCENES)[number]) => (
    <span style={{ color: VerdictColor[o.e.verdict] }}>{word(o.e.verdict)}</span>
  );
  const answers = OFFER_SCENES.filter((o) => !o.timer && o.id !== "voice");
  const voiceOffer = OFFER_SCENES.find((o) => o.id === "voice");
  const items: { from: number; to: number; lines: React.ReactNode[]; extra?: React.ReactNode; extraAt?: number }[] = [
    { from: timeline.hookLinesSec[0], to: timeline.hookLinesSec[2] - 0.1, lines: ["New offer.", "15 seconds."] },
    { from: timeline.hookLinesSec[2], to: S.hook[1], lines: ["Is it worth it?"] },
    { from: S.pain[0] + 0.1, to: S.pain[1], lines: ["Doing the math", "while driving?"] },
    {
      from: S.answer[0] + 0.45,
      to: S.answer[1],
      lines: ["nah.meter does", "the math for you."],
      extra: sub("It reads every offer and answers in one word."),
      extraAt: S.answer[0] + 1.5,
    },
    // The three answers, each explained.
    ...answers.map((o) => ({ from: o.verdictIn + 0.3, to: o.scene[1], lines: [verdictLine(o), ...MEANING[o.e.verdict].split(/(?<=,) /)] })),
    ...(voiceOffer
      ? [{ from: voiceOffer.verdictIn + 0.4, to: voiceOffer.scene[1], lines: ["It says it", "out loud."], extra: sub("Eyes stay on the road."), extraAt: voiceOffer.verdictIn + 1.1 }]
      : []),
    { from: S.rules[0] + 0.3, to: S.rules[1], lines: ["You set", "the numbers."], extra: sub("Minimum pay, $ per hour, $ per km."), extraAt: S.rules[0] + 1.0 },
  ];
  return (
    <>
      {items.map((it, i) => {
        if (t < it.from - 0.1 || t > it.to + 0.1) return null;
        const exAt = it.extraAt ?? it.from + 0.3;
        const extraIn = spring({ frame: Math.max(0, (t - exAt) * 30), fps: 30, config: { damping: 14 } });
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
              gap: 28,
            }}
          >
            <SlamLines t={t} lines={it.lines} at={it.lines.map((_, j) => it.from + j * 0.25)} outAt={it.to} size={it.lines.length > 2 ? c.size * 0.85 : c.size} align={c.align} />
            {it.extra && t >= exAt && <div style={{ opacity: Math.min(1, extraIn) * (1 - extraOut), translate: `0 ${(1 - Math.min(1, extraIn)) * 24}px` }}>{it.extra}</div>}
          </div>
        );
      })}
    </>
  );
};

/**
 * The night drive and the phone in its dash mount, under the camera, from the first frame. The
 * phone sits tilted in 3D, squares up as the camera moves in, and leaves after the rules.
 */
const PhoneStage: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  if (t > S.rules[1] + 0.6) return null;
  const fadeIn = interpolate(t, [0, 0.3], [0, 1], clamp);
  const out = interpolate(t, [S.rules[1] - 0.2, S.rules[1] + 0.35], [0, 1], { ...clamp, easing: Easing.in(Easing.cubic) });
  const cam = camera(L, t, fps);
  const p = L.phone;
  const tilt = 1 - interpolate(cam.zoom, [1, 1.35], [0, 1], clamp);
  const glare = ((t * 0.35) % 1.6) - 0.3; // a slow light across the glass
  return (
    <>
      <div style={{ position: "absolute", inset: 0, opacity: (1 - out) * fadeIn }}>
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
            translate: `0 ${out * L.H}px`,
            transform: `perspective(2400px) rotateY(${-11 * tilt}deg) rotateX(${6 * tilt}deg) rotateZ(${-out * 10}deg)`,
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
  if (L.W > L.H || t > S.rules[1]) return null;
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
 * Can I trust it: three promises, one at a time and full screen, each knocking the last away.
 */
const Trust: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const [t0, t1] = S.trust;
  if (t < t0 || t > t1 + 0.1) return null;
  const { fps } = useVideoConfig();
  const at = timeline.trustLinesSec;
  const wide = L.W > L.H;
  const lines: [string, string?][] = [["It never taps anything.", "You decide."], ["No internet.", "Nothing leaves your phone."], ["No account."]];
  const out = interpolate(t, [t1 - 0.25, t1], [0, 1], clamp);
  return (
    <AbsoluteFill style={{ opacity: 1 - out }}>
      {lines.map(([line, second], i) => {
        const next = at[i + 1] ?? Infinity;
        if (t < at[i] - 0.05 || t > next + 0.35) return null;
        const inP = spring({ frame: Math.max(0, (t - at[i]) * fps), fps, config: { damping: 14, stiffness: 170 } });
        const leave = Number.isFinite(next) ? interpolate(t, [next, next + 0.3], [0, 1], { ...clamp, easing: Easing.in(Easing.cubic) }) : 0;
        const secondIn = interpolate(t, [at[i] + 0.45, at[i] + 0.7], [0, 1], { ...clamp, easing: outExpo });
        const size = wide ? 120 : 116;
        return (
          <AbsoluteFill
            key={i}
            style={{
              alignItems: "center",
              justifyContent: "center",
              padding: `0 ${L.pad}px`,
              opacity: Math.min(1, inP * 1.5) * (1 - leave),
              translate: `0 ${-leave * L.H * 0.3}px`,
              scale: String(interpolate(inP, [0, 1], [2, 1]) * (1 - leave * 0.25)),
              filter: `blur(${(1 - Math.min(1, inP)) * 16 + leave * 12}px)`,
            }}
          >
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: size * 0.3, textAlign: "center", maxWidth: wide ? 1500 : 940 }}>
              <Stop size={size * 0.36} style={{ rotate: `${(1 - Math.min(1, inP)) * 90}deg` }} />
              <div style={nahText(size, 900, size * 1.05, -0.05, Mono.ink)}>{line}</div>
              {second && <div style={{ ...nahText(size * 0.55, 700, size * 0.65, -0.02, "#D4D4D4"), opacity: secondIn, translate: `0 ${(1 - secondIn) * 20}px` }}>{second}</div>}
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
      <PhoneStage t={t} L={L} />
      <CaptionShade t={t} L={L} />
      <MathOverlay t={t} L={L} />
      <Captions t={t} L={L} />
      <SpeechChip t={t} L={L} />
      <Trust t={t} L={L} />
      <End t={t} L={L} />
      <VerdictSlam t={t} W={L.W} H={L.H} cx={L.zoomQ.x} cy={L.zoomQ.y} />
      <Vignette />
      <Grain frame={frame} W={L.W} H={L.H} />
      {withAudio && <Audio src={staticFile("audio/mix.wav")} />}
    </AbsoluteFill>
  );
};
