package com.gabriel.tvmando.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabriel.tvmando.domain.VolumeLevel
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkFaint
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.EmberGlow
import com.gabriel.tvmando.ui.theme.EmberSunk
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.InkHigh
import com.gabriel.tvmando.ui.theme.InkRaised
import com.gabriel.tvmando.ui.theme.Radius
import com.gabriel.tvmando.ui.theme.Space
import kotlin.math.roundToInt

/**
 * Botones del mando.
 *
 * Todos comparten el mismo gesto: al pulsar, la tecla encoge un poco y se enciende
 * un anillo de luz en su borde; al soltar, la luz se apaga despacio. Luz, no pintura:
 * el fondo de la tecla no cambia de color. Ver [PressLight.kt].
 */

/**
 * Marca y estado en una sola pieza: el nombre de la app y, debajo, la luz de conexion
 * con su etiqueta. Antes eran una pastilla aparte; asi la cabecera es mas baja y la
 * marca queda donde se espera, arriba a la izquierda.
 */
@Composable
fun BrandStatus(
    color: Color,
    label: String,
    detail: String?,
    pulse: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val animated by animateColorAsState(color, label = "status-color")
    Column(modifier) {
        Text(
            text = "Ember",
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 26.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.6).sp,
            ),
            color = Chalk,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(animated, pulse)
            Spacer(Modifier.width(Space.sm))
            Text(
                text = (if (detail.isNullOrBlank()) label else "$label  \u00B7  $detail").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = ChalkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * La brasa: un punto del color del estado con su halo. Mientras se espera a la TV
 * respira, apagandose y encendiendose despacio; es la unica animacion continua de la
 * app y solo existe mientras hay algo pendiente.
 */
@Composable
private fun StatusDot(color: Color, pulse: Boolean) {
    val breath = if (pulse) {
        rememberInfiniteTransition(label = "brasa").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "respiracion",
        ).value
    } else {
        1f
    }
    Box(
        Modifier
            .size(8.dp)
            .drawBehind {
                val radius = size.minDimension / 2
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.55f * breath), color.copy(alpha = 0f)),
                        center = center,
                        radius = radius * 2.4f,
                    ),
                    radius = radius * 2.4f,
                )
                drawCircle(color.copy(alpha = color.alpha * breath), radius = radius)
            },
    )
}

/**
 * Boton de encendido: circulo grande, separado del resto para que no se pulse por
 * error. Es el unico control con relleno degradado en color acento, y su luz es la
 * mas ancha.
 *
 * Si se sabe si la TV esta encendida, el icono lo cuenta: encendido con la TV en
 * marcha, apagado con la TV en reposo. KEYCODE_POWER es un interruptor y hasta ahora
 * se pulsaba a ciegas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PowerKey(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 96.dp,
    awake: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val press = rememberPress()
    val ember = Ember
    val glow = EmberGlow

    Box(
        modifier = modifier
            .size(size)
            .pressScale(press, pressedScale = 0.94f)
            .emberRing({ press.light.value }, ember, glow, CircleShape, strokeWidth = 2.5.dp, glowWidth = 18.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(EmberSunk, InkHigh)))
            .border(1.5.dp, Hairline, CircleShape)
            .combinedClickable(
                interactionSource = press.interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = when (awake) {
                true -> "Apagar la TV"
                false -> "Encender la TV"
                null -> "Encender o apagar la TV"
            },
            tint = when {
                !enabled -> ChalkFaint
                awake == false -> ChalkMuted
                else -> ember
            },
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
    /** Nivel exacto, si la TV lo cuenta: se pinta como una raya de luz en el borde. */
    level: VolumeLevel? = null,
    /** Con nivel conocido, arrastrar a lo largo de la barra lo fija de golpe. */
    onLevelChange: ((Int) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Radius.bar)
    val ember = Ember
    val glow = EmberGlow

    // Mientras se arrastra manda lo que hay bajo el dedo; al soltar, se sigue viendo
    // hasta que la TV confirme el nivel de verdad en la siguiente consulta.
    var dragging by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(level) { dragging = null }
    val shown = dragging ?: level?.current
    val fraction = if (level != null && shown != null) shown.toFloat() / level.max else 0f
    val draggable = level != null && onLevelChange != null && enabled

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(shape)
            .background(InkRaised)
            .border(1.dp, Hairline, shape)
            .then(
                if (draggable) {
                    Modifier.pointerInput(level!!.max) {
                        val inset = TRACK_INSET.toPx()
                        fun levelAt(x: Float): Int {
                            val usable = (size.width - inset * 2).coerceAtLeast(1f)
                            return ((x - inset) / usable * level.max).roundToInt().coerceIn(0, level.max)
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { start -> dragging = levelAt(start.x) },
                            onDragEnd = { dragging?.let { onLevelChange!!(it) } },
                            onDragCancel = { dragging = null },
                        ) { change, _ ->
                            change.consume()
                            val next = levelAt(change.position.x)
                            if (next != dragging) {
                                dragging = next
                                onLevelChange!!(next)
                            }
                        }
                    }
                } else {
                    Modifier
                },
            )
            .emberTrack({ fraction }, ember, glow, inset = TRACK_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VolumeZone(onDown, iconDown, "Bajar volumen", enabled, Modifier.weight(1f), repeats = true)
        Divider()
        VolumeZone(
            onMute, iconMute, "Silenciar", enabled, Modifier.weight(0.8f),
            caption = shown?.toString(),
        )
        Divider()
        VolumeZone(onUp, iconUp, "Subir volumen", enabled, Modifier.weight(1f), repeats = true)
    }
}

