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
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gabriel.tvmando.AppContainer
import com.gabriel.tvmando.MainActivity
import com.gabriel.tvmando.R
import com.gabriel.tvmando.TvMandoApp
import com.gabriel.tvmando.domain.PowerState
import com.gabriel.tvmando.domain.PressKey
import com.gabriel.tvmando.domain.RawShell
import com.gabriel.tvmando.domain.SceneRunner
import com.gabriel.tvmando.domain.TvKey
import com.gabriel.tvmando.domain.TvQuery
import com.gabriel.tvmando.domain.TvStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Los temporizadores de la TV, sostenidos por un servicio en primer plano.
 *
 * El de apagado es una cuenta atras de hasta hora y media: un hilo dormido en un
 * proceso que Android congela en cuanto se apaga la pantalla no vale, y una alarma
 * inexacta puede retrasarse diez minutos, que para quedarse dormido viendo algo es
 * justo lo que no se quiere. Un servicio en primer plano con su notificacion es la
 * forma que da Android de decir "esto tiene que seguir corriendo", y la notificacion
 * ademas cuenta cuanto queda y deja cancelarlo desde ahi.
 *
 * El mismo servicio ejecuta lo programado por el despertador ([WakeAlarms]): una
 * escena puede tardar quince segundos y un receiver de alarma no vive tanto.
 */
class SleepTimerService : Service() {

    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val container = (application as? TvMandoApp)?.container

        when (intent?.action) {
            ACTION_CANCEL -> {
                finish()
                return START_NOT_STICKY
            }

            ACTION_RUN_WAKE -> {
                foreground(notification("Encendiendo la TV", "Ember esta ejecutando lo programado."))
                val sceneId = intent.getStringExtra(EXTRA_SCENE_ID)
                job?.cancel()
                job = container?.backgroundScope?.launch {
                    try {
                        runWake(container, sceneId)
                    } finally {
                        stopSelf()
                    }
                }
                if (container == null) stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                val deadline = intent?.getLongExtra(EXTRA_DEADLINE, 0L) ?: 0L
                // Pase lo que pase, primero el contrato del servicio en primer plano.
                foreground(notification(deadline))
                if (deadline <= System.currentTimeMillis() || container == null) {
                    finish()
                    return START_NOT_STICKY
                }
                _deadline.value = deadline
                job?.cancel()
                job = container.backgroundScope.launch {
                    try {
                        while (true) {
                            val remaining = deadline - System.currentTimeMillis()
                            if (remaining <= 0) break
                            delay(minOf(remaining, MINUTE_MS))
                            notify(notification(deadline))
                        }
                        turnOff(container)
                    } finally {
                        finish()
                    }
                }
                // Si el sistema se lleva el servicio, al revivirlo el intent trae la hora
                // limite y la cuenta sigue donde iba, no desde cero.
                return START_REDELIVER_INTENT
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        _deadline.value = null
        super.onDestroy()
    }

    private fun finish() {
        job?.cancel()
        job = null
        _deadline.value = null
        stopSelf()
    }

    /** Apaga solo si sigue encendida: POWER es un interruptor y a oscuras la encenderia. */
    private suspend fun turnOff(container: AppContainer) {
        val power = container.tvController.run(TvQuery.STATUS)
            .map { TvStatus.parse(it).power }
            .getOrDefault(PowerState.UNKNOWN)
        if (power == PowerState.ASLEEP) return
        container.tvController.run(PressKey(TvKey.POWER))
    }

    private suspend fun runWake(container: AppContainer, sceneId: String?) {
        val scene = sceneId?.let { id ->
            container.settingsRepository.scenes.first().firstOrNull { it.id == id }
        }
        if (scene == null) {
            container.tvController.run(PressKey(TvKey.WAKEUP))
            return
        }
        SceneRunner(execute = { shell -> container.tvController.run(RawShell(shell)) }).run(scene)
    }

    private fun foreground(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun notify(notification: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    private fun notification(deadline: Long): android.app.Notification {
        val remaining = ((deadline - System.currentTimeMillis()) / MINUTE_MS).coerceAtLeast(0)
        return notification(
            title = "La TV se apaga a las ${TIME.format(Date(deadline))}",
            text = if (remaining <= 0) "Menos de un minuto." else "Quedan $remaining min.",
            cancellable = true,
        )
    }

    private fun notification(title: String, text: String, cancellable: Boolean = false): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .apply { if (cancellable) addAction(0, "Cancelar", cancelIntent()) }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Temporizadores",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Cuenta atras del apagado y encendidos programados"
            setShowBadge(false)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun cancelIntent(): PendingIntent = PendingIntent.getService(
        this,
        2,
        Intent(this, SleepTimerService::class.java).setAction(ACTION_CANCEL),
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
        private const val CHANNEL_ID = "temporizadores"
        private const val NOTIFICATION_ID = 44
        private const val ACTION_CANCEL = "com.gabriel.tvmando.action.CANCEL_SLEEP"
        private const val ACTION_RUN_WAKE = "com.gabriel.tvmando.action.RUN_WAKE"
        private const val EXTRA_DEADLINE = "deadline"
        private const val EXTRA_SCENE_ID = "scene_id"
        private const val MINUTE_MS = 60_000L
        private val TIME = SimpleDateFormat("HH:mm", Locale.getDefault())

        private val _deadline = MutableStateFlow<Long?>(null)

        /** Cuando se apagara la TV, en milisegundos de epoch, o null si no hay cuenta atras. */
        val deadline: StateFlow<Long?> = _deadline

        /** Desde la UI, que es quien puede arrancar un servicio en primer plano. */
        fun start(context: Context, minutes: Int) {
            val intent = Intent(context, SleepTimerService::class.java)
                .putExtra(EXTRA_DEADLINE, System.currentTimeMillis() + minutes * MINUTE_MS)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, SleepTimerService::class.java).setAction(ACTION_CANCEL)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        /** Desde el receptor de la alarma: ejecutar la escena programada, o solo encender. */
        fun runWake(context: Context, sceneId: String?) {
            val intent = Intent(context, SleepTimerService::class.java)
                .setAction(ACTION_RUN_WAKE)
                .putExtra(EXTRA_SCENE_ID, sceneId)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
