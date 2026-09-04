package com.gabriel.tvmando.domain

import java.util.Base64

/**
 * Un paso de una escena: ejecutar algo y esperar.
 *
 * El comando se guarda ya traducido a su linea de shell en lugar de como un
 * [TvCommand] tipado. Es a proposito: una escena se persiste, y guardar la linea
 * final significa que el formato no se rompe cada vez que se anade un tipo de
 * comando nuevo. La etiqueta viaja al lado para poder pintar el paso sin volver a
 * interpretarlo.
 *
 * @param shell null cuando el paso es solo una espera.
 * @param delayMs tiempo de espera DESPUES de ejecutar el comando.
 */
data class SceneStep(
    val label: String,
    val shell: String? = null,
    val delayMs: Long = 0,
) {
    val isWaitOnly: Boolean get() = shell == null

    /** Texto para la lista del editor: "Abrir Netflix, esperar 3 s". */
    fun describe(): String = buildString {
        append(label)
        if (delayMs > 0) {
            append(", esperar ")
            append(formatDelay(delayMs))
        }
    }

    companion object {
        fun of(command: TvCommand, delayMs: Long = 0): SceneStep =
            SceneStep(label = command.label, shell = command.shell, delayMs = delayMs)

        fun wait(delayMs: Long): SceneStep =
            SceneStep(label = "Esperar ${formatDelay(delayMs)}", shell = null, delayMs = delayMs)

        fun formatDelay(delayMs: Long): String = when {
            delayMs % 1000L == 0L -> "${delayMs / 1000} s"
            else -> "${delayMs / 1000.0} s"
        }
    }
}

/** Una secuencia con nombre. */
data class Scene(
    val id: String,
    val name: String,
    val steps: List<SceneStep>,
) {
    val totalDurationMs: Long get() = steps.sumOf { it.delayMs }
}

/**
 * Las escenas de fabrica: las tres de la seccion 7 de la especificacion mas las que
 * salieron de como se usa el salon de verdad.
 *
 * No hay ninguna atada a una hora ("apagar a las 23:30" y demas) a proposito: el uso
 * aqui es a ratos sueltos y sin patron fijo, asi que una escena por reloj se
 * dispararia casi siempre en mal momento. Lo que si cambia mucho es *quien* esta en el
 * salon, y de ahi salen las tres ultimas.
 */
object SceneLibrary {

    fun defaults(): List<Scene> = listOf(
        Scene(
            id = "cine",
            name = "Modo cine",
            steps = listOf(
                SceneStep.of(PressKey(TvKey.POWER), delayMs = 5_000),
                // El paquete de Netflix va escrito aqui solo como punto de partida:
                // es una escena editable y la pantalla de Apps da el paquete real.
                SceneStep.of(LaunchApp("com.netflix.ninja"), delayMs = 4_000),
                SceneStep.of(SetBrightness(60)),
                SceneStep.of(SetVolume(8)),
            ),
        ),
        Scene(
            id = "musica",
            name = "Modo musica",
            steps = listOf(
                SceneStep.of(PressKey(TvKey.POWER), delayMs = 5_000),
                SceneStep.of(LaunchApp("com.spotify.tv.android"), delayMs = 4_000),
                // Ojo: en muchas builds de Google TV KEYCODE_SLEEP suspende el
                // aparato entero y corta tambien el audio. Si te pasa, cambia este
                // paso por bajar el brillo a cero.
                SceneStep.of(PressKey(TvKey.SLEEP)),
            ),
        ),
        Scene(
            id = "apagar",
            name = "Apagar todo",
            steps = listOf(
                SceneStep.of(SetVolume(0), delayMs = 300),
                SceneStep.of(PressKey(TvKey.HOME), delayMs = 800),
                SceneStep.of(PressKey(TvKey.POWER)),
            ),
        ),
        Scene(
            id = "silencio",
            name = "Silencio ya",
            // Sin esperas ni encendidos: suena el timbre o alguien habla y se corta
            // el sonido en el acto. Es la unica escena pensada para ejecutarse con
            // prisa, asi que va primero lo que corta el audio.
            steps = listOf(
                SceneStep.of(PressKey(TvKey.VOLUME_MUTE)),
                SceneStep.of(PressKey(TvKey.MEDIA_PLAY_PAUSE)),
            ),
        ),
        Scene(
            id = "visita",
            name = "Llega visita",
            // Deja la TV en la pantalla de inicio, con brillo alto y volumen de
            // conversacion, para que la coja quien sea y elija sin pelearse con lo
            // que hubiera puesto antes.
            steps = listOf(
                SceneStep.of(PressKey(TvKey.WAKEUP), delayMs = 1_500),
                SceneStep.of(PressKey(TvKey.HOME), delayMs = 800),
                SceneStep.of(SetBrightness(200)),
                SceneStep.of(SetVolume(6)),
            ),
        ),
        Scene(
            id = "fondo",
            name = "Musica de fondo",
            // Para cuando hay gente y la tele es ambiente, no espectaculo: musica
            // baja y pantalla apagada sin apagar el aparato.
            steps = listOf(
                SceneStep.of(PressKey(TvKey.WAKEUP), delayMs = 1_500),
                SceneStep.of(LaunchApp("com.spotify.tv.android"), delayMs = 4_000),
                SceneStep.of(SetVolume(4)),
                SceneStep.of(SetBrightness(0)),
            ),
        ),
    )

