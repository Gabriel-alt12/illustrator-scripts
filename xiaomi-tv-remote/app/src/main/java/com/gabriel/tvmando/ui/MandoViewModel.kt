package com.gabriel.tvmando.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gabriel.tvmando.AppContainer
import com.gabriel.tvmando.data.SettingsRepository
import com.gabriel.tvmando.data.TvSettings
import com.gabriel.tvmando.domain.AppCatalog
import com.gabriel.tvmando.domain.ConnectionState
import com.gabriel.tvmando.domain.ForceStopApp
import com.gabriel.tvmando.domain.LaunchApp
import com.gabriel.tvmando.domain.RawShell
import com.gabriel.tvmando.domain.Scene
import com.gabriel.tvmando.domain.SceneLibrary
import com.gabriel.tvmando.domain.SceneOutcome
import com.gabriel.tvmando.domain.SceneProgress
import com.gabriel.tvmando.domain.SceneRunner
import com.gabriel.tvmando.domain.SearchTarget
import com.gabriel.tvmando.domain.TvApp
import com.gabriel.tvmando.domain.TvCommand
import com.gabriel.tvmando.domain.TvController
import com.gabriel.tvmando.domain.TvQuery
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pantallas de la app. */
enum class Destination(val label: String) {
    REMOTE("Mando"),
    APPS("Apps"),
    SEARCH("Buscar"),
    SCENES("Escenas"),
}

/** Ultimo resultado mostrado bajo los controles. */
data class Feedback(val text: String, val isError: Boolean)

/** Estado de la cascara: lo que se ve en todas las pantallas. */
data class MandoUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val settings: TvSettings = TvSettings(),
    val feedback: Feedback? = null,
    val isSending: Boolean = false,
    val keyFingerprint: String = "",
) {
    /** Los controles siguen activos sin sesion: pulsar reconecta. */
    val controlsEnabled: Boolean get() = settings.isConfigured && !connection.isBusy
}

/** Estado de la pantalla de Apps. */
data class AppsUiState(
    val apps: List<TvApp> = emptyList(),
    val foregroundPackage: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val hasLoaded: Boolean get() = apps.isNotEmpty()
}

/** Estado de la pantalla de Escenas. */
data class ScenesUiState(
    val scenes: List<Scene> = emptyList(),
    val running: SceneProgress? = null,
)

/** Estado de la pantalla de Busqueda. */
data class SearchUiState(
    val history: List<String> = emptyList(),
    val target: SearchTarget = SearchTarget.GoogleTv,
    val isTyping: Boolean = false,
)

