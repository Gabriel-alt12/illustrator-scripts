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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        server()?.start()

        // NOT_STICKY a proposito: si el sistema se lleva el servicio por delante, al
        // revivirlo se generaria una direccion nueva y la visita seguiria con la
        // vieja en la mano, sin entender nada. Mejor que se quede apagado y que lo
        // encienda quien vive aqui.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        server()?.stop()
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
