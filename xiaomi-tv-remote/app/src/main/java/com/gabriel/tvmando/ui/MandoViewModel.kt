package com.gabriel.tvmando.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gabriel.tvmando.AppContainer
import com.gabriel.tvmando.data.SettingsRepository
import com.gabriel.tvmando.data.ThemeMode
import com.gabriel.tvmando.data.TvSettings
import com.gabriel.tvmando.domain.AppCatalog
import com.gabriel.tvmando.domain.ConnectionState
import com.gabriel.tvmando.domain.ForceStopApp
import com.gabriel.tvmando.domain.LaunchApp
import com.gabriel.tvmando.domain.PowerState
import com.gabriel.tvmando.domain.PressKey
import com.gabriel.tvmando.domain.RawShell
import com.gabriel.tvmando.domain.Scene
import com.gabriel.tvmando.domain.SceneLibrary
import com.gabriel.tvmando.domain.SceneOutcome
import com.gabriel.tvmando.domain.SceneProgress
import com.gabriel.tvmando.domain.SceneRunner
import com.gabriel.tvmando.domain.SearchTarget
import com.gabriel.tvmando.domain.SetVolume
import com.gabriel.tvmando.domain.TvApp
import com.gabriel.tvmando.domain.TvCommand
import com.gabriel.tvmando.domain.TvController
import com.gabriel.tvmando.domain.TvKey
import com.gabriel.tvmando.domain.TvQuery
import com.gabriel.tvmando.domain.TvStatus
import com.gabriel.tvmando.domain.decodeScreenshot
import com.gabriel.tvmando.system.GuestRemoteServer
import com.gabriel.tvmando.system.GuestRemoteState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    /** Paquetes fijados arriba en la rejilla. */
    val favorites: Set<String> = emptySet(),
    /** De donde se venia al abrir la de ahora, para poder volver de un toque. */
    val previousApp: TvApp? = null,
) {
    val hasLoaded: Boolean get() = apps.isNotEmpty()
}

/** Estado de la pantalla de Escenas. */
data class ScenesUiState(
    val scenes: List<Scene> = emptyList(),
    val running: SceneProgress? = null,
)

/**
 * Ultima captura de la TV. No es data class a proposito: lleva un ByteArray, y la
 * igualdad por contenido no aporta nada aqui.
 */
