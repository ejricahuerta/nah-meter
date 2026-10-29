// The whole promo, laid out for one aspect: "vertical" (1080×1920) or "wide" (1920×1080).
// Every time comes from timeline.json, which the audio script reads too.
import React from "react";
import { AbsoluteFill, Easing, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { Audio } from "@remotion/media";
import timeline from "./timeline.json";
import { Mono, VerdictColor, nahText } from "./tokens";
import { MEANING, speech, word } from "./nah";
import { OFFER_SCENES, PHONE_H, PHONE_W, Phone } from "./Phone";
import { StatusPill, Stop, Wordmark } from "./ui";
import { CamLayout, Grain, VerdictGlow, VerdictSlam, Vignette, camera, shakeAt } from "./fx";

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

/** Scene 1: a giant countdown ticking behind "New offer. 15 seconds. Worth it?" */
const Hook: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  if (t > S.hook[1] + 0.1) return null;
  const wide = L.W > L.H;
  const n = Math.max(10, 15 - Math.floor(t / 0.5));
  const tickP = (t % 0.5) / 0.5;
  const out = interpolate(t, [2.75, 3.0], [0, 1], clamp);
  const sh = shakeAt(t);
  return (
    <AbsoluteFill style={{ translate: `${sh.x}px ${sh.y}px`, scale: String(1 + out * 0.6), opacity: 1 - out, filter: `blur(${out * 12}px)` }}>
      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center" }}>
        <div
          style={{
            ...nahText(wide ? 980 : 900, 900, wide ? 980 : 900, -0.08, "transparent"),
            WebkitTextStroke: `4px rgba(255,255,255,${0.22 - tickP * 0.1})`,
            scale: String(1.12 - 0.12 * Math.min(1, tickP * 4)),
          }}
        >
          {n}
        </div>
      </AbsoluteFill>
      <AbsoluteFill style={{ justifyContent: "center", padding: `0 ${L.pad}px` }}>
        <SlamLines
          t={t}
          lines={["New offer.", "15 seconds.", <span key="w" style={{ color: Mono.inkMuted }}>Worth it?</span>]}
          at={timeline.hookLinesSec}
          outAt={3.5}
          size={wide ? 170 : 138}
          align="left"
        />
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

/** The spoken line: a speaker, a moving waveform while it speaks, and the words in quotes. */
const SpeechChip: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  const active = OFFER_SCENES.find(({ scene }) => t >= scene[0] + cue.speechInSec - 0.1 && t < scene[1]);
  if (!active) return null;
  const local = t - active.scene[0] - cue.speechInSec;
  const inP = spring({ frame: Math.max(0, (local + 0.1) * fps), fps, config: { damping: 11, stiffness: 160 } });
  const out = interpolate(t, [active.scene[1] - 0.25, active.scene[1]], [0, 1], clamp);
  const speaking = local > 0 && local < 1.5;
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
  const items: { from: number; to: number; lines: React.ReactNode[]; extra?: React.ReactNode }[] = [
    { from: S.shift[0] + 0.2, to: S.shift[1], lines: ["On shift."], extra: <StatusPill text="Reading offers" scale={1.7} /> },
    ...OFFER_SCENES.map(({ scene, e }) => ({
      from: scene[0] + cue.verdictInSec + 0.6,
      to: scene[1],
      lines: MEANING[e.verdict].split(/(?<=[?,]) /), // "Can't read it?" / "It won't guess."
    })),
    { from: S.rules[0] + 0.4, to: S.rules[1], lines: ["Your rules.", "Your call."] },
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

/** The phone under the camera: it rises in for the shift, zooms with each offer, leaves after the rules. */
const PhoneStage: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  if (t < S.shift[0] - 0.05 || t > S.rules[1] + 0.6) return null;
  const rise = spring({ frame: Math.max(0, (t - S.shift[0]) * fps), fps, config: { damping: 15, stiffness: 110 } });
  const out = interpolate(t, [S.rules[1] - 0.2, S.rules[1] + 0.35], [0, 1], { ...clamp, easing: Easing.in(Easing.cubic) });
  const cam = camera(L, t, fps);
  const p = L.phone;
  return (
    <AbsoluteFill style={{ transformOrigin: "0 0", transform: cam.transform, filter: cam.blur > 0.4 ? `blur(${cam.blur}px)` : undefined }}>
      <div
        style={{
          position: "absolute",
          left: p.cx - (PHONE_W * p.scale) / 2,
          top: p.top,
          width: PHONE_W * p.scale,
          height: PHONE_H * p.scale,
          translate: `0 ${(1 - rise) * L.H * 0.8 + out * L.H}px`,
          rotate: `${(1 - rise) * 12 - out * 10}deg`,
          scale: String(0.7 + 0.3 * rise),
        }}
      >
        <div style={{ transformOrigin: "top left", scale: String(p.scale) }}>
          <Phone t={t} />
        </div>
      </div>
    </AbsoluteFill>
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

/** Scene 8: the three promises slam in, each with the square full stop, then "You decide." */
const Promises: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  if (t < S.promise[0] || t > S.promise[1] + 0.1) return null;
  const { fps } = useVideoConfig();
  const at = timeline.promiseLinesSec;
  const wide = L.W > L.H;
  const lines = ["It never taps anything for you.", "No internet permission. Nothing leaves your phone.", "No account."];
  const size = wide ? 70 : 76;
  const out = interpolate(t, [S.promise[1] - 0.3, S.promise[1]], [0, 1], clamp);
  const push = interpolate(t, [S.promise[0], S.promise[1]], [1, 1.08]);
  const decide = spring({ frame: Math.max(0, (t - at[3]) * fps), fps, config: { damping: 10, stiffness: 140 } });
  return (
    <AbsoluteFill
      style={{ justifyContent: "center", padding: `0 ${L.pad}px`, opacity: 1 - out, scale: String(push + out * 0.4), filter: `blur(${out * 10}px)` }}
    >
      <div style={{ display: "flex", flexDirection: "column", gap: size * 0.7, maxWidth: wide ? 1400 : 920 }}>
        {lines.map((line, i) => {
          const p = interpolate(t, [at[i], at[i] + 0.3], [0, 1], { ...clamp, easing: outExpo });
          const sq = spring({ frame: Math.max(0, (t - at[i]) * fps), fps, config: { damping: 8, stiffness: 200 } });
          const dimmed = t > at[i + 1] ? 0.55 : 1;
          return (
            <div key={i} style={{ display: "flex", gap: size * 0.45, opacity: p * dimmed, transformOrigin: "left center", scale: String(interpolate(p, [0, 1], [1.4, 1])), filter: `blur(${(1 - p) * 14}px)` }}>
              <Stop size={size * 0.34} style={{ marginTop: size * 0.36, scale: String(sq) }} />
              <div style={nahText(size, 800, size * 1.1, -0.035, Mono.ink)}>{line}</div>
            </div>
          );
        })}
        <div style={{ marginTop: size * 0.4, opacity: Math.min(1, decide * 2), transformOrigin: "left center", scale: String(interpolate(decide, [0, 1], [2.2, 1])) }}>
          <span style={nahText(size * 2.3, 900, size * 2.3, -0.06, Mono.ink)}>You decide</span>
          <span style={{ display: "inline-block", width: size * 0.46, height: size * 0.46, marginLeft: size * 0.08, background: Mono.stop }} />
        </div>
      </div>
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
  const glowY = L.phone.top + PHONE_H * L.phone.scale * 0.6;
  return (
    <AbsoluteFill style={{ background: Mono.ground, overflow: "hidden" }}>
      <VerdictGlow t={t} x={L.phone.cx} y={glowY} r={Math.max(L.W, L.H) * 0.55} />
      <Hook t={t} L={L} />
      <PhoneStage t={t} L={L} />
      <CaptionShade t={t} L={L} />
      <Captions t={t} L={L} />
      <SpeechChip t={t} L={L} />
      <Promises t={t} L={L} />
      <End t={t} L={L} />
      <VerdictSlam t={t} W={L.W} H={L.H} />
      <Vignette />
      <Grain frame={frame} W={L.W} H={L.H} />
      {withAudio && <Audio src={staticFile("audio/mix.wav")} />}
    </AbsoluteFill>
  );
};
