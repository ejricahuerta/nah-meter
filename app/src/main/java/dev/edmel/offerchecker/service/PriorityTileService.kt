package dev.edmel.offerchecker.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.edmel.offerchecker.R
import dev.edmel.offerchecker.core.Priority
import dev.edmel.offerchecker.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A quick-settings tile to flip which rate is strict, $/hr or $/km, from the notification shade
 * mid-shift (when it gets busy, traffic stretches the minutes but not the km). It flips the same
 * setting as Rules → Strict on. Lit means $/km, the busy-time mode.
 */
class PriorityTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        scope.launch { show(SettingsRepository(this@PriorityTileService).settings.first().thresholds.priority) }
    }

    override fun onClick() {
        scope.launch {
            val repo = SettingsRepository(this@PriorityTileService)
            val next = if (repo.settings.first().thresholds.priority == Priority.PER_KM) Priority.HOURLY else Priority.PER_KM
            repo.setPriority(next)
            ServiceState.log(label(next), "from the quick-settings tile")
            show(next)
        }
    }

    private fun show(priority: Priority) {
        val tile = qsTile ?: return
        // The label carries the value: subtitles only exist from Android 10.
        tile.label = label(priority)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_check)
        tile.state = if (priority == Priority.PER_KM) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun label(priority: Priority) = if (priority == Priority.PER_KM) "Strict on $/km" else "Strict on $/hr"

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
