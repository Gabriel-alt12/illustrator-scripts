package com.gabriel.tvmando.ui.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gabriel.tvmando.domain.ForceStopApp
import com.gabriel.tvmando.domain.LaunchApp
import com.gabriel.tvmando.domain.PressKey
import com.gabriel.tvmando.domain.Scene
import com.gabriel.tvmando.domain.SceneStep
import com.gabriel.tvmando.domain.SetBrightness
import com.gabriel.tvmando.domain.SetVolume
import com.gabriel.tvmando.domain.TvApp
import com.gabriel.tvmando.domain.TvKey
import com.gabriel.tvmando.domain.TypeText
import com.gabriel.tvmando.ui.components.Tap
import com.gabriel.tvmando.ui.theme.Alert
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkFaint
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.EmberSunk
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.InkRaised

/**
 * Editor visual de una escena: anadir paso, elegir comando y definir el retardo,
 * que es lo que pide la seccion 7 de la especificacion, mas mover y borrar pasos.
 *
 * Los pasos se editan como bloques cerrados: para cambiar uno se borra y se vuelve
 * a anadir. Es mas tosco que un formulario por paso, pero evita tener que
 * deserializar la linea de shell de vuelta a un comando tipado.
 */
@Composable
fun SceneEditor(
    scene: Scene,
    apps: List<TvApp>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Scene) -> Unit,
    onDelete: () -> Unit,
    haptics: (Tap) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(scene.id) { mutableStateOf(scene.name) }
    var steps by remember(scene.id) { mutableStateOf(scene.steps) }
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Icons.Rounded.ArrowBack, "Volver a la lista", onClick = onDismiss)
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (isNew) "Escena nueva" else "Editar escena",
                style = MaterialTheme.typography.labelMedium,
                color = ChalkMuted,
                modifier = Modifier.weight(1f),
            )
            IconAction(
                icon = Icons.Rounded.Check,
                description = "Guardar la escena",
                accent = true,
                enabled = name.isNotBlank(),
                onClick = { onSave(scene.copy(name = name.trim(), steps = steps)) },
            )
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre de la escena") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Pasos",
                style = MaterialTheme.typography.labelMedium,
                color = ChalkMuted,
            )
            Spacer(Modifier.height(10.dp))

            if (steps.isEmpty()) {
                Text(
                    text = "Todavia no hay pasos. Anade el primero abajo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChalkFaint,
                )
            }

            steps.forEachIndexed { index, step ->
                StepRow(
                    number = index + 1,
                    step = step,
                    canMoveUp = index > 0,
                    canMoveDown = index < steps.size - 1,
                    onMoveUp = {
                        haptics(Tap.Press)
                        steps = steps.swapped(index, index - 1)
                    },
                    onMoveDown = {
                        haptics(Tap.Press)
                        steps = steps.swapped(index, index + 1)
                    },
                    onDelete = {
                        haptics(Tap.Reject)
                        steps = steps.filterIndexed { i, _ -> i != index }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))

            AddStepButton {
                haptics(Tap.Press)
                showPicker = true
            }

            if (!isNew) {
                Spacer(Modifier.height(24.dp))
                QuietTextButton("Eliminar esta escena", Alert, onDelete)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPicker) {
        StepPickerDialog(
            apps = apps,
            onDismiss = { showPicker = false },
            onAdd = { step ->
                steps = steps + step
                showPicker = false
            },
        )
    }
}

@Composable
private fun StepRow(
    number: Int,
    step: SceneStep,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(InkRaised)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelSmall,
            color = Ember,
            modifier = Modifier.width(26.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = step.label,
                style = MaterialTheme.typography.bodyLarge,
                color = Chalk,
            )
            if (step.delayMs > 0) {
                Text(
                    text = "espera " + SceneStep.formatDelay(step.delayMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = ChalkMuted,
                )
            }
        }
        SmallIcon(Icons.Rounded.KeyboardArrowUp, "Subir el paso", canMoveUp, onMoveUp)
        SmallIcon(Icons.Rounded.KeyboardArrowDown, "Bajar el paso", canMoveDown, onMoveDown)
        SmallIcon(Icons.Rounded.Delete, "Quitar el paso", true, onDelete)
    }
}

@Composable
private fun SmallIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) ChalkMuted else ChalkFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AddStepButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EmberSunk)
            .border(1.dp, Ember, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = Ember,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Anadir paso".uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Ember,
        )
    }
}

/** Tipos de paso que ofrece el editor. */
private enum class StepKind(val label: String) {
    KEY("Tecla"),
    APP("Abrir app"),
    STOP("Cerrar app"),
    TEXT("Escribir"),
    VOLUME("Volumen"),
    BRIGHTNESS("Brillo"),
    WAIT("Esperar"),
}

