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
import androidx.compose.ui.unit.dp
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.EmberSunk
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.InkHigh
import com.gabriel.tvmando.ui.theme.InkRaised

/**
 * Botones del mando.
 *
 * Todos comparten el mismo gesto: al pulsar encogen un poco y el borde se enciende
 * en color acento. Es la unica animacion de la app y esta para confirmar la
 * pulsacion, no para decorar.
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
 * Boton de encendido: circulo grande, centrado y separado del resto para que no se
 * pulse por error. Es el unico control con relleno degradado.
 */
@Composable
fun PowerKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector,
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
            .size(128.dp)
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
            tint = if (enabled) Ember else ChalkMuted,
            modifier = Modifier.size(48.dp),
        )
    }
}

/**
 * Balancin de volumen: una pieza vertical alta partida en dos mitades. Alto a
 * proposito, para acertar con el pulgar sin mirar y con el movil en una mano.
 */
@Composable
fun VolumeRocker(
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconUp: ImageVector,
    iconDown: ImageVector,
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(InkRaised)
            .border(1.dp, Hairline, RoundedCornerShape(36.dp)),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        RockerHalf(
            onClick = onUp,
            enabled = enabled,
            icon = iconUp,
            description = "Subir volumen",
            modifier = Modifier.height(132.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Hairline),
        )
        RockerHalf(
            onClick = onDown,
            enabled = enabled,
            icon = iconDown,
            description = "Bajar volumen",
            modifier = Modifier.height(132.dp),
        )
    }
}

@Composable
private fun RockerHalf(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateColorAsState(if (pressed) Ember else Chalk, label = "rocker-tint")
    val background by animateColorAsState(
        if (pressed) EmberSunk else Color.Transparent,
        label = "rocker-bg",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
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
            tint = if (enabled) tint else ChalkMuted,
            modifier = Modifier.size(34.dp),
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
