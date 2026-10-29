// A small TypeScript port of core/Scorer.kt and service/NahCopy.kt, so every number and string
// on screen is computed the way the app computes it, not typed in by hand. Integer cents keep the
// maths exact, as BigDecimal does in the app; rounding (half-up) is for display only.

export type Verdict = "GREEN" | "YELLOW" | "RED" | "GREY";
type Check = "MIN_PAY" | "HOURLY" | "PER_KM" | null;

export type Offer = { payCents: number; minutes: number; kmTenths: number };
export type Rules = { minPayCents: number; minHourlyCents: number; minPerKmCents: number };

export const DEFAULT_RULES: Rules = { minPayCents: 550, minHourlyCents: 2000, minPerKmCents: 100 };

export type Evaluation = {
  verdict: Verdict;
  offer: Offer | null;
  hourlyCents: number | null;
  perKmCents: number | null;
  failed: Check;
};

/** num / den rounded half-up, for non-negative integers. */
const divHalfUp = (num: number, den: number) => Math.floor((2 * num + den) / (2 * den));

export const score = (o: Offer, t: Rules): Evaluation => {
  const hourlyCents = divHalfUp(o.payCents * 60, o.minutes);
  const perKmCents = o.kmTenths > 0 ? divHalfUp(o.payCents * 10, o.kmTenths) : null;
  let verdict: Verdict = "GREEN";
  let failed: Check = null;
  if (o.payCents < t.minPayCents) [verdict, failed] = ["RED", "MIN_PAY"];
  else if (o.payCents * 60 < t.minHourlyCents * o.minutes) [verdict, failed] = ["RED", "HOURLY"];
  else if (o.kmTenths > 0 && o.payCents * 10 < t.minPerKmCents * o.kmTenths) [verdict, failed] = ["YELLOW", "PER_KM"];
  return { verdict, offer: o, hourlyCents, perKmCents, failed };
};

export const CANT_READ: Evaluation = { verdict: "GREY", offer: null, hourlyCents: null, perKmCents: null, failed: null };

/** "$5.50", always with cents (as the offer card and the rate show it). */
export const cents = (c: number) => `$${Math.floor(c / 100)}.${String(c % 100).padStart(2, "0")}`;

/** Scorer.money: "$20" for whole dollars, "$5.50" otherwise. */
export const money = (c: number) => (c % 100 === 0 ? `$${c / 100}` : cents(c));

export const km = (tenths: number) => `${Math.floor(tenths / 10)}.${tenths % 10}`;

// ---- NahCopy ----

export const word = (v: Verdict) =>
  ({ GREEN: "bet.", YELLOW: "eh.", RED: "nah.", GREY: "can't read." })[v];

export const kmChip = (e: Evaluation) =>
  e.verdict === "GREY" || e.perKmCents === null ? null : `${cents(e.perKmCents)}/km`;

export const cardReason = (e: Evaluation, t: Rules) => {
  if (e.verdict === "GREY") return "Check this offer yourself";
  if (e.failed === "MIN_PAY") return `Under ${money(t.minPayCents)} minimum`;
  if (e.failed === "HOURLY") return `Under your ${money(t.minHourlyCents)}`;
  if (e.failed === "PER_KM") return "Short on $/km";
  return `Clears your ${money(t.minHourlyCents)}`;
};

/** NahCopy.speech, VoiceMode.WORD_HOURLY (the default): "nah. 15 an hour." */
export const speech = (e: Evaluation) => {
  if (e.verdict === "GREY") return "can't read. check it yourself.";
  return `${word(e.verdict)} ${Math.floor((e.hourlyCents ?? 0) / 100)} an hour.`;
};

export const hourly = (e: Evaluation) => (e.hourlyCents === null ? null : cents(e.hourlyCents));

// ---- The offers in the video ----

/** The PRD's worked example (EXAMPLE_OFFER in SetupScreen.kt): $6.07, 23 min, 11.5 km. */
export const EXAMPLE: Offer = { payCents: 607, minutes: 23, kmTenths: 115 };

export const OFFERS: Record<"nah" | "bet" | "eh", Offer> = {
  nah: EXAMPLE,
  bet: { payCents: 1420, minutes: 28, kmTenths: 98 },
  eh: { payCents: 940, minutes: 26, kmTenths: 124 },
};

/** "Under your $20" style captions, from WelcomeStep's examples. */
export const MEANING: Record<Verdict, string> = {
  GREEN: "Pays your rate.",
  YELLOW: "Pays the hour, long drive.",
  RED: "Not worth your time.",
  GREY: "Can't read it? It won't guess.",
};
