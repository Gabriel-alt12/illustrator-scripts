package com.gabriel.tvmando.ui.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.gabriel.tvmando.domain.Scene
import com.gabriel.tvmando.domain.SceneStep
import com.gabriel.tvmando.domain.TvApp
import com.gabriel.tvmando.ui.ScenesUiState
import com.gabriel.tvmando.ui.components.Tap
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkFaint
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.EmberSunk
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.InkRaised
import com.gabriel.tvmando.ui.theme.Waiting

/**
 * Escenas: secuencias de comandos con retardos entre pasos.
 *
 * La pantalla tiene dos modos, lista y editor, en lugar de navegar a otra pantalla:
 * asi el editor hereda la cabecera de estado de conexion y no hay que arrastrar
 * navigation-compose para un unico salto.
 */
@Composable
fun ScenesScreen(
    state: ScenesUiState,
    apps: List<TvApp>,
    enabled: Boolean,
    onRun: (Scene) -> Unit,
    onCancel: () -> Unit,
    onSave: (Scene) -> Unit,
    onDelete: (String) -> Unit,
    onRestoreDefaults: () -> Unit,
    haptics: (Tap) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Scene?>(null) }

    val current = editing
    if (current != null) {
        SceneEditor(
            scene = current,
            apps = apps,
            isNew = state.scenes.none { it.id == current.id },
            onDismiss = { editing = null },
            onSave = { scene ->
                haptics(Tap.Confirm)
                onSave(scene)
                editing = null
            },
            onDelete = {
                haptics(Tap.Reject)
                onDelete(current.id)
                editing = null
            },
            haptics = haptics,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${state.scenes.size} escenas guardadas",
                style = MaterialTheme.typography.labelMedium,
                color = ChalkMuted,
                modifier = Modifier.weight(1f),
            )
            IconAction(Icons.Rounded.Restore, "Restaurar escenas de fabrica") {
                haptics(Tap.Press)
                onRestoreDefaults()
            }
            Spacer(Modifier.width(8.dp))
            IconAction(Icons.Rounded.Add, "Nueva escena", accent = true) {
                haptics(Tap.Press)
                editing = Scene(
                    id = "user-" + System.currentTimeMillis().toString(36),
                    name = "Escena nueva",
                    steps = emptyList(),
                )
            }
        }

        state.running?.let { progress ->
            Spacer(Modifier.height(14.dp))
            RunningBanner(
                sceneName = progress.scene.name,
                stepNumber = progress.stepIndex + 1,
                total = progress.total,
                detail = progress.step?.label.orEmpty(),
                isWaiting = progress.isWaiting,
                onCancel = {
                    haptics(Tap.Reject)
                    onCancel()
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        if (state.scenes.isEmpty()) {
            Text(
                text = "No queda ninguna escena. Usa el boton de restaurar para " +
                    "recuperar las tres de fabrica.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkFaint,
            )
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = state.scenes, key = { it.id }) { scene ->
                SceneCard(
                    scene = scene,
                    enabled = enabled && state.running == null,
                    onRun = {
                        haptics(Tap.Confirm)
                        onRun(scene)
                    },
                    onEdit = {
                        haptics(Tap.Press)
                        editing = scene
                    },
                )
            }
        }
    }
}

@Composable
private fun SceneCard(
    scene: Scene,
    enabled: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(InkRaised)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = scene.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Chalk,
                )
                Text(
                    text = summary(scene),
                    style = MaterialTheme.typography.labelSmall,
                    color = ChalkMuted,
                )
            }
            IconAction(Icons.Rounded.Edit, "Editar ${scene.name}", onClick = onEdit)
            Spacer(Modifier.width(8.dp))
            IconAction(
                icon = Icons.Rounded.PlayArrow,
                description = "Ejecutar ${scene.name}",
                accent = true,
                enabled = enabled,
                onClick = onRun,
            )
        }

        if (scene.steps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            scene.steps.take(MAX_PREVIEW).forEachIndexed { index, step ->
                StepLine(index + 1, step.describe())
            }
            if (scene.steps.size > MAX_PREVIEW) {
                StepLine(null, "y ${scene.steps.size - MAX_PREVIEW} pasos mas")
            }
        }
    }
}

@Composable
private fun StepLine(number: Int?, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number?.toString()?.padStart(2, '0') ?: "  ",
            style = MaterialTheme.typography.labelSmall,
            color = Ember,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = ChalkMuted,
        )
    }
}

@Composable
private fun RunningBanner(
    sceneName: String,
    stepNumber: Int,
    total: Int,
    detail: String,
    isWaiting: Boolean,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(EmberSunk)
            .border(1.dp, if (isWaiting) Waiting else Ember, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "$sceneName  ·  paso $stepNumber de $total".uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Ember,
            )
            Text(
                text = if (isWaiting) "Esperando..." else detail,
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkMuted,
            )
        }
        IconAction(Icons.Rounded.Stop, "Interrumpir la escena", onClick = onCancel)
    }
}

/** Boton cuadrado de accion, con o sin acento. */
@Composable
internal fun IconAction(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint: Color = when {
        !enabled -> ChalkFaint
        accent -> Ember
        else -> ChalkMuted
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (accent && enabled) EmberSunk else InkRaised)
            .border(1.dp, if (accent && enabled) Ember else Hairline, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Boton de texto discreto, para acciones destructivas o secundarias. */
@Composable
internal fun QuietTextButton(label: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

private fun summary(scene: Scene): String {
    val steps = if (scene.steps.size == 1) "1 paso" else "${scene.steps.size} pasos"
    if (scene.totalDurationMs <= 0) return steps
    return "$steps  ·  ${SceneStep.formatDelay(scene.totalDurationMs)} de esperas"
}

private const val MAX_PREVIEW = 3
