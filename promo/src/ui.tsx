// nah.meter's components, redrawn from ui/Nah.kt and ui/SetupScreen.kt. Sizes are in dp (1 css px
// = 1 dp); the video scales them up as a whole.
import React from "react";
import { Card, Mono, StandIn, VerdictColor, nahText } from "./tokens";
import { Evaluation, Offer, Rules, cardReason, cents, km, rates, word } from "./nah";

/** The square full stop: the brand. */
export const Stop: React.FC<{ size: number; color?: string; style?: React.CSSProperties }> = ({
  size,
  color = Mono.stop,
  style,
}) => <div style={{ width: size, height: size, background: color, flexShrink: 0, ...style }} />;

/** The wordmark: "nah" + the square red full stop + "meter", Geist Black, tracked −5.5%. */
export const Wordmark: React.FC<{
  size: number;
  ink?: string;
  stop?: string;
  stopScale?: number;
  stopDrop?: number;
  children?: React.ReactNode;
}> = ({ size, ink = Mono.ink, stop = Mono.stop, stopScale = 1, stopDrop = 0, children }) => {
  const style = nahText(size, 900, size, -0.055, ink);
  return (
    <div style={{ display: "flex", alignItems: "flex-end" }}>
      <span style={style}>nah</span>
      <div style={{ padding: `0 ${size * 0.05}px ${size * 0.12}px` }}>
        <div style={{ position: "relative" }}>
          <Stop size={size * 0.2} color={stop} style={{ scale: String(stopScale), translate: `0 ${stopDrop}px` }} />
          {children}
        </div>
      </div>
      <span style={style}>meter</span>
    </div>
  );
};

/** "$15.83" with a smaller, muted "/hr". */
const Rate: React.FC<{ amount: string; unit: string; unitSize: number; unitColor: string }> = ({
  amount,
  unit,
  unitSize,
  unitColor,
}) => (
  <>
    {amount}
    <span style={{ fontSize: unitSize, fontWeight: 600, letterSpacing: 0, color: unitColor }}>{unit}</span>
  </>
);

/** VerdictCard: black, the word left in its colour, the strict rate right, the reason and the other rate underneath. */
export const VerdictCard: React.FC<{ e: Evaluation; rules: Rules; wordScale?: number; style?: React.CSSProperties }> = ({
  e,
  rules,
  wordScale = 1,
  style,
}) => {
  const unknown = e.verdict === "GREY";
  // NahCopy.rates: the strict rate big beside the word, the other one in the chip.
  const r = rates(e, rules);
  const big = r?.[0];
  const chip = r?.[1] ? r[1].amount + r[1].unit : null;
  return (
    <div
      style={{
        background: Card.card,
        border: `1px solid ${Card.cardEdge}`,
        borderRadius: 22,
        padding: "18px 20px",
        display: "flex",
        flexDirection: "column",
        gap: 6,
        boxShadow: "0 18px 40px rgba(0,0,0,0.55)",
        ...style,
      }}
    >
      <div style={{ display: "flex", alignItems: "flex-end" }}>
        <div
          style={{
            flex: 1,
            transformOrigin: "left bottom",
            scale: String(wordScale),
            ...(unknown
              ? nahText(44, 900, 48, -0.05, VerdictColor.GREY)
              : nahText(64, 900, 56, -0.07, VerdictColor[e.verdict])),
          }}
        >
          {word(e.verdict)}
        </div>
        {big && (
          <div style={{ ...nahText(36, 800, 38, -0.04, "#FFFFFF"), whiteSpace: "nowrap" }}>
            <Rate amount={big.amount} unit={big.unit} unitSize={15} unitColor={Card.cardMuted} />
          </div>
        )}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <div style={{ flex: 1, ...nahText(17, 600, 22, 0, Card.cardSoft) }}>{cardReason(e, rules)}</div>
        {chip && (
          <div
            style={{
              height: 30,
              borderRadius: 15,
              background: Card.cardChip,
              padding: "0 12px",
              display: "flex",
              alignItems: "center",
              ...nahText(15, 700, 20, 0, "#FFFFFF"),
              whiteSpace: "nowrap",
            }}
          >
            {chip}
          </div>
        )}
      </div>
    </div>
  );
};

/** A plain stand-in for the delivery app's offer card (StandInOffer): only shows where the verdict sits. */
export const StandInOffer: React.FC<{ offer: Offer | null; blur?: number; timerLeft?: number; style?: React.CSSProperties }> = ({
  offer,
  blur = 0,
  timerLeft,
  style,
}) => (
  <div
    style={{
      background: StandIn.bg,
      borderRadius: 16,
      padding: 16,
      display: "flex",
      flexDirection: "column",
      gap: 6,
      boxShadow: "0 -10px 40px rgba(0,0,0,0.5)",
      position: "relative",
      ...style,
    }}
  >
    {timerLeft !== undefined && <OfferTimer left={Math.max(0, timerLeft)} />}
    <div style={{ display: "flex", flexDirection: "column", gap: 6, filter: blur ? `blur(${blur}px)` : undefined }}>
      <div style={nahText(14, 600, 20, 0, StandIn.label)}>Delivery</div>
      <div style={nahText(32, 800, 36, -0.03, "#FFFFFF")}>{offer ? cents(offer.payCents) : "$1l.5?"}</div>
      <div style={nahText(15, 500, 22, 0, "#FFFFFF")}>
        {offer ? `${offer.minutes} min (${km(offer.kmTenths)} km) total` : "2? min (1?.? km) total"}
      </div>
    </div>
    <div
      style={{
        height: 44,
        borderRadius: 10,
        background: StandIn.button,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        ...nahText(16, 700, 22, 0, "#000000"),
      }}
    >
      Match
    </div>
  </div>
);

/** The offer's 15-second timer: a draining ring with the seconds left (neutral white, no brand). */
const OfferTimer: React.FC<{ left: number }> = ({ left }) => {
  const R = 20;
  const c = 2 * Math.PI * R;
  return (
    <div style={{ position: "absolute", top: 14, right: 14, width: 52, height: 52 }}>
      <svg width={52} height={52}>
        <circle cx={26} cy={26} r={R} fill="none" stroke="#3A3A3C" strokeWidth={5} />
        <circle cx={26} cy={26} r={R} fill="none" stroke="#FFFFFF" strokeWidth={5} strokeLinecap="round" strokeDasharray={c} strokeDashoffset={c * (1 - left / 15)} transform="rotate(-90 26 26)" />
      </svg>
      <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", ...nahText(17, 800, 20, 0, "#FFFFFF") }}>
        {Math.ceil(left)}
      </div>
    </div>
  );
};
