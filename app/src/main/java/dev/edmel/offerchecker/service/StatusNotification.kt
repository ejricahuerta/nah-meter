package dev.edmel.offerchecker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import dev.edmel.offerchecker.R
import dev.edmel.offerchecker.ui.MainActivity

/** The ongoing "Offer checker on" notice shown while the reader runs. */
object StatusNotification {
    private const val CHANNEL = "status"
    const val ID = 1

    fun build(context: Context, reading: Boolean = true): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reader status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows that nah.meter is reading Uber offers"
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Pause / Resume right from the shade, without opening the app (handy while driving).
        val toggle = PendingIntent.getBroadcast(
            context, 0, Intent(context, PauseReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(if (reading) "nah.meter is on" else "nah.meter is paused")
            .setContentText(if (reading) "bet, meh or nah above every Uber offer. Read-only." else "No verdicts until you resume.")
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_stat_check), if (reading) "Pause" else "Resume", toggle,
                ).build(),
            )
            .setOngoing(true)
            .build()
    }

    fun show(context: Context, reading: Boolean = true) {
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID, build(context, reading)) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