class ScreenshotUiState(
    val image: ByteArray? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** Estado de la pantalla de Busqueda. */
data class SearchUiState(
    val history: List<String> = emptyList(),
    val target: SearchTarget = SearchTarget.GoogleTv,
    val isTyping: Boolean = false,
    /** Teclear letra a letra en vez de inyectar el texto de golpe. */
    val slowTyping: Boolean = true,
)

class MandoViewModel(
    private val controller: TvController,
    private val settings: SettingsRepository,
    guestServer: GuestRemoteServer,
) : ViewModel() {

    /**
     * Estado del mando para invitados, solo para pintarlo. Encenderlo y apagarlo es
     * cosa de [com.gabriel.tvmando.system.GuestRemoteService], que necesita Context.
     *
     * Va aparte de [uiState] porque no se persiste: es un interruptor de "ahora hay
     * gente en casa", no una preferencia.
     */
    val guestState: StateFlow<GuestRemoteState> = guestServer.state

    private val feedback = FeedbackBox()
    private val sending = MutableStateFlow(false)
    private val fingerprint = MutableStateFlow("")

    private val _appsState = MutableStateFlow(AppsUiState())
    private val previousApp = MutableStateFlow<TvApp?>(null)

    /** Lo ultimo que se abrio desde aqui, para saber de donde se viene. */
    private var lastLaunched: TvApp? = null

    val appsState: StateFlow<AppsUiState> = combine(
        _appsState,
        settings.favorites,
        previousApp,
    ) { apps, favorites, previous ->
        apps.copy(favorites = favorites, previousApp = previous)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    private val _screenshot = MutableStateFlow(ScreenshotUiState())
    val screenshot: StateFlow<ScreenshotUiState> = _screenshot

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
        feedback.state,
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
        settings.fastTypingTargets,
    ) { history, target, progress, fastTargets ->
        SearchUiState(
            history = history,
            target = target,
            isTyping = progress?.scene?.id == SEARCH_SCENE_ID,
            // Tecla a tecla salvo que este destino este apuntado como excepcion.
            slowTyping = target.key !in fastTargets,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    // --- estado de la tele -------------------------------------------------

    /** Cambia para forzar una consulta inmediata tras un comando que altera el estado. */
    private val statusNonce = MutableStateFlow(0L)

    /**
     * Encendido, volumen y que suena, preguntado cada pocos segundos mientras alguien
     * mira la app y hay sesion. Tras un comando que lo cambia (encender, volumen,
     * play) se vuelve a preguntar enseguida, con un respiro para que la TV lo aplique.
     * Si la consulta falla se conserva lo ultimo que se supo.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val tvStatus: StateFlow<TvStatus?> = combine(controller.state, statusNonce) { connection, nonce ->
        connection to nonce
    }.flatMapLatest { (connection, nonce) ->
        if (connection !is ConnectionState.Connected) {
            flowOf<TvStatus?>(null)
        } else {
            flow<TvStatus?> {
                if (nonce > 0L) delay(STATUS_SETTLE_MS)
                while (true) {
                    controller.run(TvQuery.STATUS).onSuccess { emit(TvStatus.parse(it)) }
                    delay(STATUS_EVERY_MS)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshStatus() {
        statusNonce.value = System.currentTimeMillis()
    }

    /**
     * Ultimo nivel pedido desde la barra de volumen. Es un StateFlow a proposito: se
     * queda con el valor mas reciente mientras la TV procesa el anterior, asi que
     * arrastrar la barra de un extremo al otro no encola veinte ordenes.
     */
    private val requestedVolume = MutableStateFlow<Int?>(null)

    fun setVolumeLevel(level: Int) {
        requestedVolume.value = level
    }

    private val _diagnostics = MutableStateFlow<String?>(null)

    /** Para Ajustes: que sabe hacer esta TV, contado en tres lineas. */
    val diagnostics: StateFlow<String?> = _diagnostics

    fun diagnose() {
        viewModelScope.launch {
            _diagnostics.value = "Preguntando a la TV..."
            _diagnostics.value = controller.run(TvQuery.STATUS).fold(
                onSuccess = { raw -> describe(TvStatus.parse(raw)) },
                onFailure = { "La TV no respondio: ${it.message ?: "sin detalle"}" },
            )
        }
    }

    private fun describe(status: TvStatus): String = buildString {
        append("Encendido: ")
        appendLine(
            when (status.power) {
                PowerState.AWAKE -> "lo sabe; ahora esta encendida"
                PowerState.ASLEEP -> "lo sabe; ahora esta en reposo"
                PowerState.UNKNOWN -> "esta TV no lo dice"
            },
        )
        append("Volumen exacto: ")
        appendLine(status.volume?.let { "si, ${it.current} de ${it.max}" } ?: "no disponible en esta TV")
        append("Que suena: ")
        val playing = status.nowPlaying
        append(
            when {
                !status.mediaAvailable -> "no disponible en esta TV"
                playing == null -> "lo cuenta; ahora no suena nada"
                else -> "${AppCatalog.describe(playing.packageName).displayName}: ${playing.title}"
            },
        )
    }

    init {
        viewModelScope.launch {
            fingerprint.value = runCatching { controller.keyFingerprint() }.getOrDefault("")
            // Reconexion automatica al abrir la app, si ya sabemos donde esta la TV.
            if (settings.current().isConfigured) controller.connect()
        }
        viewModelScope.launch {
            requestedVolume.filterNotNull().collect { level ->
                controller.run(SetVolume(level))
                refreshStatus()
            }
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
            if (command.touchesStatus()) refreshStatus()
        }
    }

    /** Lo que cambia el encendido, el volumen o lo que suena, y merece re-preguntar. */
    private fun TvCommand.touchesStatus(): Boolean = when (this) {
        is SetVolume -> true
        is PressKey -> key in STATUS_KEYS
        else -> false
    }

    fun reconnect() {
        viewModelScope.launch {
            controller.connect().onFailure {
                feedback.value = Feedback(it.message ?: "No se pudo conectar", isError = true)
            }
        }
    }

    /**
     * Trae una foto de lo que hay en la TV.
     *
     * Va a peticion y no en bucle: cada captura son un par de megas de PNG en base64,
     * y refrescarla sola cada pocos segundos cargaria la red de casa para algo que se
     * mira de reojo. Sirve sobre todo para ver si el texto esta entrando de verdad
     * cuando se escribe a ciegas.
     */
    fun captureScreen() {
        viewModelScope.launch {
            _screenshot.value = ScreenshotUiState(
                image = _screenshot.value.image,
                isLoading = true,
            )
            _screenshot.value = controller.run(TvQuery.SCREENSHOT).fold(
                onSuccess = { raw ->
                    val bytes = decodeScreenshot(raw)
                    ScreenshotUiState(
                        image = bytes ?: _screenshot.value.image,
                        error = if (bytes == null) "La TV no devolvio una imagen" else null,
                    )
                },
                onFailure = { error ->
                    ScreenshotUiState(
                        image = _screenshot.value.image,
                        error = error.message ?: "No se pudo capturar la pantalla",
                    )
                },
            )
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
                    // "-3" solo trae las apps que instalo el usuario: las de streaming
                    // preinstaladas de fabrica (Prime Video, Netflix...) son apps de
                    // sistema y se quedaban fuera. Se completa con las que tienen icono
                    // en la pantalla de inicio de la TV, que es lo que uno espera ver, y
                    // con el catalogo completo como red de seguridad. Las consultas de
                    // apoyo que fallen se ignoran: mejor una lista corta que ninguna.
                    val launcherOutput = controller.run(TvQuery.LAUNCHER_ACTIVITIES).getOrDefault("")
                    val allOutput = controller.run(TvQuery.ALL_PACKAGES).getOrDefault("")
                    val apps = AppCatalog.parseInstalledPackages(output, allOutput, launcherOutput)
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
        // De donde se viene: lo que hubiera en pantalla, y si no lo sabemos, lo ultimo
        // que se abrio desde aqui. Sirve para el boton de volver.
        val leaving = _appsState.value.apps
            .firstOrNull { it.packageName == _appsState.value.foregroundPackage }
            ?: lastLaunched
        if (leaving != null && leaving.packageName != app.packageName) {
            previousApp.value = leaving
        }
        lastLaunched = app

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

    /** Fija o quita una app de la cabecera de la rejilla. */
    fun toggleFavorite(app: TvApp) {
        viewModelScope.launch { settings.toggleFavorite(app.packageName) }
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
                refreshStatus()
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
     * Cambia como se teclea en el destino elegido ahora mismo, y lo recuerda para la
     * proxima vez: por defecto se va tecla a tecla, que es lo unico que entienden los
     * buscadores de varias apps de television, pero donde el texto de golpe funcione
     * no hay que volver a decirlo.
     */
    fun setSlowTyping(enabled: Boolean) {
        val target = searchTarget.value
        viewModelScope.launch { settings.setFastTyping(target.key, fast = !enabled) }
    }

    /**
     * Buscar es una escena efimera: abrir donde toque, esperar, escribir y aceptar.
     * Se reutiliza el motor de escenas para no duplicar la logica de los retardos.
     */
    fun search(query: String) {
        val clean = query.trim().replace('\n', ' ')
        if (clean.isEmpty()) return
        val target = searchTarget.value
        viewModelScope.launch {
            settings.rememberSearch(clean)
            val fast = target.key in settings.fastTypingTargets.first()
            runScene(SceneLibrary.search(clean, target, slowly = !fast))
        }
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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /** Genera una clave nueva: la TV volvera a pedir autorizacion. */
    fun repair() {
        viewModelScope.launch {
            controller.repair()
            fingerprint.value = runCatching { controller.keyFingerprint() }.getOrDefault("")
        }
    }

    /**
     * El mensaje de abajo se quita solo pasados unos segundos (los errores aguantan
     * mas) para dejar sitio a lo que este sonando en la TV. Se asigna como siempre,
     * `feedback.value = ...`; el temporizador va dentro.
     */
    private inner class FeedbackBox {
        private val flow = MutableStateFlow<Feedback?>(null)
        private var clearing: Job? = null

        val state: StateFlow<Feedback?> get() = flow

        var value: Feedback?
            get() = flow.value
            set(message) {
                flow.value = message
                clearing?.cancel()
                if (message == null) return
                clearing = viewModelScope.launch {
                    delay(if (message.isError) ERROR_SHOWN_MS else FEEDBACK_SHOWN_MS)
                    if (flow.value === message) flow.value = null
                }
            }
    }

    companion object {
        private const val SEARCH_SCENE_ID = "busqueda"
        private const val STATUS_EVERY_MS = 10_000L
        private const val STATUS_SETTLE_MS = 1_200L
        private const val FEEDBACK_SHOWN_MS = 4_000L
        private const val ERROR_SHOWN_MS = 8_000L

        private val STATUS_KEYS = setOf(
            TvKey.POWER, TvKey.SLEEP, TvKey.WAKEUP,
            TvKey.VOLUME_UP, TvKey.VOLUME_DOWN, TvKey.VOLUME_MUTE,
            TvKey.MEDIA_PLAY_PAUSE, TvKey.MEDIA_NEXT, TvKey.MEDIA_PREVIOUS,
        )

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MandoViewModel(
                    controller = container.tvController,
                    settings = container.settingsRepository,
                    guestServer = container.guestRemoteServer,
                )
            }
        }
    }
}
