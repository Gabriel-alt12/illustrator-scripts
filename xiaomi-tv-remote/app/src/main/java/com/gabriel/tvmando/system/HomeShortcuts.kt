package com.gabriel.tvmando.system

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import com.gabriel.tvmando.MainActivity
import com.gabriel.tvmando.R
import com.gabriel.tvmando.domain.AppCatalog
import com.gabriel.tvmando.domain.Shortcut

/**
 * Los accesos directos, tambien fuera de la app: fijados en la pantalla de inicio del
 * movil (un icono por serie: tocarlo pone la tele en marcha sin abrir Ember) y como
 * atajos al mantener pulsado el icono de la app.
 *
 * El icono se dibuja aqui: el color de la app de la TV con la inicial encima, en la
 * fuente de la marca. No hay carátulas, y una letra grande se distingue en el
 * escritorio mejor que cualquier miniatura.
 */
object HomeShortcuts {

    /** Pide al lanzador fijar el acceso en el escritorio. Android pregunta antes. */
    fun pin(context: Context, shortcut: Shortcut): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, info(context, shortcut), null)
        }.getOrDefault(false)
    }

    /**
     * Los mas recientes como atajos del icono de la app. Se rehace entero cada vez:
     * es una lista corta y asi los borrados desaparecen tambien de ahi.
     */
    fun publish(context: Context, shortcuts: List<Shortcut>) {
        runCatching {
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtMost(4)
            val infos = shortcuts.take(max).map { info(context, it) }
            ShortcutManagerCompat.setDynamicShortcuts(context, infos)
        }
    }

    fun launchIntent(context: Context, shortcutId: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_SHORTCUT)
            .putExtra(MainActivity.EXTRA_SHORTCUT_ID, shortcutId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun info(context: Context, shortcut: Shortcut): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, shortcut.id)
            .setShortLabel(shortcut.title.take(SHORT_LABEL_CHARS).ifBlank { "Ember" })
            .setLongLabel(shortcut.title)
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon(context, shortcut)))
            .setIntent(launchIntent(context, shortcut.id))
            .build()

    /**
     * Icono adaptativo: 108 dp de lienzo de los que el lanzador ensena los 72 del
     * centro, asi que la letra va en esa zona segura.
     */
    private fun icon(context: Context, shortcut: Shortcut): Bitmap {
        val size = ICON_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = shortcut.packageName?.let { AppCatalog.brandColor(it) }?.toInt() ?: INK
        canvas.drawColor(background)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = ResourcesCompat.getFont(context, R.font.barlow_extrabold)
            textSize = size * 0.42f
        }
        val initials = shortcut.title
            .split(' ', '-', ':')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1) }
            .uppercase()
            .ifBlank { "E" }
        // Centrado vertical de verdad: la linea base va a la mitad menos el centro
        // del cuerpo del texto.
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(initials, size / 2f, baseline, paint)
        return bitmap
    }

    private const val ICON_PX = 216
    private const val SHORT_LABEL_CHARS = 24
    private const val INK = 0xFF08090C.toInt()
}
