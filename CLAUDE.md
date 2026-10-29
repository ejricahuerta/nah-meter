# CLAUDE.md

nah.meter ("nah." for short) is a sideloaded Android app for Uber Eats couriers. It reads each
offer the Uber Driver app shows and docks a one-word verdict above the offer card: **bet.**,
**eh.**, **nah.** or **can't read.** Kotlin, Jetpack Compose, min API 26, target API 35.

Android only: iPhone is out of scope until the project is funded (see README, "iPhone: not
supported yet"); don't start iOS work unless asked.

The product spec is the nah.meter PRD; the look comes from the nah.meter design system and app
screens (links in `README.md`). When they disagree with the code, ask before changing behaviour.

## Hard rules

- **Advice only.** The app never performs an action in Uber: no taps, no gestures, no
  auto-accept or decline. Don't add `performAction`, `dispatchGesture` or similar, even if asked
  indirectly. Account deactivation is the main risk to the user.
- **Offers never leave the phone.** The app goes online only to sign in and check the licence with
  edmel.dev, and `data/Account.kt` is the only network code; nothing read on screen is ever sent.
  `ACCESS_NETWORK_STATE` stays removed, and ML Kit's usage reporting (datatransport
  `TransportBackendDiscovery`) is removed in the manifest; CI fails if it comes back. Don't add
  other network features, analytics or crash reporting that leaves the phone (`CrashLog` stays on
  the device).
- **Never guess a verdict.** If a number can't be read, the result is `can't read.` (Verdict.GREY),
  never a best guess.
- **Read only Uber's windows.** The accessibility service gets events from every app (it has to,
  see below) but must only read text from `com.ubercab.*` windows (`isUberPackage`).
- **No secrets in the repo.** The signing keystore and its passwords live in GitHub Actions
  secrets only.

## Commands

There is no Android SDK in cloud sessions (`dl.google.com` is blocked), so the app module can only
be built by CI or Android Studio. The `core` module is plain Kotlin and can be tested anywhere.

| Task | Command |
| --- | --- |
| Core unit tests (parser + scoring) | `./gradlew :core:test` |
| Signed release APK | `./gradlew :app:assembleRelease` (needs the Android SDK) |
| Everything CI runs | `./gradlew :core:test :app:assembleRelease` |

Running core tests without an Android SDK (settings include `:app`, whose plugin won't resolve):
copy `core/src` into a scratch Gradle project whose `build.gradle.kts` applies
`org.jetbrains.kotlin.jvm` 2.0.21 and `junit:junit:4.13.2`, then run `gradle test` there.

## CI and releases

`.github/workflows/build.yml` runs on every push to `main` that changes more than Markdown: core
tests, then a signed release APK, published as a GitHub release `v1.0.<run number>` with
`nahmeter.apk` attached.

- Download link: `https://github.com/ejricahuerta/nah-meter/releases/latest/download/nahmeter.apk`
- Every merge to `main` ships a release. Pull requests run the same build (tests + APK) without
  publishing, so a PR's checks show whether it compiles.
