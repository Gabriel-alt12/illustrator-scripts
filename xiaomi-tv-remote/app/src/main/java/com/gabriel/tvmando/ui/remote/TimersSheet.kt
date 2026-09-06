package com.gabriel.tvmando.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gabriel.tvmando.data.WakeSchedule
import com.gabriel.tvmando.domain.Scene
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Temporizadores, con pulsacion larga en la tecla de encendido: apagar dentro de un
 * rato (para quedarse dormido viendo algo) y encender a una hora, solo o con una
 * escena.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimersSheet(
    sleepDeadline: Long?,
    wake: WakeSchedule?,
    scenes: List<Scene>,
    onSleep: (minutes: Int) -> Unit,
    onCancelSleep: () -> Unit,
    onWake: (hour: Int, minute: Int, sceneId: String?) -> Unit,
    onCancelWake: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeState = rememberTimePickerState(initialHour = 20, initialMinute = 0, is24Hour = true)
    var sceneId by rememberSaveable { mutableStateOf<String?>(null) }

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
                .padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text("Temporizadores", style = MaterialTheme.typography.titleLarge, color = Chalk)

            Section("Apagar dentro de")
            if (sleepDeadline != null) {
                Text(
                    text = "La TV se apaga a las ${TIME.format(Date(sleepDeadline))} " +
                        "(quedan ${remainingMinutes(sleepDeadline)} min). Solo si sigue encendida.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Chalk,
                )
                TextButton(onClick = onCancelSleep, contentPadding = PaddingValues(0.dp)) {
                    Text("Cancelar el apagado", style = MaterialTheme.typography.labelLarge, color = EmberInk)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SLEEP_MINUTES.forEach { minutes ->
                        Chip(
                            label = "$minutes min",
                            selected = false,
                            onClick = { onSleep(minutes) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    text = "Sale una notificacion con la cuenta atras y un boton para cancelarla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChalkFaint,
                )
            }

            Section("Encender a las")
            if (wake != null) {
                Text(
                    text = "Programado ${describeWhen(wake.at)}" +
                        (wake.sceneId?.let { id -> scenes.firstOrNull { it.id == id }?.name }
                            ?.let { "  ·  $it" } ?: "  ·  solo encender"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Chalk,
                )
                TextButton(onClick = onCancelWake, contentPadding = PaddingValues(0.dp)) {
                    Text("Cancelar el encendido", style = MaterialTheme.typography.labelLarge, color = EmberInk)
                }
            } else {
                TimeInput(state = timeState)
                Text(text = "QUE HACER", style = MaterialTheme.typography.labelMedium, color = ChalkMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    item {
                        Chip(label = "Solo encender", selected = sceneId == null, onClick = { sceneId = null })
                    }
                    items(scenes, key = { it.id }) { scene ->
                        Chip(label = scene.name, selected = sceneId == scene.id, onClick = { sceneId = scene.id })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onWake(timeState.hour, timeState.minute, sceneId) }) {
                        Text("Programar", style = MaterialTheme.typography.labelLarge, color = EmberInk)
                    }
                }
                Text(
                    text = "Una sola vez: hoy si la hora no ha pasado, si no manana. La TV tiene que " +
                        "estar en reposo con la WiFi activa, como cuando la enciende el mando.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChalkFaint,
                )
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Hairline),
        )
        Spacer(Modifier.height(Space.md))
        Text(text = text.uppercase(), style = MaterialTheme.typography.labelMedium, color = ChalkMuted)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.chip)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) EmberSunk else InkHigh)
            .border(1.dp, if (selected) Ember else Hairline, shape)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
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

private fun remainingMinutes(deadline: Long): Long =
    ((deadline - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)

/** "hoy a las 20:00" o "manana a las 20:00". */
private fun describeWhen(at: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance()
    val sameDay = target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
        target.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    return (if (sameDay) "hoy" else "manana") + " a las " + TIME.format(Date(at))
}

private val SLEEP_MINUTES = listOf(15, 30, 45, 60, 90)
private val TIME = SimpleDateFormat("HH:mm", Locale.getDefault())
