package com.gabriel.tvmando.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabriel.tvmando.domain.ConnectionState
import com.gabriel.tvmando.ui.apps.AppsScreen
import com.gabriel.tvmando.ui.components.GhostButton
import com.gabriel.tvmando.ui.components.NavBar
import com.gabriel.tvmando.ui.components.NavBarItem
import com.gabriel.tvmando.ui.components.StatusBadge
import com.gabriel.tvmando.ui.components.Tap
import com.gabriel.tvmando.ui.components.rememberHaptics
import com.gabriel.tvmando.ui.remote.RemoteScreen
import com.gabriel.tvmando.system.GuestRemoteService
import com.gabriel.tvmando.system.GuestRemoteState
import com.gabriel.tvmando.system.MandoNotification
import com.gabriel.tvmando.ui.scenes.ScenesScreen
import com.gabriel.tvmando.ui.search.SearchScreen
import com.gabriel.tvmando.ui.theme.Alert
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkFaint
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.Ink
import com.gabriel.tvmando.ui.theme.InkRaised
import com.gabriel.tvmando.ui.theme.Signal
import com.gabriel.tvmando.ui.theme.Waiting

/**
 * Cascara de la app: cabecera con el estado de conexion, contenido de la pantalla
 * activa y barra de pestanas.
 *
 * El indicador de conexion vive aqui y no en cada pantalla porque la especificacion
 * pide que este siempre visible.
 */
