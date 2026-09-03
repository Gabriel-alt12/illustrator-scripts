package com.gabriel.tvmando

import android.content.Context
import com.gabriel.tvmando.data.AdbKeyProvider
import com.gabriel.tvmando.data.SettingsRepository
import com.gabriel.tvmando.domain.TvController
import com.gabriel.tvmando.system.GuestRemoteServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Inyeccion de dependencias a mano.
 *
 * Con cuatro objetos de vida larga no compensa meter Hilt: eso son dos plugins de
 * Gradle, KSP y un minuto mas de compilacion a cambio de nada. Si en la fase 4 las
 * escenas traen mas piezas, se cambia.
 */
class AppContainer(context: Context) {

    /**
     * Alcance de la sesion ADB. Vive mas que cualquier ViewModel a proposito: la
     * corrutina que lee el socket no debe morir al girar el movil.
     */
    private val adbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * El mismo alcance, expuesto para los componentes de sistema (widget, tiles,
     * notificacion). Viven fuera de cualquier Activity y necesitan lanzar corrutinas
     * que sobrevivan al componente que las dispara.
     */
    val backgroundScope: CoroutineScope get() = adbScope

    val settingsRepository = SettingsRepository(context.applicationContext)

    private val adbKeyProvider = AdbKeyProvider(settingsRepository)

    val tvController = TvController(
        settings = settingsRepository,
        keys = adbKeyProvider,
        scope = adbScope,
    )

    /**
     * Vive en el mismo alcance que la sesion ADB para que el mando de la visita no se
     * caiga al cerrar la pantalla de ajustes desde la que se enciende.
     */
    val guestRemoteServer = GuestRemoteServer(
        controller = tvController,
        scope = adbScope,
    )
}
