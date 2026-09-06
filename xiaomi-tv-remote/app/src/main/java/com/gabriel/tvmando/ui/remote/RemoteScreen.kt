package com.gabriel.tvmando.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gabriel.tvmando.domain.PowerState
import com.gabriel.tvmando.domain.PressKey
import com.gabriel.tvmando.domain.TvCommand
import com.gabriel.tvmando.domain.TvKey
import com.gabriel.tvmando.domain.TvStatus
import com.gabriel.tvmando.ui.components.Dpad
import com.gabriel.tvmando.ui.components.PillKey
import com.gabriel.tvmando.ui.components.PowerKey
import com.gabriel.tvmando.ui.components.RoundKey
import com.gabriel.tvmando.ui.components.Tap
import com.gabriel.tvmando.ui.components.VolumeBar
import com.gabriel.tvmando.ui.theme.Space

/**
 * Mando completo (fase 3).
 *
 * De arriba abajo y en orden de uso: navegacion, cruceta, transporte multimedia,
 * volumen y encendido. Lo que mas se toca queda en la mitad inferior, al alcance del
 * pulgar; el encendido, abajo del todo y aislado, para no darle sin querer.
 *
 * La pantalla no conoce el ViewModel: recibe un callback y emite [TvCommand].
 */
@Composable
fun RemoteScreen(
    enabled: Boolean,
    onCommand: (TvCommand) -> Unit,
    haptics: (Tap) -> Unit,
    modifier: Modifier = Modifier,
    /** Lo ultimo que se sabe de la TV: si esta encendida y a que volumen. */
    status: TvStatus? = null,
    onVolumeLevel: ((Int) -> Unit)? = null,
    onPowerLongPress: (() -> Unit)? = null,
) {
    fun press(key: TvKey, feel: Tap = Tap.Press) {
        haptics(feel)
        onCommand(PressKey(key))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            PillKey(
                onClick = { press(TvKey.BACK) },
                icon = Icons.Rounded.ArrowBack,
                label = "Atras",
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            PillKey(
                onClick = { press(TvKey.HOME) },
                icon = Icons.Rounded.Home,
                label = "Inicio",
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            PillKey(
                onClick = { press(TvKey.APP_SWITCH) },
                icon = Icons.Rounded.Layers,
                label = "Recientes",
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            PillKey(
                onClick = { press(TvKey.ASSIST) },
                icon = Icons.Rounded.Mic,
                label = "Asistente",
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Space.lg))

        Dpad(
            onUp = { press(TvKey.DPAD_UP) },
            onDown = { press(TvKey.DPAD_DOWN) },
            onLeft = { press(TvKey.DPAD_LEFT) },
            onRight = { press(TvKey.DPAD_RIGHT) },
            onCenter = { press(TvKey.DPAD_CENTER, Tap.Confirm) },
            enabled = enabled,
        )

        Spacer(Modifier.height(Space.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundKey(
                onClick = { press(TvKey.MEDIA_REWIND) },
                icon = Icons.Rounded.FastRewind,
                description = "Retroceder",
                enabled = enabled,
            )
            RoundKey(
                onClick = { press(TvKey.MEDIA_PREVIOUS) },
                icon = Icons.Rounded.SkipPrevious,
                description = "Anterior",
                enabled = enabled,
            )
            RoundKey(
                onClick = { press(TvKey.MEDIA_PLAY_PAUSE, Tap.Confirm) },
                icon = Icons.Rounded.PlayArrow,
                description = "Play o pausa",
                enabled = enabled,
                size = 64.dp,
                emphasis = true,
            )
            RoundKey(
                onClick = { press(TvKey.MEDIA_NEXT) },
                icon = Icons.Rounded.SkipNext,
                description = "Siguiente",
                enabled = enabled,
            )
            RoundKey(
                onClick = { press(TvKey.MEDIA_FAST_FORWARD) },
                icon = Icons.Rounded.FastForward,
                description = "Avanzar",
                enabled = enabled,
            )
        }

        Spacer(Modifier.height(Space.lg))

        VolumeBar(
            onDown = { press(TvKey.VOLUME_DOWN) },
            onMute = { press(TvKey.VOLUME_MUTE, Tap.Confirm) },
            onUp = { press(TvKey.VOLUME_UP) },
            iconDown = Icons.Rounded.Remove,
            iconMute = Icons.Rounded.VolumeOff,
            iconUp = Icons.Rounded.Add,
            enabled = enabled,
            level = status?.volume,
            onLevelChange = onVolumeLevel,
        )

        Spacer(Modifier.height(Space.lg))

        PowerKey(
            onClick = { press(TvKey.POWER, Tap.Confirm) },
            icon = Icons.Rounded.PowerSettingsNew,
            enabled = enabled,
            awake = when (status?.power) {
                PowerState.AWAKE -> true
                PowerState.ASLEEP -> false
                else -> null
            },
            onLongClick = onPowerLongPress,
        )

    }
}
