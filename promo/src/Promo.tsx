// The whole promo, laid out for one aspect: "vertical" (1080×1920) or "wide" (1920×1080).
// Every time comes from timeline.json, which the audio script reads too.
import React from "react";
import { AbsoluteFill, Easing, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { Audio } from "@remotion/media";
import timeline from "./timeline.json";
import { Mono, VerdictColor, nahText } from "./tokens";
import { MEANING, speech, word } from "./nah";
import { OFFER_SCENES, PHONE_H, PHONE_W, Phone } from "./Phone";
import { StatusPill, Stop, StopLine, Wordmark } from "./ui";

export type Aspect = "vertical" | "wide";

const S = timeline.scenes;
const cue = timeline.offerCue;
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const outExpo = Easing.bezier(0.16, 1, 0.3, 1);

type Box = { x: number; y: number; w: number; h: number };
type Layout = {
  W: number;
  H: number;
  phone: { cx: number; top: number; scale: number };
  caption: Box & { size: number; align: "left" | "center" };
  chip: { x: number; y: number; align: "left" | "center"; scale: number };
  pad: number;
};

const LAYOUTS: Record<Aspect, Layout> = {
  vertical: {
    W: 1080,
    H: 1920,
    phone: { cx: 540, top: 560, scale: 1180 / PHONE_H },
    caption: { x: 80, y: 150, w: 920, h: 360, size: 92, align: "center" },
    chip: { x: 540, y: 1770, align: "center", scale: 1.25 },
    pad: 90,
  },
  wide: {
    W: 1920,
    H: 1080,
    phone: { cx: 600, top: 70, scale: 940 / PHONE_H },
    caption: { x: 1000, y: 300, w: 820, h: 360, size: 96, align: "left" },
    chip: { x: 1000, y: 720, align: "left", scale: 1.25 },
    pad: 140,
  },
};

/** Lines that rise in one after another, from `at` (seconds), and leave at `outAt`. */
const RiseLines: React.FC<{
  t: number;
  lines: React.ReactNode[];
  at: number[];
  outAt: number;
  size: number;
  align: "left" | "center";
  color?: string;
  weight?: number;
  gap?: number;
}> = ({ t, lines, at, outAt, size, align, color = Mono.ink, weight = 800, gap = 0.08 }) => {
  const out = interpolate(t, [outAt - 0.25, outAt], [0, 1], { ...clamp, easing: Easing.in(Easing.cubic) });
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: align === "center" ? "center" : "flex-start", gap: size * gap }}>
      {lines.map((line, i) => {
        const p = interpolate(t, [at[i], at[i] + 0.45], [0, 1], { ...clamp, easing: outExpo });
        return (
          <div key={i} style={{ overflow: "hidden", padding: `0 0 ${size * 0.12}px` }}>
            <div
              style={{
                ...nahText(size, weight, size * 1.04, -0.045, color),
                textAlign: align,
                translate: `0 ${(1 - p) * size * 1.1 - out * size * 0.6}px`,
                opacity: p * (1 - out),
              }}
            >
              {line}
            </div>
          </div>
        );
      })}
    </div>
  );
};

/** Scene 1: "New offer. 15 seconds. Worth it?" with a draining timer bar. */
const Hook: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  if (t > S.hook[1] + 0.1) return null;
  const size = L.W > L.H ? 150 : 150;
  const drain = interpolate(t, [0.25, 3.0], [1, 0], clamp);
  const out = interpolate(t, [2.75, 3.0], [0, 1], clamp);
  return (
    <AbsoluteFill style={{ justifyContent: "center", padding: `0 ${L.pad}px` }}>
      <RiseLines
        t={t}
        lines={["New offer.", "15 seconds.", <span key="w" style={{ color: Mono.inkMuted }}>Worth it?</span>]}
        at={timeline.hookLinesSec}
        outAt={3.0}
        size={size}
        align="left"
        weight={900}
      />
      <div style={{ marginTop: 50, height: 10, width: L.W - L.pad * 2, background: Mono.surfaceRaised, opacity: 1 - out }}>
        <div style={{ height: "100%", width: `${drain * 100}%`, background: Mono.ink }} />
      </div>
    </AbsoluteFill>
  );
};

