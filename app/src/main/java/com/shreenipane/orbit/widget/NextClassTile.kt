package com.shreenipane.orbit.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.shreenipane.orbit.MainActivity
import com.shreenipane.orbit.OrbitApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Quick settings tile showing the current or next class (from the cached
 * timetable, no network). Tapping it opens Orbit.
 */
class NextClassTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val c = withContext(Dispatchers.IO) {
                runCatching {
                    val repo = (applicationContext as OrbitApp).repository
                    OrbitWidget.content(repo.cached(), repo.overrides(), LocalDateTime.now())
                }.getOrNull()
            }
            val tile = qsTile ?: return@launch
            if (c == null) {
                tile.label = "Orbit"
                tile.state = Tile.STATE_INACTIVE
            } else {
                tile.label = c.title.take(28)
                if (Build.VERSION.SDK_INT >= 29) tile.subtitle = c.label.lowercase().replaceFirstChar { it.uppercase() }
                tile.state = Tile.STATE_ACTIVE
            }
            tile.updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