/** Margen de la raya de nivel; el mismo que usa el arrastre para traducir el dedo a nivel. */
private val TRACK_INSET = 18.dp

@Composable
private fun Divider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(Hairline),
    )
}

/** Zona de la barra: sin silueta propia, la luz es una raya bajo el icono. */
@Composable
private fun VolumeZone(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    repeats: Boolean = false,
    caption: String? = null,
) {
    val press = rememberPress()
    val ember = Ember
    val glow = EmberGlow

    // Subir y bajar se mantienen pulsados; silenciar no, que es un interruptor y
    // repetirlo lo unico que hace es dejarlo como estaba.
    if (repeats) RepeatWhilePressed(pressed = press.pressed, enabled = enabled, onRepeat = onClick)
    val tint by animateColorAsState(if (press.pressed) ember else Chalk, label = "vol-tint")

    Box(
        modifier = modifier
            .fillMaxHeight()
            .emberUnderline({ press.light.value }, ember, glow, bottomInset = 16.dp)
            .clickable(
                interactionSource = press.interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (enabled) tint else ChalkFaint,
                modifier = Modifier.size(32.dp),
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = ChalkMuted,
                )
            }
        }
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
    val press = rememberPress()
    val ember = Ember
    val glow = EmberGlow
    val tint by animateColorAsState(
        if (press.pressed) ember else if (emphasis) Chalk else ChalkMuted,
        label = "round-tint",
    )

    Box(
        modifier = modifier
            .size(size)
            .pressScale(press)
            .emberRing({ press.light.value }, ember, glow, CircleShape)
            .clip(CircleShape)
            .background(if (emphasis) InkHigh else InkRaised)
            .border(1.dp, if (emphasis) Hairline else Color.Transparent, CircleShape)
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
    val press = rememberPress()
    val ember = Ember
    val glow = EmberGlow
    val tint by animateColorAsState(if (press.pressed) ember else Chalk, label = "pill-tint")
    val shape = RoundedCornerShape(Radius.card)

    Column(
        modifier = modifier
            .height(60.dp)
            .pressScale(press)
            .emberRing({ press.light.value }, ember, glow, shape)
            .clip(shape)
            .background(InkRaised)
            .border(1.dp, Hairline, shape)
            .clickable(
                interactionSource = press.interaction,
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
        Spacer(Modifier.height(Space.xs))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) ChalkMuted else ChalkFaint,
        )
    }
}

/** Boton secundario de la cabecera: discreto, sin relleno de acento. */
@Composable
fun GhostButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
) {
    val press = rememberPress()
    val ember = Ember
    val glow = EmberGlow
    val tint by animateColorAsState(if (press.pressed) ember else ChalkMuted, label = "ghost-tint")
    val shape = RoundedCornerShape(Radius.button)

    Box(
        modifier = modifier
            .size(46.dp)
            .pressScale(press)
            .emberRing({ press.light.value }, ember, glow, shape)
            .clip(shape)
            .background(InkRaised)
            .border(1.dp, Hairline, shape)
            .clickable(
                interactionSource = press.interaction,
                indication = null,
                onClick = onClick,
            ),
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
