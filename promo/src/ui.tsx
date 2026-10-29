// nah.meter's components, redrawn from ui/Nah.kt and ui/SetupScreen.kt. Sizes are in dp: the phone
// screen is laid out at 1 css px = 1 dp and scaled up as a whole.
import React from "react";
import { Card, Mono, StandIn, VerdictColor, nahText } from "./tokens";
import { Evaluation, Offer, Rules, cardReason, cents, hourly, km, kmChip, word } from "./nah";

/** The square full stop: the brand. */
export const Stop: React.FC<{ size: number; color?: string; style?: React.CSSProperties }> = ({
  size,
  color = Mono.stop,
  style,
}) => <div style={{ width: size, height: size, background: color, flexShrink: 0, ...style }} />;

/** The wordmark: "nah" + the square red full stop + "meter", Geist Black, tracked −5.5%. */
export const Wordmark: React.FC<{ size: number; stopScale?: number; stopDrop?: number; children?: React.ReactNode }> = ({
  size,
  stopScale = 1,
  stopDrop = 0,
  children,
}) => {
  const style = nahText(size, 900, size, -0.055, Mono.ink);
  return (
    <div style={{ display: "flex", alignItems: "flex-end" }}>
      <span style={style}>nah</span>
      <div style={{ padding: `0 ${size * 0.05}px ${size * 0.12}px` }}>
        <div style={{ position: "relative" }}>
          <Stop size={size * 0.2} style={{ scale: String(stopScale), translate: `0 ${stopDrop}px` }} />
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

/** VerdictCard: black, the word left in its colour, $/hr right, the reason and $/km chip underneath. */
export const VerdictCard: React.FC<{ e: Evaluation; rules: Rules; wordScale?: number; style?: React.CSSProperties }> = ({
  e,
  rules,
  wordScale = 1,
  style,
}) => {
  const unknown = e.verdict === "GREY";
  const rate = hourly(e);
  const chip = kmChip(e);
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
        {rate && (
          <div style={{ ...nahText(36, 800, 38, -0.04, "#FFFFFF"), whiteSpace: "nowrap" }}>
            <Rate amount={rate} unit="/hr" unitSize={15} unitColor={Card.cardMuted} />
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
export const StandInOffer: React.FC<{ offer: Offer | null; blur?: number; style?: React.CSSProperties }> = ({
  offer,
  blur = 0,
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
      ...style,
    }}
  >
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

/** StatusPill: "Reading offers" with a green dot. */
export const StatusPill: React.FC<{ text: string; scale?: number }> = ({ text, scale = 1 }) => (
  <div
    style={{
      height: 44 * scale,
      borderRadius: 22 * scale,
      background: Mono.surfaceRaised,
      padding: `0 ${18 * scale}px 0 ${14 * scale}px`,
      display: "inline-flex",
      alignItems: "center",
      gap: 10 * scale,
    }}
  >
    <div style={{ width: 18 * scale, height: 18 * scale, position: "relative" }}>
      <div style={{ position: "absolute", inset: 0, borderRadius: "50%", background: "rgba(48,209,88,0.22)" }} />
      <div
        style={{
          position: "absolute",
          left: 4 * scale,
          top: 4 * scale,
          width: 10 * scale,
          height: 10 * scale,
          borderRadius: "50%",
          background: VerdictColor.GREEN,
        }}
      />
    </div>
    <div style={nahText(16 * scale, 600, 20 * scale, 0, Mono.ink)}>{text}</div>
  </div>
);

const RoundButton: React.FC<{ plus: boolean; pressed: number }> = ({ plus, pressed }) => (
  <div
    style={{
      width: 44,
      height: 44,
      borderRadius: 22,
      background: pressed > 0 ? `rgba(255,255,255,${0.12 + pressed * 0.5})` : Mono.surfaceRaised,
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      flexShrink: 0,
      scale: String(1 - pressed * 0.08),
      boxShadow: pressed > 0 ? `0 0 0 ${pressed * 6}px rgba(255,255,255,${pressed * 0.18})` : undefined,
    }}
  >
    <svg width={20} height={20} viewBox="0 0 24 24" fill="none">
      <path
        d={plus ? "M6 12h12M12 6v12" : "M6 12h12"}
        stroke={pressed > 0.5 ? "#000" : Mono.ink}
        strokeWidth={2.5}
        strokeLinecap="round"
      />
    </svg>
  </div>
);

/** ThresholdField: label, big value with unit, hint, and round −/+ steppers. */
export const ThresholdField: React.FC<{
  label: string;
  value: string;
  unit: string | null;
  hint: string;
  plusPressed?: number;
  valuePop?: number;
}> = ({ label, value, unit, hint, plusPressed = 0, valuePop = 0 }) => (
  <div style={{ minHeight: 84, padding: "12px 16px", display: "flex", alignItems: "center", gap: 12 }}>
    <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 2 }}>
      <div style={nahText(14, 500, 20, 0, Mono.inkMuted)}>{label}</div>
      <div style={{ ...nahText(24, 800, 28, -0.03, Mono.ink), transformOrigin: "left center", scale: String(1 + valuePop * 0.08) }}>
        {unit ? <Rate amount={value} unit={unit} unitSize={15} unitColor={Mono.inkMuted} /> : value}
      </div>
      <div style={nahText(13, 400, 18, 0, Mono.inkMuted)}>{hint}</div>
    </div>
    <RoundButton plus={false} pressed={0} />
    <RoundButton plus pressed={plusPressed} />
  </div>
);

export const Divider = () => <div style={{ height: 1, background: Mono.line }} />;

/** The Android status bar: time left, simple signal and battery right. No brand. */
export const StatusBar: React.FC<{ time?: string }> = ({ time = "6:42" }) => (
  <div
    style={{
      height: 40,
      padding: "0 26px",
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      ...nahText(15, 600, 20, 0, "#FFFFFF"),
    }}
  >
    <span>{time}</span>
    <div style={{ display: "flex", gap: 7, alignItems: "center" }}>
      <svg width={16} height={14} viewBox="0 0 16 14">
        <path d="M1 13L15 1V13Z" fill="#FFFFFF" />
      </svg>
      <div style={{ width: 22, height: 11, border: "1.5px solid #FFFFFF", borderRadius: 3, padding: 1.5 }}>
        <div style={{ width: "72%", height: "100%", background: "#FFFFFF", borderRadius: 1 }} />
      </div>
    </div>
  </div>
);

/** A sentence that ends in the red square full stop: "Bad offer? Just say nah". */
export const StopLine: React.FC<{ text: string; size: number; weight?: number; color?: string; stopScale?: number }> = ({
  text,
  size,
  weight = 900,
  color = Mono.ink,
  stopScale = 1,
}) => (
  <span style={{ ...nahText(size, weight, size * 1.02, -0.06, color), display: "inline" }}>
    {text}
    <span
      style={{
        display: "inline-block",
        width: size * 0.2,
        height: size * 0.2,
        marginLeft: size * 0.04,
        background: Mono.stop,
        scale: String(stopScale),
      }}
    />
  </span>
);
