package com.gabriel.tvmando.domain

import java.text.Normalizer
import java.util.Base64

/**
 * Catalogo de comandos de la seccion 5 de la especificacion.
 *
 * La fase 2 solo cablea power y volumen, pero el modelo esta completo a proposito:
 * las pantallas de mando, apps, busqueda y escenas se construyen encima sin tocar
 * esta capa. Cada comando sabe traducirse al shell que se ejecuta en la TV.
 */
sealed interface TvCommand {
    /** Linea que se ejecuta en el shell de la TV. */
    val shell: String

    /** Texto corto para el registro y el feedback en pantalla. */
    val label: String
}

/** Teclas de control remoto que acepta `input keyevent`. */
enum class TvKey(val keycode: String, val label: String) {
    POWER("KEYCODE_POWER", "Encendido"),
    SLEEP("KEYCODE_SLEEP", "Apagar pantalla"),
    WAKEUP("KEYCODE_WAKEUP", "Despertar"),
    HOME("KEYCODE_HOME", "Inicio"),
    BACK("KEYCODE_BACK", "Atras"),
    APP_SWITCH("KEYCODE_APP_SWITCH", "Recientes"),
    ASSIST("KEYCODE_ASSIST", "Asistente"),

    DPAD_UP("KEYCODE_DPAD_UP", "Arriba"),
    DPAD_DOWN("KEYCODE_DPAD_DOWN", "Abajo"),
    DPAD_LEFT("KEYCODE_DPAD_LEFT", "Izquierda"),
    DPAD_RIGHT("KEYCODE_DPAD_RIGHT", "Derecha"),
    DPAD_CENTER("KEYCODE_DPAD_CENTER", "OK"),

    VOLUME_UP("KEYCODE_VOLUME_UP", "Subir volumen"),
    VOLUME_DOWN("KEYCODE_VOLUME_DOWN", "Bajar volumen"),
    VOLUME_MUTE("KEYCODE_VOLUME_MUTE", "Silenciar"),

    MEDIA_PLAY_PAUSE("KEYCODE_MEDIA_PLAY_PAUSE", "Play / Pausa"),
    MEDIA_FAST_FORWARD("KEYCODE_MEDIA_FAST_FORWARD", "Avanzar"),
    MEDIA_REWIND("KEYCODE_MEDIA_REWIND", "Retroceder"),
    MEDIA_NEXT("KEYCODE_MEDIA_NEXT", "Siguiente"),
    MEDIA_PREVIOUS("KEYCODE_MEDIA_PREVIOUS", "Anterior"),

    CHANNEL_UP("KEYCODE_CHANNEL_UP", "Canal +"),
    CHANNEL_DOWN("KEYCODE_CHANNEL_DOWN", "Canal -"),

    ENTER("KEYCODE_ENTER", "Aceptar"),
    DEL("KEYCODE_DEL", "Borrar"),

    ;

    companion object {
        /**
         * Lo que se le presta a una visita desde el mando del navegador.
         *
         * Fuera quedan POWER, SLEEP y WAKEUP a proposito: apagar la tele de la casa no
         * es cosa de quien esta de paso, y asi una pestaña olvidada en el movil de
         * alguien tampoco puede hacerlo sin querer. Navegar, pausar y tocar el volumen
         * es justo lo que se espera de un mando prestado.
         *
         * Vive aqui y no en el servidor porque es una decision sobre lo que la app
         * deja hacer, no sobre como viaja: asi se puede probar sin levantar un socket.
         */
        val GUEST: List<TvKey> = listOf(
            DPAD_UP,
            DPAD_DOWN,
            DPAD_LEFT,
            DPAD_RIGHT,
            DPAD_CENTER,
            BACK,
            HOME,
            VOLUME_DOWN,
            VOLUME_MUTE,
            VOLUME_UP,
            MEDIA_PLAY_PAUSE,
            MEDIA_PREVIOUS,
            MEDIA_NEXT,
        )
    }
}

/** Pulsacion de una tecla del mando. */
data class PressKey(val key: TvKey) : TvCommand {
    override val shell: String get() = "input keyevent ${key.keycode}"
    override val label: String get() = key.label
}

/** Digito 0-9, para los canales de TDT. */
data class PressDigit(val digit: Int) : TvCommand {
    init {
        require(digit in 0..9) { "Digito fuera de rango: $digit" }
    }

    override val shell: String get() = "input keyevent KEYCODE_$digit"
    override val label: String get() = "Digito $digit"
}

