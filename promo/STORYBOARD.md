# nah.meter promo: storyboard

41 s at 30 fps. Vertical 1080×1920 (Reels, TikTok, Shorts) and wide 1920×1080 from the same
scenes: in the wide cut the phone sits left and the captions sit right. Times are in
`src/timeline.json`, which both the picture (`src/`) and the sound (`scripts/make-audio.mjs`) read,
so a retimed scene moves its sounds with it.

Look: black ground, white Geist (800–900, lowercase where it's the brand), colour only on verdict
words (bet! #30D158, meh. #FFD60A, nah. #FF3B30, can't read. #8E8E93). The red square full stop is
the only other red. The phone is a plain Android phone (punch-hole camera, no brand). The offer
is the app's neutral stand-in (`StandInOffer`): "Delivery", the pay, "23 min (11.5 km) total",
a grey "Match" button. No Uber name, logo, colours or screens.

| # | Time | Picture | On-screen text | Sound |
| --- | --- | --- | --- | --- |
| 1 | 0.0–3.0 | Black. A giant outlined countdown (15, 14, 13…) ticks behind; the lines slam in. | "New offer." / "15 seconds." / "Worth it?" | Clock tick every 0.5 s, a slam per line, riser into 2 |
| 2 | 3.0–5.0 | Phone rises into frame over a dark, slowly panning street map (grey lines only). Status pill "Reading offers". Clock 6:42. | "On shift." | Music drops in (kick, sub, hats, soft chords) |
| 3 | 5.0–10.0 | Offer card slides up. The verdict lands: **nah.** fills the screen, then the card: $15.83/hr, "Under your $20", chip $0.53/km. A speech chip shows the clip's waveform. | "Not worth your time." · chip "nah." | Whoosh, riser, sub boom + "nah" sting, the nah clip |
| 4 | 10.0–14.0 | New offer: $14.20 · 28 min (9.8 km). **bet!** $30.43/hr, "Clears your $20", $1.45/km. | "Pays your rate." · chip "bet!" | Whoosh, bright two-note pop, the bet clip |
| 5 | 14.0–18.5 | New offer: $9.40 · 26 min (12.4 km). **meh.** $21.69/hr, "Short on $/km", $0.76/km. | "Pays the hour," / "long drive." · chip "meh." | Whoosh, neutral double tick, the meh clip |
| 6 | 18.5–24.0 | A blurred, unreadable offer. **can't read.** "Check this offer yourself". | "Can't read it?" / "It won't guess." · chip "can't read." | Whoosh, soft muted blip, the can't-read clip |
| 7 | 24.0–31.5 | The Rules tab as in the app: live preview card, "Strict on $/hr / $/km", Minimum pay, Per hour, Per km, Max km. + on Per hour twice ($20 → $21), then Strict flips to $/km: the card's big rate becomes $0.53/km, "Under your $1/km", chip $15.83/hr; the hints swap nah./meh. | "Your rules." / "Your call.", then "Traffic?" / "Go strict on $/km." | A UI tick per step; a switch click on the flip |
| 8 | 31.5–36.5 | Three promise lines slam in with the red square bullet. Then "You decide." | "It never taps anything for you." / "No internet permission. Nothing leaves your phone." / "No account." / "You decide." | Tick per line, music breaks down |
| 9 | 36.5–41.0 | The wordmark zooms in; the square lands with a shockwave. Tagline word by word. | "Bad offer? Just say nah." (full stop as the red square) · "For Android" | "nah" sting and boom on the square, final chord, fade |

## Motion (second pass: flashier)

- Camera: for each offer it pushes in on the offer card, punch-zooms (spring overshoot) onto the
  verdict card as it lands, eases back so both cards show, then whip-zooms out with motion blur.
  On the Rules screen it zooms in on the steppers and nudges on every step.
- Verdict impact: the word fills the screen in its colour for about half a second with a white
  flash and burst lines, then shrinks away to reveal the docked card. Hard shake on nah., a nudge
  on the others. A glow in the verdict's colour pulses on the beat behind the phone.
- Hook: a giant outlined countdown (15, 14, 13…) ticks behind the lines, which slam in.
- Captions, promises and the tagline slam in (big and blurred, then sharp). The wordmark zooms
  in, and the red square lands with a shockwave.
- Film grain and a vignette over everything.
- Sound: a short riser and a sub boom under each verdict hit, and a whip whoosh on each exit.

Voice: the four verdict clips in `audio/voice/` (supplied by the project owner), one per verdict.
The speech chip shows the verdict word and a waveform drawn from the clip itself, not a
transcript. No narrator.

Rules followed: no Uber branding; no tap on the offer card ever (the only presses shown are on
nah.meter's own + button); the words "Accept" and "Decline" never appear; no emoji or exclamation
marks except bet!; verdict words lowercase (nah. meh. bet! can't read.) from `NahCopy`; no iPhone
and no "iOS".
