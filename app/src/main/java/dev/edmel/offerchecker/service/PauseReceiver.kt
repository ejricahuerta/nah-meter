package dev.edmel.offerchecker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.edmel.offerchecker.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The status notification's Pause / Resume button: flips reading on or off. */
class PauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SettingsRepository(app)
                val on = !repo.settings.first().readingEnabled
                repo.setReadingEnabled(on)
                ServiceState.log(if (on) "Reading resumed" else "Reading paused", "from the notification")
            } finally {
                pending.finish()
            }
        }
    }
}
