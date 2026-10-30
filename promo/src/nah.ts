// A small TypeScript port of core/Scorer.kt and service/NahCopy.kt, so every number and string
// on screen is computed the way the app computes it, not typed in by hand. Integer cents keep the
// maths exact, as BigDecimal does in the app; rounding (half-up) is for display only.
// Not ported (off by default in the app, and not shown): max km, the unpaid extra km, the wait buffer.

export type Verdict = "GREEN" | "YELLOW" | "RED" | "GREY";
type Check = "MIN_PAY" | "HOURLY" | "PER_KM" | null;
/** Which rate is strict: missing it is nah., missing the other is only meh. */
export type Priority = "HOURLY" | "PER_KM";

export type Offer = { payCents: number; minutes: number; kmTenths: number };
export type Rules = { minPayCents: number; minHourlyCents: number; minPerKmCents: number; priority: Priority };

export const DEFAULT_RULES: Rules = { minPayCents: 550, minHourlyCents: 2000, minPerKmCents: 100, priority: "HOURLY" };

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
  const shortHourly = o.payCents * 60 < t.minHourlyCents * o.minutes;
  const shortPerKm = o.kmTenths > 0 && o.payCents * 10 < t.minPerKmCents * o.kmTenths;
  const [strict, soft]: [[boolean, Check], [boolean, Check]] =
    t.priority === "HOURLY" ? [[shortHourly, "HOURLY"], [shortPerKm, "PER_KM"]] : [[shortPerKm, "PER_KM"], [shortHourly, "HOURLY"]];
  let verdict: Verdict = "GREEN";
  let failed: Check = null;
  if (o.payCents < t.minPayCents) [verdict, failed] = ["RED", "MIN_PAY"];
  else if (strict[0]) [verdict, failed] = ["RED", strict[1]];
  else if (soft[0]) [verdict, failed] = ["YELLOW", soft[1]];
  return { verdict, offer: o, hourlyCents, perKmCents, failed };
};

export const CANT_READ: Evaluation = { verdict: "GREY", offer: null, hourlyCents: null, perKmCents: null, failed: null };

/** "$5.50", always with cents (as the offer card and the rate show it). */
export const cents = (c: number) => `$${Math.floor(c / 100)}.${String(c % 100).padStart(2, "0")}`;

/** Scorer.money: "$20" for whole dollars, "$5.50" otherwise. */
export const money = (c: number) => (c % 100 === 0 ? `$${c / 100}` : cents(c));

export const km = (tenths: number) => `${Math.floor(tenths / 10)}.${tenths % 10}`;

// ---- NahCopy ----

export const word = (v: Verdict) => ({ GREEN: "bet!", YELLOW: "meh.", RED: "nah.", GREY: "can't read." })[v];

export type Rate = { amount: string; unit: string };

/** NahCopy.rates: the strict rate big beside the word, the other in the chip. Null for can't read. */
export const rates = (e: Evaluation, t: Rules): [Rate, Rate | null] | null => {
  if (e.verdict === "GREY" || e.hourlyCents === null) return null;
  const hr: Rate = { amount: cents(e.hourlyCents), unit: "/hr" };
  const perKm: Rate | null = e.perKmCents === null ? null : { amount: cents(e.perKmCents), unit: "/km" };
  return t.priority === "PER_KM" && perKm ? [perKm, hr] : [hr, perKm];
};

const hourlyTarget = (t: Rules) => money(t.minHourlyCents);
const perKmTarget = (t: Rules) => `${money(t.minPerKmCents)}/km`;
const strictTarget = (t: Rules) => (t.priority === "PER_KM" ? perKmTarget(t) : hourlyTarget(t));

export const cardReason = (e: Evaluation, t: Rules) => {
  if (e.verdict === "GREY") return "Check this offer yourself";
  if (e.failed === "MIN_PAY") return `Under ${money(t.minPayCents)} minimum`;
  if (e.failed === "HOURLY") return e.verdict === "RED" ? `Under your ${hourlyTarget(t)}` : "Short on $/hr";
  if (e.failed === "PER_KM") return e.verdict === "RED" ? `Under your ${perKmTarget(t)}` : "Short on $/km";
  return `Clears your ${strictTarget(t)}`;
};

// ---- The offers in the video ----

/** The PRD's worked example (EXAMPLE_OFFER in SetupScreen.kt): $6.07, 23 min, 11.5 km. */
export const EXAMPLE: Offer = { payCents: 607, minutes: 23, kmTenths: 115 };

export const OFFERS: Record<string, Offer> = {
  hero: EXAMPLE,
  bet: { payCents: 1420, minutes: 28, kmTenths: 98 },
  meh: { payCents: 940, minutes: 26, kmTenths: 124 },
  nah: { payCents: 725, minutes: 31, kmTenths: 90 },
  voice: { payCents: 1180, minutes: 19, kmTenths: 64 },
};
