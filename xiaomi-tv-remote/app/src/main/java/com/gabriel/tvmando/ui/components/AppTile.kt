package com.gabriel.tvmando.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gabriel.tvmando.domain.TvApp
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.Hairline

/**
 * Ficha de una app instalada en la TV.
 *
 * Por ADB no hay forma barata de sacar el icono real de una app, asi que en lugar de
 * poner un cuadro gris se dibuja un monograma con un color estable derivado del
 * nombre del paquete: la misma app cae siempre en el mismo color y la rejilla se
 * reconoce de un vistazo.
 *
 * Pulsacion larga = forzar el cierre, como pide la seccion 7 de la especificacion.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: TvApp,
    isForeground: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
        label = "tile-scale",
    )
    val ring by animateColorAsState(
        when {
            pressed -> Ember
            isForeground -> Ember
            else -> Hairline
        },
        label = "tile-ring",
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(tileColor(app.packageName))
                .border(if (isForeground) 2.dp else 1.dp, ring, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = monogram(app.displayName),
                style = MaterialTheme.typography.displaySmall,
                color = Chalk,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = app.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isForeground) Ember else Chalk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (isForeground) "en pantalla" else app.packageName.substringAfterLast('.'),
            style = MaterialTheme.typography.labelSmall,
            color = ChalkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Una o dos iniciales: "Prime Video" -> "PV", "Netflix" -> "N". */
private fun monogram(displayName: String): String {
    val words = displayName.split(' ', '-', '_').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * Color estable por paquete. Tonos apagados elegidos a mano para que convivan sobre
 * el fondo casi negro sin competir con el naranja de acento.
 */
private fun tileColor(packageName: String): Color {
    val palette = listOf(
        Color(0xFF7A3B2E), // teja
        Color(0xFF2E5F55), // verde profundo
        Color(0xFF4A3F6B), // violeta
        Color(0xFF6B5623), // ocre
        Color(0xFF2F4A70), // azul acero
        Color(0xFF63304A), // ciruela
    )
    val index = (packageName.hashCode().toLong() and 0xFFFFFFFFL) % palette.size
    return palette[index.toInt()]
}
