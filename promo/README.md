# nah.meter promo video

A 41-second promo in two cuts, made with [Remotion](https://www.remotion.dev) (React → MP4):

- `NahPromoVertical`: 1080×1920 for Reels, TikTok and Shorts
- `NahPromoWide`: 1920×1080

The plan is in `STORYBOARD.md`; audio sources and licences are in `CREDITS.md`. This folder is
separate from the app: nothing here is built by Gradle or shipped in the APK.

## Build

Needs Node 22.18+ (the audio script imports `src/nah.ts` directly) and ffmpeg.

```bash
cd promo
npm install
npm run render        # fonts + audio, then both MP4s into out/
npm run studio        # live preview in the browser
```

Renders go to `out/`, which is gitignored. Remotion uses the Chromium at
`/opt/pw-browsers/chromium` (set in `remotion.config.ts`); point `REMOTION_BROWSER` at another
Chrome or Chromium, or add `--browser-executable`, to use a different one.

## How it fits together

| File | What it does |
| --- | --- |
| `src/timeline.json` | Every scene and cue time. The picture and the audio both read it, so a retimed scene moves its sounds with it. |
| `src/nah.ts` | A port of `core/Scorer.kt` and `service/NahCopy.kt` (verdict words, strict rate, reasons): every number and reason on screen is computed, not typed. |
| `src/tokens.ts` | Colours and type from `ui/Nah.kt` (Mono). |
| `src/ui.tsx` | The verdict card, stand-in offer, status pill, rule steppers, wordmark. |
| `src/Phone.tsx` | The phone, the map with the offers, and the Rules screen. |
| `src/Promo.tsx` | Scenes and captions, laid out per aspect. |
| `scripts/make-audio.mjs` | Synthesises the music, layers the recorded effects from `audio/sfx/`, places the verdict clips from `audio/voice/`, mixes to `public/audio/mix.wav`, and writes each clip's loudness to `src/voice.json` for the speech chip. |
| `audio/sfx/` | Recorded sound effects (Kenney, CC0). |
| `audio/voice/` | The four spoken verdicts. Replace a file and re-run `npm run render` to change a voice line. |
| `scripts/copy-fonts.mjs` | Copies Geist from the app into `public/fonts`. |