/**
 * Abre una app por su nombre de paquete.
 *
 * Se piden las dos categorias porque las apps de television declaran
 * LEANBACK_LAUNCHER y muchas no declaran LAUNCHER: pidiendo solo esa ultima, monkey
 * responde "No activities found to run" justo con las apps que mas se usan aqui.
 * `-c` se puede repetir y monkey lanza la actividad que case con cualquiera de ellas.
 */
data class LaunchApp(val packageName: String) : TvCommand {
    override val shell: String
        get() = "monkey -p ${packageName.shellQuoted()} " +
            "-c android.intent.category.LEANBACK_LAUNCHER " +
            "-c android.intent.category.LAUNCHER 1"
    override val label: String get() = "Abrir $packageName"
}

/** Fuerza el cierre de una app (pulsacion larga en la pantalla de Apps). */
data class ForceStopApp(val packageName: String) : TvCommand {
    override val shell: String get() = "am force-stop ${packageName.shellQuoted()}"
    override val label: String get() = "Cerrar $packageName"
}

/** Escribe texto en el campo que tenga el foco en la TV. */
data class TypeText(val text: String) : TvCommand {
    override val shell: String get() = "input text ${text.shellQuoted()}"
    override val label: String get() = "Escribir"
}

/**
 * Escribe pulsando una tecla por letra en lugar de inyectar el texto de golpe.
 *
 * [TypeText] manda `input text`, que entrega la cadena entera de una vez. Los
 * buscadores de las apps de television suelen ser rejillas de letras hechas a mano, no
 * campos de texto normales, y con esa reyerta se quedan solo con una letra o con
 * ninguna: Prime Video escribia una "a" y ya. Con `input keyevent` las teclas entran de
 * una en una por el mismo camino que las del mando fisico, que es lo que esas
 * pantallas si saben atender.
 *
 * Los keycodes van todos en una sola llamada a proposito: arrancar `input` en la TV
 * cuesta un cuarto de segundo, y una llamada por letra convertiria una busqueda de
 * diez letras en tres segundos de espera.
 */
data class TypeKeys(val text: String) : TvCommand {

    val keycodes: List<String> = keycodesFor(text)

    override val shell: String
        get() = if (keycodes.isEmpty()) "true" else "input keyevent " + keycodes.joinToString(" ")

    override val label: String get() = "Escribir"
}

/** Ajusta el brillo del panel (0-255). */
data class SetBrightness(val value: Int) : TvCommand {
    override val shell: String get() = "settings put system screen_brightness ${value.coerceIn(0, 255)}"
    override val label: String get() = "Brillo $value"
}

/**
 * Fija el volumen absoluto del canal multimedia.
 *
 * Va por `cmd media_session`, que es lo que hay detras del atajo `media` y existe
 * en mas builds. Si tu TV lo ignora, la barra de volumen de la app lo nota (la
 * consulta de estado no devuelve nivel) y se queda con subir y bajar a ciegas.
 */
data class SetVolume(val level: Int) : TvCommand {
    override val shell: String
        get() = "cmd media_session volume --stream 3 --set ${level.coerceIn(0, 100)}"
    override val label: String get() = "Volumen ${level.coerceIn(0, 100)}"
}

/** Consultas de diagnostico, sin efectos secundarios. */
enum class TvQuery(override val shell: String, override val label: String) : TvCommand {
    MODEL("getprop ro.product.model", "Modelo"),
    ANDROID_VERSION("getprop ro.build.version.release", "Version de Android"),
    THIRD_PARTY_PACKAGES("pm list packages -3", "Apps instaladas"),

    /**
     * Muchas apps de streaming vienen preinstaladas de fabrica en la TV (Prime Video,
     * Netflix...) y Android las cuenta como apps de sistema, no de terceros: la
     * consulta de arriba sola las deja fuera. Esta segunda consulta trae todo el
     * catalogo del dispositivo para poder rescatar, del catalogo conocido, las que
     * "-3" esconde. Ver [com.gabriel.tvmando.domain.AppCatalog.parseInstalledPackages].
     */
    ALL_PACKAGES("pm list packages", "Todas las apps"),

