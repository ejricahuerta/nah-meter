package dev.edmel.offerchecker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.edmel.offerchecker.R
import dev.edmel.offerchecker.ui.MainActivity

/** The ongoing "Offer checker on" notice shown while the reader runs. */
object StatusNotification {
    private const val CHANNEL = "status"
    const val ID = 1

    fun build(context: Context): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reader status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows that Offer Checker is reading Uber offers"
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("Offer checker on")
            .setContentText("Watching Uber Driver for offers. Read-only.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    fun show(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID, build(context)) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
