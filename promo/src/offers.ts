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
  speechIn: number;
  /** The whole clip, or just the verdict word. */
  speech: "full" | "word";
  /** The offer card shows its 15-second timer. */
  timer: boolean;
  offer: Offer | null;
  e: Evaluation;
};

export const OFFER_SCENES: OfferScene[] = timeline.offers.map((o) => {
  const offer = OFFERS[o.offer] ?? null;
  return {
    id: o.id,
    from: o.offerInSec,
    until: o.until,
    offerIn: o.offerInSec,
    verdictIn: o.verdictInSec,
    speechIn: o.speechInSec,
    speech: o.speech as "full" | "word",
    timer: Boolean((o as { timer?: boolean }).timer),
    offer,
    e: offer ? score(offer, DEFAULT_RULES) : CANT_READ,
  };
});
