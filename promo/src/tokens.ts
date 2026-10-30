// nah.meter tokens, copied from app/src/main/java/dev/edmel/offerchecker/ui/Nah.kt (Mono and Daylight).
// The verdict card is black in both themes; colour only ever means a verdict.

export const Mono = {
  ground: "#000000",
  surface: "#111111",
  surfaceRaised: "#1F1F1F",
  line: "#262626",
  controlBorder: "#6B6B6B",
  ink: "#FFFFFF",
  inkMuted: "#A8A8A8",
  onInk: "#000000",
  stop: "#FF3B30",
};

/** Daylight (light theme), from Nah.kt: the white scenes. */
export const Daylight = {
  ground: "#FFFFFF",
  surface: "#F3F3F3",
  surfaceRaised: "#E8E8E8",
  ink: "#000000",
  inkMuted: "#5E5E5E",
  stop: "#E5251B",
};

export const Card = {
  card: "#000000",
  cardEdge: "#2A2A2A",
  cardMuted: "#A8A8A8",
  cardSoft: "#DEDEDE",
  cardChip: "#262626",
};

export const VerdictColor = {
  GREEN: "#30D158",
  YELLOW: "#FFD60A",
  RED: "#FF3B30",
  GREY: "#8E8E93",
} as const;

// The stand-in offer card from SetupScreen.kt (StandInOffer): neutral greys only.
export const StandIn = {
  bg: "#1C1C1E",
  label: "#A8A8A8",
  button: "#DEDEDE",
};

export const GEIST = "Geist";

/** Nah.style: Geist, tabular figures, tracking in em. */
export const nahText = (
  size: number,
  weight: number,
  line?: number,
  trackingEm = 0,
  color?: string,
): React.CSSProperties => ({
  fontFamily: GEIST,
  fontSize: size,
  fontWeight: weight,
  lineHeight: line ? `${line}px` : undefined,
  letterSpacing: `${trackingEm}em`,
  color,
  fontFeatureSettings: '"tnum"',
});
