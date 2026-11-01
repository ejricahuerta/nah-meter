// The showcase cut: the promo's voiceover and clips over its own trap beat (scripts/make-trap.py),
// with the promo's offer scenes played on the
// map photos from social/ (real Toronto streets, made-up offers). Each photo sits in a phone on
// the white ground; its verdict card snaps on when the clip says it, and the camera leans in on it.
// The scenes after the offers (money, road, rules, trust, end) are the promo's own.
// The photos come from `node social/render.mjs --layers 05 01 03 06`, handed over by
// social/scripts/copy-to-promo.mjs (images in public/showcase/, positions in showcase.json).
import React from "react";
import { AbsoluteFill, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { Audio } from "@remotion/media";
import timeline from "./timeline.json";
import { Daylight, VerdictInkOnWhite, nahText } from "./tokens";
import { Benefit, End, LAYOUTS, RulesField, Trust, Words, useSnap } from "./Minimal";
import { OFFER_SCENES, speechAt } from "./offers";
import voice from "./voice.json";
import photos from "./showcase.json";

const S = timeline.scenes;
const clamp = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const L = LAYOUTS.vertical;

type Layer = { id: string; width: number; height: number; verdict: number[]; button: number[]; exclusive: boolean; timer: number; word: string };

// Which photo plays each of the promo's offer scenes. The clips say a whole-dollar rate, so each
// photo's rate matches its clip: nah "15" ($15.80), bet "30" ($30.41), meh "21" ($21.19).
const PHOTO: Record<string, Layer> = { hero: photos["05"], bet: photos["01"], meh: photos["03"], nah: photos["06"] };
// Under each verdict, what it means (the promo's captions).
const MEANING: Record<string, [string, string, keyof typeof VerdictInkOnWhite]> = {
  bet: ["bet!", "Pays your rate.", "GREEN"],
  meh: ["meh.", "Pays the hour, long drive.", "YELLOW"],
  nah: ["nah.", "Not worth your time.", "RED"],
};

// The phone: 1080 px of screen shown at IDLE scale, centred, top at PHONE_TOP; captions above it.
const IDLE = 0.6;
const ZOOM = 0.9;
const PHONE_TOP = 430;
const BEZEL = 22;
const CAPTION_Y = 250;

const ease = (x: number) => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x));

/** A phone showing one photo; the verdict card lands at `verdictIn`, the camera leans in on it. */
const Phone: React.FC<{ t: number; layer: Layer; from: number; offerIn: number; verdictIn: number; timer: boolean; enter: "up" | "right" }> = ({
  t,
  layer,
  from,
  offerIn,
  verdictIn,
  timer,
  enter,
}) => {
  const { fps } = useVideoConfig();
  const snap = useSnap();
  const inP = snap(t, offerIn);
  const vP = t >= verdictIn ? snap(t, verdictIn) : 0;
  const [vx, vy, vw, vh] = layer.verdict;
  const [bx, by, bw, bh] = layer.button;

  // Camera: a slow push-in, then in on the verdict card when it lands, then back out.
  const zoomIn = ease((t - verdictIn - 0.05) / 0.35);
  const zoomOut = ease((t - verdictIn - 1.5) / 0.5);
  const z = zoomIn * (1 - zoomOut);
  const k = interpolate(z, [0, 1], [IDLE + 0.04 * Math.min(1, Math.max(0, (t - from) / 6)), ZOOM]);
  // The point of the screen held still: its middle when idle, the verdict card when zoomed.
  const fx = 540;
  const fy = interpolate(z, [0, 1], [layer.height / 2, vy + vh / 2]);
  const cy = interpolate(z, [0, 1], [PHONE_TOP + (layer.height / 2) * IDLE, 960]);
  const enterX = enter === "right" ? (1 - inP) * 1100 : 0;
  const enterY = enter === "up" ? (1 - inP) * 260 : 0;
  const left = 540 - fx * k + enterX;
  const top = cy - fy * k + enterY;
  const pop = interpolate(t - verdictIn, [0.02, 0.12, 0.3], [1, 1.04, 1], clamp);

  // The offer's 15-second timer, run down on the button (darker green is the time left).
  const left15 = timer ? Math.max(0, 15 - (t - offerIn)) / 15 : layer.timer;
  return (
    <div
      style={{
        position: "absolute",
        left,
        top,
        width: layer.width,
        height: layer.height,
        transformOrigin: "top left",
        scale: String(k),
        opacity: Math.min(1, inP * 1.6),
      }}
    >
      <div
        style={{
          position: "absolute",
          inset: -BEZEL,
          borderRadius: 110,
          background: "#0B0B0B",
          boxShadow: "0 60px 120px rgba(0,0,0,0.28), 0 0 0 3px #2A2A2A inset",
        }}
      />
      <div style={{ position: "absolute", inset: 0, borderRadius: 90, overflow: "hidden", background: "#262A33" }}>
        <Img src={staticFile(`showcase/${layer.id}-screen.png`)} style={{ position: "absolute", inset: 0, width: layer.width, height: layer.height }} />
        {layer.exclusive && (
          <>
            <div style={{ position: "absolute", left: bx, top: by, width: bw * left15, height: bh, background: "#1EAD55", borderRadius: `${bh * 0.14}px 0 0 ${bh * 0.14}px` }} />
            <Img src={staticFile(`showcase/${layer.id}-label.png`)} style={{ position: "absolute", inset: 0, width: layer.width, height: layer.height }} />
          </>
        )}
        {t >= verdictIn && (
          <Img
            src={staticFile(`showcase/${layer.id}-verdict.png`)}
            style={{
              position: "absolute",
              inset: 0,
              width: layer.width,
              height: layer.height,
              transformOrigin: `${vx + vw / 2}px ${vy + vh / 2}px`,
              translate: `0 ${(1 - vP) * -70}px`,
              scale: String(interpolate(vP, [0, 1], [1.1, 1]) * pop),
              opacity: Math.min(1, vP * 2),
            }}
          />
        )}
      </div>
    </div>
  );
};

