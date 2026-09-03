package com.gabriel.tvmando.domain

/** Estado de la sesion ADB, tal cual lo pinta el indicador de la pantalla principal. */
sealed interface ConnectionState {

    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    /** La TV esta mostrando el dialogo "Permitir depuracion USB". */
    data object AwaitingAuthorization : ConnectionState

    data class Connected(val model: String?) : ConnectionState

    /** [hint] explica que hacer; viene de AdbException. */
    data class Failed(val message: String, val hint: String? = null) : ConnectionState

    val isConnected: Boolean get() = this is Connected
    val isBusy: Boolean get() = this is Connecting || this is AwaitingAuthorization
}
