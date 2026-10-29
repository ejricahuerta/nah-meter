import React from "react";
import { Composition, staticFile } from "remotion";
import { loadFont } from "@remotion/fonts";
import timeline from "./timeline.json";
import { GEIST } from "./tokens";
import { Promo } from "./Promo";

// Geist from the app (app/src/main/res/font, SIL OFL 1.1); scripts/copy-fonts.mjs puts it in public/.
for (const weight of [400, 500, 600, 700, 800, 900]) {
  loadFont({ family: GEIST, url: staticFile(`fonts/geist_${weight}.ttf`), weight: String(weight) });
}

const frames = Math.round(timeline.durationSec * timeline.fps);

export const Root: React.FC = () => (
  <>
    <Composition
      id="NahPromoVertical"
      component={Promo}
      durationInFrames={frames}
      fps={timeline.fps}
      width={1080}
      height={1920}
      defaultProps={{ aspect: "vertical" as const, withAudio: true }}
    />
    <Composition
      id="NahPromoWide"
      component={Promo}
      durationInFrames={frames}
      fps={timeline.fps}
      width={1920}
      height={1080}
      defaultProps={{ aspect: "wide" as const, withAudio: true }}
    />
  </>
);
