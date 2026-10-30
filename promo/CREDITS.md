# Credits and licences

Music and sound effects are made by this project's own scripts; the spoken verdicts are clips the
project owner supplied. No stock samples or loops are used, and nothing is downloaded at build time.

## Audio

| What | Source | Licence |
| --- | --- | --- |
| Music: 120 bpm A-minor loop (kick, clap, hats, sub bass, pad, plucked arpeggio), intro drone, risers, final chord | Synthesised sample by sample in `scripts/make-audio.mjs` | Original work of this project, same licence as the repo |
| Sound effects: whooshes, clock and UI ticks, thumps, the "nah" sting, the bet! pop, the meh. double tick, the can't read. blip | Synthesised in `scripts/make-audio.mjs` | Original work of this project |
| Reverb | A small Freeverb-style room written in `scripts/make-audio.mjs` (algorithm only, no code copied) | Original work of this project |
| Spoken verdicts (`audio/voice/verdict_nah.mp3`, `verdict_bet.mp3`, `verdict_meh.mp3`, `verdict_cant_read.mp3`) | Supplied by the project owner for this video; trimmed and levelled by `scripts/make-audio.mjs` | Owned by the project owner |
| Loudness (about -14 LUFS, peaks under -1.5 dBTP) | ffmpeg `loudnorm` | ffmpeg (LGPL/GPL); the output is ours |

## Picture

| What | Source | Licence |
| --- | --- | --- |
| Geist (all weights) | `app/src/main/res/font/geist_*.ttf`, copied into `public/fonts` at build time (not committed twice) | SIL Open Font License 1.1 |
| UI (verdict card, stand-in offer, Rules screen, tab bar, wordmark) | Redrawn in code from `ui/Nah.kt`, `ui/SetupScreen.kt`, `ui/ThresholdsScreen.kt`, `ui/MainActivity.kt` | Same licence as the repo |
| Phone, map | Drawn in code (a plain phone, seeded grey street lines) | Same licence as the repo |

No third-party brand marks, logos, colours or screenshots appear. The offer card is the app's
neutral stand-in (`StandInOffer`).

## Tools

| Tool | Licence |
| --- | --- |
| [Remotion](https://www.remotion.dev) 4.0.539 (`remotion`, `@remotion/cli`, `@remotion/fonts`, `@remotion/media`) | Remotion License: free for individuals and companies of up to 3 people; larger for-profit companies need a company licence. See `node_modules/remotion/LICENSE.md`. |
| React 19 | MIT |
| ffmpeg 6.1 (system) | LGPL/GPL |
| Guidance only (not installed or vendored): [remotion-dev/skills](https://github.com/remotion-dev/skills) | Read for Remotion best practices (fonts, timing, audio, rendering) |