/** The spoken line: a speaker, a moving waveform while it speaks, and the words in quotes. */
const SpeechChip: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  const active = OFFER_SCENES.find(({ scene }) => t >= scene[0] + cue.speechInSec - 0.1 && t < scene[1]);
  if (!active) return null;
  const local = t - active.scene[0] - cue.speechInSec;
  const inP = spring({ frame: Math.max(0, (local + 0.1) * fps), fps, config: { damping: 16 } });
  const out = interpolate(t, [active.scene[1] - 0.25, active.scene[1]], [0, 1], clamp);
  const speaking = local > 0 && local < 1.5;
  const s = L.chip.scale;
  return (
    <div
      style={{
        position: "absolute",
        left: L.chip.x,
        top: L.chip.y,
        translate: `${L.chip.align === "center" ? "-50%" : "0"} ${(1 - inP) * 30}px`,
        opacity: inP * (1 - out),
        height: 64 * s,
        borderRadius: 32 * s,
        background: Mono.surfaceRaised,
        padding: `0 ${28 * s}px 0 ${20 * s}px`,
        display: "flex",
        alignItems: "center",
        gap: 16 * s,
        whiteSpace: "nowrap",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 4 * s, height: 30 * s }}>
        {[0, 1, 2, 3, 4].map((i) => {
          const h = speaking ? 0.35 + 0.65 * Math.abs(Math.sin(t * 13 + i * 1.7) * Math.cos(t * 7.3 + i)) : 0.25;
          return <div key={i} style={{ width: 5 * s, height: `${h * 100}%`, borderRadius: 3 * s, background: Mono.ink }} />;
        })}
      </div>
      <div style={nahText(28 * s, 600, 34 * s, -0.01, Mono.ink)}>
        “<span style={{ color: VerdictColor[active.e.verdict], fontWeight: 900 }}>{word(active.e.verdict)}</span>
        {speech(active.e).slice(word(active.e.verdict).length)}”
      </div>
    </div>
  );
};

/** Captions beside (wide) or above (vertical) the phone, one per scene. */
const Captions: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const c = L.caption;
  const items: { from: number; to: number; lines: React.ReactNode[]; extra?: React.ReactNode; muted?: boolean }[] = [
    { from: S.shift[0] + 0.2, to: S.shift[1], lines: ["On shift."], extra: <StatusPill text="Reading offers" scale={1.6} /> },
    ...OFFER_SCENES.map(({ scene, e }) => ({
      from: scene[0] + cue.verdictInSec + 0.15,
      to: scene[1],
      lines: MEANING[e.verdict].split(/(?<=\?) /), // "Can't read it?" / "It won't guess."
    })),
    { from: S.rules[0] + 0.4, to: S.rules[1], lines: ["Your rules.", "Your call."] },
  ];
  return (
    <>
      {items.map((it, i) => {
        if (t < it.from - 0.1 || t > it.to + 0.1) return null;
        const extraIn = interpolate(t, [it.from + 0.3, it.from + 0.7], [0, 1], { ...clamp, easing: outExpo });
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
            <RiseLines
              t={t}
              lines={it.lines}
              at={it.lines.map((_, j) => it.from + j * 0.35)}
              outAt={it.to}
              size={it.lines.length > 1 ? c.size * 1.1 : c.size}
              align={c.align}
              weight={900}
            />
            {it.extra && <div style={{ opacity: extraIn * (1 - extraOut), translate: `0 ${(1 - extraIn) * 20}px` }}>{it.extra}</div>}
          </div>
        );
      })}
    </>
  );
};

/** The phone, rising in for the shift and leaving after the rules. */
const PhoneStage: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  if (t < S.shift[0] - 0.05 || t > S.rules[1] + 0.6) return null;
  const rise = spring({ frame: Math.max(0, (t - S.shift[0]) * fps), fps, config: { damping: 20, stiffness: 90 } });
  const out = interpolate(t, [S.rules[1] - 0.2, S.rules[1] + 0.4], [0, 1], { ...clamp, easing: Easing.in(Easing.cubic) });
  const p = L.phone;
  return (
    <div
      style={{
        position: "absolute",
        left: p.cx - (PHONE_W * p.scale) / 2,
        top: p.top,
        width: PHONE_W * p.scale,
        height: PHONE_H * p.scale,
        translate: `0 ${(1 - rise) * L.H * 0.7 + out * L.H * 0.8}px`,
        rotate: `${(1 - rise) * 6 - out * 4}deg`,
      }}
    >
      <div style={{ transformOrigin: "top left", scale: String(p.scale) }}>
        <Phone t={t} />
      </div>
    </div>
  );
};

