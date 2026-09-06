package com.gabriel.tvmando.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabriel.tvmando.data.AppIconStore
import com.gabriel.tvmando.domain.AppCatalog
import com.gabriel.tvmando.domain.TvApp
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.EmberGlow
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.Radius

/**
 * Ficha de una app instalada en la TV.
 *
 * El icono no se puede traer del televisor por ADB: sale del movil, de una descarga
 * anterior o de la Play Store, en ese orden (ver [AppIconStore]). Si no hay forma,
 * el nombre entero hace de logotipo sobre el color de la marca (o uno estable
 * derivado del paquete, si tampoco se conoce): la misma app cae siempre en el mismo
 * color y la rejilla se reconoce de un vistazo.
 *
 * Pulsacion larga = forzar el cierre, como pide la seccion 7 de la especificacion.
 * La estrella de la esquina fija la app arriba en la rejilla.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: TvApp,
    isForeground: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    // Fuera del hilo principal: leer el icono es una llamada al sistema mas rasterizar
    // un drawable, y con la rejilla llena serian veinte de esas en la misma pasada de
    // composicion, justo al abrir la pestana.
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = AppIconStore.load(context, app.packageName)
    }
    val press = rememberPress()
    val ember = Ember
    val glow = EmberGlow
    // La app que esta en la TV lleva el borde encendido fijo; las demas, solo la luz
    // de pulsacion, la misma que las teclas del mando.
    val ring = if (isForeground) ember else Hairline
    val shape = RoundedCornerShape(Radius.tile)

    Column(
        modifier = modifier
            .pressScale(press, pressedScale = 0.94f)
            .combinedClickable(
                interactionSource = press.interaction,
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
                .emberRing({ press.light.value }, ember, glow, shape)
                .clip(shape)
                .background(tileColor(app.packageName))
                .border(if (isForeground) 2.dp else 1.dp, ring, shape),
            contentAlignment = Alignment.Center,
        ) {
            // A una propiedad delegada no se le hace smart cast: hay que sacar el
            // valor a una variable normal antes de comprobarlo.
            val bitmap = icon
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                )
            } else {
                // Sin icono, el nombre entero hace de logotipo: sobre el color de la
                // marca se reconoce igual y no parece una ficha rota.
                Text(
                    text = app.displayName.uppercase(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        lineHeight = 19.sp,
                        letterSpacing = 0.6.sp,
                    ),
                    // Fijo y no del tema: el color de marca de debajo es el mismo en
                    // claro y en oscuro.
                    color = Color(0xFFF2F3F5),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }

            // La estrella va encima de la ficha y no en un menu: fijar las cuatro apps
            // de siempre es lo primero que se hace al ver la rejilla entera, y meterlo
            // en la pulsacion larga chocaria con forzar el cierre.
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (isFavorite) {
                    "Quitar ${app.displayName} de los fijados"
                } else {
                    "Fijar ${app.displayName} arriba"
                },
                tint = if (isFavorite) Ember else ChalkMuted,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .clip(CircleShape)
                    // Velo fijo y no del tema: va sobre el color de marca de la app,
                    // que es el mismo en claro y en oscuro.
                    .background(Color(0x8C08090C))
                    .clickable(onClick = onToggleFavorite)
                    // El relleno lleva la zona tactil a los 48 dp sin agrandar el
                    // icono: esta encima de la ficha y un toque desviado abriria la
                    // app en vez de fijarla.
                    .padding(15.dp)
                    .size(18.dp),
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

/**
 * Color de fondo de la ficha: el de la marca si se conoce, y si no uno estable
 * derivado del paquete. Tonos apagados para que convivan sobre el fondo casi negro
 * sin competir con el naranja de acento.
 */
private fun tileColor(packageName: String): Color {
    AppCatalog.brandColor(packageName)?.let { return Color(it) }
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
