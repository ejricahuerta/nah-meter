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

    @Volatile internal var service: OfferAccessibilityService? = null

    /** Shows a sample badge. Returns false when the reader is off. */
    fun showTestBadge(): Boolean = service?.showTestBadge() != null
}
