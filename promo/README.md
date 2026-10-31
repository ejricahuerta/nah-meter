# nah.meter promo video

A 46-second promo in two cuts, made with [Remotion](https://www.remotion.dev) (React → MP4):

- `NahPromoVertical`: 1080×1920 for Reels, TikTok and Shorts
- `NahPromoWide`: 1920×1080

The plan is in `STORYBOARD.md`; audio sources and licences are in `CREDITS.md`. This folder is
separate from the app: nothing here is built by Gradle or shipped in the APK.

## Build

Needs Node 22.18+ (the audio script imports `src/nah.ts` directly), ffmpeg, and FluidSynth with the
MuseScore General soundfont for the music (Ubuntu: `apt install fluidsynth musescore-general-soundfont`;
or set `SOUNDFONT` to another .sf2/.sf3).

```bash
cd promo
npm install
npm run render        # fonts, timeline + music + mix, then both MP4s into out/
npm run studio        # live preview in the browser
```

Renders go to `out/`, which is gitignored. Remotion uses the Chromium at
`/opt/pw-browsers/chromium` (set in `remotion.config.ts`); point `REMOTION_BROWSER` at another
Chrome or Chromium, or add `--browser-executable`, to use a different one.

## How it fits together

| File | What it does |
| --- | --- |
| `scripts/make-timeline.mjs` | Splits the voiceover (`audio/voice/voiceover.mp3`) into its ten lines (checking it finds exactly the script's sentences), trims the verdict clips, and lays the shots out so every line and clip plays in full. Writes `audio/build/speech/`, `src/timeline.json` and `src/voice.json`. |
| `src/timeline.json` | Generated: every shot, offer, speech placement and cue. The picture, the music and the mix all read it. |
| `src/nah.ts` | A port of `core/Scorer.kt` and `service/NahCopy.kt` (verdict words, strict rate, reasons): every number and reason on screen is computed, not typed. |
| `src/tokens.ts` | Colours and type from `ui/Nah.kt` (Mono). |
| `src/ui.tsx` | The verdict card, the stand-in offer card (with its timer), the wordmark. |
| `src/offers.ts` | The offers in the video, from the timeline, scored the app's way. |
| `src/Minimal.tsx` | The shots, laid out per aspect. |
| `scripts/make-music.mjs` | Composes the music from the timeline (drums, bass, electric piano, strings, piano), writes it as MIDI and renders it with FluidSynth to `audio/build/music.wav`. |
| `scripts/make-audio.mjs` | Takes that music, layers the recorded effects from `audio/sfx/`, places every voiceover line and verdict clip from the timeline (and fails if one would overlap or be cut off), ducks the music under them, and mixes to `public/audio/mix.wav`. |
| `audio/sfx/` | Recorded sound effects (Kenney, CC0). |
| `audio/voice/` | The owner's voiceover (`voiceover.mp3`) and verdict clips. Replace a file and re-run `npm run render`; if the new voiceover's sentences differ, update `LINES` in `make-timeline.mjs`. |
| `scripts/copy-fonts.mjs` | Copies Geist from the app into `public/fonts`. |
