package com.gabriel.tvmando.domain

/** Una app instalada en la TV, tal como la enseña la pantalla de Apps. */
data class TvApp(
    val packageName: String,
    val displayName: String,
    /** true si el paquete esta en el catalogo y por tanto el nombre es fiable. */
    val isKnown: Boolean,
)

/**
 * Coincidencia para los filtros de la UI. Busca en el nombre y tambien en el paquete,
 * porque con la tele llena de apps a veces uno se acuerda antes de "amazon" que de
 * "Prime Video", y porque las desconocidas solo tienen el paquete como pista.
 */
fun TvApp.matches(query: String): Boolean {
    val clean = query.trim()
    return clean.isEmpty() ||
        displayName.contains(clean, ignoreCase = true) ||
        packageName.contains(clean, ignoreCase = true)
}

/**
 * Traduce la salida cruda del shell de la TV a algo que se pueda pintar.
 *
 * La seccion 11 de la especificacion avisa de que los nombres de paquete cambian
 * entre versiones y regiones, asi que la lista se descubre siempre preguntandole a la
 * TV (ver [parseInstalledPackages], que cruza tres consultas distintas) y este
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
        // Netflix reparte dos paquetes distintos: "ninja" es el de Android TV y
        // "mediaclient" el de movil. En la Xiaomi TV el bueno es el primero.
        "com.netflix.ninja" to "Netflix",
        "com.netflix.mediaclient" to "Netflix",
        "com.google.android.youtube.tv" to "YouTube",
        // Y Amazon otros dos: "thirdpartyclient" es el que se instala en Google TV
        // desde la Play Store; "livingroom" es el de los Fire TV.
        "com.amazon.avod.thirdpartyclient" to "Prime Video",
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
        "tv.twitch.android.viewer" to "Twitch",
        "com.dazn" to "DAZN",
        "com.google.android.youtube.tvmusic" to "YouTube Music",
        "com.google.android.apps.mediashell" to "Chromecast",
        "org.videolan.vlc" to "VLC",
        "org.xbmc.kodi" to "Kodi",
        "com.valvesoftware.steamlink" to "Steam Link",
        "com.android.vending" to "Play Store",
        "com.google.android.tvlauncher" to "Inicio",
        "com.google.android.apps.tv.launcherx" to "Inicio",
    )

    /**
     * Sufijos que no aportan nada al deducir el nombre de un paquete desconocido.
     *
     * Los nombres internos de las apps de TV estan llenos de estos: sin quitarlos,
     * `com.amazon.avod.thirdpartyclient` se quedaria en "Thirdpartyclient" en vez de
     * en "Amazon", que al menos dice algo.
     */
    private val NOISE_SEGMENTS = setOf(
        "tv", "android", "androidtv", "app", "apps", "mobile", "client", "player",
        "leanback", "atv", "google", "avod", "thirdpartyclient", "livingroom",
        "mediaclient", "ninja", "firetv", "smarttv", "androidtvlauncher",
    )

    /**
     * Equivalente en el movil de cada app de la TV, para poder sacar su icono real.
     *
     * Por ADB no hay forma barata de traerse el icono desde el televisor (haria falta
     * aapt alli), pero muchas de estas apps estan tambien en el movil, y de ahi si se
     * puede leer; y es tambien el paquete que tiene ficha publica en la Play Store,
     * de donde se descarga el icono si en el movil no esta. Casi todas usan el mismo
     * paquete en los dos sitios; las que no, estan aqui.
     */
    private val PHONE_PACKAGES: Map<String, String> = mapOf(
        "com.netflix.ninja" to "com.netflix.mediaclient",
        // La variante de Fire TV no existe en el movil: su icono es el del otro paquete.
        "com.amazon.amazonvideo.livingroom" to "com.amazon.avod.thirdpartyclient",
        "com.google.android.youtube.tv" to "com.google.android.youtube",
        "com.google.android.youtube.tvmusic" to "com.google.android.apps.youtube.music",
        "com.spotify.tv.android" to "com.spotify.music",
        "es.atresmedia.atresplayer.tv" to "es.atresmedia.atresplayer",
        "com.movistarplus.androidtv" to "com.movistarplus.movistarplus",
        "com.rtve.androidtv" to "es.rtve.rtveplay",
        "com.mitele.tv" to "es.mediaset.mitele",
        "com.filmin.androidtv" to "com.filmin.filmin",
        "tv.twitch.android.viewer" to "tv.twitch.android.app",
        "com.apple.atve.androidtv.appletv" to "com.apple.atve.android.appletv",
        "com.google.android.apps.tv.launcherx" to "com.google.android.apps.nexuslauncher",
    )

    /**
     * Nombre del paquete que hay que buscar en el movil para sacar el icono, o null si
     * esta app no es de las que se pueden mirar. Solo se responden las conocidas
     * porque el manifiesto declara justo esas en `<queries>`: preguntarle a Android por
     * un paquete que no esta declarado devuelve "no instalado" aunque lo este.
     */
    fun phonePackageFor(tvPackage: String): String? = when {
        tvPackage in PHONE_PACKAGES -> PHONE_PACKAGES[tvPackage]
        tvPackage in KNOWN_NAMES -> tvPackage
        else -> null
    }

    /**
     * Color de marca en ARGB, o null si no se conoce. Va como Long y no como Color de
     * Compose para no meter Android en esta capa, que se prueba en la JVM a secas.
     */
    fun brandColor(packageName: String): Long? = BRAND_COLORS[packageName]

    /**
     * Tonos sacados de la identidad de cada servicio, apagados un punto para que
     * convivan sobre el fondo casi negro sin pelearse con el naranja de acento.
     */
    private val BRAND_COLORS: Map<String, Long> = mapOf(
        "com.netflix.ninja" to 0xFF8C1116,
        "com.netflix.mediaclient" to 0xFF8C1116,
        "com.google.android.youtube.tv" to 0xFF8E1414,
        "com.google.android.youtube.tvmusic" to 0xFF8E1414,
        "com.amazon.avod.thirdpartyclient" to 0xFF15607A,
        "com.amazon.amazonvideo.livingroom" to 0xFF15607A,
        "com.disney.disneyplus" to 0xFF1B2A6B,
        "com.wbd.stream" to 0xFF4A2380,
        "com.hbo.hbonow" to 0xFF4A2380,
        "com.spotify.tv.android" to 0xFF1A6B39,
        "com.apple.atve.androidtv.appletv" to 0xFF3A3A3C,
        "com.movistarplus.androidtv" to 0xFF14567A,
        "es.atresmedia.atresplayer.tv" to 0xFF6B2340,
        "com.rtve.androidtv" to 0xFF1E4E7A,
        "com.mitele.tv" to 0xFF7A3A15,
        "com.filmin.androidtv" to 0xFF2A2A6B,
        "com.plexapp.android" to 0xFF8A6215,
        "tv.twitch.android.app" to 0xFF5A2E96,
        "tv.twitch.android.viewer" to 0xFF5A2E96,
        "com.dazn" to 0xFF2E2E33,
        "org.videolan.vlc" to 0xFF8A4A0F,
        "org.xbmc.kodi" to 0xFF15607A,
        "com.valvesoftware.steamlink" to 0xFF23324A,
        "com.android.vending" to 0xFF15563E,
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
     * Construye la rejilla de apps cruzando lo que responden tres consultas de la TV.
     *
     * Las dos de `pm list packages` llegan como una linea por app, y se toleran tanto
     * el formato con ruta (`-f`) como los retornos de carro que a veces mete el shell:
     *
     *     package:com.netflix.ninja
     *     package:com.spotify.tv.android
     *
     * Se cruzan tres fuentes porque ninguna sola vale:
     *
     *  - **Terceros** (`-3`): entran todas, se conozcan o no. Son las que el usuario
     *    ha instalado el mismo.
     *  - **Con icono en la TV** (`launcherOutput`): entran todas tambien. Es la mejor
     *    fuente, porque es literalmente lo que se ve en la pantalla de inicio del
     *    televisor, venga de fabrica o de la Play Store.
     *  - **Catalogo completo**: de aqui solo se rescatan las que aparecen en
     *    [KNOWN_NAMES]. Sirve de red de seguridad para las preinstaladas de fabrica si
     *    la consulta del lanzador no funciona en esta TV. Sin filtrar inundaria la
     *    rejilla de servicios internos de Android sin nombre reconocible.
     */
    fun parseInstalledPackages(
        thirdPartyOutput: String,
        allPackagesOutput: String = "",
        launcherOutput: String = "",
    ): List<TvApp> {
        val thirdParty = extractPackageNames(thirdPartyOutput)
        val launchable = extractLauncherPackages(launcherOutput)
        val preinstalled = extractPackageNames(allPackagesOutput).filter { it in KNOWN_NAMES }
        return (thirdParty + launchable + preinstalled)
            .distinct()
            .map(::describe)
            .sortedWith(compareBy({ rankOf(it.packageName) }, { it.displayName.lowercase() }))
            .toList()
    }

    /**
     * Saca los paquetes de la salida de `cmd package query-activities`, que no viene
     * en lineas "package:..." sino en bloques por actividad:
     *
     *     Activity #0:
     *       ActivityInfo:
     *         name=com.netflix.ninja.MainActivity
     *         packageName=com.netflix.ninja
     *
     * Solo se mira `packageName=` y no el resto de campos, que cambian de forma entre
     * versiones de Android.
     */
    private fun extractLauncherPackages(shellOutput: String): List<String> =
        LAUNCHER_PACKAGE_PATTERN
            .findAll(shellOutput)
            .map { it.groupValues[1] }
            .filter { it.contains('.') }
            .distinct()
            .toList()

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

    private val LAUNCHER_PACKAGE_PATTERN =
        Regex("""packageName=([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)""")
}
