package dev.edmel.offerchecker.service

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Keeps the last crash on the phone (never sent anywhere): the stack trace is written to a file
 * and shown in Status → Diagnostics, so "nahmeter keeps stopping" can be pasted and fixed.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"
    @Volatile private var installed = false

    fun install(context: Context) {
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
                    "nahmeter $version crashed at $time on ${thread.name}, " +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        error.stackTraceToString().take(MAX_CHARS),
                )
            }
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
