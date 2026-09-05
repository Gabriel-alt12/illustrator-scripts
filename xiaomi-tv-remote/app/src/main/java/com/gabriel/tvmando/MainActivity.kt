package com.gabriel.tvmando

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gabriel.tvmando.data.ThemeMode
import com.gabriel.tvmando.ui.MandoApp
import com.gabriel.tvmando.ui.MandoViewModel
import com.gabriel.tvmando.ui.theme.DarkEmberColors
import com.gabriel.tvmando.ui.theme.LightEmberColors
import com.gabriel.tvmando.ui.theme.MandoTheme
import com.gabriel.tvmando.ui.theme.resolveDark
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val viewModel: MandoViewModel by viewModels {
        MandoViewModel.factory((application as TvMandoApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as TvMandoApp).container.settingsRepository

        // El tema se lee antes de componer nada. Es una lectura local del DataStore,
        // cuestion de milisegundos, y evita el fogonazo oscuro que veria en cada
        // arranque quien tiene puesto el claro si se empezara con el valor por defecto.
        val initial = runBlocking { repository.current() }
        val initialDark = initial.theme.isDarkNow()
        paintWindow(initialDark)
        val bars = if (initialDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)

        setContent {
            val settings by repository.settings.collectAsStateWithLifecycle(initialValue = initial)
            val dark = settings.theme.resolveDark()
            val view = LocalView.current
            SideEffect {
                // enableEdgeToEdge decide el color de los iconos de las barras una
                // sola vez; al cambiar de tema en caliente hay que volver a decirlo.
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                paintWindow(dark)
            }
            MandoTheme(mode = settings.theme) {
                MandoApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Al volver de segundo plano la TV puede haber cerrado el socket: que el
        // indicador diga la verdad en lugar de mentir en verde.
        viewModel.refreshLiveness()
    }

    /** El fondo de la ventana asoma en las transiciones: del mismo color que la app. */
    private fun paintWindow(dark: Boolean) {
        val colors = if (dark) DarkEmberColors else LightEmberColors
        window.setBackgroundDrawable(ColorDrawable(colors.ink.toArgb()))
    }

    /** Lo mismo que [resolveDark], pero antes de que exista la composicion. */
    private fun ThemeMode.isDarkNow(): Boolean = when (this) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> {
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            night == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
