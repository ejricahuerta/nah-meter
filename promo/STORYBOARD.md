# nah.meter promo: storyboard

**Concept: the world is black and white; only the verdict has colour.** The courier's world is a
night drive seen from the driver's seat, in greyscale. When a verdict lands, the screen bursts open
in the verdict's colour with the word stamped huge in black, then closes back into the card.

39.5 s at 30 fps. Vertical 1080×1920 (Reels, TikTok, Shorts) and wide 1920×1080 from the same
scenes. All times are in `src/timeline.json`, which the picture, the music and the sound read.

Look: Geist 800–900; colour only on verdicts (bet! #30D158, meh. #FFD60A, nah. #FF3B30, can't read.
#8E8E93) and the red square full stop. A plain Android phone in a dash mount (no brand). The offer
is the app's neutral stand-in (`StandInOffer`): "Delivery", the pay, "23 min (11.5 km) total",
a grey "Match" button. No Uber name, logo, colours or screens.

| # | Time | Picture | On-screen text | Sound |
| --- | --- | --- | --- | --- |
| 1 | 0.0–4.0 | Black. A 15 s ring drains; the offer's numbers slam in one by one; "Worth it?" stamps over them; then a wall of churning digits and maths signs behind "Don't do the math." | "$6.07" / "23 min" / "11.5 km" / "Worth it?" / "Don't do the math." | Clock tick every 0.5 s, a punch per line; strings swell, snare roll into the drop |
| 2 | 4.0–6.0 | Hard cut to the night drive, wide: rain on the windshield, city bokeh, streetlights passing overhead, the road rushing in, the phone in its mount tilted in 3D. The camera pushes in. | "On shift." · "Reading offers" | Music drops with a crash; whoosh |
| 3 | 6.0–11.0 | The offer slides up; the verdict lands: the screen bursts red with a huge black **nah.**, then closes into the docked card ($15.83/hr, "Under your $20", $0.53/km). Red spills onto the glass and the dash. | "Not worth your time." · chip "nah." | Swell, punch + metal + boom, crash; the nah clip |
| 4 | 11.0–14.0 | Next offer, $14.20 · 28 min (9.8 km): green burst, **bet!** ($30.43/hr, "Clears your $20", $1.45/km). | "Pays your rate." | Chips and a chime; the bet clip |
| 5 | 14.0–18.5 | $9.40 · 26 min (12.4 km): yellow burst, **meh.** ($21.69/hr, "Short on $/km", $0.76/km). | "Pays the hour," / "long drive." | Two wood knocks; the meh clip |
| 6 | 18.5–24.0 | A blurred, unreadable offer: grey burst, **can't read.** ("Check this offer yourself"). | "Can't read it?" / "It won't guess." | Glitch stutter; the can't-read clip |
| 7 | 24.0–30.0 | The Rules tab as in the app. + on Per hour twice ($20 → $21), then Strict flips to $/km: the card shows $0.53/km big, "Under your $1/km". | "Your rules." / "Your call.", then "Traffic?" / "Go strict on $/km." | Clicks, a switch, a crash on the flip |
| 8 | 30.0–35.0 | One promise at a time, full screen, each knocking the last up and away, the red square spinning in. | "It never taps anything for you." / "No internet permission." / "Nothing leaves your phone." / "No account." / "You decide." | A drop and a punch per line, a boom on "You decide."; drums out, then a snare roll |
| 9 | 35.0–39.5 | The wordmark zooms in; the square lands with a shockwave; the tagline word by word. | "Bad offer? Just say nah." · "For Android" | Crash and a big Am; punch and boom on the square; fade |

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
- Music: recorded instruments (MuseScore General soundfont via FluidSynth), a crash on each verdict and on the strict flip.
- Sound: recorded effects (Kenney, CC0): slowed card slides for whooshes, reversed for the swell
  into each verdict; nah. is a heavy punch, low metal and a boom; bet! is a stack of poker chips and
  a chime; meh. is two knocks on wood; can't read. is a digital stutter. Real clicks on the rules.

Voice: the four verdict clips in `audio/voice/` (supplied by the project owner), one per verdict.
The speech chip shows the verdict word and a waveform drawn from the clip itself, not a
transcript. No narrator.

Rules followed: no Uber branding; no tap on the offer card ever (the only presses shown are on
nah.meter's own + button); the words "Accept" and "Decline" never appear; no emoji or exclamation
marks except bet!; verdict words lowercase (nah. meh. bet! can't read.) from `NahCopy`; no iPhone
and no "iOS".
