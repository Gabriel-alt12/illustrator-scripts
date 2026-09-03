package com.gabriel.tvmando.domain

/** Una app instalada en la TV, tal como la enseña la pantalla de Apps. */
data class TvApp(
    val packageName: String,
    val displayName: String,
    /** true si el paquete esta en el catalogo y por tanto el nombre es fiable. */
    val isKnown: Boolean,
)

/**
 * Traduce la salida cruda del shell de la TV a algo que se pueda pintar.
 *
 * La seccion 11 de la especificacion avisa de que los nombres de paquete cambian
 * entre versiones y regiones, asi que la lista se descubre siempre con
 * `pm list packages -3` (mas una segunda pasada con `pm list packages` para rescatar
 * las de streaming preinstaladas de fabrica, ver [parseInstalledPackages]) y este
 * catalogo solo sirve para ponerle un nombre bonito a las que reconocemos. Una app
 * que no este aqui aparece igual, con el nombre deducido del paquete.
 *
 * Nota: por ADB no hay forma barata de sacar la etiqueta ni el icono reales de una
 * app (haria falta aapt en el dispositivo). De ahi el catalogo mas el monograma de
 * color que dibuja la UI.
 */
object AppCatalog {

    /**
     * El orden de declaracion es el orden en que salen en la rejilla: primero lo que
     * se usa a diario.
     */
    private val KNOWN_NAMES: Map<String, String> = linkedMapOf(
        "com.netflix.mediaclient" to "Netflix",
        "com.google.android.youtube.tv" to "YouTube",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.disney.disneyplus" to "Disney+",
        "com.wbd.stream" to "HBO Max",
        "com.hbo.hbonow" to "HBO Max",
        "com.spotify.tv.android" to "Spotify",
        "com.apple.atve.androidtv.appletv" to "Apple TV",
        "com.movistarplus.androidtv" to "Movistar Plus+",
        "es.atresmedia.atresplayer.tv" to "atresplayer",
        "com.rtve.androidtv" to "RTVE Play",
        "com.mitele.tv" to "Mitele",
        "com.filmin.androidtv" to "Filmin",
        "com.plexapp.android" to "Plex",
        "tv.twitch.android.app" to "Twitch",
        "com.google.android.youtube.tvmusic" to "YouTube Music",
        "com.google.android.apps.mediashell" to "Chromecast",
        "org.videolan.vlc" to "VLC",
        "com.google.android.tvlauncher" to "Inicio",
    )

    /** Sufijos que no aportan nada al deducir el nombre de un paquete desconocido. */
    private val NOISE_SEGMENTS = setOf(
        "tv", "android", "androidtv", "app", "apps", "mobile", "client", "player",
        "leanback", "atv", "google",
    )

    fun describe(packageName: String): TvApp {
        val known = KNOWN_NAMES[packageName]
        return TvApp(
            packageName = packageName,
            displayName = known ?: prettify(packageName),
            isKnown = known != null,
        )
    }

    /**
     * Parsea la salida de `pm list packages -3` (apps de terceros) y, opcionalmente,
     * la de `pm list packages` (catalogo completo del dispositivo), que llegan como
     * una linea por app:
     *
     *     package:com.netflix.mediaclient
     *     package:com.spotify.tv.android
     *
     * Tolera el formato con ruta (`-f`) y los retornos de carro que a veces mete el
     * shell de la TV.
     *
     * Las apps de terceros entran todas, se conozcan o no: son las que el usuario ha
     * instalado el mismo. Del catalogo completo solo se rescatan las que aparecen en
     * [KNOWN_NAMES] (Prime Video, Netflix, YouTube...), porque en las Xiaomi TV suelen
     * venir preinstaladas de fabrica y por tanto "-3" las esconde al contarlas como
     * apps de sistema. Meter el catalogo completo sin filtrar aqui inundaria la
     * rejilla de servicios internos de Android sin nombre reconocible.
     */
    fun parseInstalledPackages(
        thirdPartyOutput: String,
        allPackagesOutput: String = "",
    ): List<TvApp> {
        val thirdParty = extractPackageNames(thirdPartyOutput)
        val preinstalled = extractPackageNames(allPackagesOutput).filter { it in KNOWN_NAMES }
        return (thirdParty + preinstalled)
            .distinct()
            .map(::describe)
            .sortedWith(compareBy({ rankOf(it.packageName) }, { it.displayName.lowercase() }))
            .toList()
    }

    private fun extractPackageNames(shellOutput: String): List<String> = shellOutput
        .lineSequence()
        .map { line ->
            // Con -f la linea es "package:/data/app/.../base.apk=com.foo"; sin -f,
            // solo "package:com.foo". El paquete es siempre lo que va tras el igual.
            val raw = line.trim().removePrefix("package:").trim()
            if ('=' in raw) raw.substringAfterLast('=') else raw
        }
        .filter { it.isNotEmpty() && it.contains('.') && !it.contains(' ') }
        .distinct()
        .toList()

    /**
     * Saca el paquete en primer plano de la salida de
     * `dumpsys activity activities | grep mResumedActivity`, que tiene esta pinta:
     *
     *     mResumedActivity: ActivityRecord{7f3a u0 com.netflix.mediaclient/.MainActivity t42}
     */
    fun parseForegroundPackage(shellOutput: String): String? =
        FOREGROUND_PATTERN.find(shellOutput)?.groupValues?.get(1)

    private fun rankOf(packageName: String): Int {
        val index = KNOWN_NAMES.keys.indexOf(packageName)
        return if (index >= 0) index else Int.MAX_VALUE
    }

    /**
     * Nombre deducido: se tira el dominio inicial y los sufijos genericos, y se usa
     * el segmento mas significativo que quede. "tv.twitch.android.app" -> "Twitch".
     */
    private fun prettify(packageName: String): String {
        val segments = packageName.split('.').filter { it.isNotBlank() }
        if (segments.isEmpty()) return packageName

        val candidates = segments
            .drop(1) // el primero es el dominio: com, es, tv, org...
            .filter { it.lowercase() !in NOISE_SEGMENTS }

        val chosen = candidates.lastOrNull()
            ?: segments.lastOrNull { it.lowercase() !in NOISE_SEGMENTS }
            ?: segments.last()

        return chosen.replaceFirstChar { it.uppercase() }
    }

    private val FOREGROUND_PATTERN = Regex("""u\d+\s+([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/""")
}
