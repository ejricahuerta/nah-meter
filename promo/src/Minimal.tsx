// The promo, clean and minimal: white and black grounds, one big offer card, the verdict card
// snapping onto it, few words, hard cuts on the beat. Colour only on verdict words.
// Laid out for "vertical" (1080×1920) or "wide" (1920×1080). Times come from timeline.json.
import React from "react";
import { AbsoluteFill, Easing, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { Audio } from "@remotion/media";
import timeline from "./timeline.json";
import { Card, Daylight, Mono, VerdictColor, nahText } from "./tokens";
import { DEFAULT_RULES, cents } from "./nah";
import { OFFER_SCENES, OfferScene } from "./offers";
import voice from "./voice.json";
import { StandInOffer, VerdictCard, Wordmark } from "./ui";

export type Aspect = "vertical" | "wide";

const S = timeline.scenes;
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;

type Layout = {
  W: number;
  H: number;
  /** Scale of the cards (they're drawn in dp). */
  k: number;
  /** Centre x and top of the card stack. */
  stackX: number;
  stackTop: number;
  /** Where the words under the stack sit. */
  wordsY: number;
  text: number;
};

const LAYOUTS: Record<"vertical" | "wide", Layout> = {
  vertical: { W: 1080, H: 1920, k: 2.35, stackX: 540, stackTop: 560, wordsY: 1420, text: 104 },
  wide: { W: 1920, H: 1080, k: 2.0, stackX: 960, stackTop: 150, wordsY: 840, text: 96 },
};

const CARD_W = 388; // dp: the cards are 12dp in from a 412dp screen
const VERDICT_H = 130;
const DOCK_GAP = 20;

/** A fast, clean entrance: 0 → 1 with a little settle. */
const useSnap = () => {
  const { fps } = useVideoConfig();
  return (t: number, at: number) => spring({ frame: Math.max(0, (t - at) * fps), fps, config: { damping: 18, stiffness: 260, mass: 0.7 } });
};

/** Words that snap in at `at`. */
const Words: React.FC<{ t: number; at: number; size: number; color: string; children: React.ReactNode; style?: React.CSSProperties }> = ({
  t,
  at,
  size,
  color,
  children,
  style,
}) => {
  const snap = useSnap();
  if (t < at) return null;
  const p = snap(t, at);
  return (
    <div
      style={{
        ...nahText(size, 900, size * 1.05, -0.05, color),
        textAlign: "center",
        opacity: Math.min(1, p * 1.6),
        scale: String(interpolate(p, [0, 1], [1.08, 1])),
        ...style,
      }}
    >
      {children}
    </div>
  );
};

/** The offer card with the verdict card docking above it, scaled up from dp. */
const Stack: React.FC<{ t: number; o: OfferScene; L: Layout; question?: React.ReactNode }> = ({ t, o, L, question }) => {
  const snap = useSnap();
  const offerP = snap(t, o.offerIn);
  const verdictP = t >= o.verdictIn ? snap(t, o.verdictIn) : 0;
  const wordPop = interpolate(t - o.verdictIn, [0.02, 0.12, 0.3], [1, 1.1, 1], clamp);
  return (
    <div style={{ position: "absolute", left: L.stackX - (CARD_W * L.k) / 2, top: L.stackTop, width: CARD_W, transformOrigin: "top left", scale: String(L.k) }}>
      {/* The verdict's place: the question first, then the card snapping down onto it */}
      <div style={{ position: "relative", height: VERDICT_H, marginBottom: DOCK_GAP }}>
        {question && verdictP < 0.5 && (
          <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", opacity: 1 - verdictP * 2 }}>{question}</div>
        )}
        {t >= o.verdictIn && (
          <VerdictCard
            e={o.e}
            rules={DEFAULT_RULES}
            wordScale={wordPop}
            style={{
              position: "absolute",
              inset: 0,
              translate: `0 ${(1 - verdictP) * -70}px`,
              scale: String(interpolate(verdictP, [0, 1], [1.12, 1])),
              opacity: Math.min(1, verdictP * 2),
              boxShadow: "0 24px 50px rgba(0,0,0,0.28)",
            }}
          />
        )}
      </div>
      <StandInOffer
        offer={o.offer}
        timerLeft={o.timer ? 15 - (t - o.offerIn) : undefined}
        style={{
          translate: `0 ${(1 - offerP) * 40}px`,
          opacity: Math.min(1, offerP * 1.6),
          boxShadow: "0 24px 50px rgba(0,0,0,0.22)",
        }}
      />
    </div>
  );
};

/** Says it out loud: the verdict card big on black, and a waveform drawn from the real clip. */
const Loud: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  const snap = useSnap();
  const o = OFFER_SCENES.find((x) => x.id === "voice")!;
  const clip = (voice as Record<string, { envelope: number[] }>)[o.id];
  const local = t - o.speechIn;
  const level = (k: number) => clip?.envelope[Math.floor(local * fps) - k] ?? 0;
  const bars = L.W > L.H ? 44 : 30;
  const p = snap(t, S.loud[0]);
  const k = L.k * 1.05;
  return (
    <AbsoluteFill style={{ background: Mono.ground }}>
      <Words t={t} at={S.loud[0] + 0.15} size={L.text} color={Mono.ink} style={{ position: "absolute", left: 0, right: 0, top: L.W > L.H ? 110 : 330 }}>
        Says it out loud.
      </Words>
      <div style={{ position: "absolute", left: L.stackX - (CARD_W * k) / 2, top: L.W > L.H ? 300 : 700, width: CARD_W, transformOrigin: "top left", scale: String(k * interpolate(p, [0, 1], [0.94, 1])), opacity: p }}>
        <VerdictCard e={o.e} rules={DEFAULT_RULES} style={{ border: `1px solid ${Card.cardEdge}` }} />
      </div>
      <div style={{ position: "absolute", left: 0, right: 0, top: L.W > L.H ? 730 : 1220, height: L.W > L.H ? 180 : 260, display: "flex", alignItems: "center", justifyContent: "center", gap: L.W > L.H ? 12 : 14 }}>
        {Array.from({ length: bars }, (_, i) => {
          // The newest loudness in the middle, spreading out to both sides.
          const d = Math.abs(i - (bars - 1) / 2);
          const h = 0.06 + 0.94 * level(Math.round(d / 1.5)) * (1 - d / bars);
          return <div key={i} style={{ width: L.W > L.H ? 12 : 14, height: `${h * 100}%`, borderRadius: 8, background: i % 6 === 0 ? VerdictColor[o.e.verdict] : Mono.ink }} />;
        })}
      </div>
    </AbsoluteFill>
  );
};

