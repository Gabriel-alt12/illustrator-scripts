package com.gabriel.tvmando.system

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gabriel.tvmando.TvMandoApp
import com.gabriel.tvmando.domain.QuickCommand
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tile de ajustes rapidos: un comando y ya.
 *
 * El tile se queda en estado inactivo siempre. Podria ponerse "activo" mientras hay
 * sesion ADB, pero el sistema solo pregunta al abrir el panel y la sesion se cae
 * sola cuando la TV se duerme: acabaria mostrando un estado falso la mitad del
 * tiempo.
 */
abstract class QuickTileService : TileService() {

    protected abstract val quickCommand: QuickCommand

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = quickCommand.label
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val container = (applicationContext as? TvMandoApp)?.container ?: return
        container.backgroundScope.launch {
            withTimeoutOrNull(TIMEOUT_MS) { container.tvController.run(quickCommand.command) }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}

class PowerTileService : QuickTileService() {
    override val quickCommand = QuickCommand.POWER
}

class MuteTileService : QuickTileService() {
    override val quickCommand = QuickCommand.MUTE
}
