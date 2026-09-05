package com.gabriel.tvmando.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.gabriel.tvmando.data.ThemeMode

/**
 * La app arranca en oscuro y ese sigue siendo el tema por defecto: se usa de noche
 * frente a la TV y un fogonazo blanco a media pelicula no tiene ninguna gracia. Pero
 * ahora existe un claro de verdad y un ajuste para elegirlo o seguir al sistema.
 */
@Composable
fun MandoTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val colors = if (mode.resolveDark()) DarkEmberColors else LightEmberColors
    CompositionLocalProvider(LocalEmberColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = MandoTypography,
            content = content,
        )
    }
}

/** Traduce el ajuste a "¿toca oscuro?", consultando al sistema solo si se le pide. */
@Composable
fun ThemeMode.resolveDark(): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/**
 * Los componentes de Material 3 que si se usan (campos de texto, interruptores,
 * dialogos, botones de texto) leen de aqui. Sale de la misma paleta para que no
 * puedan discrepar.
 */
private fun EmberColors.toMaterial(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = ember,
        onPrimary = ink,
        primaryContainer = emberSunk,
        onPrimaryContainer = emberInk,
        secondary = chalkMuted,
        onSecondary = ink,
        background = ink,
        onBackground = chalk,
        surface = inkRaised,
        onSurface = chalk,
        surfaceVariant = inkHigh,
        onSurfaceVariant = chalkMuted,
        outline = hairline,
        error = alert,
        onError = ink,
    )
} else {
    lightColorScheme(
        primary = ember,
        onPrimary = inkRaised,
        primaryContainer = emberSunk,
        onPrimaryContainer = emberInk,
        secondary = chalkMuted,
        onSecondary = inkRaised,
        background = ink,
        onBackground = chalk,
        surface = inkRaised,
        onSurface = chalk,
        surfaceVariant = inkHigh,
        onSurfaceVariant = chalkMuted,
        outline = hairline,
        error = alert,
        onError = inkRaised,
    )
}
