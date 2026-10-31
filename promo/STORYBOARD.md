# nah.meter promo: storyboard (v5, with voiceover)

45.7 s at 30 fps, vertical 1080×1920 and wide 1920×1080. Clean and minimal: white and black
grounds, one big offer card, the black verdict card, colour only on verdicts. The owner's voiceover
explains it; the on-screen words are short captions of what's said (many viewers watch muted).

The timing is built from the audio by `scripts/make-timeline.mjs`: every voiceover line and
verdict clip plays in full, one after another, and each shot lasts as long as its audio.

## Who it's for

A delivery driver scrolling Reels or TikTok. An offer gives them 15 seconds, and nothing on it
says whether it's worth it per hour. nah.meter does that maths and answers in one word; the
benefits shown are earning more (skip bad offers) and keeping eyes on the road (it speaks).

## Shots

| Time | Ground | Picture | Voiceover | Caption |
| --- | --- | --- | --- | --- |
| 0.0–4.6 s | White | The offer card arrives on "New offer", its timer running | "Delivery driver? New offer. Fifteen seconds. Is it worth it?" | "For delivery drivers." · "New offer. 15 seconds." · "Worth it?" |
| 4.6–6.9 s | White | The verdict card snaps on: **nah.** $15.83/hr | (the nah clip) | — |
| 6.9–11.4 s | White | Same | "nah meter does the math for you, and answers in one word." | "nah.meter does the math." |
| 11.4–15.9 s | White | $14.20 · 28 min: **bet!** | (the bet clip), then "Bet. It pays your rate." | "bet! Pays your rate." |
| 15.9–23.7 s | White | $9.40 · 26 min: **meh.** | (the meh clip), then "Meh. It pays the hour, but it's a long drive." | "meh. Pays the hour, long drive." |
| 23.7–26.5 s | White | $7.25 · 31 min: **nah.** | "And nah? Not worth your time." | "nah. Not worth your time." |
| 26.5–29.8 s | Black | The red square | "Skip the bad offers, and earn more every hour." | "Skip the bad offers." · "Earn more per hour." |
| 29.8–32.7 s | Black | A waveform drawn from the line itself | "It says it out loud, so your eyes stay on the road." | "Says it out loud." · "Eyes on the road." |
| 32.7–37.1 s | White | A huge Per hour stepper, + tapped: $20.50, $21.00 | "You set the rules: your dollars an hour, your dollars a kilometre." | "Your rules." |
| 37.1–41.7 s | Black | Two cards | "It never taps anything. You decide. No internet. No account." | "Never taps for you." · "You decide." · "No internet. No account." |
| 41.7–45.7 s | White | The wordmark, the red square landing | "Bad offer? Just say nah." | "Bad offer? Just say nah." · "For Android" |

## Decisions (from the owner)

- "For delivery drivers": no delivery app is named.
- Benefits: earn more by skipping bad offers; eyes on the road.
- Voiceover: recorded by the owner in ElevenLabs (voice "Lauren") from the script in this PR's
  discussion; used 10% faster. No call to action.

## Rules kept

- No Uber logo, colours or screens. The offer card is the app's neutral stand-in.
- Nothing is ever tapped on the offer card (the only tap is on nah.meter's own + button).
- "Accept" and "Decline" are never used.
- Lowercase verdicts from the app: **nah.**, **meh.**, **bet!**. No emoji; the only exclamation
  mark is in **bet!**. Verdict words on white use the app's Daylight inks for contrast.
- Android only.