@Composable
fun MandoApp(viewModel: MandoViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appsState by viewModel.appsState.collectAsStateWithLifecycle()
    val scenesState by viewModel.scenesState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val guestState by viewModel.guestState.collectAsStateWithLifecycle()
    val screenshot by viewModel.screenshot.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    var destination by rememberSaveable { mutableStateOf(Destination.REMOTE) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showScreen by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Si el usuario dice que no, el ajuste se queda apagado: no tiene sentido
        // guardar una preferencia que el sistema no va a dejar cumplir.
        if (granted) viewModel.setPersistentRemote(true)
    }

    // Si algo falla, el movil lo dice sin que haya que mirar la pantalla.
    LaunchedEffect(state.feedback) {
        if (state.feedback?.isError == true) haptics(Tap.Reject)
    }

    // La notificacion la pone y la quita la UI, que es quien tiene Context. Al
    // arrancar la app esto tambien la restaura tras un reinicio del movil.
    LaunchedEffect(state.settings.persistentRemote) {
        if (state.settings.persistentRemote) {
            MandoNotification.show(context)
        } else {
            MandoNotification.hide(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Header(
            state = state,
            destination = destination,
            onReconnect = {
                haptics(Tap.Press)
                viewModel.reconnect()
            },
            onSettings = {
                haptics(Tap.Press)
                showSettings = true
            },
            onSeeScreen = {
                haptics(Tap.Press)
                showScreen = true
                viewModel.captureScreen()
            },
        )

        state.banner()?.let { banner ->
            Spacer(Modifier.height(16.dp))
            Notice(
                title = banner.title,
                body = banner.body,
                accent = banner.accent,
                onAction = if (banner.offersSettings) {
                    { showSettings = true }
                } else {
                    null
                },
            )
        }

        Spacer(Modifier.height(20.dp))

        Box(Modifier.weight(1f)) {
            when (destination) {
                Destination.REMOTE -> RemoteScreen(
                    enabled = state.controlsEnabled,
                    onCommand = viewModel::send,
                    haptics = haptics,
                )

                Destination.APPS -> AppsScreen(
                    state = appsState,
                    connected = state.connection.isConnected,
                    enabled = state.controlsEnabled,
                    onLoad = { viewModel.loadApps() },
                    onRefresh = { viewModel.loadApps(force = true) },
                    onLaunch = viewModel::launchApp,
                    onForceStop = viewModel::forceStopApp,
                    onToggleFavorite = viewModel::toggleFavorite,
                    haptics = haptics,
                )

                Destination.SEARCH -> SearchScreen(
                    state = searchState,
                    apps = appsState.apps,
                    enabled = state.controlsEnabled,
                    onTargetChange = viewModel::setSearchTarget,
                    onSlowTypingChange = viewModel::setSlowTyping,
                    onSearch = viewModel::search,
                    onClearHistory = viewModel::clearSearchHistory,
                    haptics = haptics,
                )

                Destination.SCENES -> ScenesScreen(
                    state = scenesState,
                    apps = appsState.apps,
                    enabled = state.controlsEnabled,
                    onRun = viewModel::runScene,
                    onCancel = viewModel::cancelScene,
                    onSave = viewModel::saveScene,
                    onDelete = viewModel::deleteScene,
                    onRestoreDefaults = viewModel::restoreDefaultScenes,
                    haptics = haptics,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = state.feedback?.text ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = if (state.feedback?.isError == true) Alert else ChalkFaint,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        NavBar {
            NavBarItem(
                selected = destination == Destination.REMOTE,
                label = Destination.REMOTE.label,
                icon = Icons.Rounded.SettingsRemote,
                onClick = {
                    haptics(Tap.Press)
                    destination = Destination.REMOTE
                },
            )
            NavBarItem(
                selected = destination == Destination.APPS,
                label = Destination.APPS.label,
                icon = Icons.Rounded.Apps,
                onClick = {
                    haptics(Tap.Press)
                    destination = Destination.APPS
                },
            )
            NavBarItem(
                selected = destination == Destination.SEARCH,
                label = Destination.SEARCH.label,
                icon = Icons.Rounded.Search,
                onClick = {
                    haptics(Tap.Press)
                    destination = Destination.SEARCH
                },
            )
            NavBarItem(
                selected = destination == Destination.SCENES,
                label = Destination.SCENES.label,
                icon = Icons.Rounded.PlaylistPlay,
                onClick = {
                    haptics(Tap.Press)
                    destination = Destination.SCENES
                },
            )
        }
    }

    if (showScreen) {
        ScreenDialog(
            state = screenshot,
            onRefresh = {
                haptics(Tap.Press)
                viewModel.captureScreen()
            },
            onDismiss = { showScreen = false },
        )
    }

    if (showSettings) {
        EndpointDialog(
            host = state.settings.host,
            port = state.settings.port.toString(),
            fingerprint = state.keyFingerprint,
            persistentRemote = state.settings.persistentRemote,
            guestState = guestState,
            onGuestRemoteChange = { enabled ->
                // Lo arranca un servicio en primer plano: si no, Android congela el
                // proceso al bloquear la pantalla y la visita se queda sin mando.
                if (enabled) {
                    GuestRemoteService.start(context)
                } else {
                    GuestRemoteService.stop(context)
                }
            },
            onPersistentRemoteChange = { enabled ->
                when {
                    !enabled -> viewModel.setPersistentRemote(false)
                    MandoNotification.canPost(context) -> viewModel.setPersistentRemote(true)
                    else -> askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = { showSettings = false },
            onSave = { host, port ->
                showSettings = false
                viewModel.saveEndpoint(host, port)
            },
            onRepair = {
                showSettings = false
                viewModel.repair()
            },
        )
    }
}

@Composable
private fun Header(
    state: MandoUiState,
    destination: Destination,
    onReconnect: () -> Unit,
    onSettings: () -> Unit,
    onSeeScreen: () -> Unit,
) {
    val status = state.connection.asStatus(state.settings.isConfigured)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(
            color = status.color,
            label = status.label,
            detail = if (state.settings.isConfigured) state.settings.endpoint else "sin IP",
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        GhostButton(onClick = onReconnect, icon = Icons.Rounded.Refresh, description = "Reconectar")
        Spacer(Modifier.width(10.dp))
        GhostButton(onClick = onSeeScreen, icon = Icons.Rounded.Tv, description = "Ver la pantalla de la TV")
        Spacer(Modifier.width(10.dp))
        GhostButton(onClick = onSettings, icon = Icons.Rounded.Settings, description = "Ajustes")
    }

    Spacer(Modifier.height(18.dp))

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = destination.label.uppercase(),
            style = MaterialTheme.typography.displaySmall,
            color = Chalk,
        )
        Text(
            text = (state.settings.lastKnownModel ?: "Xiaomi TV S Mini LED").uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Ember,
        )
    }
}

/**
 * Lo que hay en la TV ahora mismo.
 *
 * Existe sobre todo para escribir sin mirar la television: hasta ahora, al mandar
 * texto no habia forma de saber si estaba entrando hasta levantar la vista.
 */
@Composable
private fun ScreenDialog(
    state: ScreenshotUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(state.image) {
        state.image?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                .getOrNull()
                ?.asImageBitmap()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkRaised,
        title = {
            Text("Pantalla de la TV", style = MaterialTheme.typography.titleLarge, color = Chalk)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Lo que se ve en la television",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                Text(
                    text = when {
                        state.isLoading -> "Pidiendo la captura..."
                        state.error != null -> state.error
                        bitmap != null -> "Toca actualizar para volver a mirar."
                        else -> "Todavia no hay ninguna captura."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.error != null) Alert else ChalkFaint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh, enabled = !state.isLoading) {
                Text("Actualizar", color = Ember)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = ChalkMuted)
            }
        },
    )
}

/** Aviso contextual: lo que hay que hacer ahora mismo, en una frase. */
@Composable
private fun Notice(
    title: String,
    body: String,
    accent: Color,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InkRaised, RoundedCornerShape(18.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
        )
        if (body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkMuted,
            )
        }
        if (onAction != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                Text("Configurar la IP", style = MaterialTheme.typography.labelLarge, color = Ember)
            }
        }
    }
}

