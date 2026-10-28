package dev.edmel.offerchecker.service

import dev.edmel.offerchecker.core.Evaluation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val UBER_PACKAGE = "com.ubercab.driver"

/** The last card the reader saw, with the raw text it read (for diagnosing parse misses). */
data class LastRead(val time: Long, val evaluation: Evaluation, val raw: List<String>)

/** Shared between the accessibility service and the app's screens (same process). */
object ServiceState {
    internal val runningFlow = MutableStateFlow(false)
    val running: StateFlow<Boolean> = runningFlow

    internal val lastReadFlow = MutableStateFlow<LastRead?>(null)
    val lastRead: StateFlow<LastRead?> = lastReadFlow

    /** Diagnostics: how many Uber screen events arrived, and the text of Uber's screen at the last scan. */
    internal val uberEventsFlow = MutableStateFlow(0)
    val uberEvents: StateFlow<Int> = uberEventsFlow

    internal val lastScreenFlow = MutableStateFlow<Pair<Long, List<String>>?>(null)
    val lastScreen: StateFlow<Pair<Long, List<String>>?> = lastScreenFlow

    /** Apps with a window on screen at the last scan (Uber's offer pop-up should be among them). */
    internal val windowsFlow = MutableStateFlow<List<String>>(emptyList())
    val windows: StateFlow<List<String>> = windowsFlow

    @Volatile internal var service: OfferAccessibilityService? = null

    /** Shows a sample badge. Returns false when the reader is off. */
    fun showTestBadge(): Boolean = service?.showTestBadge() != null
}
