package com.gabriel.tvmando.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Luz de pulsacion: la respuesta comun de todas las teclas del mando.
 *
 * Al pulsar no se pinta la tecla de naranja. Se enciende un anillo (o un arco) de
 * luz en su borde y un halo tenue alrededor; al soltar, la luz se apaga despacio,
 * como una brasa que se enfria. Encender es inmediato porque el pulgar espera la
 * confirmacion al instante; apagar tarda mas para que el ojo, de reojo, vea donde
 * acaba de pasar algo.
 *
 * Se dibuja en la fase de dibujo leyendo el progreso por lambda: la animacion no
 * recompone nada, solo repinta.
 */

/** Lo que una tecla necesita saber de su pulsacion: la fuente, si esta pulsada y su luz. */
class Press(
    val interaction: MutableInteractionSource,
    val pressed: Boolean,
    val light: State<Float>,
)

@Composable
fun rememberPress(): Press {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val light = rememberPressLight(pressed)
    return Press(interaction, pressed, light)
}

@Composable
fun rememberPressLight(pressed: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (pressed) 1f else 0f,
    animationSpec = if (pressed) {
        tween(durationMillis = LIGHT_ON_MS, easing = LinearEasing)
    } else {
        tween(durationMillis = LIGHT_OFF_MS, easing = FastOutSlowInEasing)
    },
    label = "press-light",
)

/** Encoge un poco la tecla mientras se mantiene: la parte tactil del gesto. */
@Composable
fun Modifier.pressScale(press: Press, pressedScale: Float = 0.96f): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (press.pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Anillo de luz siguiendo la silueta de la tecla. Va antes del clip en la cadena de
 * modificadores: asi el halo puede salirse del borde.
 */
fun Modifier.emberRing(
    light: () -> Float,
    color: Color,
    glow: Color,
    shape: Shape,
    strokeWidth: Dp = 2.dp,
    glowWidth: Dp = 12.dp,
): Modifier = drawWithContent {
    drawContent()
    val p = light()
    if (p < MIN_VISIBLE) return@drawWithContent
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = strokeWidth.toPx()
    halo(glow, p, glowWidth.toPx()) { haloColor, spread ->
        drawOutline(outline, haloColor, style = Stroke(width = stroke + spread * 2))
    }
    drawOutline(outline, color.copy(alpha = color.alpha * p), style = Stroke(width = stroke))
}

/** Arco de luz en el borde de una pieza redonda: una direccion de la cruceta. */
fun Modifier.emberArc(
    light: () -> Float,
    color: Color,
    glow: Color,
    startAngle: Float,
    sweepAngle: Float,
    strokeWidth: Dp = 3.dp,
    glowWidth: Dp = 12.dp,
    inset: Dp = 3.dp,
): Modifier = drawWithContent {
    drawContent()
    val p = light()
    if (p < MIN_VISIBLE) return@drawWithContent
    val stroke = strokeWidth.toPx()
    val pad = inset.toPx() + stroke / 2
    val topLeft = Offset(pad, pad)
    val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
    halo(glow, p, glowWidth.toPx()) { haloColor, spread ->
        drawArc(
            color = haloColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke + spread * 2, cap = StrokeCap.Round),
        )
    }
    drawArc(
        color = color.copy(alpha = color.alpha * p),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

/** Raya de luz bajo el icono: para las zonas de una barra, que no tienen silueta propia. */
fun Modifier.emberUnderline(
    light: () -> Float,
    color: Color,
    glow: Color,
    width: Dp = 28.dp,
    thickness: Dp = 3.dp,
    bottomInset: Dp = 12.dp,
    glowWidth: Dp = 10.dp,
): Modifier = drawWithContent {
    drawContent()
    val p = light()
    if (p < MIN_VISIBLE) return@drawWithContent
    val y = size.height - bottomInset.toPx()
    val half = width.toPx() / 2
    val start = Offset(size.width / 2 - half, y)
    val end = Offset(size.width / 2 + half, y)
    val stroke = thickness.toPx()
    halo(glow, p, glowWidth.toPx()) { haloColor, spread ->
        drawLine(haloColor, start, end, strokeWidth = stroke + spread * 2, cap = StrokeCap.Round)
    }
    drawLine(color.copy(alpha = color.alpha * p), start, end, strokeWidth = stroke, cap = StrokeCap.Round)
}

/**
 * Raya de luz a lo largo del borde inferior, tan larga como diga [fraction]: el
 * nivel del volumen. Siempre encendida (no es una pulsacion), pero mas tenue.
 */
fun Modifier.emberTrack(
    fraction: () -> Float,
    color: Color,
    glow: Color,
    inset: Dp = 18.dp,
    thickness: Dp = 3.dp,
    bottomInset: Dp = 7.dp,
    glowWidth: Dp = 8.dp,
): Modifier = drawWithContent {
    drawContent()
    val f = fraction().coerceIn(0f, 1f)
    if (f <= 0f) return@drawWithContent
    val y = size.height - bottomInset.toPx()
    val x0 = inset.toPx()
    val x1 = x0 + (size.width - x0 * 2) * f
    val stroke = thickness.toPx()
    halo(glow, 0.8f, glowWidth.toPx()) { haloColor, spread ->
        drawLine(haloColor, Offset(x0, y), Offset(x1, y), strokeWidth = stroke + spread * 2, cap = StrokeCap.Round)
    }
    drawLine(color.copy(alpha = color.alpha * 0.85f), Offset(x0, y), Offset(x1, y), strokeWidth = stroke, cap = StrokeCap.Round)
}

/**
 * Halo por capas: varios trazos, cada uno mas ancho y mas tenue que el anterior. Es
 * mas barato y mas predecible que un desenfoque real, y a doce dp no se nota la
 * diferencia.
 */
private inline fun halo(glow: Color, progress: Float, spreadPx: Float, draw: (Color, Float) -> Unit) {
    for (step in GLOW_STEPS downTo 1) {
        val fade = 1f - step.toFloat() / (GLOW_STEPS + 1)
        draw(glow.copy(alpha = glow.alpha * progress * fade), spreadPx * step / GLOW_STEPS)
    }
}

private const val LIGHT_ON_MS = 70
private const val LIGHT_OFF_MS = 450
private const val GLOW_STEPS = 4
private const val MIN_VISIBLE = 0.02f
