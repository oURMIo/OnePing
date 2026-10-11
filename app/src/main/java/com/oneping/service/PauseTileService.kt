package com.oneping.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.oneping.OnePingApp
import com.oneping.data.FOREVER
import com.oneping.data.PAUSE_MS
import com.oneping.data.Repository
import com.oneping.ui.formatClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Quick Settings tile: tap to pause OnePing for 1 hour, tap again to resume. */
class PauseTileService : TileService() {

    override fun onStartListening() {
        render(OnePingApp.engine(this).settings.pausedUntil)
    }

    override fun onClick() {
        val now = System.currentTimeMillis()
        val pausedUntil = if (OnePingApp.engine(this).settings.isPaused(now)) 0L else now + PAUSE_MS
        render(pausedUntil)
        val repo = Repository.get(this)
        CoroutineScope(Dispatchers.IO).launch { repo.setPausedUntil(pausedUntil) }
    }

    private fun render(pausedUntil: Long) {
        val tile = qsTile ?: return
        val paused = pausedUntil > System.currentTimeMillis()
        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = "OnePing"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !paused -> "On"
                pausedUntil == FOREVER -> "Everyone rings"
                else -> "Paused till ${formatClock(this, pausedUntil)}"
            }
        }
        tile.updateTile()
    }
}
