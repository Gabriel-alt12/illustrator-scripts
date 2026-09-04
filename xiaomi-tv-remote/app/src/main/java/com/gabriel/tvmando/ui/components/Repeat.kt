package com.gabriel.tvmando.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

/**
 * Repite una accion mientras el boton siga pulsado.
 *
 * Bajar el volumen diez puntos eran diez toques, y recorrer una lista larga de Netflix
 * con la cruceta, otros tantos. Con esto se deja el dedo puesto y ya.
 *
 * El primer disparo no sale de aqui sino del `clickable` del boton, que salta al
 * soltar: asi un toque corto manda exactamente un comando, que es lo que espera
 * cualquiera, y esto solo entra cuando de verdad se esta manteniendo. La pausa
 * inicial es la que separa "he tocado" de "estoy manteniendo".
 *
 * El ritmo es deliberadamente tranquilo: cada repeticion es un viaje de ida y vuelta
 * por ADB, y [com.gabriel.tvmando.domain.TvController] los serializa con un mutex.
 * Repetir mas rapido de lo que la TV contesta solo acumularia comandos que seguirian
 * llegando despues de soltar.
 */
@Composable
fun RepeatWhilePressed(
    pressed: Boolean,
    enabled: Boolean,
    onRepeat: () -> Unit,
) {
    val current by rememberUpdatedState(onRepeat)
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        delay(HOLD_BEFORE_REPEAT_MS)
        while (true) {
            current()
            delay(REPEAT_EVERY_MS)
        }
    }
}

/** Lo que hay que mantener para que se note que no era un toque. */
private const val HOLD_BEFORE_REPEAT_MS = 450L

/** Unas cuatro por segundo: se nota fluido y la TV llega de sobra. */
private const val REPEAT_EVERY_MS = 250L
