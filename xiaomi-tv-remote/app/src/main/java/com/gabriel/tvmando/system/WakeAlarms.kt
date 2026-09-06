package com.gabriel.tvmando.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gabriel.tvmando.MainActivity
import com.gabriel.tvmando.TvMandoApp
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * El despertador de la tele: a una hora, encenderla o ejecutar una escena entera
 * ("Modo cine" a las nueve, por ejemplo).
 *
 * Va por una alarma de reloj exacta, la misma que usan las apps de alarmas: es la
 * unica que Android promete disparar a su hora aunque el movil este en reposo
 * profundo. Si el sistema no la deja (se puede revocar en ajustes), se programa
 * una aproximada y se avisa de que puede retrasarse unos minutos.
 *
 * Es de un solo disparo y no sobrevive a un reinicio del movil; para lo que se usa
 * (manana a las nueve) no compensa mas maquinaria.
 */
object WakeAlarms {

    const val ACTION_WAKE = "com.gabriel.tvmando.action.WAKE"
    const val EXTRA_SCENE_ID = "scene_id"
    private const val REQUEST_CODE = 2000

    /** Programa el encendido; devuelve false si tuvo que ser una alarma aproximada. */
    fun schedule(context: Context, atMillis: Long, sceneId: String?): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = operation(context, sceneId)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        runCatching {
            if (exact) {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, openApp(context)), operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
            }
        }
        persist(context, atMillis, sceneId)
        return exact
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        runCatching { manager.cancel(operation(context, null)) }
        persist(context, null, null)
    }

    /** La proxima vez que sean esa hora y esos minutos: hoy si aun no han pasado, si no manana. */
    fun nextOccurrence(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    /**
     * El mismo PendingIntent para programar y para cancelar: Android los empareja por
     * accion y componente, no por los extras, asi que el id de la escena no estorba.
     */
    private fun operation(context: Context, sceneId: String?): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WakeAlarmReceiver::class.java)
            .setAction(ACTION_WAKE)
            .putExtra(EXTRA_SCENE_ID, sceneId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE + 1,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun persist(context: Context, atMillis: Long?, sceneId: String?) {
        val container = (context.applicationContext as? TvMandoApp)?.container ?: return
        container.backgroundScope.launch { container.settingsRepository.setWakeSchedule(atMillis, sceneId) }
    }
}

/** Suena la alarma: se borra lo programado (es de un disparo) y se ejecuta en el servicio. */
class WakeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WakeAlarms.ACTION_WAKE) return
        val container = (context.applicationContext as? TvMandoApp)?.container
        container?.backgroundScope?.launch { container.settingsRepository.setWakeSchedule(null, null) }
        SleepTimerService.runWake(context, intent.getStringExtra(WakeAlarms.EXTRA_SCENE_ID))
    }
}