@Composable
private fun StepPickerDialog(
    apps: List<TvApp>,
    onDismiss: () -> Unit,
    onAdd: (SceneStep) -> Unit,
) {
    var kind by remember { mutableStateOf(StepKind.KEY) }
    var key by remember { mutableStateOf(TvKey.POWER) }
    var packageName by remember { mutableStateOf(apps.firstOrNull()?.packageName.orEmpty()) }
    var text by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("8") }
    var delaySeconds by remember { mutableStateOf("0") }

    fun buildStep(): SceneStep? {
        val delayMs = ((delaySeconds.replace(',', '.').toDoubleOrNull() ?: 0.0) * 1000).toLong()
        return when (kind) {
            StepKind.KEY -> SceneStep.of(PressKey(key), delayMs)
            StepKind.APP -> packageName.takeIf { it.isNotBlank() }
                ?.let { SceneStep.of(LaunchApp(it), delayMs).withAppLabel(apps, it, "Abrir") }
            StepKind.STOP -> packageName.takeIf { it.isNotBlank() }
                ?.let { SceneStep.of(ForceStopApp(it), delayMs).withAppLabel(apps, it, "Cerrar") }
            StepKind.TEXT -> text.takeIf { it.isNotBlank() }
                ?.let { SceneStep.of(TypeText(it), delayMs).copy(label = "Escribir \"$it\"") }
            StepKind.VOLUME -> SceneStep.of(SetVolume(number.toIntOrNull() ?: 0), delayMs)
            StepKind.BRIGHTNESS -> SceneStep.of(SetBrightness(number.toIntOrNull() ?: 0), delayMs)
            StepKind.WAIT -> if (delayMs > 0) SceneStep.wait(delayMs) else null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkRaised,
        title = {
            Text("Nuevo paso", style = MaterialTheme.typography.titleLarge, color = Chalk)
        },
        text = {
            Column(
                modifier = Modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StepKind.entries.forEach { option ->
                        PickerChip(
                            label = option.label,
                            selected = option == kind,
                            onClick = { kind = option },
                        )
                    }
                }

                when (kind) {
                    StepKind.KEY -> OptionList(
                        options = TvKey.entries.map { it.label },
                        selectedIndex = TvKey.entries.indexOf(key),
                        onSelect = { key = TvKey.entries[it] },
                    )

                    StepKind.APP, StepKind.STOP -> if (apps.isEmpty()) {
                        Text(
                            text = "Abre la pestana de Apps con la TV conectada para " +
                                "que aparezcan aqui los paquetes reales.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChalkFaint,
                        )
                    } else {
                        OptionList(
                            options = apps.map { it.displayName },
                            selectedIndex = apps.indexOfFirst { it.packageName == packageName },
                            onSelect = { packageName = apps[it].packageName },
                        )
                    }

                    StepKind.TEXT -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.replace("\n", "") },
                        label = { Text("Texto a escribir") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    StepKind.VOLUME -> NumberField(
                        value = number,
                        onValueChange = { number = it },
                        label = "Nivel de volumen (0-15 en casi todas las TV)",
                    )

                    StepKind.BRIGHTNESS -> NumberField(
                        value = number,
                        onValueChange = { number = it },
                        label = "Brillo (0-255)",
                    )

                    StepKind.WAIT -> Text(
                        text = "Un paso que solo espera. Pon los segundos abajo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkFaint,
                    )
                }

                NumberField(
                    value = delaySeconds,
                    onValueChange = { delaySeconds = it },
                    label = if (kind == StepKind.WAIT) {
                        "Segundos de espera"
                    } else {
                        "Esperar despues (segundos)"
                    },
                    allowDecimals = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { buildStep()?.let(onAdd) }) {
                Text("Anadir", color = Ember)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = ChalkMuted)
            }
        },
    )
}

@Composable
private fun PickerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) EmberSunk else InkRaised)
            .border(1.dp, if (selected) Ember else Hairline, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Ember else ChalkMuted,
        )
    }
}

@Composable
private fun OptionList(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Hairline, RoundedCornerShape(14.dp)),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(items = options.withIndex().toList(), key = { it.index }) { (index, option) ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) EmberSunk else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) Ember else Chalk,
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    allowDecimals: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onValueChange(raw.filter { it.isDigit() || (allowDecimals && (it == '.' || it == ',')) })
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimals) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Cambia "Abrir com.netflix.mediaclient" por "Abrir Netflix" si sabemos el nombre. */
private fun SceneStep.withAppLabel(apps: List<TvApp>, packageName: String, verb: String): SceneStep {
    val name = apps.firstOrNull { it.packageName == packageName }?.displayName ?: packageName
    return copy(label = "$verb $name")
}

private fun <T> List<T>.swapped(a: Int, b: Int): List<T> = toMutableList().apply {
    val tmp = this[a]
    this[a] = this[b]
    this[b] = tmp
}