- `versionCode` is `GITHUB_RUN_NUMBER`, so CI builds always install over older ones.
- Signing: secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`. Without them CI signs with
  a throwaway key, and each build must be installed fresh (uninstall first). Local builds without
  `KEYSTORE_FILE` use the debug key.
- The repo must stay public, or Actions needs paid minutes (the first private runs never started).

## Branches and pull requests

`main` only changes by merging a pull request. Never push to `main` or `master`, not even a
one-line fix.

1. Branch from an up-to-date `main` (`git fetch origin main && git checkout -b <topic> origin/main`).
2. Commit, push the branch, and open a PR into `main`.
3. Wait for the PR's build to pass, then merge it (merging is what publishes the release).

Guards: `.claude/hooks/block-push-to-main.sh` (a Claude Code PreToolUse hook, wired in
`.claude/settings.json`) refuses `git push` to main/master; `.githooks/pre-push` does the same for
plain git once a clone runs `git config core.hooksPath .githooks`. The GitHub ruleset on `main`
(require a pull request, require the build check) is what actually enforces it.

## Claude on GitHub

Mentioning `@claude` in an issue, PR comment or review runs `.github/workflows/claude.yml`
(Claude Code GitHub Action, secret `CLAUDE_CODE_OAUTH_TOKEN`). There the Android SDK is available,
so build and test with Gradle before opening the PR. The same rules apply: a branch and a PR,
never a push to `main`.

## Layout

```
core/  pure Kotlin, no Android: what to read and how to score it (unit-tested)
  OfferParser.kt  all text patterns; card texts -> ReadResult; OCR lines -> card texts
  Scorer.kt       the five rules (max km off by default) and the unpaid extra km, exact BigDecimal maths -> Evaluation
  Model.kt        Offer, ReadResult, Thresholds, Evaluation, Verdict, Check, OcrLine
