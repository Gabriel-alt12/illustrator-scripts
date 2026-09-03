package com.gabriel.tvmando.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gabriel.tvmando.MainActivity
import com.gabriel.tvmando.R
import com.gabriel.tvmando.TvMandoApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Mantiene vivo el [GuestRemoteServer] mientras hay visita.
 *
 * Sin esto el mando de invitados no aguanta: el servidor es un socket escuchando en
 * una corrutina, y desde Android 12 el sistema congela el proceso a los pocos
 * segundos de irse a segundo plano. En cuanto el anfitrion bloquease la pantalla, la
 * visita se quedaba con una pagina que no responde y sin forma de saber por que.
 *
 * Un servicio en primer plano es justo la manera que da Android de decir "esto tiene
 * que seguir corriendo aunque no me estes mirando", y la notificacion que obliga a
 * enseñar no sobra: recuerda al anfitrion que hay un mando prestado dando vueltas, y
 * le da un sitio donde apagarlo.
 *
 * Al reves que [MandoNotification], aqui si hace falta proceso vivo, de ahi el
 * servicio. El tipo es `specialUse` porque ninguno de los tipos con nombre de Android
 * describe esto; queda declarado en el manifiesto con su explicacion.
 */
class GuestRemoteService : Service() {

    /** Vigila que el servidor siga en pie; si se cae, este servicio se va con el. */
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Pase lo que pase despues, primero se cumple el contrato del servicio en
        // primer plano: Android da unos segundos para esto y no perdona pasarse.
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val container = (application as? TvMandoApp)?.container
        val server = container?.guestRemoteServer
        if (server == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        server.start()

        // Este servicio no tiene sentido sin servidor detras. Si no levanta (sin WiFi,
        // puerto ocupado) o si el bucle se cae mas tarde, hay que irse: si no, queda
        // una notificacion anunciando un mando prestado que no existe y, como el
        // interruptor de Ajustes se dibuja a partir del estado del servidor, se veria
        // apagado y no habria forma de pararlo salvo forzando el cierre de la app.
        watcher?.cancel()
        watcher = container.backgroundScope.launch {
            server.state.collect { state ->
                if (state !is GuestRemoteState.Running) stopSelf()
            }
        }

        // NOT_STICKY a proposito: si el sistema se lleva el servicio por delante, al
        // revivirlo se generaria una direccion nueva y la visita seguiria con la
        // vieja en la mano, sin entender nada. Mejor que se quede apagado y que lo
        // encienda quien vive aqui.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        watcher = null
        // Solo se para lo que estuviera en marcha: en los caminos de fallo, parar aqui
        // pondria Stopped encima del Failed y Ajustes perderia el motivo justo antes
        // de poder enseñarlo.
        val server = server()
        if (server?.state?.value is GuestRemoteState.Running) server.stop()
        super.onDestroy()
    }

    private fun server(): GuestRemoteServer? =
        (application as? TvMandoApp)?.container?.guestRemoteServer

    private fun notification(): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Mando para invitados encendido")
            .setContentText("La visita puede controlar la TV desde su navegador.")
            .setContentIntent(openApp())
            // La unica salida si algo va mal y el interruptor de Ajustes no responde.
            .addAction(0, "Apagar", stopIntent())
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mando para invitados",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Avisa de que hay un mando prestado activo en la red de casa"
            setShowBadge(false)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, GuestRemoteService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_ID = "mando_invitados"
        private const val NOTIFICATION_ID = 43
        private const val ACTION_STOP = "com.gabriel.tvmando.action.STOP_GUEST"

        /**
         * Se llama desde la UI, que es quien tiene Context y quien sabe que el usuario
         * acaba de tocar el interruptor: arrancar un servicio en primer plano desde
         * segundo plano esta restringido desde Android 12.
         */
        fun start(context: Context) {
            val intent = Intent(context, GuestRemoteService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GuestRemoteService::class.java)) }
        }
    }
}
