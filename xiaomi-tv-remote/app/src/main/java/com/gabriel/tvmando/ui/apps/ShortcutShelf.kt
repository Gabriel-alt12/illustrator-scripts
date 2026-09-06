package com.gabriel.tvmando.ui.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabriel.tvmando.data.AppIconStore
import com.gabriel.tvmando.domain.AppCatalog
import com.gabriel.tvmando.domain.Shortcut
import com.gabriel.tvmando.ui.components.brandTile
import com.gabriel.tvmando.ui.components.emberRing
import com.gabriel.tvmando.ui.components.pressScale
import com.gabriel.tvmando.ui.components.rememberPress
import com.gabriel.tvmando.ui.theme.ChalkFaint
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.EmberGlow
import com.gabriel.tvmando.ui.theme.EmberInk
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.Radius
import com.gabriel.tvmando.ui.theme.Space

/**
 * Estanteria de "seguir viendo": una tarjeta por serie, episodio o video guardado.
 *
 * Va en horizontal y encima de la rejilla de apps porque es a lo que se vuelve cada
 * noche: un toque y la tele arranca donde se dejo. Pulsacion larga para editar,
 * fijar en el escritorio o borrar.
 */
@Composable
fun ShortcutShelf(
    shortcuts: List<Shortcut>,
    enabled: Boolean,
    onOpen: (Shortcut) -> Unit,
    onMenu: (Shortcut) -> Unit,
    onAdd: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SEGUIR VIENDO",
                style = MaterialTheme.typography.labelMedium,
                color = ChalkMuted,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = Space.xs)) {
                Text("Anadir", style = MaterialTheme.typography.labelLarge, color = EmberInk)
            }
        }
        Spacer(Modifier.height(Space.xs))
        if (shortcuts.isEmpty()) {
            EmptyShelf()
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                items(shortcuts, key = { it.id }) { shortcut ->
                    ShortcutCard(
                        shortcut = shortcut,
                        enabled = enabled,
                        onClick = { onOpen(shortcut) },
                        onLongClick = { onMenu(shortcut) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyShelf() {
    val shape = RoundedCornerShape(Radius.card)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, shape)
            .padding(Space.lg),
    ) {
        Text(
            text = "Comparte una serie con Ember desde Netflix, Prime Video o YouTube " +
                "(boton Compartir del movil) y aparecera aqui. Un toque y la tele la " +
                "pone por donde la dejaste. Tambien puedes anadirla a mano.",
            style = MaterialTheme.typography.bodyMedium,
            color = ChalkFaint,
        )
    }
}

/**
 * Tarjeta con el color de la app de la TV, su icono pequeno arriba y el titulo grande
 * abajo. Mismo gesto que el resto de la app al pulsar: encoge y se enciende el borde.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutCard(
    shortcut: Shortcut,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val press = rememberPress()
    val ember = Ember
    val glow = EmberGlow
    val shape = RoundedCornerShape(Radius.card)
    val appName = shortcut.packageName?.let { AppCatalog.describe(it).displayName } ?: "Enlace"
    val icon by produceState<ImageBitmap?>(null, shortcut.packageName) {
        value = shortcut.packageName?.let { AppIconStore.load(context, it) }
    }

    Column(
        modifier = modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .pressScale(press, pressedScale = 0.95f)
            .emberRing({ press.light.value }, ember, glow, shape)
            .clip(shape)
            .background(brandTile(shortcut.packageName))
            .border(1.dp, Hairline, shape)
            .combinedClickable(
                interactionSource = press.interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val bitmap = icon
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
            } else {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF)),
                )
            }
            Spacer(Modifier.width(Space.sm))
            Text(
                text = appName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                // Fijos: el color de marca de debajo es el mismo en los dos temas.
                color = Color(0xCCF2F3F5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = shortcut.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp, lineHeight = 19.sp),
            color = Color(0xFFF2F3F5),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val CARD_WIDTH = 156.dp
private val CARD_HEIGHT = 96.dp
