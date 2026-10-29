// A plain Android phone (punch-hole camera, no brand) and what's on its screen: a neutral street
// map with the stand-in offer and nah.meter's verdict card, then nah.meter's Rules screen.
import React, { useMemo } from "react";
import { Easing, interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import timeline from "./timeline.json";
import { Mono, nahText } from "./tokens";
import { CANT_READ, DEFAULT_RULES, EXAMPLE, Evaluation, OFFERS, Offer, Rules, cents, score } from "./nah";
import { Divider, StandInOffer, StatusBar, ThresholdField, VerdictCard } from "./ui";

export const SCREEN_W = 412;
export const SCREEN_H = 892;
export const BEZEL = 14;
export const PHONE_W = SCREEN_W + BEZEL * 2;
export const PHONE_H = SCREEN_H + BEZEL * 2;

const S = timeline.scenes;
const cue = timeline.offerCue;
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;

/** One offer on screen: its scene, the offer (null = unreadable) and the verdict it gets. */
export const OFFER_SCENES: { scene: [number, number]; offer: Offer | null; e: Evaluation }[] = [
  { scene: S.nah as [number, number], offer: OFFERS.nah, e: score(OFFERS.nah, DEFAULT_RULES) },
  { scene: S.bet as [number, number], offer: OFFERS.bet, e: score(OFFERS.bet, DEFAULT_RULES) },
  { scene: S.eh as [number, number], offer: OFFERS.eh, e: score(OFFERS.eh, DEFAULT_RULES) },
  { scene: S.grey as [number, number], offer: null, e: CANT_READ },
];

/** A seeded street map: grey lines only (colour is kept for verdicts). */
const StreetMap: React.FC<{ t: number }> = ({ t }) => {
  const streets = useMemo(() => {
    let seed = 7;
    const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    const lines: { d: string; w: number }[] = [];
    for (let i = 0; i < 14; i++) {
      const y = i * 150 + rnd() * 60;
      lines.push({ d: `M-200 ${y} L1400 ${y + (rnd() - 0.5) * 220}`, w: rnd() > 0.7 ? 18 : 9 });
    }
    for (let i = 0; i < 10; i++) {
      const x = i * 140 + rnd() * 50;
      lines.push({ d: `M${x} -200 L${x + (rnd() - 0.5) * 260} 2200`, w: rnd() > 0.75 ? 18 : 9 });
    }
    lines.push({ d: "M-200 1900 L1300 300", w: 22 });
    return lines;
  }, []);
  // Slow drive north-east.
  const dx = -interpolate(t, [3, 21], [120, 520]);
  const dy = -interpolate(t, [3, 21], [700, 420]);
  return (
    <svg width={SCREEN_W} height={SCREEN_H} style={{ position: "absolute", inset: 0 }}>
      <rect width={SCREEN_W} height={SCREEN_H} fill="#0A0A0A" />
      <g transform={`translate(${dx} ${dy})`}>
        {streets.map((s, i) => (
          <path key={i} d={s.d} stroke="#262626" strokeWidth={s.w} fill="none" strokeLinecap="round" />
        ))}
        {streets.map((s, i) => (
          <path key={`c${i}`} d={s.d} stroke="#151515" strokeWidth={s.w * 0.25} fill="none" />
        ))}
      </g>
      {/* The route ahead and the courier's position. */}
      <path d={`M206 520 C 206 420, 260 380, 300 300 S 330 160, 360 60`} stroke="#FFFFFF" strokeOpacity={0.75} strokeWidth={7} fill="none" strokeLinecap="round" />
      <circle cx={206} cy={540} r={22} fill="#FFFFFF" fillOpacity={0.14} />
      <circle cx={206} cy={540} r={12} fill="#FFFFFF" stroke="#000" strokeWidth={4} />
      <rect width={SCREEN_W} height={SCREEN_H} fill="url(#vig)" />
      <defs>
        <radialGradient id="vig" cx="50%" cy="45%" r="70%">
          <stop offset="60%" stopColor="#000" stopOpacity={0} />
          <stop offset="100%" stopColor="#000" stopOpacity={0.7} />
        </radialGradient>
      </defs>
    </svg>
  );
};

const OFFER_H = 172; // StandInOffer's height in dp
const OFFER_BOTTOM = 24;
const DOCK_GAP = 20; // DOCK_GAP_DP

/** The offers, one after another: the card slides up, then the verdict card docks above it. */
const Offers: React.FC<{ t: number }> = ({ t }) => {
  const { fps } = useVideoConfig();
  return (
    <>
      {OFFER_SCENES.map(({ scene, offer, e }, i) => {
        const [start, end] = scene;
        if (t < start || t > end + 0.5) return null;
        const offerIn = spring({ frame: Math.max(0, (t - start - cue.offerInSec) * fps), fps, config: { damping: 18, stiffness: 140 } });
        const verdictIn = spring({ frame: Math.max(0, (t - start - cue.verdictInSec) * fps), fps, config: { damping: 11, stiffness: 170, mass: 0.8 } });
        const out = interpolate(t, [end - 0.3, end + 0.02], [0, 1], { ...clamp, easing: Easing.in(Easing.cubic) });
        const wordPop = interpolate(t - start - cue.verdictInSec, [0.05, 0.18, 0.4], [1, 1.12, 1], clamp);
        const offerY = (1 - offerIn) * (OFFER_H + 60) + out * (OFFER_H + 80);
        const verdictY = interpolate(verdictIn, [0, 1], [-140, 0]) + out * (OFFER_H + 260);
        return (
          <React.Fragment key={i}>
            <StandInOffer
              offer={offer}
              blur={offer ? 0 : 6}
              style={{
                position: "absolute",
                left: 12,
                right: 12,
                bottom: OFFER_BOTTOM,
                translate: `0 ${offerY}px`,
                opacity: interpolate(offerIn, [0, 0.3], [0, 1], clamp) * (1 - out),
              }}
            />
            <VerdictCard
              e={e}
              rules={DEFAULT_RULES}
              wordScale={wordPop}
              style={{
                position: "absolute",
                left: 12,
                right: 12,
                bottom: OFFER_BOTTOM + OFFER_H + DOCK_GAP,
                translate: `0 ${verdictY}px`,
                scale: String(interpolate(verdictIn, [0, 1], [0.86, 1])),
                opacity: interpolate(verdictIn, [0, 0.25], [0, 1], clamp) * (1 - out),
              }}
            />
          </React.Fragment>
        );
      })}
    </>
  );
};

/** The Rules tab: the live preview card scored on the example offer, and the steppers. */
const RulesScreen: React.FC<{ t: number }> = ({ t }) => {
  const { fps } = useVideoConfig();
  const steps = timeline.rulesStepsSec;
  const done = steps.filter((s) => t >= s).length;
  const rules: Rules = { ...DEFAULT_RULES, minHourlyCents: DEFAULT_RULES.minHourlyCents + done * 50 };
  const preview = score(EXAMPLE, rules);
  // The press on + lights just before each step lands.
  const pressed = Math.max(0, ...steps.map((s) => interpolate(t, [s - 0.12, s, s + 0.2], [0, 1, 0], clamp)));
  const pop = Math.max(0, ...steps.map((s) => spring({ frame: Math.max(0, (t - s) * fps), fps, config: { damping: 9 }, durationInFrames: 10 }) * interpolate(t, [s, s + 0.35], [1, 0], clamp)));
  return (
    <div style={{ position: "absolute", inset: 0, background: Mono.ground }}>
      <StatusBar />
      <div style={{ padding: "20px 16px 16px", display: "flex", flexDirection: "column", gap: 20 }}>
        <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          <div style={nahText(28, 800, 32, -0.035, Mono.ink)}>Rules</div>
          <div style={nahText(14, 400, 20, 0, Mono.inkMuted)}>Pay first, then $/hr, then $/km.</div>
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          <div style={nahText(14, 600, 20, 0, Mono.inkMuted)}>Live preview</div>
          <VerdictCard e={preview} rules={rules} />
          <div style={nahText(14, 400, 20, 0, Mono.inkMuted)}>
            An example offer · {cents(EXAMPLE.payCents)} · {EXAMPLE.minutes} min · 11.5 km
          </div>
        </div>
        <div style={{ background: Mono.surface, borderRadius: 14, overflow: "hidden" }}>
          <ThresholdField label="Minimum pay" value={cents(rules.minPayCents)} unit={null} hint="Below this is always nah." />
          <Divider />
          <ThresholdField
            label="Per hour"
            value={cents(rules.minHourlyCents)}
            unit="/hr"
            hint="Under this is nah."
            plusPressed={pressed}
            valuePop={pop}
          />
          <Divider />
          <ThresholdField label="Per km" value={cents(rules.minPerKmCents)} unit="/km" hint="Under this is eh." />
        </div>
      </div>
      <TabBar selected={2} />
    </div>
  );
};

const TABS: { label: string; paths: string[] }[] = [
  { label: "Home", paths: ["M4 17a8 8 0 0 1 16 0", "M12 17l4-5"] },
  { label: "History", paths: ["M8 6h12M8 12h12M8 18h12", "M4 6h.01M4 12h.01M4 18h.01"] },
  {
    label: "Rules",
    paths: ["M4 7h10M18 7h2M4 17h4M12 17h8", "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0"],
  },
  { label: "Settings", paths: [
      "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z",
      "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z",
    ] },
];

/** TabBar (MainActivity.kt): four tabs under a hairline, the current one in ink. */
const TabBar: React.FC<{ selected: number }> = ({ selected }) => (
  <div style={{ position: "absolute", left: 0, right: 0, bottom: 0, borderTop: `1px solid ${Mono.line}`, background: Mono.ground, padding: "6px 8px 22px", display: "flex" }}>
    {TABS.map((tab, i) => {
      const color = i === selected ? Mono.ink : Mono.inkMuted;
      return (
        <div key={tab.label} style={{ flex: 1, minHeight: 56, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 4 }}>
          <svg width={24} height={24} viewBox="0 0 24 24" fill="none">
            {tab.paths.map((d) => (
              <path key={d} d={d} stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
            ))}
          </svg>
          <div style={nahText(12, 600, 16, 0, color)}>{tab.label}</div>
        </div>
      );
    })}
  </div>
);

/** The phone with its screen; `t` is the video time in seconds. */
export const Phone: React.FC<{ t: number }> = ({ t }) => {
  const rulesIn = interpolate(t, [S.rules[0] - 0.05, S.rules[0] + 0.45], [0, 1], { ...clamp, easing: Easing.bezier(0.16, 1, 0.3, 1) });
  return (
    <div
      style={{
        width: PHONE_W,
        height: PHONE_H,
        borderRadius: 62,
        background: "#141414",
        padding: BEZEL,
        boxShadow: "0 0 0 2px #2E2E2E, 0 40px 120px rgba(0,0,0,0.8), inset 0 0 0 1px #3A3A3A",
        position: "relative",
      }}
    >
      <div style={{ width: SCREEN_W, height: SCREEN_H, borderRadius: 48, overflow: "hidden", position: "relative", background: "#000" }}>
        {rulesIn < 1 && (
          <div style={{ position: "absolute", inset: 0, translate: `${-rulesIn * 120}px 0`, opacity: 1 - rulesIn * 0.6 }}>
            <StreetMap t={t} />
            <div style={{ position: "absolute", top: 0, left: 0, right: 0, background: "linear-gradient(#000c, #0000)" }}>
              <StatusBar />
            </div>
            <Offers t={t} />
          </div>
        )}
        {rulesIn > 0 && (
          <div style={{ position: "absolute", inset: 0, translate: `${(1 - rulesIn) * SCREEN_W}px 0` }}>
            <RulesScreen t={t} />
          </div>
        )}
        {/* Punch-hole camera */}
        <div style={{ position: "absolute", top: 12, left: SCREEN_W / 2 - 9, width: 18, height: 18, borderRadius: 9, background: "#050505", boxShadow: "inset 0 0 0 2px #1d1d1d" }} />
      </div>
    </div>
  );
};
