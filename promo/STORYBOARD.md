# nah.meter promo: storyboard

37 s at 30 fps. Vertical 1080×1920 (Reels, TikTok, Shorts) and wide 1920×1080 from the same
scenes: in the wide cut the phone sits left and the captions sit right. Times are in
`src/timeline.json`, which both the picture (`src/`) and the sound (`scripts/make-audio.mjs`) read,
so a retimed scene moves its sounds with it.

Look: black ground, white Geist (800–900, lowercase where it's the brand), colour only on verdict
words (bet. #30D158, eh. #FFD60A, nah. #FF3B30, can't read. #8E8E93). The red square full stop is
the only other red. The phone is a plain Android phone (punch-hole camera, no brand). The offer
is the app's neutral stand-in (`StandInOffer`): "Delivery", the pay, "23 min (11.5 km) total",
a grey "Match" button. No Uber name, logo, colours or screens.

| # | Time | Picture | On-screen text | Sound |
| --- | --- | --- | --- | --- |
| 1 | 0.0–3.0 | Black. Type slams in, line by line. A thin timer bar drains under it. | "New offer." / "15 seconds." / "Worth it?" | Beat starts muted; a clock tick per line; riser into 2 |
| 2 | 3.0–5.0 | Phone rises into frame over a dark, slowly panning street map (grey lines only). Status pill "Reading offers" with the green dot. Clock 6:42. | "On shift." | Music drops in (kick, sub, hats, soft chords) |
| 3 | 5.0–10.0 | Offer card slides up from the bottom. ~0.5 s later the black verdict card drops onto it with a spring and settles 20dp above: **nah.** $15.83/hr, "Under your $20", chip $0.53/km. A speech chip under the phone shows the spoken line. | "Not worth your time." · speech chip "nah. 15 an hour." | Whoosh (card in), low thud + "nah" sting (verdict), spoken "nah. 15 an hour." |
| 4 | 10.0–14.0 | Old cards slide away; a new offer: $14.20 · 28 min (9.8 km). Verdict **bet.** $30.43/hr, "Clears your $20", $1.45/km. | "Pays your rate." · speech chip "bet. 30 an hour." | Whoosh, bright two-note pop, spoken "bet. 30 an hour." |
| 5 | 14.0–18.0 | New offer: $9.40 · 26 min (12.4 km). Verdict **eh.** $21.69/hr, "Short on $/km", $0.76/km. | "Pays the hour, long drive." · speech chip "eh. 21 an hour." | Whoosh, neutral double tick, spoken "eh. 21 an hour." |
| 6 | 18.0–21.0 | A blurred, unreadable offer. Verdict **can't read.** "Check this offer yourself". | "Can't read it?" / "It won't guess." · speech chip "can't read. check it yourself." | Whoosh, soft muted blip, spoken "can't read. check it yourself." |
| 7 | 21.0–27.5 | Phone shows the Rules screen: Minimum pay $5.50, Per hour $20.00/hr, Per km $1.00/km with − / + buttons. The + on Per hour lights four times: $20.00 → $22.00 in $0.50 steps. Under it the example offer's verdict card stays live ("Under your $22"). | "Your rules." / "Your call." | A UI tick per step; music keeps going |
| 8 | 27.5–32.5 | Phone slides away. Three promise lines land one by one, each with the red square bullet. Then "You decide." | "It never taps anything for you." / "No internet permission. Nothing leaves your phone." / "No account." / "You decide." | Tick per line, music filter opens |
| 9 | 32.5–37.0 | The wordmark: "nah" + red square + "meter", the square drops in last. Tagline under it. Small line at the bottom. | "Bad offer? Just say nah." (full stop as the red square) · "For Android" | "nah" sting on the square, final chord, music fades out |

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

Voice: the only spoken lines are nah.meter's own verdict lines from `NahCopy.speech`, generated
locally with ffmpeg's flite voice (no online TTS). No narrator.

Rules followed: no Uber branding; no tap on the offer card ever (the only presses shown are on
nah.meter's own + button); the words "Accept" and "Decline" never appear; no emoji or exclamation
marks; verdict words lowercase with a full stop; no iPhone and no "iOS".