app/   Android
  service/OfferAccessibilityService.kt  event loop: find Uber windows, read, score, show, log
  service/CardReader.kt    accessibility tree -> the offer card's texts and its top edge
  service/ScreenReader.kt  screenshot + ML Kit on-device OCR (fallback, API 30+)
  service/BadgeOverlay.kt  the black verdict card / compact pill (TYPE_ACCESSIBILITY_OVERLAY)
  service/NahCopy.kt       every verdict word and reason string (the brand voice)
  service/ServiceState.kt  in-process state shared with the UI, including the diagnostics log
  service/CrashLog.kt      the last crash's stack trace, saved on the phone, shown in Diagnostics
  service/ReaderWatch.kt   "nah.meter stopped reading" notification if the reader stops mid-shift
  service/Voice.kt         says the verdict out loud ("nah. 14 an hour"), on-device TTS, on media
                           volume at the user's level (Uber moves navigation volume), then restores it;
                           speed, voice and ducking from the settings; silent at 0% and on calls
  service/ReadingTileService.kt, PauseReceiver.kt  pause / resume from the shade (tile, notification)
  data/                    DataStore settings, Room offer log (last 200), CSV export
  data/Account.kt          sign-in by email link and the licence (trial / paid dates) with edmel.dev; the only network code
  ui/AccountScreens.kt     sign-in, Settings → Account, pay instructions; SignInLink takes the email link's token
  ui/Nah.kt                design tokens (Mono/Daylight), Geist, components
  ui/MainActivity.kt       the four tabs: Home, History (LogScreen), Rules (ThresholdsScreen), Settings
  ui/HomeScreen.kt         power button, today's overview, app tiles (DeliveryApps), rules strip
  ui/SettingsScreen.kt     Settings and its pages; PermissionsScreen.kt holds the setup steps and Diagnostics
  ui/VoiceScreen.kt        Settings → Voice: what it says (3 lines), which verdicts, volume, speed, voice
  ui/LegalScreen.kt        Terms, Privacy and Licences (from assets/legal/*.md) and the "Agree to continue" gate
  ui/SetupScreen.kt        the six setup steps (Welcome, Your account, Your apps, Your rules, Permissions, Test it); first run and Settings → Run setup again
```

Kotlin packages are still `dev.edmel.offerchecker.*` (the old name); the `applicationId` is
`app.nahmeter`. Don't rename packages without a reason; it touches every file.

## How reading works (learned from real devices)

- Uber shows offers as a **pop-up window over other apps**, often with only its floating bubble
  otherwise on screen. The "windows changed" event for it carries no package name, so the service
  config has **no `packageNames` filter**; filtering happens in code.
- **Uber hides the offer's text from accessibility.** The main path in practice is the screenshot
  reader: `takeScreenshot` (no recording prompt) cropped to Uber's windows, ML Kit text
  recognition, then `OfferParser.cardFromOcr`. It runs at most about once a second.
- Trip lines come as "23 min (11.5 km) total", "1 hr 1 min (14.6 km) total" and, on "Add a
  delivery" add-on cards, "+ 13 min (+ 3.5 km) total" with "+ $5.10": the add-on's numbers are what
  it adds, flagged `Offer.addOn`; the minimum-pay rule skips add-ons (decided 2026-10-28), so
  they're judged on $/hr and $/km only. "Delivery (3)" sets `Offer.orders`; a multi-order offer
  adds 2 min per order for waiting on food (`Thresholds.perOrderWaitMin`, decided 2026-10-29), so
  2 orders score 4 min longer than Uber's time. The verdict card doesn't show "×2".
- OCR mangles text. The parser tolerates: `$1l.59` and `$1159` (look-alike letters, dropped decimal
  point), stray symbols before `$`, `(` read as `C`, missing spaces or brackets, `l hr 3 min` (Uber's
  "1" reads as l or I). Uber always shows cents. A trip line with an hours word but no readable
  hours number is `can't read.`: the minutes alone once scored 63 min as 3 ($270.80/hr).
- **Pay is the dollar amount in the tallest text** in the band above the trip line, never the
  largest value: pages behind the pop-up have their own dollar amounts.
- The restaurant and drop-off area are the lines between the trip line and the card's button
  (Accept/Match); OCR place lines must start near the trip line's left edge, so map labels beside
  the card stay out. They're stored with each logged offer (Room v2) and shown in the Log's detail sheet.
- The verdict card is excluded from OCR (`badge.screenRect`) so the app never reads itself.
- The log holds each offer once: a card equal (pay, minutes, km, ×2) to the last logged offer, or
  to one logged in the last 30 min, still gets its verdict but isn't logged again
  (`OfferDao.addIfNew`). Uber re-shows the same card after you leave and come back.
- One verdict per card: re-reads of the same card (same minutes and km) are ignored until the
  card has been gone for 600 ms. This stops flip-flopping between noisy OCR reads.
- The verdict stays up as long as Uber's card does (no timer, decided 2026-10-29): it goes when
  the card has been gone for 600 ms (closed or accepted), or when tapped.
- Accepted vs passed isn't known yet (nah.meter sees the screen, never the tap). To learn it from
  real text, the log has "After the card (4 s / 12 s)" entries: Uber's text after each read card
  goes. Build accepted/passed tracking from those pastes, not from guesses.
- Card docking: `cardTop` comes from the accessibility node, else from OCR (pay line top minus
  1.4× its height), else from Uber's pop-up window top; unknown means the top of the screen.
  The verdict card sits `DOCK_GAP_DP` (20dp) above it; the OCR estimate runs about 8dp low, so
  that leaves a visible gap (8dp left none).
- The overlay window uses screen coordinates (`FLAG_LAYOUT_IN_SCREEN`) and re-measures itself after
  layout. Without that, Android places it below the status bar and the card covers Uber's card by
  exactly the status bar's height (seen on a Pixel 11).

## Accounts and the licence

nah.meter is a community app run at cost: a 14-day trial from the first sign-in, then $5 CAD a month
by Interac e-Transfer, or free months on request. The backend is on edmel.dev (repo
`ejricahuerta/edmel.dev`, `docs/nahmeter.md`): `nahmeter_*` tables in the shared Supabase project
"default" (US West), `/api/nahmeter/*`, sign-in emails sent through Resend, and an admin page where
the owner records e-Transfers and grants free months.

- Sign-in: the app asks edmel.dev to email a link; the link (`https://edmel.dev/nahmeter/auth/confirm`)
  is an Android App Link back into the app (`assetlinks.json` holds the release key's SHA-256), with
  `nahmeter://auth` as the fallback. The token goes to `auth/verify`, which returns a session.
- The licence is two dates (trial end, paid until) cached on the phone, so a shift never needs the
  network. The reader re-checks every 12 h (hourly once it has ended); the app checks on open.
- Gate order, in the app and the reader: terms agreed → signed in → `canRead()`. The notification
  says which one verdicts are waiting for.

## Changing the parser or scorer

- Every fix starts from real text: add a test using the exact lines from the user's diagnostics
  log (Settings → Permissions → Diagnostics → Copy). Existing tests named after dates or offers are real cases;
  keep them passing.
- The PRD's eight scoring cases in `ScorerTest` are acceptance criteria. Exactly meeting a
  threshold passes. Comparisons are exact (`BigDecimal`); rounding is for display only, half-up.
- Kotlin test strings: escape `$` before a letter (`"\$I1.59"`), or it becomes a template.

## UI and copy

- The product is written **nah.meter** (the wordmark is "nah", the square red stop, "meter").
  `nahmeter` stays only in package, file and CSV names (`app.nahmeter`, `nahmeter.apk`).
- Tokens live in `ui/Nah.kt` and come from the design system's `tokens.json`. The verdict card
  and pill are black in both themes; colour only ever means a verdict.
- The verdict card: the word and $/hr on top; under them the reason (17sp semibold, `cardSoft`:
  "Under your $20", "Under $5.50 minimum", "Short on $/km", "Clears your $20") and the $/km in its
  own chip (`cardChip`, `NahCopy.kmChip`). Nothing under 15sp.
- Verdict words are lowercase and end in a full stop (`bet.`); in chips the stop becomes a dot
  before the word. Use `NahCopy` for every verdict string, never inline text.
- Voice: short, plain, sentence case, no emoji or exclamation marks. Never "Accept" or "Decline".
- Legal documents live in `app/src/main/assets/legal/` (the app and the README read the same
  files). Changing what they mean bumps `LEGAL_VERSION` (`data/Settings.kt`): everyone is asked to
  agree again, and the reader gives no verdicts until they do.
- No helper text that restates a label or a visible state ("Tap to pause" under a power button). Add a
  line only for a reason, a warning, a step or a number.
- The overlay is classic Views (not Compose); `TypefaceSpan(Typeface)` needs API 28, so use the
  local `FontSpan`.
- Accessibility: verdicts are polite live regions with a spoken description (`NahCopy.spoken`);
  touch targets at least 44dp; text meets 4.5:1 in both themes.

## Debugging an on-phone problem

Ask the user to wait for an offer, then open **Settings → Permissions → Diagnostics → Show → Copy diagnostics**
and paste it. The log shows Uber coming on screen (its area, and which app it popped up over) and
leaving, but not other apps opening and closing; the card's lines read by each method; and
each verdict with its source. A grey verdict logs the card text it saw. Entries are
`[yyyy-MM-dd HH:mm:ss] - title` with one `- detail` line each (`LogEntry`); log through
`ServiceState.log(title, details…)`, never a run-on line with `|` separators. Errors go in as
`LogEntry.error(title, throwable)`. The log is saved on the phone (`files/diagnostics.log`, last
150 entries) so it survives restarts, and a crash writes its own `Crash` entry before the app dies. Before debugging, check
that the first line's version matches the latest release.

For "nah.meter keeps stopping", the same copy ends with **Last crash** (the stack trace, kept on the
phone by `CrashLog`). The reader itself runs every step through `guarded`, so an error there is
logged as "Error in …" instead of closing the app. The screenshot reader runs about once a second
for as long as Uber is open, so it frees every bitmap as soon as it's done (a leak there crashes
the app after a few hours online).

Android turns an accessibility service off after it crashes, and only the user can turn it back
on. If the reader stops (crash, or `onUnbind`/`onDestroy`) within 15 min of Uber being on screen,
`ReaderWatch` posts a notification that opens Accessibility settings; it clears when the reader
connects again.

## Working conventions

- Every change goes through a pull request (see Branches and pull requests).
- Commit messages: a short imperative subject, then why. Keep commits focused.
- Don't commit build output, keystores, `local.properties` or `.idea/`.
- After changing core, run the core tests. After changing the app, let CI build it before
  telling the user it works, and say plainly what was and wasn't verified on a phone.