class MandoViewModel(
    private val controller: TvController,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val feedback = MutableStateFlow<Feedback?>(null)
    private val sending = MutableStateFlow(false)
    private val fingerprint = MutableStateFlow("")

    private val _appsState = MutableStateFlow(AppsUiState())
    val appsState: StateFlow<AppsUiState> = _appsState.asStateFlow()

    private val sceneProgress = MutableStateFlow<SceneProgress?>(null)
    private val searchTarget = MutableStateFlow<SearchTarget>(SearchTarget.GoogleTv)
    private var sceneJob: Job? = null

    /**
     * El motor de escenas solo necesita saber ejecutar una linea de shell; el
     * transporte y la reconexion los pone [TvController].
     */
    private val sceneRunner = SceneRunner(
        execute = { shell -> controller.run(RawShell(shell)) },
    )

    val uiState: StateFlow<MandoUiState> = combine(
        controller.state,
        settings.settings,
        feedback,
        sending,
        fingerprint,
    ) { connection, config, message, isSending, print ->
        MandoUiState(
            connection = connection,
            settings = config,
            feedback = message,
            isSending = isSending,
            keyFingerprint = print,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MandoUiState())

    val scenesState: StateFlow<ScenesUiState> = combine(
        settings.scenes,
        sceneProgress,
    ) { scenes, progress ->
        ScenesUiState(scenes = scenes, running = progress)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScenesUiState())

    val searchState: StateFlow<SearchUiState> = combine(
        settings.searchHistory,
        searchTarget,
        sceneProgress,
    ) { history, target, progress ->
        SearchUiState(
            history = history,
            target = target,
            isTyping = progress?.scene?.id == SEARCH_SCENE_ID,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch {
            fingerprint.value = runCatching { controller.keyFingerprint() }.getOrDefault("")
            // Reconexion automatica al abrir la app, si ya sabemos donde esta la TV.
            if (settings.current().isConfigured) controller.connect()
        }
    }

    // --- mando -------------------------------------------------------------

    fun send(command: TvCommand) {
        viewModelScope.launch {
            sending.value = true
            val result = controller.run(command)
            sending.value = false
            feedback.value = result.fold(
                onSuccess = { output -> Feedback(output.ifBlank { command.label }, isError = false) },
                onFailure = { error -> Feedback(error.message ?: "No se pudo enviar", isError = true) },
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

    /** Se llama al volver a primer plano: si la sesion murio, el indicador lo dice. */
    fun refreshLiveness() {
        viewModelScope.launch { controller.refreshLiveness() }
    }

    // --- apps --------------------------------------------------------------

    /**
     * Descubre las apps instaladas en la TV. Nada de paquetes escritos a fuego: la
     * seccion 11 avisa de que cambian entre versiones y regiones.
     */
    fun loadApps(force: Boolean = false) {
        if (!force && (_appsState.value.isLoading || _appsState.value.hasLoaded)) return
        viewModelScope.launch {
            _appsState.value = _appsState.value.copy(isLoading = true, error = null)
            val result = controller.run(TvQuery.THIRD_PARTY_PACKAGES)
            _appsState.value = result.fold(
                onSuccess = { output ->
                    // Segunda pasada para rescatar apps de streaming preinstaladas de
                    // fabrica (Prime Video, Netflix...), que "-3" no trae porque Android
                    // las cuenta como apps de sistema. Si esta consulta falla, seguimos
                    // solo con las de terceros en lugar de perder la lista entera.
                    val allOutput = controller.run(TvQuery.ALL_PACKAGES).getOrDefault("")
                    val apps = AppCatalog.parseInstalledPackages(output, allOutput)
                    _appsState.value.copy(
                        apps = apps,
                        isLoading = false,
                        error = if (apps.isEmpty()) "La TV no devolvio ninguna app" else null,
                    )
                },
                onFailure = { error ->
                    _appsState.value.copy(
                        isLoading = false,
                        error = error.message ?: "No se pudo leer la lista de apps",
                    )
                },
            )
            refreshForegroundApp()
        }
    }

    /** Detecta que app esta en primer plano para resaltarla en la rejilla. */
    fun refreshForegroundApp() {
        viewModelScope.launch {
            controller.run(TvQuery.CURRENT_ACTIVITY).onSuccess { output ->
                _appsState.value = _appsState.value.copy(
                    foregroundPackage = AppCatalog.parseForegroundPackage(output),
                )
            }
        }
    }

    fun launchApp(app: TvApp) {
        viewModelScope.launch {
            sending.value = true
            val result = controller.run(LaunchApp(app.packageName))
            sending.value = false
            feedback.value = result.fold(
                // monkey escupe estadisticas por stdout aunque haya ido bien.
                onSuccess = { Feedback("Abriendo ${app.displayName}", isError = false) },
                onFailure = { Feedback(it.message ?: "No se pudo abrir", isError = true) },
            )
            refreshForegroundApp()
        }
    }

    fun forceStopApp(app: TvApp) {
        viewModelScope.launch {
            val result = controller.run(ForceStopApp(app.packageName))
            feedback.value = result.fold(
                onSuccess = { Feedback("${app.displayName} cerrada", isError = false) },
                onFailure = { Feedback(it.message ?: "No se pudo cerrar", isError = true) },
            )
            refreshForegroundApp()
        }
    }

    // --- escenas -----------------------------------------------------------

    /** Solo una escena a la vez: encadenar dos deja la TV en un estado imposible. */
    fun runScene(scene: Scene) {
        sceneJob?.cancel()
        sceneJob = viewModelScope.launch {
            try {
                val outcome = sceneRunner.run(scene) { progress -> sceneProgress.value = progress }
                feedback.value = when (outcome) {
                    is SceneOutcome.Completed ->
                        Feedback("${scene.name}: hecho", isError = false)

                    is SceneOutcome.Failed -> Feedback(
                        "${scene.name}: fallo en el paso ${outcome.stepIndex + 1}. ${outcome.message}",
                        isError = true,
                    )
                }
            } finally {
                sceneProgress.value = null
            }
        }
    }

    fun cancelScene() {
        sceneJob?.cancel()
        sceneJob = null
        sceneProgress.value = null
        feedback.value = Feedback("Escena interrumpida", isError = false)
    }

    /** Alta o edicion: si el id ya existe se reemplaza, si no se anade al final. */
    fun saveScene(scene: Scene) {
        viewModelScope.launch {
            val current = scenesState.value.scenes
            val updated = if (current.any { it.id == scene.id }) {
                current.map { if (it.id == scene.id) scene else it }
            } else {
                current + scene
            }
            settings.saveScenes(updated)
            feedback.value = Feedback("${scene.name} guardada", isError = false)
        }
    }

    fun deleteScene(sceneId: String) {
        viewModelScope.launch {
            settings.saveScenes(scenesState.value.scenes.filterNot { it.id == sceneId })
        }
    }

    fun restoreDefaultScenes() {
        viewModelScope.launch {
            settings.restoreDefaultScenes()
            feedback.value = Feedback("Escenas de fabrica restauradas", isError = false)
        }
    }

    // --- busqueda ----------------------------------------------------------

    fun setSearchTarget(target: SearchTarget) {
        searchTarget.value = target
    }

    /**
     * Buscar es una escena efimera: abrir donde toque, esperar, escribir y aceptar.
     * Se reutiliza el motor de escenas para no duplicar la logica de los retardos.
     */
    fun search(query: String) {
        val clean = query.trim().replace('\n', ' ')
        if (clean.isEmpty()) return
        viewModelScope.launch { settings.rememberSearch(clean) }
        runScene(SceneLibrary.search(clean, searchTarget.value))
    }

    fun clearSearchHistory() {
        viewModelScope.launch { settings.clearSearchHistory() }
    }

    // --- ajustes -----------------------------------------------------------

    fun saveEndpoint(host: String, port: String) {
        viewModelScope.launch {
            val parsedPort = port.trim().toIntOrNull()
            if (host.isBlank() || parsedPort == null || parsedPort !in 1..65535) {
                feedback.value = Feedback("IP o puerto no validos", isError = true)
                return@launch
            }
            settings.setEndpoint(host, parsedPort)
            controller.disconnect()
            _appsState.value = AppsUiState()
            controller.connect().onFailure {
                feedback.value = Feedback(it.message ?: "No se pudo conectar", isError = true)
            }
        }
    }

    /**
     * Guarda el ajuste del mando persistente. Mostrar o quitar la notificacion es
     * cosa de la UI, que es quien tiene Context: aqui solo se persiste la decision.
     */
    fun setPersistentRemote(enabled: Boolean) {
        viewModelScope.launch { settings.setPersistentRemote(enabled) }
    }

    /** Genera una clave nueva: la TV volvera a pedir autorizacion. */
    fun repair() {
        viewModelScope.launch {
            controller.repair()
            fingerprint.value = runCatching { controller.keyFingerprint() }.getOrDefault("")
        }
    }

    companion object {
        private const val SEARCH_SCENE_ID = "busqueda"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MandoViewModel(container.tvController, container.settingsRepository) }
        }
    }
}
