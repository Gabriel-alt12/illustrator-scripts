package com.gabriel.tvmando.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkFaint
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.EmberGlow
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
 * Al pulsar una direccion se enciende un arco de luz en el aro del dial, en ese
 * lado: se ve de reojo hacia donde se ha ido sin mirar el icono. Las flechas repiten
 * al mantenerlas pulsadas; el OK no, que confirmar dos veces sin querer se paga caro.
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
    val ember = Ember
    val glow = EmberGlow
    val up = rememberPress()
    val down = rememberPress()
    val left = rememberPress()
    val right = rememberPress()

    // Angulos de Compose: 0 a las tres en punto, positivo en sentido horario.
    Box(
        modifier = modifier
            .size(DIAL_SIZE)
            .emberArc({ up.light.value }, ember, glow, startAngle = -90f - ARC_SWEEP / 2, sweepAngle = ARC_SWEEP)
            .emberArc({ right.light.value }, ember, glow, startAngle = -ARC_SWEEP / 2, sweepAngle = ARC_SWEEP)
            .emberArc({ down.light.value }, ember, glow, startAngle = 90f - ARC_SWEEP / 2, sweepAngle = ARC_SWEEP)
            .emberArc({ left.light.value }, ember, glow, startAngle = 180f - ARC_SWEEP / 2, sweepAngle = ARC_SWEEP)
            .clip(CircleShape)
            .background(InkRaised)
            .border(1.dp, Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Direction(Alignment.TopCenter, Icons.Rounded.KeyboardArrowUp, "Arriba", enabled, up, onUp)
        Direction(Alignment.BottomCenter, Icons.Rounded.KeyboardArrowDown, "Abajo", enabled, down, onDown)
        Direction(Alignment.CenterStart, Icons.Rounded.KeyboardArrowLeft, "Izquierda", enabled, left, onLeft)
        Direction(Alignment.CenterEnd, Icons.Rounded.KeyboardArrowRight, "Derecha", enabled, right, onRight)
        CenterKey(enabled = enabled, onClick = onCenter)
    }
}

@Composable
private fun BoxScope.Direction(
    alignment: Alignment,
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    press: Press,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (press.pressed) Ember else Chalk, label = "dpad-tint")

    // Mantener una flecha la repite: recorrer un menu largo sin machacar el dedo.
    RepeatWhilePressed(pressed = press.pressed, enabled = enabled, onRepeat = onClick)

    Box(
        modifier = Modifier
            .align(alignment)
            .padding(5.dp)
            .size(ZONE_SIZE)
            .clickable(
                interactionSource = press.interaction,
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
    val press = rememberPress()
    val ember = Ember
    val glow = EmberGlow
    val tint by animateColorAsState(if (press.pressed) ember else Chalk, label = "ok-tint")

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(CENTER_SIZE)
            .pressScale(press, pressedScale = 0.92f)
            .emberRing({ press.light.value }, ember, glow, CircleShape, glowWidth = 14.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(InkHigh, InkRaised)))
            .border(1.5.dp, Hairline, CircleShape)
            .clickable(
                interactionSource = press.interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "OK",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) tint else ChalkFaint,
        )
    }
}

// Medido contra un Galaxy S23 (412 x 891 dp): con estos tamanos el mando entero
// cabe sin scroll, que es lo que importa en un control que se usa sin mirar.
private val DIAL_SIZE = 208.dp
private val ZONE_SIZE = 64.dp
private val CENTER_SIZE = 80.dp

/** Grados de arco por direccion: cabe holgado en su cuarto (90) sin tocar al vecino. */
private const val ARC_SWEEP = 64f
