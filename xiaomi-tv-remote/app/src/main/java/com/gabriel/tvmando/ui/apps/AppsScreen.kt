package com.gabriel.tvmando.ui.apps

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gabriel.tvmando.domain.TvApp
import com.gabriel.tvmando.domain.matches
import com.gabriel.tvmando.ui.AppsUiState
import com.gabriel.tvmando.ui.components.AppTile
import com.gabriel.tvmando.ui.components.GhostButton
import com.gabriel.tvmando.ui.components.Tap
import com.gabriel.tvmando.ui.theme.Alert
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkFaint
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.InkRaised

/**
 * Rejilla de apps instaladas en la TV.
 *
 * La lista se descubre con `pm list packages -3` en lugar de llevar los paquetes
 * escritos a fuego: la seccion 11 de la especificacion avisa de que cambian entre
 * versiones y regiones. Se completa con las de streaming preinstaladas de fabrica que
 * esa consulta no trae (ver [com.gabriel.tvmando.domain.AppCatalog.parseInstalledPackages]).
 * La app en primer plano se detecta con `dumpsys` y se resalta.
 */
@Composable
fun AppsScreen(
    state: AppsUiState,
    connected: Boolean,
    enabled: Boolean,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onLaunch: (TvApp) -> Unit,
    onForceStop: (TvApp) -> Unit,
    haptics: (Tap) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Al conectar (o reconectar) se pide la lista una vez.
    LaunchedEffect(connected) {
        if (connected) onLoad()
    }

    var filter by rememberSaveable { mutableStateOf("") }
    val shown = remember(state.apps, filter) { state.apps.filter { it.matches(filter) } }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        state.isLoading -> "Buscando apps en la TV"
                        state.apps.isEmpty() -> "Sin apps todavia"
                        shown.size != state.apps.size ->
                            "${shown.size} de ${state.apps.size} apps"
                        else -> "${state.apps.size} apps instaladas"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = ChalkMuted,
                )
                Text(
                    text = "Manten pulsado para forzar el cierre",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChalkFaint,
                )
            }
            GhostButton(
                onClick = {
                    haptics(Tap.Press)
                    onRefresh()
                },
                icon = Icons.Rounded.Refresh,
                description = "Actualizar la lista de apps",
            )
        }

        Spacer(Modifier.height(14.dp))

        // El filtro solo sale cuando la rejilla ya es larga: en una TV con cuatro apps
        // seria una caja de texto ocupando sitio para nada.
        if (state.apps.size > FILTER_THRESHOLD) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it.replace("\n", "") },
                label = { Text("Buscar app") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Rounded.FilterList, contentDescription = null, tint = ChalkMuted)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
        }

        when {
            state.error != null && state.apps.isEmpty() -> EmptyState(
                title = "No se pudo leer la lista",
                body = state.error,
                accent = Alert,
            )

            !connected && state.apps.isEmpty() -> EmptyState(
                title = "Sin conexion con la TV",
                body = "En cuanto haya sesion ADB se cargan las apps instaladas.",
                accent = Hairline,
            )

            shown.isEmpty() && state.apps.isNotEmpty() -> EmptyState(
                title = "Ninguna app con ese nombre",
                body = "Prueba con parte del nombre o del paquete.",
                accent = Hairline,
            )

            state.apps.isEmpty() -> EmptyState(
                title = if (state.isLoading) "Preguntando a la TV" else "Nada que mostrar",
                body = if (state.isLoading) {
                    "pm list packages -3"
                } else {
                    "Toca el boton de actualizar para volver a intentarlo."
                },
                accent = Hairline,
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = shown, key = { it.packageName }) { app ->
                    AppTile(
                        app = app,
                        isForeground = app.packageName == state.foregroundPackage,
                        enabled = enabled,
                        onClick = {
                            haptics(Tap.Confirm)
                            onLaunch(app)
                        },
                        onLongClick = {
                            haptics(Tap.Reject)
                            onForceStop(app)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    accent: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(InkRaised, RoundedCornerShape(20.dp))
            .border(1.dp, accent, RoundedCornerShape(20.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Chalk,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Por debajo de esto la rejilla se recorre de un vistazo y el filtro sobra. */
private const val FILTER_THRESHOLD = 8
