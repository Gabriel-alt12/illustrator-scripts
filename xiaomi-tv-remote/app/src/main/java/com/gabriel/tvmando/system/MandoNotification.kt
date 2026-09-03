package com.gabriel.tvmando.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gabriel.tvmando.MainActivity
import com.gabriel.tvmando.R
import com.gabriel.tvmando.domain.QuickCommand

/**
 * Mando siempre visible en la barra de notificaciones.
 *
 * Es una notificacion persistente normal, no un servicio en primer plano: desde
 * Android 14 un foreground service necesita declarar un tipo y justificarlo, y aqui
 * no hace falta ningun proceso vivo. La notificacion la mantiene el sistema y los
 * botones son PendingIntents al mismo receiver que usa el widget, asi que funcionan
 * aunque la app este muerta.
 *
 * A cambio no sobrevive a un reinicio del movil: la app la vuelve a poner al
 * arrancar si el ajuste sigue activado.
 */
object MandoNotification {

    private const val CHANNEL_ID = "mando_persistente"
    private const val NOTIFICATION_ID = 42

    private val BUTTON_IDS = listOf(
        R.id.notification_button_1,
        R.id.notification_button_2,
        R.id.notification_button_3,
        R.id.notification_button_4,
        R.id.notification_button_5,
    )

    fun show(context: Context) {
        if (!canPost(context)) return
        ensureChannel(context)

        val views = RemoteViews(context.packageName, R.layout.notification_remote).apply {
            QuickCommand.notificationCommands.forEachIndexed { index, quick ->
                val viewId = BUTTON_IDS[index]
                setOnClickPendingIntent(viewId, TvCommandReceiver.pendingIntent(context, quick))
                setContentDescription(viewId, quick.label)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // Aunque comprobemos el permiso antes, un cambio de estado a mitad de camino
        // puede colarse: mejor tragarse el fallo que reventar el arranque de la app.
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun hide(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    /** En Android 13+ hay que pedir POST_NOTIFICATIONS antes de poder mostrar nada. */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mando persistente",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Controles de la TV siempre a mano en la barra de notificaciones"
            setShowBadge(false)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