    /**
     * Las apps que tienen icono en la pantalla de inicio de la TV, que es
     * exactamente lo que el usuario espera ver en la rejilla.
     *
     * Es la fuente de verdad buena: no depende de si la app vino de fabrica o de la
     * Play Store, ni de que su paquete este en nuestro catalogo. Se preguntan las dos
     * categorias porque en Google TV lo normal es LEANBACK_LAUNCHER, pero una app de
     * movil colada por sideload puede declarar solo LAUNCHER.
     *
     * `cmd package` existe desde Android 9; si la TV no lo entiende, la salida no
     * casara con el patron y el resto de consultas siguen cubriendo el caso.
     */
    LAUNCHER_ACTIVITIES(
        "cmd package query-activities -a android.intent.action.MAIN " +
            "-c android.intent.category.LEANBACK_LAUNCHER; " +
            "cmd package query-activities -a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER",
        "Apps con icono en la TV",
    ),
    CURRENT_ACTIVITY(
        "dumpsys activity activities | grep mResumedActivity",
        "App en primer plano",
    ),
    BRIGHTNESS("settings get system screen_brightness", "Brillo actual"),

    /**
     * Captura de lo que hay en la TV ahora mismo.
     *
     * `screencap -p` escupe un PNG binario, y el transporte de esta app entrega la
     * salida como texto: pasarlo por `base64` en la propia TV evita tener que abrir un
     * camino binario en la capa ADB solo para esto. Cuesta un tercio mas de bytes, que
     * en la red de casa da igual.
     */
    SCREENSHOT("screencap -p | base64", "Captura de la TV"),
    WLAN("ip addr show wlan0", "Red"),

    /**
     * Encendido, volumen exacto y que suena, en una sola ida y vuelta: se pregunta
     * cada pocos segundos mientras la app esta a la vista y no merece tres viajes.
     * Cada tramo va precedido de una marca para que [TvStatus.parse] sepa donde
     * empieza cada cosa aunque alguna consulta falle o no exista en esa TV.
     */
    STATUS(
        "echo '#power'; dumpsys power 2>/dev/null | grep -m1 -E 'mWakefulness=|Display Power: state='; " +
            "echo '#volume'; cmd media_session volume --stream 3 --get 2>&1; " +
            "echo '#media'; dumpsys media_session 2>/dev/null | head -c 24000",
        "Estado de la TV",
    ),
}

/** Escotilla de escape para comandos sueltos (util en Ajustes y al depurar). */
data class RawShell(override val shell: String) : TvCommand {
    override val label: String get() = shell
}

/**
 * Traduce texto a keycodes de `input keyevent`.
 *
 * Solo hay tecla para letras sin tilde, digitos y el espacio, asi que antes se pasa por
 * un normalizador que deja "El Senor" a partir de "El Señor". Para buscar en una tele es
 * justo lo que se quiere: los buscadores ignoran mayusculas y tildes, y lo que no se
 * puede teclear vale mas tirarlo que mandarlo como basura.
 */
internal fun keycodesFor(text: String): List<String> =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase()
        .mapNotNull { char ->
            when (char) {
                in 'a'..'z' -> "KEYCODE_${char.uppercaseChar()}"
                in '0'..'9' -> "KEYCODE_$char"
                ' ' -> "KEYCODE_SPACE"
                else -> null
            }
        }

private val DIACRITICS = Regex("""\p{Mn}+""")

/**
 * Deshace el base64 de [TvQuery.SCREENSHOT].
 *
 * Se usa el decodificador MIME y no el estricto porque `base64` de la TV parte la
 * salida en lineas, y el estricto se atraganta con los saltos. A cambio, el MIME se
 * salta lo que no es base64 en vez de quejarse, asi que un "base64: not found" del
 * shell se convertiria en veinte bytes de basura que luego no hay quien pinte: por eso
 * se exige ademas la firma de un PNG. Lo que no la lleve se devuelve como null y quien
 * llama ya tiene donde contar lo que dijo la TV.
 */
fun decodeScreenshot(raw: String): ByteArray? = runCatching {
    Base64.getMimeDecoder().decode(raw.trim())
}.getOrNull()?.takeIf { bytes ->
    bytes.size > PNG_SIGNATURE.size &&
        PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }
}

/** Los cuatro primeros bytes de cualquier PNG: 0x89 "PNG". */
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

/**
 * Entrecomilla para el shell de la TV.
 *
 * `input text` y `monkey` reciben el argumento a traves de `sh`, asi que un titulo
 * con espacios o comillas rompe el comando si no se escapa. Comillas simples y el
 * truco clasico para la propia comilla simple.
 */
internal fun String.shellQuoted(): String = "'" + replace("'", "'\\''") + "'"
