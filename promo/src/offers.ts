// The offers in the video, from timeline.offers (absolute times in seconds), scored the app's way.
import timeline from "./timeline.json";
import { CANT_READ, DEFAULT_RULES, Evaluation, OFFERS, Offer, score } from "./nah";

export type OfferScene = {
  id: string;
  /** When the offer is on screen. */
  from: number;
  until: number;
  offerIn: number;
  verdictIn: number;
  /** The offer card shows its 15-second timer. */
  timer: boolean;
  offer: Offer | null;
  e: Evaluation;
};

export const OFFER_SCENES: OfferScene[] = timeline.offers.map((o) => {
  const offer = OFFERS[o.offer] ?? null;
  return {
    id: o.id,
    from: o.from,
    until: o.until,
    offerIn: o.offerInSec,
    verdictIn: o.verdictInSec,
    timer: o.timer,
    offer,
    e: offer ? score(offer, DEFAULT_RULES) : CANT_READ,
  };
});

/** When a voiceover line or verdict clip starts (from timeline.speech). */
export const speechAt = (id: string) => timeline.speech.find((s) => s.id === id)?.at ?? 0;
