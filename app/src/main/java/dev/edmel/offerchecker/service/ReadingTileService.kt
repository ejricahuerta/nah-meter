package dev.edmel.offerchecker.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.edmel.offerchecker.R
import dev.edmel.offerchecker.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A quick-settings tile to pause and resume reading from the notification shade, without opening
 * the app (handy while driving). It flips the same setting as Home's power button.
 */
class ReadingTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        scope.launch { show(SettingsRepository(this@ReadingTileService).settings.first().readingEnabled) }
    }

    override fun onClick() {
        scope.launch {
            val repo = SettingsRepository(this@ReadingTileService)
            val on = !repo.settings.first().readingEnabled
            repo.setReadingEnabled(on)
            ServiceState.log(if (on) "Reading resumed" else "Reading paused", "from the quick-settings tile")
            show(on)
        }
    }

    private fun show(reading: Boolean) {
        val tile = qsTile ?: return
        tile.label = "nah.meter"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_check)
        tile.state = if (reading) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (reading) "Reading" else "Paused"
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