    /**
     * Escena efimera de la pantalla de busqueda. No se persiste.
     *
     * [slowly] elige como se escribe: tecla a tecla ([TypeKeys], lo que entienden los
     * buscadores caseros de las apps de TV) o el texto entero de golpe ([TypeText], que
     * es mas rapido y admite tildes y simbolos, cuando el destino lo acepta).
     */
    fun search(query: String, target: SearchTarget, slowly: Boolean = true): Scene {
        val steps = buildList {
            when (target) {
                SearchTarget.Focused -> Unit
                SearchTarget.GoogleTv -> add(SceneStep.of(PressKey(TvKey.ASSIST), delayMs = 2_500))
                is SearchTarget.App -> add(SceneStep.of(LaunchApp(target.packageName), delayMs = 3_500))
            }
            add(SceneStep.of(if (slowly) TypeKeys(query) else TypeText(query), delayMs = 400))
            add(SceneStep.of(PressKey(TvKey.ENTER)))
        }
        return Scene(id = "busqueda", name = "Buscar $query", steps = steps)
    }
}

/** Donde escribir el texto de busqueda. */
sealed interface SearchTarget {
    /** Escribe en lo que tenga el foco ahora mismo. */
    data object Focused : SearchTarget

    /** Abre primero el buscador de Google TV con KEYCODE_ASSIST. */
    data object GoogleTv : SearchTarget

    /** Abre primero una app y luego escribe. */
    data class App(val packageName: String, val displayName: String) : SearchTarget

    val label: String
        get() = when (this) {
            Focused -> "Donde este el foco"
            GoogleTv -> "Buscador de Google TV"
            is App -> displayName
        }
}

/**
 * Serializa las escenas para el DataStore.
 *
 * Cada campo de texto viaja en base64 para no tener que escapar nada: los comandos
 * llevan comillas, espacios y hasta saltos de linea si el usuario busca algo raro,
 * y un formato con separadores a pelo se rompe el primer dia.
 *
 *     linea  := "1" ";" id ";" b64(nombre) ";" pasos
 *     pasos  := paso ("," paso)*
 *     paso   := delayMs ":" b64(etiqueta) ":" b64(shell)
 */
object SceneCodec {

    private const val VERSION = "1"

    fun encode(scenes: List<Scene>): String = scenes.joinToString("\n") { scene ->
        val steps = scene.steps.joinToString(",") { step ->
            "${step.delayMs}:${b64(step.label)}:${b64(step.shell.orEmpty())}"
        }
        "$VERSION;${scene.id};${b64(scene.name)};$steps"
    }

    /** Las lineas que no se entiendan se descartan: mejor perder una escena que todas. */
    fun decode(raw: String): List<Scene> = raw
        .lineSequence()
        .mapNotNull { decodeScene(it.trim()) }
        .toList()

    private fun decodeScene(line: String): Scene? {
        if (line.isEmpty()) return null
        val parts = line.split(';', limit = 4)
        if (parts.size < 4 || parts[0] != VERSION) return null

        val id = parts[1].takeIf { it.isNotBlank() } ?: return null
        val name = unb64(parts[2]) ?: return null
        val steps = parts[3]
            .split(',')
            .filter { it.isNotBlank() }
            .map { decodeStep(it) ?: return null }

        return Scene(id = id, name = name, steps = steps)
    }

    private fun decodeStep(raw: String): SceneStep? {
        val parts = raw.split(':')
        if (parts.size != 3) return null
        val delay = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val label = unb64(parts[1]) ?: return null
        val shell = unb64(parts[2]) ?: return null
        return SceneStep(
            label = label,
            shell = shell.ifEmpty { null },
            delayMs = delay,
        )
    }

    private fun b64(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun unb64(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }.getOrNull()
}