/** Scene 8: the three promises, each with the square full stop, then "You decide." */
const Promises: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  if (t < S.promise[0] || t > S.promise[1] + 0.1) return null;
  const at = timeline.promiseLinesSec;
  const lines = ["It never taps anything for you.", "No internet permission. Nothing leaves your phone.", "No account."];
  const size = L.W > L.H ? 66 : 70;
  const out = interpolate(t, [S.promise[1] - 0.3, S.promise[1]], [0, 1], clamp);
  return (
    <AbsoluteFill style={{ justifyContent: "center", padding: `0 ${L.pad}px`, opacity: 1 - out }}>
      <div style={{ display: "flex", flexDirection: "column", gap: size * 0.75, maxWidth: L.W > L.H ? 1300 : 900 }}>
        {lines.map((line, i) => {
          const p = interpolate(t, [at[i], at[i] + 0.4], [0, 1], { ...clamp, easing: outExpo });
          return (
            <div key={i} style={{ display: "flex", gap: size * 0.45, opacity: p, translate: `${(1 - p) * 60}px 0` }}>
              <Stop size={size * 0.32} style={{ marginTop: size * 0.36, scale: String(interpolate(p, [0, 1], [0, 1])) }} />
              <div style={nahText(size, 800, size * 1.12, -0.035, Mono.ink)}>{line}</div>
            </div>
          );
        })}
        <div
          style={{
            marginTop: size * 0.5,
            opacity: interpolate(t, [at[3], at[3] + 0.3], [0, 1], clamp),
            scale: String(interpolate(t, [at[3], at[3] + 0.45], [1.25, 1], { ...clamp, easing: outExpo })),
            transformOrigin: "left center",
          }}
        >
          <StopLine text="You decide" size={size * 2} />
        </div>
      </div>
    </AbsoluteFill>
  );
};

/** Scene 9: the wordmark, the square landing last, then the tagline. */
const End: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  const e = timeline.endCue;
  if (t < S.end[0]) return null;
  const wide = L.W > L.H;
  const wIn = interpolate(t, [e.wordSec, e.wordSec + 0.5], [0, 1], { ...clamp, easing: outExpo });
  const stop = spring({ frame: Math.max(0, (t - e.stopSec) * fps), fps, config: { damping: 9, stiffness: 160 } });
  const tag = interpolate(t, [e.taglineSec, e.taglineSec + 0.5], [0, 1], { ...clamp, easing: outExpo });
  const fade = interpolate(t, [e.fadeOutSec[1] - 0.6, e.fadeOutSec[1]], [1, 0], clamp);
  return (
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", gap: wide ? 50 : 70, opacity: fade }}>
      <div style={{ opacity: wIn, translate: `0 ${(1 - wIn) * 40}px`, letterSpacing: 0 }}>
        <Wordmark size={wide ? 190 : 170} stopScale={t < e.stopSec ? 0 : stop} stopDrop={(1 - stop) * -120} />
      </div>
      <div style={{ opacity: tag, translate: `0 ${(1 - tag) * 30}px`, textAlign: "center", padding: "0 60px" }}>
        <StopLine text="Bad offer? Just say nah" size={wide ? 72 : 76} />
      </div>
      <div
        style={{
          position: "absolute",
          bottom: wide ? 70 : 160,
          opacity: interpolate(t, [e.taglineSec + 0.5, e.taglineSec + 1], [0, 1], clamp),
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
      <Captions t={t} L={L} />
      <SpeechChip t={t} L={L} />
      <Promises t={t} L={L} />
      <End t={t} L={L} />
      {withAudio && <Audio src={staticFile("audio/mix.wav")} />}
    </AbsoluteFill>
  );
};
