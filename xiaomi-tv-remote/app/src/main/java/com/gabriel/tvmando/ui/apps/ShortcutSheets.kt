package com.gabriel.tvmando.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gabriel.tvmando.domain.AppCatalog
import com.gabriel.tvmando.domain.SharedLinkParser
import com.gabriel.tvmando.domain.Shortcut
import com.gabriel.tvmando.domain.TvApp
import com.gabriel.tvmando.ui.theme.Alert
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkFaint
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.EmberInk
import com.gabriel.tvmando.ui.theme.EmberSunk
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.InkHigh
import com.gabriel.tvmando.ui.theme.InkRaised
import com.gabriel.tvmando.ui.theme.Radius
import com.gabriel.tvmando.ui.theme.Space
import kotlinx.coroutines.launch

/** Lo que se esta rellenando en la hoja: alta desde un enlace compartido, a mano o edicion. */
data class ShortcutDraft(
    val id: String? = null,
    val title: String = "",
    val url: String = "",
    val packageName: String? = null,
    val autoOk: Boolean = false,
)

/**
 * Hoja para guardar o editar un acceso directo. Con un enlace, la app se deduce del
 * dominio; sin enlace hay que elegirla, porque entonces se busca por nombre dentro
 * de ella.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutSheet(
    draft: ShortcutDraft,
    apps: List<TvApp>,
    onSave: (ShortcutDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable(draft) { mutableStateOf(draft.title) }
    var url by rememberSaveable(draft) { mutableStateOf(draft.url) }
    var packageName by rememberSaveable(draft) { mutableStateOf(draft.packageName) }
    var autoOk by rememberSaveable(draft) { mutableStateOf(draft.autoOk) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun hideThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    // Las de la TV si ya se conocen; si no, las de siempre, para poder guardar algo
    // antes de haber abierto la pestana de Apps con la tele encendida.
    val choices = apps.ifEmpty { DEFAULT_APPS.map { AppCatalog.describe(it) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.tile, topEnd = Radius.tile),
        containerColor = InkRaised,
        contentColor = Chalk,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Hairline) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl)
                .padding(bottom = Space.xxl)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                text = if (draft.id == null) "Nuevo acceso directo" else "Editar acceso directo",
                style = MaterialTheme.typography.titleLarge,
                color = Chalk,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.replace("\n", " ") },
                label = { Text("Titulo") },
                placeholder = { Text("Reacher") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { value ->
                    url = value.trim()
                    // Al pegar un enlace, la app sale sola del dominio.
                    SharedLinkParser.tvPackageFor(url)?.let { packageName = it }
                },
                label = { Text("Enlace (opcional)") },
                placeholder = { Text("https://www.netflix.com/title/...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (url.isBlank()) {
                    "Sin enlace, Ember abre la app, busca el titulo y da a OK en el primer resultado."
                } else {
                    "Con enlace, la app lo abre en el sitio exacto y por donde lo dejaste."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkFaint,
            )

            Text(text = "APP DE LA TV", style = MaterialTheme.typography.labelMedium, color = ChalkMuted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                items(choices, key = { it.packageName }) { app ->
                    Chip(
                        label = app.displayName,
                        selected = packageName == app.packageName,
                        onClick = { packageName = app.packageName },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Pulsar OK al abrir", style = MaterialTheme.typography.bodyLarge, color = Chalk)
                    Text(
                        text = "Para los enlaces a la ficha de una serie: OK es el boton de Reanudar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkFaint,
                    )
                }
                Spacer(Modifier.width(Space.md))
                Switch(
                    checked = autoOk,
                    onCheckedChange = { autoOk = it },
                    enabled = url.isNotBlank(),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ember,
                        checkedTrackColor = EmberSunk,
                        checkedBorderColor = Ember,
                        uncheckedThumbColor = ChalkMuted,
                        uncheckedTrackColor = InkHigh,
                        uncheckedBorderColor = Hairline,
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { hideThen(onDismiss) }) {
                    Text("Cancelar", style = MaterialTheme.typography.labelLarge, color = ChalkMuted)
                }
                TextButton(
                    enabled = title.isNotBlank() && (url.isNotBlank() || packageName != null),
                    onClick = {
                        val result = ShortcutDraft(
                            id = draft.id,
                            title = title.trim(),
                            url = url,
                            packageName = packageName,
                            autoOk = autoOk && url.isNotBlank(),
                        )
                        hideThen { onSave(result) }
                    },
                ) {
                    Text("Guardar", style = MaterialTheme.typography.labelLarge, color = EmberInk)
                }
            }
        }
    }
}

/** Pulsacion larga sobre una tarjeta: lo que se puede hacer con ella. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutMenuSheet(
    shortcut: Shortcut,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.tile, topEnd = Radius.tile),
        containerColor = InkRaised,
        contentColor = Chalk,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Hairline) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xl)
                .padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(text = shortcut.title, style = MaterialTheme.typography.titleLarge, color = Chalk)
            Text(
                text = shortcut.packageName?.let { AppCatalog.describe(it).displayName } ?: "Enlace",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkMuted,
            )
            Spacer(Modifier.padding(Space.xs))
            MenuRow("Abrir en la TV", onOpen)
            MenuRow("Editar", onEdit)
            MenuRow("Poner en la pantalla de inicio del movil", onPin)
            MenuRow("Borrar", onDelete, color = Alert)
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit, color: androidx.compose.ui.graphics.Color = EmberInk) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(vertical = Space.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.chip)
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) EmberSunk else InkHigh)
            .border(1.dp, if (selected) Ember else Hairline, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) EmberInk else Chalk,
        )
    }
}

/** Las que casi seguro estan en cualquier Google TV de aqui. */
private val DEFAULT_APPS = listOf(
    "com.netflix.ninja",
    "com.amazon.avod.thirdpartyclient",
    "com.disney.disneyplus",
    "com.google.android.youtube.tv",
    "com.wbd.stream",
    "com.movistarplus.androidtv",
    "es.atresmedia.atresplayer.tv",
    "com.rtve.androidtv",
)
