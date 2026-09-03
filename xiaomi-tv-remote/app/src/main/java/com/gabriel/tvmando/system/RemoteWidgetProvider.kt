package com.gabriel.tvmando.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gabriel.tvmando.MainActivity
import com.gabriel.tvmando.R
import com.gabriel.tvmando.domain.QuickCommand

/**
 * Widget de pantalla de inicio con los cuatro botones mas usados.
 *
 * No pinta estado de conexion a proposito: un widget no puede sondear la TV sin
 * gastar bateria, y un punto verde mentiroso es peor que no tener ninguno. Si un
 * comando falla, el receiver saca un toast.
 */
class RemoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {

        private val BUTTON_IDS = listOf(
            R.id.widget_button_1,
            R.id.widget_button_2,
            R.id.widget_button_3,
            R.id.widget_button_4,
        )

        fun buildViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_remote).apply {
                QuickCommand.widgetCommands.forEachIndexed { index, quick ->
                    val viewId = BUTTON_IDS[index]
                    setOnClickPendingIntent(viewId, TvCommandReceiver.pendingIntent(context, quick))
                    setContentDescription(viewId, quick.label)
                }
                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            }

        /** Redibuja todos los widgets colocados (tras cambiar algo de la app). */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RemoteWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
