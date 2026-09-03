package com.gabriel.tvmando.domain

import com.gabriel.tvmando.adb.AdbConnection
import com.gabriel.tvmando.adb.AdbException
import com.gabriel.tvmando.data.AdbKeyProvider
import com.gabriel.tvmando.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Unico punto de entrada del resto de la app hacia la TV.
 *
 * Mantiene una sesion ADB viva y la reabre sola cuando hace falta, que es el caso
 * normal: la TV cierra el socket al entrar en reposo. Los comandos se serializan con
 * un mutex para que dos pulsaciones seguidas no compitan por reconectar.
 */
class TvController(
    private val settings: SettingsRepository,
    private val keys: AdbKeyProvider,
    private val scope: CoroutineScope,
) {

    private val mutex = Mutex()
    private var connection: AdbConnection? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Envia un comando, reconectando si la sesion se habia caido. */
    suspend fun run(command: TvCommand): Result<String> = mutex.withLock {
        runCatching {
            val output = try {
                ensureConnected().shell(command.shell)
            } catch (disconnected: AdbException.Disconnected) {
                // La TV se durmio entre comandos: un reintento con sesion nueva.
                dropConnection()
                ensureConnected().shell(command.shell)
            }
            output.trim()
        }.onFailure { reportFailure(it) }
    }

    /** Fuerza la conexion (boton de reconectar y arranque de la app). */
    suspend fun connect(): Result<ConnectionState> = mutex.withLock {
        runCatching {
            ensureConnected()
            _state.value
        }.onFailure { reportFailure(it) }
    }

    /** Cierra la sesion sin marcarla como error. */
    suspend fun disconnect() = mutex.withLock {
        dropConnection()
        _state.value = ConnectionState.Disconnected
    }

    /** Comprueba si la sesion guardada sigue viva; no reconecta. */
    suspend fun refreshLiveness() = mutex.withLock {
        val live = connection?.ping() ?: false
        if (!live && _state.value.isConnected) {
            dropConnection()
            _state.value = ConnectionState.Disconnected
        }
    }

    /** Huella de la clave, para cotejarla con el dialogo de la TV. */
    suspend fun keyFingerprint(): String = keys.fingerprint()

    /** Genera una clave nueva: obliga a repetir la autorizacion en la TV. */
    suspend fun repair(): Result<ConnectionState> = mutex.withLock {
        dropConnection()
        runCatching {
            keys.reset()
            ensureConnected()
            _state.value
        }.onFailure { reportFailure(it) }
    }

    // --- interno -----------------------------------------------------------

    private suspend fun ensureConnected(): AdbConnection {
        connection?.takeIf { it.isConnected }?.let { return it }
        dropConnection()

        val config = settings.current()
        if (!config.isConfigured) throw AdbException.NotConfigured()

        _state.value = ConnectionState.Connecting
        val opened = AdbConnection.connect(
            host = config.host,
            port = config.port,
            keyPair = keys.keyPair(),
            scope = scope,
            onAuthorizationRequired = { _state.value = ConnectionState.AwaitingAuthorization },
        )
        connection = opened

        val model = modelFromBanner(opened.banner) ?: config.lastKnownModel
        settings.setLastKnownModel(model)
        _state.value = ConnectionState.Connected(model)
        return opened
    }

    private fun dropConnection() {
        connection?.close()
        connection = null
    }

    private fun reportFailure(error: Throwable) {
        dropConnection()
        _state.value = when (error) {
            is AdbException -> ConnectionState.Failed(error.message.orEmpty(), error.hint)
            else -> ConnectionState.Failed(error.message ?: "Error desconocido")
        }
    }

    private companion object {
        /** El banner viene como "device::ro.product.name=x;ro.product.model=y;...". */
        fun modelFromBanner(banner: String): String? = banner
            .substringAfter("ro.product.model=", "")
            .substringBefore(';')
            .trim()
            .takeIf { it.isNotEmpty() }
    }
}