/** Your rules: the Per hour field, huge, stepping up with taps on +. */
const RulesField: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const snap = useSnap();
  const steps = timeline.rulesStepsSec;
  const done = steps.filter((s) => t >= s).length;
  const value = cents(DEFAULT_RULES.minHourlyCents + done * 50);
  const pressed = Math.max(0, ...steps.map((s) => interpolate(t, [s - 0.12, s, s + 0.18], [0, 1, 0], clamp)));
  const pop = Math.max(0, ...steps.map((s) => interpolate(t, [s, s + 0.08, s + 0.3], [0, 1, 0], clamp)));
  const wide = L.W > L.H;
  const big = wide ? 230 : 210;
  const btn = wide ? 150 : 160;
  const p = snap(t, S.rules[0] + 0.1);
  const button = (plus: boolean) => {
    const on = plus ? pressed : 0;
    return (
      <div
        style={{
          width: btn,
          height: btn,
          borderRadius: btn / 2,
          background: on > 0.4 ? Daylight.ink : Daylight.surfaceRaised,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          scale: String(1 - on * 0.08),
        }}
      >
        <svg width={btn * 0.42} height={btn * 0.42} viewBox="0 0 24 24">
          <path d={plus ? "M5 12h14M12 5v14" : "M5 12h14"} stroke={on > 0.4 ? "#fff" : Daylight.ink} strokeWidth={2.6} strokeLinecap="round" />
        </svg>
      </div>
    );
  };
  return (
    <AbsoluteFill style={{ background: Daylight.ground, alignItems: "center", justifyContent: "center", gap: wide ? 60 : 110 }}>
      <Words t={t} at={S.rules[0] + 0.1} size={L.text} color={Daylight.ink}>
        Your rules.
      </Words>
      <div style={{ opacity: p, scale: String(interpolate(p, [0, 1], [0.95, 1])), display: "flex", flexDirection: wide ? "row" : "column", alignItems: "center", gap: wide ? 80 : 70 }}>
        <div style={{ display: "flex", flexDirection: "column", alignItems: wide ? "flex-start" : "center", gap: 6 }}>
          <div style={nahText(wide ? 46 : 50, 600, 60, 0, Daylight.inkMuted)}>Per hour</div>
          <div style={{ ...nahText(big, 800, big, -0.04, Daylight.ink), scale: String(1 + pop * 0.06), transformOrigin: wide ? "left center" : "center" }}>
            {value}
            <span style={{ fontSize: big * 0.3, fontWeight: 600, letterSpacing: 0, color: Daylight.inkMuted }}>/hr</span>
          </div>
        </div>
        <div style={{ display: "flex", gap: 36 }}>
          {button(false)}
          {button(true)}
        </div>
      </div>
    </AbsoluteFill>
  );
};

