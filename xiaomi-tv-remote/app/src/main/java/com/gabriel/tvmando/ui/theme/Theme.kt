package com.gabriel.tvmando.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * La app es oscura siempre: se usa de noche frente a la TV y un fogonazo blanco a
 * media pelicula no tiene ninguna gracia. El tema del sistema se ignora a proposito.
 */
private val MandoColors = darkColorScheme(
    primary = Ember,
    onPrimary = Ink,
    primaryContainer = EmberSunk,
    onPrimaryContainer = Ember,
    secondary = ChalkMuted,
    onSecondary = Ink,
    background = Ink,
    onBackground = Chalk,
    surface = InkRaised,
    onSurface = Chalk,
    surfaceVariant = InkHigh,
    onSurfaceVariant = ChalkMuted,
    outline = Hairline,
    error = Alert,
    onError = Ink,
)

@Composable
fun MandoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MandoColors,
        typography = MandoTypography,
        content = content,
    )
}
