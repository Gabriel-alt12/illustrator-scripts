package com.gabriel.tvmando.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Tipos de vibracion. La especificacion pide feedback hapetico en cada pulsacion
 * porque el mando se usa sin mirar el movil: el pulgar tiene que saber que ha
 * pasado algo sin apartar la vista de la tele.
 */
enum class Tap { Press, Confirm, Reject }

@Composable
fun rememberHaptics(): (Tap) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { tap: Tap ->
            val constant = when (tap) {
                Tap.Press -> HapticFeedbackConstants.KEYBOARD_TAP
                Tap.Confirm ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.KEYBOARD_TAP
                    }
                Tap.Reject ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.REJECT
                    } else {
                        HapticFeedbackConstants.LONG_PRESS
                    }
            }
            view.performHapticFeedback(constant)
        }
    }
}
