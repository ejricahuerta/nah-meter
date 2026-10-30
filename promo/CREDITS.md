# Credits and licences

The music is synthesised by this project's own script. The sound effects are real recordings
from Kenney's CC0 packs, and the spoken verdicts are clips the project owner supplied. Nothing is
downloaded at build time: every file used is in `audio/`.

## Audio

| What | Source | Licence |
| --- | --- | --- |
| Music: 120 bpm A-minor loop (kick, clap, hats, sub bass, pad, a quiet plucked arpeggio), intro drone, final chord | Synthesised sample by sample in `scripts/make-audio.mjs` | Original work of this project, same licence as the repo |
| Sound effects in `audio/sfx/` (29 files): card slides and shoves (whooshes, reversed for swells), poker chips (bet!), wood knocks (meh.), punches, metal, bell and low explosions (nah. and the slams), glitches and a scratch (can't read.), clicks, ticks, a switch, drops | [Kenney](https://www.kenney.nl) Casino Audio, Impact Sounds, Interface Sounds and Sci-fi Sounds, taken from the [soundcn](https://github.com/kapishdima/soundcn) collection (which lists each of them as CC0); pitched, reversed and given a room by `scripts/make-audio.mjs` | CC0 1.0 (public domain); `audio/sfx/Kenney-License.txt`. Credit is not required; Kenney is credited here anyway. |
| Spoken verdicts (`audio/voice/verdict_nah.mp3`, `verdict_bet.mp3`, `verdict_meh.mp3`, `verdict_cant_read.mp3`) | Supplied by the project owner for this video; trimmed and levelled by `scripts/make-audio.mjs` | Owned by the project owner |
| Echo tails and loudness (about -14 LUFS, peaks under -1.5 dBTP) | ffmpeg `aecho`, `loudnorm` | ffmpeg (LGPL/GPL); the output is ours |

Not used: soundcn also carries sounds marked "© Blizzard Entertainment, All Rights Reserved"; none
of those are in this project.

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
