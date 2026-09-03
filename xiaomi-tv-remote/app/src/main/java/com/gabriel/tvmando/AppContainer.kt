package com.gabriel.tvmando

import android.content.Context
import com.gabriel.tvmando.data.AdbKeyProvider
import com.gabriel.tvmando.data.SettingsRepository
import com.gabriel.tvmando.domain.TvController
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

    val settingsRepository = SettingsRepository(context.applicationContext)

    private val adbKeyProvider = AdbKeyProvider(settingsRepository)

    val tvController = TvController(
        settings = settingsRepository,
        keys = adbKeyProvider,
        scope = adbScope,
    )
}