/** A caption above the phone, on a white pill so it reads over the photo when the camera leans in. */
const Caption: React.FC<{ t: number; at: number; until: number; children: React.ReactNode; size?: number }> = ({ t, at, until, children, size = L.text * 0.72 }) =>
  t >= at && t < until ? (
    <div style={{ position: "absolute", left: 0, right: 0, top: CAPTION_Y, display: "flex", justifyContent: "center", zIndex: 2 }}>
      <div style={{ background: Daylight.ground, borderRadius: 40, padding: "14px 36px", boxShadow: "0 16px 40px rgba(0,0,0,0.10)" }}>
        <Words t={t} at={at} size={size} color={Daylight.ink}>
          {children}
        </Words>
      </div>
    </div>
  ) : null;

export const Showcase: React.FC<{ withAudio?: boolean }> = ({ withAudio = true }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const t = frame / fps;
  const inScene = (...ks: (keyof typeof S)[]) => ks.some((k) => t >= S[k][0] && t < S[k][1]);
  const hero = OFFER_SCENES.find((o) => o.timer)!;
  const current = OFFER_SCENES.find((o) => !o.timer && t >= o.from && t < o.until);
  const [newOfferSec, worthSec] = timeline.hookLinesSec;
  return (
    <AbsoluteFill style={{ background: Daylight.ground, overflow: "hidden" }}>
      {inScene("hook", "answer", "what") && (
        <>
          <Phone t={t} layer={PHOTO.hero} from={0} offerIn={hero.offerIn} verdictIn={hero.verdictIn} timer enter="up" />
          <Caption t={t} at={0.25} until={newOfferSec - 0.05} size={L.text * 0.62}>
            For delivery drivers.
          </Caption>
          <Caption t={t} at={newOfferSec} until={worthSec} size={L.text * 0.62}>
            New offer. 15 seconds.
          </Caption>
          <Caption t={t} at={worthSec} until={hero.verdictIn}>
            Worth it?
          </Caption>
          <Caption t={t} at={speechAt("vo_02")} until={S.what[1]}>
            nah.meter does the math.
          </Caption>
        </>
      )}
      {current && (
        <>
          <Phone key={current.id} t={t} layer={PHOTO[current.id]} from={current.from} offerIn={current.offerIn} verdictIn={current.verdictIn} timer={false} enter="right" />
          <Caption t={t} at={current.verdictIn + 0.25} until={current.until} size={L.text * (current.id === "meh" ? 0.56 : 0.66)}>
            <span style={{ color: VerdictInkOnWhite[MEANING[current.id][2]] }}>{MEANING[current.id][0]}</span> {MEANING[current.id][1]}
          </Caption>
        </>
      )}
      {inScene("money") && (
        <Benefit t={t} L={L} lines={[{ text: "Skip the bad offers.", at: speechAt("vo_06") }, { text: "Earn more per hour.", at: speechAt("vo_06") + 1.3 }]} />
      )}
      {inScene("road") && (
        <Benefit
          t={t}
          L={L}
          lines={[{ text: "Says it out loud.", at: speechAt("vo_07") }, { text: "Eyes on the road.", at: speechAt("vo_07") + 1.2 }]}
          wave={{ at: speechAt("vo_07"), env: voice.vo_07 }}
        />
      )}
      {inScene("rules") && <RulesField t={t} L={L} />}
      {inScene("trust") && <Trust t={t} L={L} />}
      {t >= S.end[0] && <End t={t} L={L} />}
      {withAudio && <Audio src={staticFile("audio/mix-showcase.wav")} />}
    </AbsoluteFill>
  );
};
