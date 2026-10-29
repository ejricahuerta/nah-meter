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
import dev.edmel.offerchecker.core.ReadResult
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.AppSettings
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
    @Volatile private var settings = AppSettings()

    private var scanPending = false
    private val scan = Runnable { scanPending = false; guarded("scan") { scanScreen() } }

    // De-dupe: each card is scored once (same pay + minutes + km within 30 s).
    private var lastKey: String? = null
    private var lastKeyAt = 0L
    private var cardMissingSince = 0L
    /** The offer read from the card on screen now, if any; cleared when the card goes away. */
    private var shownOffer: Offer? = null
    /** The offer card's top edge (screen px) from the latest read, to dock the verdict card. */
    private var cardTop: Int? = null
    /** Top of Uber's pop-up window (not the bubble), the fallback for [cardTop]. */
    private var popupTop: Int? = null

    // Screenshot reader (Android 11+), used when Uber doesn't share the offer's text.
    private var ocrBusy = false
    private var lastOcrAt = 0L

    override fun onServiceConnected() {
        CrashLog.install(this)
        settingsRepo = SettingsRepository(this)
        alerts = Alerts(this)
        badge = BadgeOverlay(this)
        scope.launch {
            settingsRepo.settings.collect {
                settings = it
                if (!it.readingEnabled) badge.hide()
            }
        }
        ServiceState.service = this
        ServiceState.runningFlow.value = true
        ReaderWatch.readerStarted(this)
        StatusNotification.show(this)
        ServiceState.log(
            "Reader started",
            "nahmeter ${runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"}",
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
        logIfChanged(
            "windows", "Windows on screen",
            listOfNotNull("apps: ${seen.joinToString()}", bounds?.let { "Uber area: ${it.toShortString()}" }),
        )
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
                guarded("screenshot") { onScreenRead(allLines, screenHeight, accessibilityRead) }
            }
        } catch (e: Throwable) {
            ocrBusy = false
            throw e
        }
    }

    private fun onScreenRead(allLines: List<OcrLine>?, screenHeight: Int, accessibilityRead: ReadResult) {
        if (allLines == null) {
            logIfChanged("ocr", "Screenshot failed", listOf("no screenshot, or text recognition found nothing"))
            return
        }
        // Never read our own verdict card or pill back in.
        val badgeArea = badge.screenRect
        val lines = if (badgeArea == null) allLines
        else allLines.filter { it.bottom <= badgeArea.top || it.top >= badgeArea.bottom }
        if (lines.any { OfferParser.isTripLine(it.text) }) {
            logIfChanged("ocr", "Uber text (screenshot)", lines.take(MAX_LOG_LINES).map { it.text })
        }
        val card = OfferParser.cardFromOcr(lines, screenHeight)
        cardTop = OfferParser.ocrCardTop(lines, screenHeight)
        val screenRead = card?.let(OfferParser::parse) ?: ReadResult.NoOffer
        if (screenRead !is ReadResult.Read && accessibilityRead is ReadResult.Unreadable) {
            handleRead(accessibilityRead, "accessibility")
        } else {
            handleRead(screenRead, "screenshot")
        }
    }

    private fun handleRead(read: ReadResult, source: String) {
        val evaluation = Scorer.evaluate(read, settings.thresholds)
        if (evaluation == null) {
            onCardGone()
            return
        }
        cardMissingSince = 0L
        val raw = when (read) {
            is ReadResult.Read -> read.raw
            is ReadResult.Unreadable -> read.raw
            ReadResult.NoOffer -> emptyList()
        }
        val offer = (read as? ReadResult.Read)?.offer
        val key = offer?.let { "${it.pay}|${it.minutes}|${it.km}" }
            ?: "grey|${raw.filter(OfferParser::isTripLine)}"
        val now = SystemClock.elapsedRealtime()
        if (key == lastKey && now - lastKeyAt < DEDUPE_MS) return
        // Screenshots of the same card are re-read about once a second and text recognition
        // varies between them. Once a card has been read, keep that verdict while it stays up:
        // a grey re-read, or a different pay with the same minutes and km, is noise.
        val shown = shownOffer
        if (shown != null && (offer == null || (offer.minutes == shown.minutes && offer.km == shown.km))) return
        shownOffer = offer
        lastKey = key
        lastKeyAt = now
        ServiceState.log("Offer: ${NahCopy.word(evaluation.verdict)} ($source)", *offerDetails(evaluation, raw).toTypedArray())
        onNewCard(evaluation, raw)
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

    /** What the log says about a verdict: the numbers read, how they were scored, and why. */
    private fun offerDetails(e: Evaluation, raw: List<String>): List<String> {
        val o = e.offer ?: return listOf("card text:") + raw.map { "  $it" }
        val wait = (e.effectiveMinutes ?: o.minutes) - o.minutes
        return listOfNotNull(
            "pay ${Scorer.money(o.pay)}" + (if (o.addOn) " (add-on)" else "") + " · ${o.minutes} min · ${o.km.toPlainString()} km" +
                (if (o.orders > 1) " · ${o.orders} orders" else ""),
            if (wait > 0) "scored as ${e.effectiveMinutes} min (+$wait min waiting)" else null,
            "rates: ${e.ratesText}",
            e.failedRule?.let { "rule: $it" } ?: "rule: meets all targets",
            o.pickup?.let { "restaurant: $it" },
            o.dropoff?.let { "drop-off: $it" },
        )
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
        if (lastKey == null && !badge.isShowing) return
        val now = SystemClock.elapsedRealtime()
        if (cardMissingSince == 0L) {
            cardMissingSince = now
            handler.postDelayed({ if (!scanPending) guarded("scan") { scanScreen() } }, CARD_GONE_MS + 50)
        } else if (now - cardMissingSince >= CARD_GONE_MS) {
            cardMissingSince = 0L
            lastKey = null
            shownOffer = null
            badge.hide()
        }
    }

    private fun onNewCard(e: Evaluation, raw: List<String>) {
        val s = settings
        guarded("verdict card") { badge.show(e, s.thresholds, s.compactBadge, cardTop ?: popupTop) }
        if (s.vibration) runCatching { alerts.vibrate(e.verdict) }
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
        )
        // The verdict always shows; the log only gets offers it doesn't already have.
        scope.launch {
            val logged = withContext(Dispatchers.IO) { OfferDatabase.get(this@OfferAccessibilityService).offers().addIfNew(entry) }
            if (!logged) ServiceState.log("Offer not logged again", "same as one already in the log; verdict still shown")
        }
    }

    /** The sample offer from the PRD: $6.07, 23 min, 11.5 km → red. Not logged. */
    fun showTestBadge() {
        val e = Scorer.score(Offer(BigDecimal("6.07"), 23, BigDecimal("11.5")), settings.thresholds)
        val s = settings
        badge.show(e, s.thresholds, s.compactBadge, null)
        if (s.vibration) alerts.vibrate(e.verdict)
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
        ServiceState.service = null
        ServiceState.runningFlow.value = false
        handler.removeCallbacksAndMessages(null)
        if (::badge.isInitialized) badge.hide()
        // Turned off mid-shift (Android does this after a crash): offer the way back.
        ReaderWatch.readerStopped(this)
        ScreenReader.close()
        scope.cancel()
    }

    companion object {
        private const val SCAN_DELAY_MS = 100L
        private const val DEDUPE_MS = 30_000L
        private const val CARD_GONE_MS = 600L
        private const val MAX_SCREEN_TEXTS = 80
        private const val OCR_INTERVAL_MS = 1000L
        private const val MAX_LOG_LINES = 20
        private const val OCR_STUCK_MS = 10_000L
    }
}