@Composable
private fun EndpointDialog(
    host: String,
    port: String,
    fingerprint: String,
    persistentRemote: Boolean,
    onPersistentRemoteChange: (Boolean) -> Unit,
    guestState: GuestRemoteState,
    onGuestRemoteChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onRepair: () -> Unit,
) {
    var hostField by rememberSaveable(host) { mutableStateOf(host) }
    var portField by rememberSaveable(port) { mutableStateOf(port) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkRaised,
        title = {
            Text("Conexion con la TV", style = MaterialTheme.typography.titleLarge, color = Chalk)
        },
        text = {
            Column(
                modifier = Modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = hostField,
                    onValueChange = { hostField = it },
                    label = { Text("IP de la TV") },
                    placeholder = { Text("192.168.1.42") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = portField,
                    onValueChange = { value -> portField = value.filter { it.isDigit() } },
                    label = { Text("Puerto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Hairline),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Mando en la barra de notificaciones",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Chalk,
                        )
                        Text(
                            text = "Encendido, volumen y silencio sin abrir la app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChalkFaint,
                        )
                    }
                    Switch(
                        checked = persistentRemote,
                        onCheckedChange = onPersistentRemoteChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ember,
                            checkedTrackColor = InkRaised,
                            checkedBorderColor = Ember,
                        ),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Hairline),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Mando para invitados",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Chalk,
                        )
                        Text(
                            text = "Una direccion para quien este de visita: controla la TV " +
                                "desde su navegador, sin instalar nada.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChalkFaint,
                        )
                    }
                    Switch(
                        checked = guestState is GuestRemoteState.Running,
                        onCheckedChange = onGuestRemoteChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ember,
                            checkedTrackColor = InkRaised,
                            checkedBorderColor = Ember,
                        ),
                    )
                }
                when (guestState) {
                    is GuestRemoteState.Running -> {
                        val url = guestState.url
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ember,
                        )
                        Text(
                            text = "Solo funciona en la WiFi de casa y mientras la app siga " +
                                "abierta. Al apagarlo, la direccion deja de valer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChalkFaint,
                        )
                        TextButton(
                            onClick = { clipboard.setText(AnnotatedString(url)) },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("Copiar enlace", color = Ember)
                        }
                    }

                    is GuestRemoteState.Failed -> Text(
                        text = guestState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Alert,
                    )

                    GuestRemoteState.Stopped -> Unit
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Hairline),
                )
                Text(
                    text = "Huella de la clave de esta app",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChalkMuted,
                )
                Text(
                    text = fingerprint.ifBlank { "generando..." },
                    style = MaterialTheme.typography.labelSmall,
                    color = Chalk,
                )
                Text(
                    text = "Debe coincidir con la que muestra la TV al pedirte permiso.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChalkFaint,
                )
                TextButton(onClick = onRepair) {
                    Text("Generar clave nueva y reemparejar", color = Ember)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(hostField, portField) }) {
                Text("Guardar y conectar", color = Ember)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = ChalkMuted)
            }
        },
    )
}

private data class Status(val color: Color, val label: String)

private fun ConnectionState.asStatus(configured: Boolean): Status = when (this) {
    is ConnectionState.Connected -> Status(Signal, "Conectada")
    ConnectionState.Connecting -> Status(Waiting, "Conectando")
    ConnectionState.AwaitingAuthorization -> Status(Waiting, "Autoriza en la TV")
    is ConnectionState.Failed -> Status(Alert, "Sin conexion")
    ConnectionState.Disconnected -> Status(
        if (configured) ChalkFaint else Alert,
        if (configured) "En reposo" else "Sin configurar",
    )
}

private data class Banner(
    val title: String,
    val body: String,
    val accent: Color,
    val offersSettings: Boolean = false,
)

@Composable
private fun MandoUiState.banner(): Banner? {
    val current = connection
    val configured = settings.isConfigured
    return remember(current, configured) {
        when {
            !configured -> Banner(
                title = "Falta la IP de la TV",
                body = "En la TV: Ajustes / Red e Internet / mira la IP. Activa antes la " +
                    "depuracion por red en Opciones de desarrollador.",
                accent = Alert,
                offersSettings = true,
            )

            current is ConnectionState.AwaitingAuthorization -> Banner(
                title = "Mira la television",
                body = "Acepta el aviso Permitir depuracion USB y marca la casilla de " +
                    "permitir siempre desde este dispositivo.",
                accent = Waiting,
            )

            current is ConnectionState.Failed -> Banner(
                title = current.message,
                body = current.hint.orEmpty(),
                accent = Alert,
            )

            else -> null
        }
    }
}
