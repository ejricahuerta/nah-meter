# Privacy policy

Version 1 · Effective October 29, 2026

nah.meter is made by edmel.dev. This policy says what the app reads, what it keeps, and where that goes.

## The short version

- No account and no sign-in.
- No internet permission: the app can't send anything anywhere.
- No ads, analytics or tracking.
- What it reads and keeps stays on your phone, unless you choose to share it.

## What nah.meter reads

nah.meter uses Android's Accessibility service to see delivery offers. Android sends it events from every app, but it only reads Uber's windows (apps named com.ubercab.something).

- **Offer text.** The pay, time, distance, restaurant and drop-off area on Uber's offer card.
- **Screenshots of Uber's windows.** When Uber hides its text, nah.meter takes a screenshot, crops it to Uber's windows and reads the text on your phone. The screenshot is dropped as soon as it's read. It is never saved or sent.
- **Other apps.** nah.meter doesn't read their content. When Uber's offer pops up over another app, the diagnostics log notes that app's package name, so a problem can be traced.

## What nah.meter keeps on your phone

- **Your settings**, such as your rules and voice options.
- **The offer log**, up to the last 200 offers: time, pay, minutes, km, $/hr, $/km, verdict, number of orders, and the restaurant and drop-off area as read.
- **The diagnostics log**, up to the last 150 entries: when Uber came on screen, the text read from each offer card, each verdict, and errors.
- **The last crash report**, if the app crashed.

## What can leave your phone

Only what you choose to share:

- **Export** in History saves the offer log as a CSV file where you pick.
- **Copy diagnostics** copies the diagnostics log to your clipboard.

The drop-off area is roughly where a customer lives. Treat exports and diagnostics with care, and only share them with people you trust.

## Other software on your phone

- **Text recognition.** nah.meter reads screenshots with Google's ML Kit, which runs on the phone. Google's ML Kit terms say it can send usage statistics to Google. Because nah.meter has no internet permission, nothing can be sent.
- **Text-to-speech.** When the spoken verdict is on, nah.meter hands your phone's text-to-speech engine the line to say, such as "nah. 14 an hour." Voices that need the internet are skipped. Your speech engine's own privacy terms apply to it.

## Permissions and why

- **Accessibility:** to see Uber's offers and read them, as above. It is never used to tap or type.
- **Notifications:** to show that nah.meter is on, and to tell you if it stops.
- **Battery unrestricted:** so Android doesn't stop nah.meter during a shift.
- **Vibrate:** to buzz with each verdict.
- **Seeing installed apps:** only Uber, DoorDash and SkipTheDishes, to open them from Home.

## Deleting your data

- History → Clear log deletes the offer log.
- Settings → Permissions → Diagnostics has Clear log and Clear last crash.
- Uninstalling nah.meter deletes everything it kept.

## Children

nah.meter is not for anyone under 18.

## Changes

If this policy changes, the app asks you to agree again before you can keep using it.

## Contact

Questions about privacy: contact@edmel.dev
