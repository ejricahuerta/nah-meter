# Privacy policy

Version 1 · Effective October 29, 2026

nah.meter is made by edmel.dev. It is a tool to help you see each delivery offer and decide for yourself, and it only advises. This policy says what the app reads, what it keeps, where that goes, and what your account holds.

## The short version

- Offers, and anything else nah.meter reads on screen, never leave your phone.
- The app goes online only to sign you in and check your licence, with edmel.dev.
- Your account holds your email, your trial and paid dates, and your payments. Nothing else.
- No ads, analytics or tracking.
- It does nothing with what it sees except advise you on each offer and keep your own log on your phone.

## What nah.meter reads

nah.meter uses Android's Accessibility service to see delivery offers. Android sends it events from every app, but it only reads Uber's windows (apps named com.ubercab.something).

- **Offer text.** The pay, time, distance, restaurant and drop-off area on Uber's offer card.
- **Screenshots of Uber's windows.** When Uber hides its text, nah.meter takes a screenshot, crops it to Uber's windows and reads the text on your phone. The screenshot is dropped as soon as it's read. It is never saved or sent.
- **Other apps.** nah.meter doesn't read their content. When Uber's offer pops up over another app, the diagnostics log notes that app's package name, so a problem can be traced.

## What nah.meter does with it

nah.meter only helps you see and decide on each offer. It uses what it sees for two things:

- It works out its advice, the verdict, and shows or says it.
- It keeps the offer log, diagnostics and crash report on your phone, so you can look back in History and Home, and so a problem can be traced.

edmel.dev never receives what nah.meter sees: the app's only connection is to sign you in and check your licence, and neither carries anything about your offers. Nobody sells it, shares it, or uses it for ads, profiling, research or tracking.

## What nah.meter keeps on your phone

- **Your settings**, such as your rules and voice options.
- **The offer log**, up to the last 200 offers: time, pay, minutes, km, $/hr, $/km, verdict, number of orders, and the restaurant and drop-off area as read.
- **The diagnostics log**, up to the last 150 entries: when Uber came on screen, the text read from each offer card, each verdict, and errors.
- **The last crash report**, if the app crashed.
- **Your sign-in**: your email, sign-in tokens, and your trial and paid dates, so verdicts keep working offline during a shift.

## What can leave your phone

Only what you choose to share:

- **Export** in History saves the offer log as a CSV file where you pick.
- **Copy diagnostics** copies the diagnostics log to your clipboard.

The drop-off area is roughly where a customer lives. Treat exports and diagnostics with care, and only share them with people you trust.

## Your account and payments

nah.meter is a community app run at cost: free for 14 days, then $5 CAD a month by Interac e-Transfer, or free months if you ask.

- **What we hold.** Your email; when you signed up; your trial and paid dates; your pay code; and for each payment or free month: the date, amount, months added, the e-Transfer reference and the sender name it shows, and any note.
- **Why.** To sign you in, run your trial and licence, match e-Transfers to your account, answer you, and keep the records tax law requires.
- **Where.** In a database run by Supabase in the United States, behind edmel.dev, which is hosted by Vercel. Sign-in emails are sent by Resend. Data stored in the United States can be accessed under US law. Like any website, edmel.dev's hosting sees your connection details, such as your IP address, when the app checks in. These providers handle it under their own privacy terms.
- **Payments.** An Interac e-Transfer goes from your bank to ours. We see what it shows: your name, your email or phone, the amount and the message.
- **How long.** Until you delete your account. Deleting it removes your email, dates and payment records from our database. Our bank keeps its own record of e-Transfers, as banks must.
- **Your rights.** To see or correct what we hold, email contact@edmel.dev. To delete it, use Settings → Account → Delete account, or email us.

## Other software on your phone

- **Text recognition.** nah.meter reads screenshots with Google's ML Kit, which runs on the phone. Google's ML Kit terms say it can send usage statistics to Google. That reporting is removed from nah.meter, and our build fails if it ever comes back.
- **Text-to-speech.** When the spoken verdict is on, nah.meter hands your phone's text-to-speech engine the line to say, such as "nah. 14 an hour." Voices that need the internet are skipped. Your speech engine's own privacy terms apply to it.

## Permissions and why

- **Internet:** only to sign in and check your licence with edmel.dev.
- **Accessibility:** to see Uber's offers and read them, as above. It is never used to tap or type.
- **Notifications:** to show that nah.meter is on, and to tell you if it stops.
- **Battery unrestricted:** so Android doesn't stop nah.meter during a shift.
- **Vibrate:** to buzz with each verdict.
- **Seeing installed apps:** only Uber, DoorDash and SkipTheDishes, to open them from Home.

## Deleting your data

- History → Clear log deletes the offer log.
- Settings → Permissions → Diagnostics has Clear log and Clear last crash.
- Settings → Account → Delete account deletes your account on edmel.dev.
- Uninstalling nah.meter deletes everything it kept on your phone.

## Children

nah.meter is not for anyone under 18.

## Changes

If this policy changes, the app asks you to agree again before you can keep using it.

## Contact

Questions about privacy: contact@edmel.dev
