package com.gabriel.tvmando.domain

import java.util.Base64

/**
 * Un acceso directo a algo concreto de la TV: una serie, un episodio, un video.
 *
 * Lleva un enlace cuando lo hay. Netflix, YouTube o Disney+ abren su propio enlace
 * en el sitio exacto y por donde se dejo, porque la posicion la guarda el servicio;
 * no hace falta llevar la cuenta de nada aqui. Si no hay enlace, o la app de la TV no
 * lo entiende, se busca por nombre dentro de la app y se abre el primer resultado.
 */
data class Shortcut(
    val id: String,
    val title: String,
    /** Paquete de la app en la TV; null si no se sabe (se abre el enlace a secas). */
    val packageName: String?,
    val url: String?,
    /** Tras abrir el enlace, pulsar OK: en la ficha de una serie es el "Reanudar". */
    val autoOk: Boolean = false,
    val createdAt: Long = 0L,
) {
    val hasLink: Boolean get() = !url.isNullOrBlank()
}

/**
 * Serializa los accesos directos para el DataStore, con el mismo truco que las
 * escenas: cada texto va en base64 para que titulos y enlaces con cualquier cosa
 * dentro no rompan el formato.
 *
 *     linea := "1" ";" id ";" b64(titulo) ";" b64(paquete) ";" b64(url) ";" autoOk ";" creado
 */
object ShortcutCodec {

    private const val VERSION = "1"

    fun encode(shortcuts: List<Shortcut>): String = shortcuts.joinToString("\n") { s ->
        listOf(
            VERSION,
            s.id,
            b64(s.title),
            b64(s.packageName.orEmpty()),
            b64(s.url.orEmpty()),
            if (s.autoOk) "1" else "0",
            s.createdAt.toString(),
        ).joinToString(";")
    }

    /** Las lineas que no se entiendan se descartan: mejor perder una que todas. */
    fun decode(raw: String): List<Shortcut> = raw
        .lineSequence()
        .mapNotNull { decodeLine(it.trim()) }
        .toList()

    private fun decodeLine(line: String): Shortcut? {
        if (line.isEmpty()) return null
        val parts = line.split(';')
        if (parts.size != 7 || parts[0] != VERSION) return null
        val id = parts[1].takeIf { it.isNotBlank() } ?: return null
        val title = unb64(parts[2])?.takeIf { it.isNotBlank() } ?: return null
        val packageName = unb64(parts[3]) ?: return null
        val url = unb64(parts[4]) ?: return null
        val createdAt = parts[6].toLongOrNull() ?: return null
        return Shortcut(
            id = id,
            title = title,
            packageName = packageName.ifEmpty { null },
            url = url.ifEmpty { null },
            autoOk = parts[5] == "1",
            createdAt = createdAt,
        )
    }

    private fun b64(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun unb64(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }.getOrNull()
}

/** Lo que se saca de un texto compartido desde otra app del movil. */
data class SharedLink(
    val title: String,
    val url: String,
    /** App de la TV que entiende ese enlace, o null si no es de ninguna conocida. */
    val packageName: String?,
)

/**
 * Entiende lo que llega por "Compartir" desde Netflix, Prime Video, YouTube o un
 * navegador: un texto con un enlace dentro y, a veces, un asunto aparte.
 *
 * Cada app adorna el texto a su manera ("Mira «Reacher» en Netflix", "Reacher -
 * Temporada 2", comillas, saltos de linea). Aqui se quita el enlace y el relleno
 * para quedarse con el titulo, que es lo que se ve en la estanteria.
 */
object SharedLinkParser {

    fun parse(text: String?, subject: String? = null): SharedLink? {
        val body = text.orEmpty()
        val url = URL.find(body)?.value?.trimEnd('.', ',', ')', ']', '>')
            ?: URL.find(subject.orEmpty())?.value?.trimEnd('.', ',', ')', ']', '>')
            ?: return null
        val packageName = tvPackageFor(url)
        val title = cleanTitle(subject)
            ?: cleanTitle(body)
            ?: packageName?.let { AppCatalog.describe(it).displayName }
            ?: "Enlace"
        return SharedLink(title = title, url = url, packageName = packageName)
    }

    /** App de la TV que abre este enlace, por el dominio. */
    fun tvPackageFor(url: String): String? {
        val host = HOST.find(url)?.groupValues?.get(1)?.lowercase()
            ?.removePrefix("www.")?.removePrefix("m.")
            ?: return null
        return HOSTS.firstOrNull { (domain, _) ->
            when {
                domain.endsWith(".") -> host.contains(domain)
                else -> host == domain || host.endsWith(".$domain")
            }
        }?.second
    }

    private fun cleanTitle(raw: String?): String? {
        var title = raw ?: return null
        title = URL.replace(title, " ")
        title = QUOTES.replace(title, " ")
        title = SPACES.replace(title, " ").trim()
        title = LEADING_NOISE.replace(title, "")
        title = TRAILING_NOISE.replace(title, "")
        title = title.trim().trim('-', '|', ':', ',', '.', ' ')
        return title.takeIf { it.isNotBlank() }
    }

    private const val PRIME = "com.amazon.avod.thirdpartyclient"

    /** Dominio a paquete de TV. Los mas concretos van antes que los genericos. */
    private val HOSTS: List<Pair<String, String>> = listOf(
        "music.youtube.com" to "com.google.android.youtube.tvmusic",
        "youtube.com" to "com.google.android.youtube.tv",
        "youtu.be" to "com.google.android.youtube.tv",
        "netflix.com" to "com.netflix.ninja",
        "primevideo.com" to PRIME,
        "amazon." to PRIME,
        "disneyplus.com" to "com.disney.disneyplus",
        "max.com" to "com.wbd.stream",
        "hbomax.com" to "com.wbd.stream",
        "movistarplus.es" to "com.movistarplus.androidtv",
        "atresplayer.com" to "es.atresmedia.atresplayer.tv",
        "rtve.es" to "com.rtve.androidtv",
        "mitele.es" to "com.mitele.tv",
        "filmin.es" to "com.filmin.androidtv",
        "spotify.com" to "com.spotify.tv.android",
        "spotify.link" to "com.spotify.tv.android",
        "twitch.tv" to "tv.twitch.android.app",
        "dazn.com" to "com.dazn",
        "tv.apple.com" to "com.apple.atve.androidtv.appletv",
        "plex.tv" to "com.plexapp.android",
    )

    private val URL = Regex("""https?://\S+""")
    private val HOST = Regex("""https?://([^/?#\s]+)""")
    private val QUOTES = Regex("""["\u00AB\u00BB\u201C\u201D\u2018\u2019']""")
    private val SPACES = Regex("""\s+""")
    private val LEADING_NOISE = Regex(
        """^(?:check out|mira|ver|watch|echa un vistazo a|te recomiendo|descubre)\s+""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_NOISE = Regex(
        """\s+(?:on|en)\s+(?:netflix|prime video|amazon prime video|disney\+|youtube|max|hbo max|""" +
            """filmin|movistar plus\+|atresplayer|rtve play|mitele)\s*$""",
        RegexOption.IGNORE_CASE,
    )
}
