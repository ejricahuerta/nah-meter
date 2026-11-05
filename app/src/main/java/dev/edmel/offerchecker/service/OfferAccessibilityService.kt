package dev.edmel.offerchecker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import dev.edmel.offerchecker.core.Evaluation
import dev.edmel.offerchecker.core.OcrLine
import dev.edmel.offerchecker.core.Offer
import dev.edmel.offerchecker.core.OfferParser
import dev.edmel.offerchecker.core.OnlineState
import dev.edmel.offerchecker.core.ReadResult
import dev.edmel.offerchecker.core.ScoreContext
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.StatusRead
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.core.flagFor
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.IdleMode
import dev.edmel.offerchecker.data.OfferDatabase
import dev.edmel.offerchecker.data.OfferEntry
import dev.edmel.offerchecker.data.SettingsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/**
 * Watches for Uber offers, reads each offer card, scores it and shows the badge.
 * Text is read from Uber's windows through accessibility; when Uber doesn't share it, from a
 * screenshot with on-device text recognition. It never performs any action in Uber.
 */
class OfferAccessibilityService : AccessibilityService() {
    // A failed write or settings read is logged, never allowed to take the reader (and Uber's overlay) down.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> ServiceState.log(LogEntry.error("Error in the reader", e, frames = 3)) },
    )
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var badge: BadgeOverlay
    private lateinit var alerts: Alerts
    private var voice: Voice? = null
    /** The status notification shows the current reading state (on / paused). */
    private var notified = false
    @Volatile private var settings = AppSettings()

    private var scanPending = false
    private val scan = Runnable { scanPending = false; guarded("scan") { scanScreen() } }

    // De-dupe: each card is scored once (same pay + minutes + km within 30 s).
    private var lastKey: String? = null
    private var lastKeyAt = 0L
    private var cardMissingSince = 0L
    /** An offer card has been read and hasn't been gone for [CARD_GONE_MS] yet. */
    private var cardUp = false
    /** The offer read from the card on screen now, if any; cleared when the card goes away. */
    private var shownOffer: Offer? = null
    /** The last readable screenshot read of the card on screen, and the grey reads since. */
    private var lastScreenRead: String? = null
    private var greyReads = 0
    /** The offer card's top edge (screen px) from the latest read, to dock the verdict card. */
    private var cardTop: Int? = null
    /** Top of Uber's pop-up window (not the bubble), the fallback for [cardTop]. */
    private var popupTop: Int? = null

    // Screenshot reader (Android 11+), used when Uber doesn't share the offer's text.
    private var ocrBusy = false
    private var lastOcrAt = 0L

    /** The last online or offline line read, waiting for the next read to agree ([onStatusRead]). */
    private var pendingStatus: OnlineState? = null

    /** The last earnings figure read from Uber's map, waiting for the next read to agree ([onEarningsRead]). */
    private var pendingEarnings: BigDecimal? = null

    override fun onServiceConnected() {
        CrashLog.install(this)
        OnlineClock.started(this)
        settingsRepo = SettingsRepository(this)
        alerts = Alerts(this)
        voice = runCatching { Voice(this) }.getOrNull()
        badge = BadgeOverlay(this)
        scope.launch {
            settingsRepo.settings.collect {
                if (it.readingEnabled != settings.readingEnabled || !notified) {
                    notified = true
                    StatusNotification.show(this@OfferAccessibilityService, it.readingEnabled)
                }
                settings = it
                if (!it.readingEnabled) {
                    badge.hide()
                    // Paused, nah.meter can't see Uber: the online time stops here.
                    OnlineClock.stopped(this@OfferAccessibilityService)
                    pendingStatus = null
                    pendingEarnings = null
                }
            }
        }
        ServiceState.service = this
        ServiceState.runningFlow.value = true
        ReaderWatch.readerStarted(this)
        StatusNotification.show(this)
        ServiceState.log(
            "Reader started",
            "nah.meter ${runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"}",
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Uber's offer can be a pop-up over another app (e.g. Maps): a window appearing or
        // disappearing anywhere triggers a scan, but only Uber's windows are ever read.
        if (!settings.readingEnabled) return
        val fromUber = isUberPackage(event.packageName)
        if (!fromUber && event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (fromUber) ServiceState.uberEventsFlow.value++
        // Throttle bursts of events to one scan per ~100 ms.
        if (!scanPending) {
            scanPending = true
            handler.postDelayed(scan, SCAN_DELAY_MS)
        }
    }

    /** Uber's windows on screen: their view trees, and the screen area they cover. */
    private class UberWindows(val roots: List<AccessibilityNodeInfo>, val bounds: Rect?, val present: Boolean)

    private fun uberWindows(): UberWindows {
        val all = runCatching { windows }.getOrDefault(emptyList())
        val roots = mutableListOf<AccessibilityNodeInfo>()
        var bounds: Rect? = null
        var hiddenAppWindow = false
        var popup: Int? = null
        val seen = mutableListOf<String>()
        for (w in all) {
            val root = w.root
            val pkg = root?.packageName?.toString()
            seen += pkg ?: "?(type ${w.type})"
            if (root == null && w.type == AccessibilityWindowInfo.TYPE_APPLICATION) hiddenAppWindow = true
            if (root == null || !isUberPackage(pkg)) continue
            roots += root
            val r = Rect().also(w::getBoundsInScreen)
            bounds = bounds?.apply { union(r) } ?: r
            // Uber's offer pop-up spans the screen's width but not its height (its bubble is small).
            val screen = resources.displayMetrics
            if (r.width() >= screen.widthPixels * 8 / 10 && r.height() <= screen.heightPixels * 85 / 100) popup = r.top
        }
        popupTop = popup
        if (roots.isNotEmpty()) ReaderWatch.uberSeen(this)
        if (roots.isEmpty()) rootInActiveWindow?.takeIf { isUberPackage(it.packageName) }?.let { roots += it }
        ServiceState.windowsFlow.value = seen.distinct()
        logUberWindow(bounds, seen.distinct())
        // A window whose content Android won't share might be Uber's offer: let the screenshot reader check.
        return UberWindows(roots, bounds, roots.isNotEmpty() || hiddenAppWindow)
    }

    private fun scanScreen() {
        val uber = uberWindows()
        val scan = runCatching { CardReader.scan(uber.roots) }.getOrNull()
        if (scan != null && scan.screenTexts.isNotEmpty()) {
            ServiceState.lastScreenFlow.value = System.currentTimeMillis() to scan.screenTexts.take(MAX_SCREEN_TEXTS)
            // Only screens with an offer's trip line: during a trip Uber's map and earnings change
            // every few seconds and would push everything else out of the log.
            if (scan.screenTexts.any(OfferParser::isTripLine)) {
                logIfChanged("a11y", "Uber text (accessibility)", scan.screenTexts.take(MAX_LOG_LINES))
            }
        }
        val read = scan?.cardTexts?.let(OfferParser::parse) ?: ReadResult.NoOffer
        if (read is ReadResult.Read) cardTop = scan?.cardTop
        // Uber's "You're online" / "Go Online", if it shares them (an offer card says online itself),
        // and the earnings figure above its map.
        if (scan != null && scan.cardTexts == null) {
            onStatusRead(OfferParser.onlineStatus(scan.visibleTexts), "accessibility")
            onEarningsRead(OfferParser.uberEarnings(scan.visibleTexts))
        }
        if (read !is ReadResult.Read && uber.present && Build.VERSION.SDK_INT >= 30) {
            // Uber is on screen but shares no (or only part of the) offer text: read a screenshot
            // instead, keeping a partial accessibility read as the grey fallback.
            requestScreenRead(uber.bounds, read)
            return
        }
        handleRead(read, "accessibility")
    }

    @RequiresApi(30)
    private fun requestScreenRead(bounds: Rect?, accessibilityRead: ReadResult) {
        // A screenshot whose answer never came back doesn't stop the reader for good.
        if (ocrBusy && SystemClock.elapsedRealtime() - lastOcrAt < OCR_STUCK_MS) return
        val wait = lastOcrAt + OCR_INTERVAL_MS - SystemClock.elapsedRealtime()
        if (wait > 0) {
            // Too soon after the last screenshot: look again once allowed.
            if (!scanPending) {
                scanPending = true
                handler.postDelayed(scan, wait)
            }
            return
        }
        ocrBusy = true
        lastOcrAt = SystemClock.elapsedRealtime()
        try {
            ScreenReader.capture(this, bounds) { allLines, screenHeight ->
                ocrBusy = false
                guarded("screenshot") { onScreenRead(allLines, screenHeight, accessibilityRead, onlyUber = bounds != null) }
            }
        } catch (e: Throwable) {
            ocrBusy = false
            throw e
        }
    }

    /** [onlyUber]: the screenshot was cropped to Uber's windows (not the whole screen). */
    private fun onScreenRead(allLines: List<OcrLine>?, screenHeight: Int, accessibilityRead: ReadResult, onlyUber: Boolean) {
        if (allLines == null) {
            logIfChanged("ocr", "Screenshot failed", listOf("no screenshot, or text recognition found nothing"))
            return
        }
        // Never read our own verdict card or pill back in.
        val badgeArea = badge.screenRect
        val lines = if (badgeArea == null) allLines
        else allLines.filter { it.bottom <= badgeArea.top || it.top >= badgeArea.bottom }
        val card = OfferParser.cardFromOcr(lines, screenHeight)
        // Uber's "Finding trips" / "GO" and its earnings figure, only from Uber's own windows; an
        // offer card says online itself.
        if (card == null && onlyUber) {
            val texts = lines.map { it.text }
            onStatusRead(OfferParser.onlineStatus(texts), "screenshot")
            onEarningsRead(OfferParser.uberEarnings(texts))
        }
        // Only the card's own lines: map labels and whatever app is behind the pop-up stay out.
        if (card != null) logIfChanged("ocr", "Uber card text (screenshot)", card.take(MAX_LOG_LINES))
        cardTop = OfferParser.ocrCardTop(lines, screenHeight)
        val screenRead = card?.let(OfferParser::parse) ?: ReadResult.NoOffer
        if (screenRead !is ReadResult.Read && accessibilityRead is ReadResult.Unreadable) {
            handleRead(accessibilityRead, "accessibility")
        } else {
            handleRead(screenRead, "screenshot")
        }
    }

    private fun handleRead(read: ReadResult, source: String) {
        val s = settings
        val ctx = TripClock.scoreContext(this)
        val evaluation = Scorer.evaluate(read, s.mainThresholds, ctx)
        if (evaluation == null) {
            onCardGone()
            return
        }
        cardMissingSince = 0L
        cardUp = true
        keepWatching()
        // Uber only sends offers to couriers who are online.
        pendingStatus = OnlineState.ONLINE
        statusSeen(OnlineState.ONLINE, "an offer card")
        val raw = when (read) {
            is ReadResult.Read -> read.raw
            is ReadResult.Unreadable -> read.raw
            ReadResult.NoOffer -> emptyList()
        }
        val offer = (read as? ReadResult.Read)?.offer
        val key = offer?.let { "${it.pay}|${it.minutes}|${it.km}|${it.orders}|${it.addOn}" }
            ?: "grey|${raw.filter(OfferParser::isTripLine)}"
        // Screenshots of the same card are re-read about once a second and text recognition
        // varies between them ($19.04 read as $9.04 on one). A screenshot read only counts once
        // the next one agrees, so a one-off misread never shows, nor replaces the verdict on
        // screen. Accessibility text is exact and counts at once.
        val agreed = source != "screenshot" || agreesWithLastRead(offer?.let { key })
        val now = SystemClock.elapsedRealtime()
        if (key == lastKey && now - lastKeyAt < DEDUPE_MS) return
        // Once a card has been read, keep that verdict while it stays up: a grey re-read, or a
        // different pay with the same minutes and km, is noise.
        val shown = shownOffer
        if (lastKey != null && (offer == null || (shown != null && offer.minutes == shown.minutes && offer.km == shown.km))) return
        if (!agreed) return
        shownOffer = offer
        lastKey = key
        lastKeyAt = now
        // The idle-aware rules' verdict beside the fixed one: shown under it in Compare, logged in both.
        val idle = when (s.idleMode) {
            IdleMode.OFF -> null
            IdleMode.COMPARE -> s.idleThresholds?.let { Scorer.evaluate(read, it, ctx) }
            IdleMode.ON -> evaluation
        }
        ServiceState.log("Offer: ${NahCopy.word(evaluation.verdict)} ($source)", *offerDetails(evaluation, raw, ctx, idle, s).toTypedArray())
        onNewCard(evaluation, raw, ctx, idle)
    }

    /**
     * An online or offline line from Uber's screen ([OfferParser.onlineStatus]). It counts once the
     * next read agrees, so one misread "GO" never flips it; reads with no such line don't break the
     * pair. Each change goes in the diagnostics log with the line that showed it.
     */
    private fun onStatusRead(read: StatusRead?, source: String) {
        if (read == null) return
        if (read.state != pendingStatus) {
            pendingStatus = read.state
            // Look again soon to confirm a change, even if Uber's screen sends no more events.
            if (OnlineClock.status(this).value.state != read.state && !scanPending) {
                scanPending = true
                handler.postDelayed(scan, OCR_INTERVAL_MS)
            }
            return
        }
        statusSeen(read.state, "${read.line} ($source)")
    }

    private fun statusSeen(state: OnlineState, seen: String) {
        if (OnlineClock.seen(this, state)) ServiceState.log("Uber: ${NahCopy.onlineWord(state)}", "seen: $seen")
    }

    /**
     * Uber's earnings figure ([OfferParser.uberEarnings]). It counts once the next read agrees, so
     * one misread amount never counts as pay; a rise is a delivery paid ([TripClock]).
     */
    private fun onEarningsRead(amount: BigDecimal?) {
        if (amount == null) return
        if (pendingEarnings?.compareTo(amount) != 0) {
            pendingEarnings = amount
            return
        }
        TripClock.seen(this, amount)?.let { (title, detail) -> ServiceState.log(title, detail) }
    }

    /**
     * Whether this screenshot read ([key], or null for can't read) matches the one before it.
     * A grey read between two matching ones doesn't break them; two greys in a row agree.
     */
    private fun agreesWithLastRead(key: String?): Boolean {
        if (key == null) return ++greyReads >= 2
        greyReads = 0
        return (key == lastScreenRead).also { lastScreenRead = key }
    }

    /**
     * While a card is up, look again every second even when Uber sends no events (a still screen
     * sends none): that read confirms the verdict, and lets the verdict go when the card does.
     */
    private val watch = Runnable { watchCard() }

    private fun watchCard() {
        if (!cardUp || !settings.readingEnabled) return
        if (!scanPending) guarded("scan") { scanScreen() }
        if (cardUp) keepWatching()
    }

    private fun keepWatching() {
        handler.removeCallbacks(watch)
        handler.postDelayed(watch, OCR_INTERVAL_MS)
    }

    /**
     * Runs one step of the reader. If it throws, the error goes to the diagnostics log and the
     * reader carries on with the next event, instead of Android closing the app ("keeps stopping").
     */
    private inline fun guarded(step: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is OutOfMemoryError) System.gc()
            val entry = LogEntry.error("Error in $step", e, frames = 3)
            logIfChanged("error", entry.title, entry.details)
        }
    }

    /**
     * What the log says about a verdict: the numbers read, how they were scored, and why, with the
     * idle time and, when the idle-aware rules are on or compared, their verdict ([idle]).
     */
    private fun offerDetails(e: Evaluation, raw: List<String>, ctx: ScoreContext, idle: Evaluation?, s: AppSettings): List<String> {
        val o = e.offer ?: return listOf("card text:") + raw.map { "  $it" }
        val wait = (e.effectiveMinutes ?: o.minutes) - o.minutes
        return listOfNotNull(
            "pay ${Scorer.money(o.pay)}" + (if (o.addOn) " (add-on)" else "") + " · ${o.minutes} min · ${o.km.toPlainString()} km" +
                (if (o.orders > 1) " · ${o.orders} orders" else ""),
            if (wait > 0) "scored as ${e.effectiveMinutes} min (+$wait min)" else null,
            e.extraKm?.let {
                "extra: ${Scorer.km(it)} km back, unpaid" + (e.area?.let { a -> " (+${e.returnMin} min from $a)" } ?: "")
            },
            ctx.idleMin?.let { "waiting: $it min since Uber paid" } ?: "waiting: not known",
            e.tipCushion?.let {
                "tip cushion: −${Scorer.money(it)}" + (if (o.orders > 1) " (${o.orders} × ${Scorer.money(it.divide(BigDecimal(o.orders)))})" else "") +
                    ", scored on ${Scorer.money((o.pay - it).max(BigDecimal.ZERO))}"
            },
            "rates: ${e.ratesText}",
            e.failedRule?.let { "rule: $it" } ?: "rule: meets all targets",
            o.pickup?.let { "restaurant: $it" },
            flagFor(o.pickup, settings.flaggedPlaces)?.let { "flag: ${NahCopy.placeFlag(it.flag).lowercase()} (flagged as ${it.name})" },
            o.dropoff?.let { "drop-off: $it" },
            idle?.let {
                (if (s.idleMode == IdleMode.ON) "idle-aware rules" else "new rules") + ": ${NahCopy.word(it.verdict)}" +
                    (it.bar?.let { b -> " · bar ${Scorer.money(b)}/hr" } ?: "") +
                    (if (it.homeBonus) " · stack ending in ${it.area}" else "") +
                    (if (it !== e) " · ${it.ratesText}" else "")
            },
        )
    }

    /** Uber's area at the last "Uber on screen" entry; null while Uber isn't on screen. */
    private var loggedUberArea: Rect? = null

    /**
     * Logs Uber coming on screen, moving or resizing (its offer pop-up can appear over any app),
     * and leaving. Other apps opening and closing aren't logged: only Uber's windows matter.
     */
    private fun logUberWindow(area: Rect?, apps: List<String>) {
        if (area == loggedUberArea) return
        val wasOn = loggedUberArea != null
        loggedUberArea = area?.let(::Rect)
        val others = apps.filter { !isUberPackage(it) && it != packageName && it != "com.android.systemui" }
        when {
            area != null -> ServiceState.log(
                "Uber on screen", *listOfNotNull(
                    "area: ${area.toShortString()}",
                    others.takeIf { it.isNotEmpty() }?.let { "over: ${it.joinToString()}" },
                ).toTypedArray(),
            )
            wasOn -> ServiceState.log("Uber left the screen")
        }
    }

    private val lastLogged = mutableMapOf<String, LogEntry>()

    /** Adds to the diagnostics log only when this kind of entry changed since last time. */
    private fun logIfChanged(kind: String, title: String, details: List<String>) {
        val entry = LogEntry(System.currentTimeMillis(), title, details)
        if (lastLogged[kind]?.sameAs(entry) == true) return
        lastLogged[kind] = entry
        ServiceState.log(entry)
    }

    /**
     * Hide the badge once the card has been gone for a moment (avoids flicker while Uber redraws).
     * A card that comes back after that counts as a new card.
     */
    private fun onCardGone() {
        // A read with no card breaks a pair of matching reads.
        lastScreenRead = null
        greyReads = 0
        if (!cardUp && !badge.isShowing) return
        val now = SystemClock.elapsedRealtime()
        if (cardMissingSince == 0L) {
            cardMissingSince = now
            handler.postDelayed({ if (!scanPending) guarded("scan") { scanScreen() } }, CARD_GONE_MS + 50)
        } else if (now - cardMissingSince >= CARD_GONE_MS) {
            cardMissingSince = 0L
            cardUp = false
            val gone = shownOffer
            lastKey = null
            shownOffer = null
            badge.hide()
            // Learning what "accepted" looks like: what Uber shows a few seconds after the card.
            if (gone != null) AFTER_CARD_MS.forEach { delay -> handler.postDelayed({ logAfterCard(gone, delay) }, delay) }
        }
    }

    /**
     * Logs Uber's screen [afterMs] after an offer card went away (accepted, closed or expired), so
     * real examples show how to tell an accepted offer from a passed one. Only Uber's text.
     */
    private fun logAfterCard(offer: Offer, afterMs: Long) = guarded("after the card") {
        val uber = uberWindows()
        val texts = runCatching { CardReader.scan(uber.roots).screenTexts }.getOrDefault(emptyList())
        ServiceState.log(
            "After the card (${afterMs / 1000} s)",
            "offer: ${Scorer.money(offer.pay)} · ${offer.minutes} min · ${offer.km.toPlainString()} km" +
                (offer.pickup?.let { " · $it" } ?: ""),
            when {
                uber.roots.isEmpty() -> "Uber not on screen"
                else -> "Uber area: ${uber.bounds?.toShortString() ?: "?"}"
            },
            *(if (texts.isEmpty()) listOf("no text shared by Uber") else texts.take(MAX_AFTER_LINES)).toTypedArray(),
        )
    }

    /** [idle]: the idle-aware rules' verdict (the same as [e] when they're on); null when they're off. */
    private fun onNewCard(e: Evaluation, raw: List<String>, ctx: ScoreContext, idle: Evaluation?) {
        val s = settings
        // The courier's own flag on this restaurant, if any: shown and said, never part of the verdict.
        val flag = flagFor(e.offer?.pickup, s.flaggedPlaces)?.flag
        // Compare: the new rules' verdict on a line under the card's own.
        val compare = idle?.takeIf { s.idleMode == IdleMode.COMPARE && e.verdict != Verdict.GREY }
        guarded("verdict card") { badge.show(e, s.mainThresholds, s.compactBadge, cardTop ?: popupTop, flag, compare) }
        if (s.vibration) runCatching { alerts.vibrate(e.verdict) }
        if (s.voice && e.verdict in s.voiceVerdicts) voice?.say(NahCopy.speechParts(e, s, flag), s)
        ServiceState.lastReadFlow.value = LastRead(System.currentTimeMillis(), e, raw)
        val offer = e.offer ?: return
        if (e.verdict == Verdict.GREY) return
        val entry = OfferEntry(
            time = System.currentTimeMillis(),
            pay = offer.pay.toDouble(),
            minutes = e.effectiveMinutes ?: offer.minutes,
            km = offer.km.toDouble(),
            hourly = e.hourly!!.toDouble(),
            perKm = e.perKm?.toDouble(),
            verdict = e.verdict.name,
            stacked = offer.stacked,
            orders = offer.orders,
            addOn = offer.addOn,
            pickup = offer.pickup,
            dropoff = offer.dropoff,
            cardMinutes = offer.minutes,
            extraKm = e.extraKm?.toDouble(),
            tipCushion = e.tipCushion?.toDouble(),
            idleMin = ctx.idleMin,
            returnMin = e.returnMin,
            returnArea = e.area,
            barHourly = idle?.bar?.toDouble(),
            idleVerdict = idle?.verdict?.name,
        )
        // The verdict always shows; the log only gets offers it doesn't already have.
        scope.launch {
            val logged = withContext(Dispatchers.IO) { OfferDatabase.get(this@OfferAccessibilityService).offers().addIfNew(entry) }
            if (!logged) ServiceState.log("Offer not logged again", "same as one already in the log; verdict still shown")
        }
    }

    /** The sample offer from the PRD: $6.07, 23 min, 11.5 km → red. Not logged. */
    fun showTestBadge() {
        val s = settings
        val sample = Offer(BigDecimal("6.07"), 23, BigDecimal("11.5"))
        val ctx = TripClock.scoreContext(this)
        val e = Scorer.score(sample, s.mainThresholds, ctx)
        val compare = if (s.idleMode == IdleMode.COMPARE) s.idleThresholds?.let { Scorer.score(sample, it, ctx) } else null
        badge.show(e, s.mainThresholds, s.compactBadge, null, compare = compare)
        if (s.vibration) alerts.vibrate(e.verdict)
        if (s.voice) voice?.say(NahCopy.speechParts(e, s), s)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (ServiceState.service !== this) return
        ServiceState.log("Reader stopped", "the accessibility service was turned off or closed")
        OnlineClock.stopped(this)
        ServiceState.service = null
        ServiceState.runningFlow.value = false
        handler.removeCallbacksAndMessages(null)
        if (::badge.isInitialized) badge.hide()
        // Turned off mid-shift (Android does this after a crash): offer the way back.
        ReaderWatch.readerStopped(this)
        ScreenReader.close()
        voice?.shutdown()
        voice = null
        scope.cancel()
    }

    companion object {
        private const val SCAN_DELAY_MS = 100L
        private const val DEDUPE_MS = 30_000L
        private const val CARD_GONE_MS = 600L
        private const val MAX_SCREEN_TEXTS = 80
        private const val OCR_INTERVAL_MS = 1000L
        private const val MAX_LOG_LINES = 20
        private val AFTER_CARD_MS = listOf(4_000L, 12_000L)
        private const val MAX_AFTER_LINES = 15
        private const val OCR_STUCK_MS = 10_000L
    }
}
