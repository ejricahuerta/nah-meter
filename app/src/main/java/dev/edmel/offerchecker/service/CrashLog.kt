package dev.edmel.offerchecker.service

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Keeps the last crash on the phone (never sent anywhere): the stack trace is written to a file
 * and shown in Status → Diagnostics, so "nah.meter keeps stopping" can be pasted and fixed.
 * A crash while the reader runs also posts [ReaderWatch]'s "turn it back on" notification.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"
    @Volatile private var installed = false

    fun install(context: Context) {
        ServiceState.init(context)
        if (installed) return
        installed = true
        val file = File(context.applicationContext.filesDir, FILE)
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                file.writeText(
                    "nah.meter $version crashed at $time on ${thread.name}, " +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        error.stackTraceToString().take(MAX_CHARS),
                )
            }
            runCatching { ServiceState.logNow(LogEntry.error("Crash (${thread.name})", error, frames = 8)) }
            // Android turns the reader off after a crash; if that happens mid-shift, say so.
            if (ServiceState.service != null) ReaderWatch.readerStopped(context.applicationContext)
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last crash's report, or null if the app hasn't crashed since it was cleared. */
    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    private const val MAX_CHARS = 8000
}
