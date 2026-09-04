package com.gabriel.tvmando.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import com.gabriel.tvmando.ui.theme.ChalkFaint
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.EmberSunk
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.InkHigh
import com.gabriel.tvmando.ui.theme.InkRaised

/**
 * Cruceta direccional con OK en el centro.
 *
 * Es la pieza mas grande de la app a proposito: se navega por menus de la TV sin
 * mirar el movil, asi que cada zona tiene 64 dp de lado (por encima de los 48 dp
 * minimos) y el OK queda en el centro geometrico, donde cae el pulgar por defecto.
 *
 * Las flechas repiten al mantenerlas pulsadas; el OK no, que confirmar dos veces sin
 * querer se paga caro.
 */
@Composable
fun Dpad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onCenter: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(DIAL_SIZE)
            .clip(CircleShape)
            .background(InkRaised)
            .border(1.dp, Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Direction(Alignment.TopCenter, Icons.Rounded.KeyboardArrowUp, "Arriba", enabled, onUp)
        Direction(Alignment.BottomCenter, Icons.Rounded.KeyboardArrowDown, "Abajo", enabled, onDown)
        Direction(Alignment.CenterStart, Icons.Rounded.KeyboardArrowLeft, "Izquierda", enabled, onLeft)
        Direction(Alignment.CenterEnd, Icons.Rounded.KeyboardArrowRight, "Derecha", enabled, onRight)
        CenterKey(enabled = enabled, onClick = onCenter)
    }
}

@Composable
private fun BoxScope.Direction(
    alignment: Alignment,
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        if (pressed) EmberSunk else Color.Transparent,
        label = "dpad-bg",
    )
    val tint by animateColorAsState(if (pressed) Ember else Chalk, label = "dpad-tint")

    // Mantener una flecha la repite: recorrer un menu largo sin machacar el dedo.
    RepeatWhilePressed(pressed = pressed, enabled = enabled, onRepeat = onClick)

    Box(
        modifier = Modifier
            .align(alignment)
            .padding(5.dp)
            .size(ZONE_SIZE)
            .clip(CircleShape)
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
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun BoxScope.CenterKey(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "ok-scale",
    )
    val ring by animateColorAsState(if (pressed) Ember else Hairline, label = "ok-ring")

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(CENTER_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(InkHigh, InkRaised)))
            .border(2.dp, ring, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "OK",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Chalk else ChalkFaint,
        )
    }
}

// Medido contra un Galaxy S23 (412 x 891 dp): con estos tamanos el mando entero
// cabe sin scroll, que es lo que importa en un control que se usa sin mirar.
private val DIAL_SIZE = 208.dp
private val ZONE_SIZE = 64.dp
private val CENTER_SIZE = 80.dp
