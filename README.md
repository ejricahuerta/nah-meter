# nah.meter

An Android app that reads each Uber Eats delivery offer as it appears and answers in one word,
docked just above the offer card within about a second: **bet!**, **meh.** or **nah.** So you can
decide without doing math under the 15-second timer. "nah." for short.

- **Advice only.** It never taps anything in Uber: no auto-accept, no auto-decline.
- **Offline.** No internet permission at all; nothing leaves the phone.
- **Android 8.0+**, sideloaded APK (not on Google Play).
- The look follows the nah.meter design system: black and white, Geist, heavy lowercase type, and
  colour only on the verdict word. Mono (dark) by default, Daylight when the phone is in light mode.

## Download

**[Download the latest APK](../../releases/latest/download/nahmeter.apk)**
(every push to `main` builds a new signed release; see [Releases](../../releases)).

## iPhone: not supported yet

nah.meter is Android only for now. iPhone support is planned once the project is funded.

iOS doesn't let one app read another app's screen or draw over it, so the Android approach (a
verdict card docked over Uber's offer) can't be ported as is. The two routes looked at:

| Route | How it would work | What it needs |
| --- | --- | --- |
| Shortcut + Back Tap | Double-tap the back of the phone on an offer; a Shortcut screenshots it, reads it on the phone and shows the verdict as a banner. | Nothing to install; one tap per offer. |
| Native iPhone app | A screen broadcast started once per shift; on-device text recognition reads each offer and posts the verdict as a notification. | A Mac and an Apple Developer account, TestFlight distribution, roughly 1–2 weeks of work. |

The scoring rules and offer patterns in `core/` carry over to either route.

## How offers are scored

`$/hr = pay ÷ minutes × 60` · `$/km = pay ÷ km` · minutes = Uber's total + optional wait buffer.

| Order | Check                  | Default                                              | If it fails |
|-------|------------------------|------------------------------------------------------|-------------|
| 1     | Minimum pay            | 1 order $5.50 · 2 orders $9 · 3+ $12 · add-on $4     | nah.        |
| 2     | Max km                 | off                                                  | nah.        |
| 3     | Strict rate            | ≥ $20.00/hr                                          | nah.        |
| 4     | The other rate         | ≥ $1.00/km                                           | meh.        |
| 5     | All passed             | —                                                    | bet!        |

$/hr is the strict rate by default. **Strict on $/km** (Rules, or the "Strict on" tile in the
notification shade) swaps them for busy times, when traffic stretches the minutes but not the km:
then missing $1.00/km is nah. and missing $20.00/hr is meh.

The minimum depends on the card: a stack of 2 or 3+ orders has its own, and so does an "Add a
delivery" add-on, whose pay is only what it adds to a trip you already have ($0 skips the add-on
minimum). A stack under its minimum says "Under $9 for 2 orders".

**Tip cushion** (Rules, off by default): Uber's pay includes the estimated tip, and the card
doesn't say how much of it is tip. A customer who doesn't tip, or takes the tip back, makes the
offer worth less. Set a cushion, say $1 per order, and every offer is scored as if its pay were
that much lower for each order (a 3-order card at $1 = $3 off): the minimum, $/hr and $/km all
use the lower pay. It's a big lever: at $2 per order most offers turn nah.

The first check that fails sets the colour. Exactly meeting a target passes. If a number can't be
read, the card says **can't read.**, never a guess. Every threshold is editable on the Rules tab.

Example: $6.07, 23 min, 11.5 km → **nah.** `$15.83/hr`, with "Under your $20 · $0.53/km" underneath.

## First-time setup on the phone (about 5 minutes)

1. Download the APK on the phone and tap it.
2. Allow **Install unknown apps** for your browser or file app when asked.
3. If Play Protect warns, tap **More details → Install anyway**.
4. Open **nah.meter**; Settings → Permissions lists what's missing, with a button for each.
5. **Android 13+:** Settings → Apps → nah.meter → **⋮ → Allow restricted settings**
   (sideloaded apps need this before step 6).
6. Settings → Accessibility → **nah.meter → On**.
7. Battery → **Unrestricted** for the app.
   Samsung: also add it to **Never sleeping apps**. Xiaomi/Redmi: turn on **Autostart** and set
   battery to **No restrictions**. Settings → Permissions links to the right page.
8. Allow notifications (for the "Offer checker on" notice).
9. Open Uber Driver and wait for an offer, or tap **Show** next to "Show a test verdict" in Settings.

**Using the verdict:** a black card docks 8dp above Uber's offer card: the word, the strict rate
($/hr, or $/km when strict on $/km), the reason and the other rate. Turn on **Compact verdict** in Thresholds for a small pill at the top instead (tap it
to expand). Tap the card to dismiss it; it also leaves when the offer closes or after 20 s. Haptics:
bet! one short buzz, meh. two short, nah. one long. The **Read offers** switch on Status pauses
everything.

