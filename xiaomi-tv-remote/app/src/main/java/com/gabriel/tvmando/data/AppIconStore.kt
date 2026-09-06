package com.gabriel.tvmando.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.gabriel.tvmando.domain.AppCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * De donde sale el icono de cada app de la rejilla, por orden de preferencia:
 *
 *  1. La misma app instalada en el movil. Gratis y sin red.
 *  2. La copia en disco de una descarga anterior.
 *  3. La Play Store: la pagina web de la ficha de cada app lleva su icono. Se prueba
 *     con el paquete del movil, que es el que tiene ficha publica, y luego con el de
 *     la TV.
 *
 * Es el unico sitio donde la app sale a internet, y solo para esto: una descarga por
 * app, la primera vez, y a disco. Lo que no se encuentra se apunta durante unos dias
 * para no preguntar a la tienda cada vez que se abre la pestana.
 *
 * Las peticiones en curso se comparten: con la rejilla llena, veinte fichas piden su
 * icono en la misma pasada y no tiene sentido abrir veinte conexiones para la misma
 * app si dos fichas coinciden.
 */
object AppIconStore {

    private val memory = ConcurrentHashMap<String, ImageBitmap>()
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Icono de la app, o null si no hay forma de conseguirlo. Nunca lanza. */
    suspend fun load(context: Context, tvPackage: String): ImageBitmap? {
        memory[tvPackage]?.let { return it.takeIf { cached -> cached !== Missing } }
        val app = context.applicationContext
        val pending = inFlight.getOrPut(tvPackage) {
            scope.async {
                val bitmap = runCatching { resolve(app, tvPackage) }.getOrNull()
                memory[tvPackage] = bitmap ?: Missing
                inFlight.remove(tvPackage)
                bitmap
            }
        }
        return pending.await()
    }

    private fun resolve(context: Context, tvPackage: String): ImageBitmap? {
        phoneIcon(context, tvPackage)?.let { return it }

        val dir = File(context.filesDir, "icons").apply { mkdirs() }
        val file = File(dir, "$tvPackage.png")
        if (file.exists()) {
            decode(file.readBytes())?.let { return it }
            file.delete()
        }

        val missing = File(dir, "$tvPackage.missing")
        if (missing.exists() && System.currentTimeMillis() - missing.lastModified() < RETRY_AFTER_MS) {
            return null
        }

        val bytes = download(tvPackage)
        val bitmap = bytes?.let { decode(it) }
        if (bitmap == null) {
            missing.writeText("")
            return null
        }
        file.writeBytes(bytes)
        missing.delete()
        return bitmap
    }

    /**
     * Icono de la misma app instalada en el movil, o null si no esta. Solo se
     * pregunta por los paquetes declarados en `<queries>` del manifiesto: por los
     * demas Android responde "no instalado" aunque lo este.
     */
    private fun phoneIcon(context: Context, tvPackage: String): ImageBitmap? = runCatching {
        val phonePackage = AppCatalog.phonePackageFor(tvPackage) ?: return null
        context.packageManager
            .getApplicationIcon(phonePackage)
            .toBitmap(ICON_PX, ICON_PX)
            .asImageBitmap()
    }.getOrNull()

    /**
     * Busca el icono en la ficha web de la Play Store. La pagina lleva el icono como
     * imagen con `alt="Icon image"` (se pide en ingles para que ese texto sea fijo) y,
     * de respaldo, una imagen de vista previa (og:image), que a veces es el icono y a
     * veces un banner apaisado: por eso [decode] descarta lo que no sea cuadrado.
     */
    private fun download(tvPackage: String): ByteArray? {
        val candidates = listOfNotNull(AppCatalog.phonePackageFor(tvPackage), tvPackage).distinct()
        for (candidate in candidates) {
            val page = fetch("https://play.google.com/store/apps/details?id=$candidate&hl=en", MAX_PAGE_BYTES)
                ?.toString(Charsets.UTF_8)
                ?: continue
            val urls = buildList {
                ICON_IMG_SRC_FIRST.find(page)?.groupValues?.get(1)?.let { add(it) }
                ICON_IMG_ALT_FIRST.find(page)?.groupValues?.get(1)?.let { add(it) }
                OG_IMAGE.find(page)?.groupValues?.get(1)?.let { add(it) }
            }
            for (url in urls.distinct()) {
                val image = fetch(url.replace("&amp;", "&"), MAX_IMAGE_BYTES) ?: continue
                if (decode(image) != null) return image
            }
        }
        return null
    }

    private fun fetch(url: String, maxBytes: Int): ByteArray? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept-Language", "en")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (out.size() < maxBytes) {
                    val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - out.size()))
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** Solo vale lo que sea una imagen y mas o menos cuadrada: un banner no es un icono. */
    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            ?: return null
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (bitmap.width < MIN_ICON_PX || ratio !in 0.8f..1.25f) return null
        return bitmap.asImageBitmap()
    }

    /** Marca de "aqui no hay icono", para poder cachear tambien eso en memoria. */
    private val Missing: ImageBitmap = ImageBitmap(1, 1)

    private val ICON_IMG_SRC_FIRST = Regex("""<img[^>]*?src="([^"]+)"[^>]*?alt="Icon image"""")
    private val ICON_IMG_ALT_FIRST = Regex("""<img[^>]*?alt="Icon image"[^>]*?src="([^"]+)"""")
    private val OG_IMAGE = Regex("""property="og:image"\s+content="([^"]+)"""")

    /** Lado del icono en pixeles cuando sale del movil: de sobra para una ficha. */
    private const val ICON_PX = 144
    private const val MIN_ICON_PX = 48
    private const val TIMEOUT_MS = 10_000
    private const val MAX_PAGE_BYTES = 1_500_000
    private const val MAX_IMAGE_BYTES = 2_000_000
    private const val RETRY_AFTER_MS = 7L * 24 * 60 * 60 * 1000
    private const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
}
