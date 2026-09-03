package com.gabriel.tvmando.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.gabriel.tvmando.TvMandoApp
import com.gabriel.tvmando.domain.QuickCommand
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Punto de entrada unico para los comandos que se disparan desde fuera de la app:
 * widget, tiles y notificacion mandan todos aqui.
 *
 * Un receiver tiene unos diez segundos antes de que el sistema lo mate, de ahi el
 * [goAsync] y el limite de ocho: si la TV esta dormida y hay que rehacer el
 * handshake ADB, tarda unos segundos, pero si no responde en ese margen tampoco
 * merece la pena seguir esperando con el usuario mirando la pantalla de inicio.
 */
class TvCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val quick = QuickCommand.fromId(intent.getStringExtra(EXTRA_COMMAND)) ?: return
        val container = (context.applicationContext as? TvMandoApp)?.container ?: return

        val pendingResult = goAsync()
        container.backgroundScope.launch {
            try {
                val result = withTimeoutOrNull(TIMEOUT_MS) {
                    container.tvController.run(quick.command)
                }
                // Sin UI delante, un toast es el unico aviso posible de que fallo.
                val error = when {
                    result == null -> "La TV no respondio a tiempo"
                    result.isFailure -> result.exceptionOrNull()?.message
                    else -> null
                }
                if (error != null) toast(context, error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_RUN = "com.gabriel.tvmando.action.RUN"
        const val EXTRA_COMMAND = "command"

        private const val TIMEOUT_MS = 8_000L

        fun intent(context: Context, quick: QuickCommand): Intent =
            Intent(context, TvCommandReceiver::class.java).apply {
                action = ACTION_RUN
                putExtra(EXTRA_COMMAND, quick.id)
            }

        /**
         * PendingIntent listo para un boton de widget o notificacion.
         *
         * El requestCode tiene que ser distinto por comando: si no, Android reutiliza
         * el mismo PendingIntent y todos los botones acaban haciendo lo mismo.
         */
        fun pendingIntent(context: Context, quick: QuickCommand): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_BASE + quick.ordinal,
                intent(context, quick),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private const val REQUEST_BASE = 1000
    }
}
