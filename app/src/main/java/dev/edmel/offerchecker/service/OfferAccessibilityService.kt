package dev.edmel.offerchecker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
 * Watches the Uber Driver app (and only it, see accessibility_service_config.xml),
 * reads each offer card, scores it and shows the badge. It never performs any action in Uber.
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

    // De-dupe: each card is scored once (same pay + minutes + km within 30 s).
    private var lastKey: String? = null
    private var lastKeyAt = 0L
    private var cardMissingSince = 0L

    override fun onServiceConnected() {
        settingsRepo = SettingsRepository(this)
        alerts = Alerts(this)
        badge = BadgeOverlay(this) { x, y -> scope.launch { settingsRepo.setBadgePosition(x, y) } }
        scope.launch { settingsRepo.settings.collect { settings = it } }
        ServiceState.service = this
        ServiceState.runningFlow.value = true
        StatusNotification.show(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Uber's offer can be a pop-up over another app (e.g. Maps): a window appearing or
        // disappearing anywhere triggers a scan, but only Uber's windows are ever read.
        val fromUber = event.packageName?.toString() == UBER_PACKAGE
        if (!fromUber && event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (fromUber) ServiceState.uberEventsFlow.value++
        // Throttle bursts of events to one scan per ~100 ms.
        if (!scanPending) {
            scanPending = true
            handler.postDelayed(scan, SCAN_DELAY_MS)
        }
    }

    private fun uberRoots(): List<AccessibilityNodeInfo> {
        val roots = runCatching { windows.mapNotNull { it.root } }.getOrDefault(emptyList())
        ServiceState.windowsFlow.value = roots.mapNotNull { it.packageName?.toString() }.distinct()
        val fromWindows = roots.filter { it.packageName?.toString() == UBER_PACKAGE }
        if (fromWindows.isNotEmpty()) return fromWindows
        return listOfNotNull(rootInActiveWindow?.takeIf { it.packageName?.toString() == UBER_PACKAGE })
    }

    private fun scanScreen() {
        val scan = runCatching { CardReader.scan(uberRoots()) }.getOrNull()
        if (scan != null && scan.screenTexts.isNotEmpty()) {
            ServiceState.lastScreenFlow.value = System.currentTimeMillis() to scan.screenTexts.take(MAX_SCREEN_TEXTS)
        }
        val read = scan?.cardTexts?.let(OfferParser::parse) ?: ReadResult.NoOffer
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
        onNewCard(evaluation, raw)
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
        scope.cancel()
    }

    companion object {
        private const val SCAN_DELAY_MS = 100L
        private const val DEDUPE_MS = 30_000L
        private const val CARD_GONE_MS = 600L
        private const val MAX_SCREEN_TEXTS = 80
    }
}
