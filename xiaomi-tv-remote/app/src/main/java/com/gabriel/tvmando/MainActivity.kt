package com.gabriel.tvmando

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.gabriel.tvmando.ui.MandoApp
import com.gabriel.tvmando.ui.MandoViewModel
import com.gabriel.tvmando.ui.theme.MandoTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MandoViewModel by viewModels {
        MandoViewModel.factory((application as TvMandoApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La app es oscura siempre, tambien las barras del sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            MandoTheme {
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
}