**Voice:** Settings → Voice reads the verdict out loud. The **Toronto voice** (on by default) says
the word as a recorded line ("nah fam.", "meh, your call fam.", "bet, say less!"), then the
phone's own voice reads the rate. Turn it off to hear the phone's voice for everything.

**Updates:** install the new APK over the old one. Settings and the offer log are kept as
long as the signing secrets below are set, because every build is then signed with the same key.

## Features

- Offer detection limited to Uber apps (Uber Eats `com.ubercab.eats`, Uber Driver
  `com.ubercab.driver`, any `com.ubercab.*`) through an Accessibility Service; only text
  inside the offer card is read. When Uber hides its text, the app reads a screenshot of
  Uber's windows with on-device text recognition (Android 11+).
- Floating badge (accessibility overlay: no "draw over apps" permission needed).
- Four tabs: **Home** (a power button for reading on or off, today's offers, the delivery apps to
  open, your rules in one line), **History** (the offer log), **Rules** (which rate is strict, $/hr, $/km, max km,
  unpaid extra km for the drive back, wait buffer, and minimum pay for 1, 2 and 3+ orders and add-ons, with the
  tip cushion, and the idle-aware rules) and **Settings** (permissions, verdict card, voice, haptics, appearance,
  delivery apps).
- Settings → Permissions: each setup step with a one-tap link, and Diagnostics with the **raw
  text** it saw (for diagnosing a grey badge).
- History: the last 2000 offers by day (offers and average $/hr per day), filtered by bet, meh or nah,
  with CSV export. Tap an offer for its restaurant, drop-off and rates.
- Idle-aware rules (Rules, off by default; Compare shows both verdicts first): the $/hr bar drops
  the longer Uber hasn't paid ($14 after 10 min, $12 after 25, sooner after 10pm), just under the
  bar is meh., far drop-offs add their drive back by area ("+30 min back from Stouffville"), and a
  stack ending home is bet!. nah.meter knows Uber paid when the earnings figure above Uber's map
  goes up; Home shows what Uber paid today and per online hour, today and in the last 2 hours.
- Stacked offers are scored on the combined numbers, with 2 min per order for waiting on food,
  against the minimum for their number of orders.
- Home's Uber tile says whether Uber last showed you **Online** or **Offline**, and Today (from
  4am, so a night shift is one day) shows your time online in Uber. It's read from Uber's own screen ("Finding trips", "Go Online"), on
  the phone. Other delivery apps' screens are never read.
- Restaurant flags: open an offer in History and flag its restaurant **Slow pickup** or **Was
  closed**. Later offers from that restaurant show the flag on the verdict card and the voice says
  it; the verdict itself doesn't change. Settings → Flagged restaurants lists them. (nah.meter
  can't check whether a restaurant is open: it has no internet.)

## Project layout

| Path | What |
|------|------|
| `core/` | Pure Kotlin: `OfferParser` (all text patterns in one file) and `Scorer` (the five rules), with unit tests for every PRD test case. |
| `app/` | Android app: accessibility service, badge overlay, Compose screens, DataStore settings, Room offer log. |
| `.github/workflows/build.yml` | Runs the tests, builds the signed APK and publishes it as the latest release. |

## Contributing

`main` only changes through merged pull requests; every merge publishes a release. After cloning,
turn on the repo's git hook that blocks direct pushes to `main`:

```sh
git config core.hooksPath .githooks
```

## Building

- **GitHub Actions (default):** merge a pull request into `main`; the workflow attaches `nahmeter.apk` to a new release. Pull requests get the same build without a release.
- **Android Studio:** open the folder, then *Build → Generate APKs*, or run
  `./gradlew :core:test :app:assembleRelease` (output in `app/build/outputs/apk/release/`).

### Signing key (do this once)

Android only installs an update over the old app if both are signed with the same key.
CI reads the key from three repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` of your keystore |
| `KEYSTORE_PASSWORD` | the keystore (and key) password |
| `KEY_ALIAS` | the key alias, e.g. `offer-checker` |

Create a keystore with:

```sh
keytool -genkeypair -keystore release.jks -alias offer-checker -keyalg RSA -keysize 2048 -validity 36500
```

Keep `release.jks` and its password somewhere safe and out of the repo. Until the secrets
are set, CI signs with a throwaway key: the APK installs fine, but each new build needs the
old app uninstalled first. Local builds without `KEYSTORE_FILE` set use the debug key.

## If Uber changes its offer screen

The patterns live in `core/src/main/kotlin/dev/edmel/offerchecker/core/OfferParser.kt`.
Check the raw text under Settings → Permissions → Diagnostics, adjust the pattern, add a test with that text,
and push; a new APK is built automatically.

## Known unknowns

- What a stacked (2-order) card says exactly isn't confirmed yet; the parser looks for
  "2 orders / deliveries / pickups" or "×2". Send a screenshot to confirm.
- If Uber stops exposing offer text to accessibility, the planned fallback is on-device
  screen capture + ML Kit text recognition (not in v1).

Uber's terms discourage third-party tools. This app is strictly read-only to keep that risk
low, but using it is at your own risk.
