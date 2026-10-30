package dev.edmel.offerchecker.service

import android.content.Context
import dev.edmel.offerchecker.core.Evaluation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.Executors

/** Uber apps whose offers are read: Uber Driver, Uber Eats and any other com.ubercab app. */
val KNOWN_UBER_PACKAGES = listOf("com.ubercab.driver", "com.ubercab.eats", "com.ubercab")

fun isUberPackage(pkg: CharSequence?): Boolean = pkg != null && pkg.startsWith("com.ubercab")

/**
 * One diagnostics log entry: a title and its details, copied as
 * ```
 * [2026-10-29 20:27:37] - Offer: Nah. (screenshot)
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

    companion object {
        private val TIME_FORMAT: ThreadLocal<java.text.SimpleDateFormat> =
            ThreadLocal.withInitial { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US) }
        private val HEADER = Regex("""^\[(.+?)] - (.*)$""")

        /** Reads back entries written by [format], separated by blank lines; oldest first. */
        fun parseAll(text: String): List<LogEntry> = text.split("\n\n").mapNotNull { block ->
            val lines = block.trim('\n').lines()
            val m = lines.firstOrNull()?.let(HEADER::matchEntire) ?: return@mapNotNull null
            val time = runCatching { TIME_FORMAT.get()!!.parse(m.groupValues[1])!!.time }.getOrNull() ?: return@mapNotNull null
            LogEntry(time, m.groupValues[2], lines.drop(1).map { it.removePrefix("- ") })
        }

        /** An error as a log entry: what it was, where it happened, and its cause. */
        fun error(title: String, e: Throwable, frames: Int = 1): LogEntry {
            val root = generateSequence(e) { it.cause }.last()
            return LogEntry(
                System.currentTimeMillis(), title,
                listOf("${e.javaClass.name}: ${e.message}") +
                    e.stackTrace.take(frames).map { "at $it" } +
                    (if (root !== e) listOf("caused by ${root.javaClass.name}: ${root.message}") else emptyList()),
            )
        }
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
        logFlow.update { (listOf(entry) + it).take(MAX_LOG) }
        val f = file ?: return
        writer.execute { save(f, entry) }
    }

    /**
     * The log is also kept in a file on the phone (never sent anywhere), so it survives the app
     * being closed or crashing: a crash's entry is the last thing written before the app dies.
     */
    @Volatile private var file: File? = null
    private val writer = Executors.newSingleThreadExecutor()

    /** Loads the saved log; call before logging, from the service and the app. */
    @Synchronized fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, LOG_FILE)
        val saved = runCatching { LogEntry.parseAll(f.readText()) }.getOrDefault(emptyList())
        logFlow.update { current -> (current + saved.asReversed()).take(MAX_LOG) }
        file = f
    }

    /** Writes [entry] now, on this thread: for a crash, when there is no later. */
    internal fun logNow(entry: LogEntry) {
        logFlow.update { (listOf(entry) + it).take(MAX_LOG) }
        file?.let { runCatching { save(it, entry) } }
    }

    private fun save(f: File, entry: LogEntry) {
        runCatching {
            f.appendText(entry.format() + "\n\n")
            // Keep the file to about the last MAX_LOG entries.
            if (f.length() > MAX_FILE_BYTES) f.writeText(logFlow.value.asReversed().joinToString("") { it.format() + "\n\n" })
        }
    }

    fun clearLog() {
        logFlow.value = emptyList()
        file?.let { f -> writer.execute { runCatching { f.delete() } } }
    }

    private const val MAX_LOG = 150
    private const val LOG_FILE = "diagnostics.log"
    private const val MAX_FILE_BYTES = 256 * 1024L

    @Volatile internal var service: OfferAccessibilityService? = null

    /** Shows a sample badge. Returns false when the reader is off. */
    fun showTestBadge(): Boolean = service?.showTestBadge() != null
}
