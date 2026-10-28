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
import dev.edmel.offerchecker.core.Offer
import dev.edmel.offerchecker.core.OfferParser
import dev.edmel.offerchecker.core.ReadResult
import dev.edmel.offerchecker.core.Scorer
import dev.edmel.offerchecker.core.Verdict
import dev.edmel.offerchecker.data.AppSettings
import dev.edmel.offerchecker.data.OfferDatabase
import dev.edmel.offerchecker.data.OfferEntry
import dev.edmel.offerchecker.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Watches for Uber offers, reads each offer card, scores it and shows the badge.
 * Text is read from Uber's windows through accessibility; when Uber doesn't share it, from a
 * screenshot with on-device text recognition. It never performs any action in Uber.
 */
class OfferAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var badge: BadgeOverlay
    private lateinit var alerts: Alerts
    @Volatile private var settings = AppSettings()

    private var scanPending = false
    private val scan = Runnable { scanPending = false; scanScreen() }
    private val poll = Runnable { if (!scanPending) scanScreen() }

    // De-dupe: each card is scored once (same pay + minutes + km within 30 s).
    private var lastKey: String? = null
    private var lastKeyAt = 0L
    private var cardMissingSince = 0L

    // Screenshot reader (Android 11+), used when Uber doesn't share the offer's text.
    private var ocrBusy = false
    private var lastOcrAt = 0L

    override fun onServiceConnected() {
        settingsRepo = SettingsRepository(this)
        alerts = Alerts(this)
        badge = BadgeOverlay(this) { x, y -> scope.launch { settingsRepo.setBadgePosition(x, y) } }
        scope.launch { settingsRepo.settings.collect { settings = it } }
        ServiceState.service = this
        ServiceState.runningFlow.value = true
        StatusNotification.show(this)
        ServiceState.log("Reader started")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Uber's offer can be a pop-up over another app (e.g. Maps): a window appearing or
        // disappearing anywhere triggers a scan, but only Uber's windows are ever read.
        val fromUber = isUberPackage(event.packageName)
        if (!fromUber && event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (fromUber) ServiceState.uberEventsFlow.value++
        // Throttle bursts of events to one scan per ~100 ms.
        if (!scanPending) {
            scanPending = true
            handler.postDelayed(scan, SCAN_DELAY_MS)
        }
    }

    /** Uber's windows on screen: their view trees, and the screen area they cover (for diagnostics). */
    private class UberWindows(val roots: List<AccessibilityNodeInfo>, val bounds: Rect?, val present: Boolean)

    private fun uberWindows(): UberWindows {
        val all = runCatching { windows }.getOrDefault(emptyList())
        val roots = mutableListOf<AccessibilityNodeInfo>()
        var bounds: Rect? = null
        var hiddenAppWindow = false
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
        }
        if (roots.isEmpty()) rootInActiveWindow?.takeIf { isUberPackage(it.packageName) }?.let { roots += it }
        ServiceState.windowsFlow.value = seen.distinct()
        logIfChanged("windows", "Windows on screen: ${seen.joinToString()}${bounds?.let { " · Uber area $it" } ?: ""}")
        // A window whose content Android won't share might be Uber's offer: let the screenshot reader check.
        return UberWindows(roots, bounds, roots.isNotEmpty() || hiddenAppWindow)
    }

    private fun scanScreen() {
        val uber = uberWindows()
        val scan = runCatching { CardReader.scan(uber.roots) }.getOrNull()
        if (scan != null && scan.screenTexts.isNotEmpty()) {
            ServiceState.lastScreenFlow.value = System.currentTimeMillis() to scan.screenTexts.take(MAX_SCREEN_TEXTS)
            if (scan.screenTexts.any { '$' in it || "km" in it }) {
                logIfChanged("a11y", "Uber text (accessibility): ${scan.screenTexts.take(20).joinToString(" | ")}")
            }
        }
        val read = scan?.cardTexts?.let(OfferParser::parse) ?: ReadResult.NoOffer

        // While Uber is online it keeps a window up (at least its floating bubble). Its offer card
        // isn't reported as a window and may fire no event, so keep looking about once a second.
        handler.removeCallbacks(poll)
        if (uber.present) handler.postDelayed(poll, POLL_MS)

        if (read == ReadResult.NoOffer && uber.present && Build.VERSION.SDK_INT >= 30) {
            // Uber shares no offer text: read the whole screen from a screenshot instead.
            // Not while Offer Checker itself is open, so it never reads its own diagnostics.
            if (rootInActiveWindow?.packageName?.toString() != packageName) requestScreenRead()
            return
        }
        handleRead(read, "accessibility")
    }

    @RequiresApi(30)
    private fun requestScreenRead() {
        if (ocrBusy) return
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
        ScreenReader.capture(this) { lines, screenHeight, error ->
            ocrBusy = false
            if (lines == null) {
                logIfChanged("ocr", "Screen read failed: $error")
                return@capture
            }
            logIfChanged("ocr-on", "Screen reader active (full screen, about once a second while Uber is online)")
            if (lines.any { '$' in it.text || "km" in it.text }) {
                logIfChanged("ocr", "Uber text (screenshot): ${lines.take(20).joinToString(" | ") { it.text }}")
            }
            val card = OfferParser.cardFromOcr(lines, screenHeight)
            handleRead(card?.let(OfferParser::parse) ?: ReadResult.NoOffer, "screenshot")
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
        val key = (read as? ReadResult.Read)?.offer?.let { "${it.pay}|${it.minutes}|${it.km}" }
            ?: "grey|${raw.filter(OfferParser::isTripLine)}"
        val now = SystemClock.elapsedRealtime()
        if (key == lastKey && now - lastKeyAt < DEDUPE_MS) return
        lastKey = key
        lastKeyAt = now
        ServiceState.log("Offer read from $source: ${evaluation.verdict} ${evaluation.ratesText}")
        onNewCard(evaluation, raw)
    }

    private val lastLogged = mutableMapOf<String, String>()

    /** Adds to the diagnostics log only when this kind of entry changed since last time. */
    private fun logIfChanged(kind: String, message: String) {
        if (lastLogged[kind] == message) return
        lastLogged[kind] = message
        ServiceState.log(message)
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
            handler.postDelayed({ if (!scanPending) scanScreen() }, CARD_GONE_MS + 50)
        } else if (now - cardMissingSince >= CARD_GONE_MS) {
            cardMissingSince = 0L
            lastKey = null
            badge.hide()
        }
    }

    private fun onNewCard(e: Evaluation, raw: List<String>) {
        val s = settings
        badge.show(e, s.badgeSize, s.badgeX, s.badgeY)
        if (s.vibration) alerts.vibrate(e.verdict)
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
        )
        scope.launch(Dispatchers.IO) { OfferDatabase.get(this@OfferAccessibilityService).offers().add(entry) }
    }

    /** The sample offer from the PRD: $6.07, 23 min, 11.5 km → red. Not logged. */
    fun showTestBadge() {
        val e = Scorer.score(Offer(BigDecimal("6.07"), 23, BigDecimal("11.5")), settings.thresholds)
        val s = settings
        badge.show(e, s.badgeSize, s.badgeX, s.badgeY)
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
        ServiceState.service = null
        ServiceState.runningFlow.value = false
        handler.removeCallbacksAndMessages(null)
        if (::badge.isInitialized) badge.hide()
        StatusNotification.cancel(this)
        ScreenReader.close()
        scope.cancel()
    }

    companion object {
        private const val SCAN_DELAY_MS = 100L
        private const val DEDUPE_MS = 30_000L
        private const val CARD_GONE_MS = 600L
        private const val MAX_SCREEN_TEXTS = 80
        private const val OCR_INTERVAL_MS = 900L
        private const val POLL_MS = 1000L
    }
}