/** Two promises, one after the other, on black. */
const Trust: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const [a, b] = timeline.trustLinesSec;
  const size = L.W > L.H ? 130 : 120;
  return (
    <AbsoluteFill style={{ background: Mono.ground, alignItems: "center", justifyContent: "center", padding: "0 80px" }}>
      {t < b ? (
        <Words t={t} at={a} size={size} color={Mono.ink}>
          Never taps
          <br />
          for you.
        </Words>
      ) : (
        <Words t={t} at={b} size={size} color={Mono.ink}>
          No internet.
          <br />
          No account.
        </Words>
      )}
    </AbsoluteFill>
  );
};

/** The end: the wordmark, the red square landing, the tagline. */
const End: React.FC<{ t: number; L: Layout }> = ({ t, L }) => {
  const { fps } = useVideoConfig();
  const snap = useSnap();
  const e = timeline.endCue;
  const wide = L.W > L.H;
  const w = snap(t, e.wordSec);
  const stop = spring({ frame: Math.max(0, (t - e.stopSec) * fps), fps, config: { damping: 9, stiffness: 200 } });
  const tag = snap(t, e.taglineSec);
  const fade = interpolate(t, [e.fadeOutSec[0], e.fadeOutSec[1]], [1, 0], clamp);
  const size = wide ? 190 : 176;
  const tagSize = wide ? 70 : 68;
  return (
    <AbsoluteFill style={{ background: Daylight.ground, alignItems: "center", justifyContent: "center", gap: wide ? 46 : 60, opacity: fade }}>
      <div style={{ opacity: w, scale: String(interpolate(w, [0, 1], [1.1, 1])) }}>
        <Wordmark size={size} ink={Daylight.ink} stop={Daylight.stop} stopScale={t < e.stopSec ? 0 : stop} stopDrop={(1 - Math.min(1, stop)) * -90} />
      </div>
      <div style={{ ...nahText(tagSize, 900, tagSize * 1.1, -0.05, Daylight.ink), opacity: tag, translate: `0 ${(1 - tag) * 20}px`, textAlign: "center" }}>
        Bad offer? Just say nah
        <span style={{ display: "inline-block", width: tagSize * 0.2, height: tagSize * 0.2, marginLeft: tagSize * 0.04, background: Daylight.stop }} />
      </div>
      <div style={{ position: "absolute", bottom: wide ? 70 : 150, ...nahText(36, 600, 44, 0, Daylight.inkMuted), opacity: interpolate(t, [e.taglineSec + 0.5, e.taglineSec + 0.8], [0, 1], clamp) }}>
        For Android
      </div>
    </AbsoluteFill>
  );
};

export const Minimal: React.FC<{ aspect: Aspect; withAudio?: boolean }> = ({ aspect, withAudio = true }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const t = frame / fps;
  const L = LAYOUTS[aspect];
  const inScene = (k: keyof typeof S) => t >= S[k][0] && t < S[k][1];
  const hero = OFFER_SCENES.find((o) => o.timer)!;
  const quick = OFFER_SCENES.find((o) => !o.timer && o.id !== "voice" && t >= o.from && t < o.until);
  const ask = <div style={nahText(56, 900, 60, -0.05, Daylight.ink)}>Worth it?</div>;
  return (
    <AbsoluteFill style={{ background: Daylight.ground, overflow: "hidden" }}>
      {(inScene("worth") || inScene("answer")) && (
        <>
          <Stack t={t} o={hero} L={L} question={t >= timeline.hookLinesSec[0] ? ask : undefined} />
          <Words t={t} at={S.answer[0] + 0.35} size={L.text * 0.8} color={Daylight.ink} style={{ position: "absolute", left: 0, right: 0, top: L.wordsY }}>
            nah.meter does the math.
          </Words>
        </>
      )}
      {inScene("quick") && quick && <Stack key={quick.id} t={t} o={quick} L={L} />}
      {inScene("loud") && <Loud t={t} L={L} />}
      {inScene("rules") && <RulesField t={t} L={L} />}
      {inScene("trust") && <Trust t={t} L={L} />}
      {t >= S.end[0] && <End t={t} L={L} />}
      {withAudio && <Audio src={staticFile("audio/mix.wav")} />}
    </AbsoluteFill>
  );
};
