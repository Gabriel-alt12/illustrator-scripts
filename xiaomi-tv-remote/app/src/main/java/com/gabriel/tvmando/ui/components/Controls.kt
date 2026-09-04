package com.gabriel.tvmando.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkFaint
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.EmberSunk
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.InkHigh
import com.gabriel.tvmando.ui.theme.InkRaised

/**
 * Botones del mando.
 *
 * Todos comparten el mismo gesto: al pulsar encogen un poco y se encienden en color
 * acento. Es la unica animacion de la app y esta para confirmar la pulsacion, no
 * para decorar.
 */

/** Punto de color con etiqueta: el indicador de conexion siempre visible. */
@Composable
fun StatusBadge(
    color: Color,
    label: String,
    detail: String?,
    modifier: Modifier = Modifier,
) {
    val animated by animateColorAsState(color, label = "status-color")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(InkRaised)
            .border(1.dp, Hairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(animated),
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Chalk,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = ChalkMuted,
                )
            }
        }
    }
}

/**
 * Boton de encendido: circulo grande, separado del resto para que no se pulse por
 * error. Es el unico control con relleno degradado en color acento.
 */
@Composable
fun PowerKey(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 96.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "power-scale",
    )
    val ring by animateColorAsState(if (pressed) Ember else Hairline, label = "power-ring")

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(EmberSunk, InkHigh)))
            .border(2.dp, ring, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Encender o apagar la TV",
            tint = if (enabled) Ember else ChalkFaint,
            modifier = Modifier.size(size * 0.38f),
        )
    }
}

/**
 * Barra de volumen: una pieza ancha partida en tres zonas (bajar, silenciar, subir).
 * Horizontal porque asi cabe entera bajo el pulgar sin mover la mano.
 *
 * Subir y bajar repiten al mantenerlas: diez puntos de volumen no son diez toques.
 */
@Composable
fun VolumeBar(
    onDown: () -> Unit,
    onMute: () -> Unit,
    onUp: () -> Unit,
    iconDown: ImageVector,
    iconMute: ImageVector,
    iconUp: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(InkRaised)
            .border(1.dp, Hairline, RoundedCornerShape(28.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VolumeZone(onDown, iconDown, "Bajar volumen", enabled, Modifier.weight(1f), repeats = true)
        Divider()
        VolumeZone(onMute, iconMute, "Silenciar", enabled, Modifier.weight(0.8f))
        Divider()
        VolumeZone(onUp, iconUp, "Subir volumen", enabled, Modifier.weight(1f), repeats = true)
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(Hairline),
    )
}

@Composable
private fun VolumeZone(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    repeats: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Subir y bajar se mantienen pulsados; silenciar no, que es un interruptor y
    // repetirlo lo unico que hace es dejarlo como estaba.
    if (repeats) RepeatWhilePressed(pressed = pressed, enabled = enabled, onRepeat = onClick)
    val tint by animateColorAsState(if (pressed) Ember else Chalk, label = "vol-tint")
    val background by animateColorAsState(
        if (pressed) EmberSunk else Color.Transparent,
        label = "vol-bg",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) tint else ChalkFaint,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Boton circular de la fila multimedia. */
@Composable
fun RoundKey(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 52.dp,
    emphasis: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ring by animateColorAsState(
        if (pressed) Ember else if (emphasis) Hairline else Color.Transparent,
        label = "round-ring",
    )
    val tint by animateColorAsState(
        if (pressed) Ember else if (emphasis) Chalk else ChalkMuted,
        label = "round-tint",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (emphasis) InkHigh else InkRaised)
            .border(1.dp, ring, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) tint else ChalkFaint,
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

/** Boton de la fila de navegacion: cuadrado redondeado con icono y etiqueta. */
@Composable
fun PillKey(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ring by animateColorAsState(if (pressed) Ember else Hairline, label = "pill-ring")
    val tint by animateColorAsState(if (pressed) Ember else Chalk, label = "pill-tint")

    Column(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(InkRaised)
            .border(1.dp, ring, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else ChalkFaint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) ChalkMuted else ChalkFaint,
        )
    }
}

/** Boton secundario de la cabecera: discreto, sin relleno. */
@Composable
fun GhostButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val border by animateColorAsState(if (pressed) Ember else Hairline, label = "ghost-border")

    Box(
        modifier = modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(InkRaised)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = ChalkMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
