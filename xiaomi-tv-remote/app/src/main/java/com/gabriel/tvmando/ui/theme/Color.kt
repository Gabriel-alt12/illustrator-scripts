package com.gabriel.tvmando.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta de la app, por papeles y no por matices.
 *
 * Los nombres describen para que sirve cada color, no de que color es: `ink` es
 * siempre el fondo y `chalk` siempre el texto, aunque en el tema claro el fondo sea
 * papel y el texto carbon. Asi el mismo codigo pinta bien en los dos temas sin
 * preguntarse en cual esta.
 *
 * El oscuro es el de siempre: casi negro para no deslumbrar de noche y un unico acento
 * calido. El claro no es blanco sino papel calido, y lleva dos tonos de acento porque
 * el naranja de la marca (#FF5A1F) da 2,7:1 de contraste sobre papel: sirve como luz
 * en un boton, pero como texto no se lee. De ahi [emberInk].
 */
@Immutable
data class EmberColors(
    /** Fondo de pantalla. */
    val ink: Color,
    /** Superficies: tarjetas, barras, fichas. */
    val inkRaised: Color,
    /** Superficies elevadas: OK, play, la tecla que destaca. */
    val inkHigh: Color,
    /** Bordes finos. */
    val hairline: Color,
    /** Texto principal. */
    val chalk: Color,
    /** Texto secundario. */
    val chalkMuted: Color,
    /** Texto terciario. No es para leer parrafos: no llega a 4,5:1 a proposito. */
    val chalkFaint: Color,
    /** Acento como luz: iconos pulsados, anillos, arcos, halos. */
    val ember: Color,
    /** Acento como texto. En oscuro coincide con [ember]; en claro es mas profundo. */
    val emberInk: Color,
    /** Acento hundido: fondo de la pestana activa y de los chips seleccionados. */
    val emberSunk: Color,
    /** Acento translucido para halos y arcos de luz. */
    val emberGlow: Color,
    /** Estado: conectada. */
    val signal: Color,
    /** Estado: conectando o esperando autorizacion. */
    val waiting: Color,
    /** Estado: error. */
    val alert: Color,
    val isDark: Boolean,
)

val DarkEmberColors = EmberColors(
    ink = Color(0xFF08090C),
    inkRaised = Color(0xFF11131A),
    inkHigh = Color(0xFF1A1D26),
    hairline = Color(0xFF272B36),
    chalk = Color(0xFFF2F3F5),
    chalkMuted = Color(0xFF858C9B),
    chalkFaint = Color(0xFF5A606D),
    ember = Color(0xFFFF5A1F),
    emberInk = Color(0xFFFF5A1F),
    emberSunk = Color(0xFF2E1409),
    emberGlow = Color(0x38FF5A1F),
    signal = Color(0xFF35D07F),
    waiting = Color(0xFFFFB020),
    alert = Color(0xFFFF4D5E),
    isDark = true,
)

/**
 * Contrastes sobre el papel (#F3EFE8), calculados y no estimados: carbon 15,5:1,
 * secundario 5,2:1, acento como luz 4,4:1, acento como texto 5,8:1. Los estados van
 * oscurecidos (3,6 a 4,7:1): valen como punto de color junto a una etiqueta, no como
 * texto suelto.
 */
val LightEmberColors = EmberColors(
    ink = Color(0xFFF3EFE8),
    inkRaised = Color(0xFFFFFFFF),
    inkHigh = Color(0xFFEAE5DC),
    hairline = Color(0xFFDAD3C8),
    chalk = Color(0xFF17181C),
    chalkMuted = Color(0xFF5F646F),
    chalkFaint = Color(0xFF8E939D),
    ember = Color(0xFFC93E0E),
    emberInk = Color(0xFFA83409),
    emberSunk = Color(0xFFFBE6DC),
    emberGlow = Color(0x2EC93E0E),
    signal = Color(0xFF1E8F52),
    waiting = Color(0xFFB26E00),
    alert = Color(0xFFC82C3E),
    isDark = false,
)

/**
 * Estatico a proposito: el tema cambia cuando el usuario lo pide y poco mas, y en ese
 * momento lo correcto es recomponer todo lo que pinta, que es justo lo que hace.
 */
val LocalEmberColors = staticCompositionLocalOf { DarkEmberColors }

/** Punto de acceso explicito a la paleta activa, al estilo de MaterialTheme. */
object EmberTheme {
    val colors: EmberColors
        @Composable @ReadOnlyComposable get() = LocalEmberColors.current
}

/*
 * Los nombres de siempre, ahora como accesores del tema activo.
 *
 * Son propiedades con getter componible: dentro de una funcion @Composable se usan
 * igual que antes (`color = Chalk`) y devuelven el color del tema que toque. Fuera
 * de la composicion (un remember { }, una funcion auxiliar sin @Composable, un
 * lambda de clickable) NO compilan, y eso es deliberado: un color leido fuera de la
 * composicion no se actualizaria al cambiar de tema, y es mejor que el compilador
 * senale la linea que descubrirlo en la tele a las once de la noche.
 */
val Ink: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.ink
val InkRaised: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.inkRaised
val InkHigh: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.inkHigh
val Hairline: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.hairline
val Chalk: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.chalk
val ChalkMuted: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.chalkMuted
val ChalkFaint: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.chalkFaint
val Ember: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.ember
val EmberInk: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.emberInk
val EmberSunk: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.emberSunk
val EmberGlow: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.emberGlow
val Signal: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.signal
val Waiting: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.waiting
val Alert: Color @Composable @ReadOnlyComposable get() = EmberTheme.colors.alert
