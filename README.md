# Worth It (Uber Eats Offer Checker)

An Android app that reads each Uber Eats delivery offer as it appears and docks a verdict,
**GO / MAYBE / SKIP**, just above the offer card within about a second, so you can accept or
decline without doing math under the 15-second timer. The look follows the Worth It design
system: black and white, Geist, and colour only on the verdict.

- **Read-only.** It never taps anything in Uber: no auto-accept, no auto-decline.
- **Offline.** No internet permission at all; nothing leaves the phone.
- **Android 8.0+**, sideloaded APK (not on Google Play).

## Download

**[Download the latest APK](../../releases/latest/download/offer-checker.apk)**
(every push to `main` builds a new signed release; see [Releases](../../releases)).

## How offers are scored

`$/hr = pay ÷ minutes × 60` · `$/km = pay ÷ km` · minutes = Uber's total + optional wait buffer.

| Order | Check                  | Default        | If it fails |
|-------|------------------------|----------------|-------------|
| 1     | Minimum pay            | pay ≥ $5.50    | SKIP        |
| 2     | Hourly rate (priority) | ≥ $20.00/hr    | SKIP        |
| 3     | Per-km rate            | ≥ $1.00/km     | MAYBE       |
| 4     | All passed             | —              | GO          |

The first check that fails sets the colour. Exactly meeting a target passes. If a number
can't be read, the badge says **CAN'T READ**, never a guess. Every threshold is editable
in Settings.

Example: $6.07, 23 min, 11.5 km → **SKIP**, `$15.83/hr · $0.53/km`, "Under $20/hr target".

## First-time setup on the phone (about 5 minutes)

1. Download the APK on the phone and tap it.
2. Allow **Install unknown apps** for your browser or file app when asked.
3. If Play Protect warns, tap **More details → Install anyway**.
4. Open **Offer Checker**; the Status screen lists what's missing, with a button for each.
5. **Android 13+:** Settings → Apps → Offer Checker → **⋮ → Allow restricted settings**
   (sideloaded apps need this before step 6).
6. Settings → Accessibility → **Offer Checker → On**.
7. Battery → **Unrestricted** for the app.
   Samsung: also add it to **Never sleeping apps**. Xiaomi/Redmi: turn on **Autostart** and set
   battery to **No restrictions**. The Status screen links to the right page.
8. Allow notifications (for the "Offer checker on" notice).
9. Open Uber Driver and wait for an offer, or tap **Test badge** on the Status screen.

**Using the badge:** the verdict card docks just above Uber's offer card; turn on
**Compact badge** in Thresholds for a small pill at the top instead. Tap it to dismiss. It
disappears when the offer closes or after 20 s. Vibration: GO one short, MAYBE two short,
SKIP one long (can be turned off). The **Read offers** switch on Status pauses everything.

**Updates:** install the new APK over the old one. Settings and the offer log are kept as
long as the signing secrets below are set, because every build is then signed with the same key.

## Features

- Offer detection limited to Uber apps (Uber Eats `com.ubercab.eats`, Uber Driver
  `com.ubercab.driver`, any `com.ubercab.*`) through an Accessibility Service; only text
  inside the offer card is read. When Uber hides its text, the app reads a screenshot of
  Uber's windows with on-device text recognition (Android 11+).
- Floating badge (accessibility overlay: no "draw over apps" permission needed).
- Settings: min pay, $/hr, $/km, wait buffer, vibration, badge size.
- Status screen: reader on/off, each setup step with a one-tap link, and the last offer read
  with the **raw text** it saw (for diagnosing a grey badge).
- Offer log: last 200 offers, today's average $/hr, CSV export.
- Stacked offers are scored on the combined numbers and marked `×2`.

## Project layout

| Path | What |
|------|------|
| `core/` | Pure Kotlin: `OfferParser` (all text patterns in one file) and `Scorer` (the four rules), with unit tests for every PRD test case. |
| `app/` | Android app: accessibility service, badge overlay, Compose screens, DataStore settings, Room offer log. |
| `.github/workflows/build.yml` | Runs the tests, builds the signed APK and publishes it as the latest release. |

## Building

- **GitHub Actions (default):** push to `main`; the workflow attaches `offer-checker.apk` to a new release.
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
Check the raw text on the Status screen, adjust the pattern, add a test with that text,
and push; a new APK is built automatically.

## Known unknowns

- What a stacked (2-order) card says exactly isn't confirmed yet; the parser looks for
  "2 orders / deliveries / pickups" or "×2". Send a screenshot to confirm.
- If Uber stops exposing offer text to accessibility, the planned fallback is on-device
  screen capture + ML Kit text recognition (not in v1).

Uber's terms discourage third-party tools. This app is strictly read-only to keep that risk
low, but using it is at your own risk.
