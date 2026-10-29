package dev.edmel.offerchecker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.edmel.offerchecker.R

/**
 * Notices when the reader stops mid-shift (a crash, or Android switching the accessibility
 * service off) and posts a notification that opens Accessibility settings to turn it back on.
 * "Mid-shift" means Uber was on screen in the last [WORKING_MS]; stopping at any other time
 * stays quiet.
 */
object ReaderWatch {
    private const val PREFS = "reader_watch"
    private const val LAST_UBER_AT = "last_uber_at"
    private const val CHANNEL = "reader_off"
    private const val ID = 2
    private const val WORKING_MS = 15 * 60 * 1000L
    private const val SAVE_EVERY_MS = 30 * 1000L

    @Volatile private var lastSaved = 0L

    /** Called while Uber is on screen; saved at most every 30 s. */
    fun uberSeen(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastSaved < SAVE_EVERY_MS) return
        lastSaved = now
        prefs(context).edit().putLong(LAST_UBER_AT, now).apply()
    }

    private fun wasWorking(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(LAST_UBER_AT, 0L) < WORKING_MS

    /** The reader is stopping: if Uber was open, say so and offer the way back. */
    fun readerStopped(context: Context) {
        runCatching {
            StatusNotification.cancel(context)
            if (wasWorking(context)) {
                context.getSystemService(NotificationManager::class.java).notify(ID, build(context))
            }
        }
    }

    /** The reader is back: the reminder has done its job. */
    fun readerStarted(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(ID) }
    }

    private fun build(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reader turned off", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When nah.meter stops reading while Uber is open"
            },
        )
        val settings = PendingIntent.getActivity(
            context, 1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("nah.meter stopped reading")
            .setContentText("Tap to turn it back on in Accessibility.")
            .setContentIntent(settings)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
