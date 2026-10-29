package dev.edmel.offerchecker.service

import dev.edmel.offerchecker.core.Evaluation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Uber apps whose offers are read: Uber Driver, Uber Eats and any other com.ubercab app. */
val KNOWN_UBER_PACKAGES = listOf("com.ubercab.driver", "com.ubercab.eats", "com.ubercab")

fun isUberPackage(pkg: CharSequence?): Boolean = pkg != null && pkg.startsWith("com.ubercab")

/**
 * One diagnostics log entry: a title and its details, copied as
 * ```
 * [2026-10-29 20:27:37] - Offer: nah. (screenshot)
 * - pay $13.54 · 63 min · 38.3 km · 2 orders
 * ```
 */
data class LogEntry(val time: Long, val title: String, val details: List<String> = emptyList()) {
    fun format(): String = buildString {
        append("[").append(TIME_FORMAT.get()!!.format(java.util.Date(time))).append("] - ").append(title)
        details.forEach { append("\n- ").append(it) }
    }

    /** Same title and details, whenever it was logged. */
    fun sameAs(o: LogEntry) = title == o.title && details == o.details

    private companion object {
        val TIME_FORMAT: ThreadLocal<java.text.SimpleDateFormat> =
            ThreadLocal.withInitial { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US) }
    }
}

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

    /** Diagnostics log, newest first: what the reader saw around each offer. */
    internal val logFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = logFlow

    internal fun log(title: String, vararg details: String) = log(LogEntry(System.currentTimeMillis(), title, details.toList()))

    internal fun log(entry: LogEntry) {
        logFlow.value = (listOf(entry) + logFlow.value).take(MAX_LOG)
    }

    private const val MAX_LOG = 60

    @Volatile internal var service: OfferAccessibilityService? = null

    /** Shows a sample badge. Returns false when the reader is off. */
    fun showTestBadge(): Boolean = service?.showTestBadge() != null
}
