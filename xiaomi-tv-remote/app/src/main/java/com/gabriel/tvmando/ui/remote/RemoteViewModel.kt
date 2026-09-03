package com.gabriel.tvmando.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gabriel.tvmando.AppContainer
import com.gabriel.tvmando.data.SettingsRepository
import com.gabriel.tvmando.data.TvSettings
import com.gabriel.tvmando.domain.ConnectionState
import com.gabriel.tvmando.domain.TvCommand
import com.gabriel.tvmando.domain.TvController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ultimo resultado mostrado bajo los botones. */
data class Feedback(val text: String, val isError: Boolean)

data class RemoteUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val settings: TvSettings = TvSettings(),
    val feedback: Feedback? = null,
    val isSending: Boolean = false,
    val keyFingerprint: String = "",
) {
    /** Los botones se dejan activos aunque no haya sesion: pulsar reconecta. */
    val controlsEnabled: Boolean get() = settings.isConfigured && !connection.isBusy
}

class RemoteViewModel(
    private val controller: TvController,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val feedback = MutableStateFlow<Feedback?>(null)
    private val sending = MutableStateFlow(false)
    private val fingerprint = MutableStateFlow("")

    val uiState: StateFlow<RemoteUiState> = combine(
        controller.state,
        settings.settings,
        feedback,
        sending,
        fingerprint,
    ) { connection, config, message, isSending, print ->
        RemoteUiState(
            connection = connection,
            settings = config,
            feedback = message,
            isSending = isSending,
            keyFingerprint = print,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoteUiState())

    init {
        viewModelScope.launch {
            fingerprint.value = runCatching { controller.keyFingerprint() }.getOrDefault("")
            // Reconexion automatica al abrir la app, si ya sabemos donde esta la TV.
            if (settings.current().isConfigured) controller.connect()
        }
    }

    fun send(command: TvCommand) {
        viewModelScope.launch {
            sending.value = true
            val result = controller.run(command)
            sending.value = false
            feedback.value = result.fold(
                onSuccess = { output ->
                    Feedback(output.ifBlank { command.label }, isError = false)
                },
                onFailure = { error ->
                    Feedback(error.message ?: "No se pudo enviar el comando", isError = true)
                },
            )
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            controller.connect().onFailure {
                feedback.value = Feedback(it.message ?: "No se pudo conectar", isError = true)
            }
        }
    }

    /** Se llama al volver a primer plano: si la sesion murio, el indicador lo refleja. */
    fun refreshLiveness() {
        viewModelScope.launch { controller.refreshLiveness() }
    }

    fun saveEndpoint(host: String, port: String) {
        viewModelScope.launch {
            val parsedPort = port.trim().toIntOrNull()
            if (host.isBlank() || parsedPort == null || parsedPort !in 1..65535) {
                feedback.value = Feedback("IP o puerto no validos", isError = true)
                return@launch
            }
            settings.setEndpoint(host, parsedPort)
            controller.disconnect()
            controller.connect().onFailure {
                feedback.value = Feedback(it.message ?: "No se pudo conectar", isError = true)
            }
        }
    }

    /** Genera una clave nueva: la TV volvera a pedir autorizacion. */
    fun repair() {
        viewModelScope.launch {
            controller.repair()
            fingerprint.value = runCatching { controller.keyFingerprint() }.getOrDefault("")
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RemoteViewModel(container.tvController, container.settingsRepository) }
        }
    }
}
