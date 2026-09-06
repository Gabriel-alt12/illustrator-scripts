package com.gabriel.tvmando.domain

/** Si el panel esta encendido, segun `dumpsys power`. */
enum class PowerState { AWAKE, ASLEEP, UNKNOWN }

/** Volumen absoluto del canal multimedia, tal como lo cuenta la TV. */
data class VolumeLevel(val current: Int, val max: Int) {
    val fraction: Float get() = if (max <= 0) 0f else (current.toFloat() / max).coerceIn(0f, 1f)
}

/** Lo que la TV dice estar reproduciendo, sacado de su sesion multimedia activa. */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val subtitle: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long? = null,
)

/**
 * Estado de la TV en una sola consulta: encendida o en reposo, volumen exacto y que
 * suena. Las tres cosas salen de `dumpsys` y de `cmd media_session`, que no todas las
 * builds traen, asi que cada parte puede faltar sin que fallen las demas.
 */
data class TvStatus(
    val power: PowerState = PowerState.UNKNOWN,
    val volume: VolumeLevel? = null,
    val nowPlaying: NowPlaying? = null,
    /** Si la TV entiende la consulta de sesiones multimedia, suene algo o no. */
    val mediaAvailable: Boolean = false,
) {
    companion object {

        /**
         * Interpreta la salida de [TvQuery.STATUS], que lleva tres tramos separados por
         * marcas `#power`, `#volume` y `#media`. Lo que no case con lo esperado se
         * queda en "desconocido": esta consulta se hace cada pocos segundos y nunca
         * debe tumbar nada.
         */
        fun parse(raw: String): TvStatus {
            val sections = splitSections(raw)
            val media = sections["media"].orEmpty()
            return TvStatus(
                power = parsePower(sections["power"].orEmpty()),
                volume = parseVolume(sections["volume"].orEmpty()),
                nowPlaying = parseNowPlaying(media),
                mediaAvailable = media.contains("Sessions Stack") || media.contains("package="),
            )
        }

        private fun splitSections(raw: String): Map<String, String> {
            val sections = mutableMapOf<String, StringBuilder>()
            var current: StringBuilder? = null
            for (line in raw.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#") && trimmed.length > 1 && trimmed.substring(1).all { it.isLetter() }) {
                    current = sections.getOrPut(trimmed.substring(1)) { StringBuilder() }
                } else {
                    current?.appendLine(line)
                }
            }
            return sections.mapValues { it.value.toString() }
        }

        private fun parsePower(text: String): PowerState {
            WAKEFULNESS.find(text)?.groupValues?.get(1)?.let { wake ->
                return if (wake == "Awake" || wake == "Dreaming") PowerState.AWAKE else PowerState.ASLEEP
            }
            DISPLAY_STATE.find(text)?.groupValues?.get(1)?.let { display ->
                return if (display.startsWith("ON")) PowerState.AWAKE else PowerState.ASLEEP
            }
            return PowerState.UNKNOWN
        }

        private fun parseVolume(text: String): VolumeLevel? {
            val match = VOLUME_LINE.find(text) ?: return null
            val current = match.groupValues[1].toIntOrNull() ?: return null
            val max = match.groupValues[3].toIntOrNull() ?: return null
            if (max <= 0) return null
            return VolumeLevel(current = current.coerceIn(0, max), max = max)
        }

        /**
         * `dumpsys media_session` lista una sesion por app con su estado y sus metadatos.
         * El formato de la cabecera de cada sesion cambia entre versiones de Android,
         * asi que se trocea por la linea `package=`, que es la que no ha cambiado.
         */
        private fun parseNowPlaying(text: String): NowPlaying? {
            val sessions = mutableListOf<Session>()
            var current: Session? = null
            for (line in text.lineSequence()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("package=") -> {
                        current = Session(packageName = trimmed.removePrefix("package=").trim())
                        sessions += current
                    }

                    current == null -> Unit

                    trimmed.startsWith("active=") ->
                        current.active = trimmed.removePrefix("active=").trim() == "true"

                    trimmed.startsWith("state=") -> {
                        current.state = STATE_NUMBER.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
                        current.positionMs = POSITION.find(trimmed)?.groupValues?.get(1)?.toLongOrNull()
                    }

                    trimmed.startsWith("metadata:") -> {
                        val description = trimmed.substringAfter("description=", "").trim()
                        if (description.isNotEmpty() && description != "null") {
                            val parts = description.split(", ")
                            current.title = parts.getOrNull(0)?.clean()
                            current.subtitle = parts.getOrNull(1)?.clean()
                        }
                    }
                }
            }

            val candidates = sessions.filter { it.title != null && it.packageName.isNotBlank() && it.state in LIVE_STATES }
            val chosen = candidates.firstOrNull { it.active && it.state == STATE_PLAYING }
                ?: candidates.firstOrNull { it.active }
                ?: candidates.firstOrNull { it.state == STATE_PLAYING }
                ?: return null

            return NowPlaying(
                packageName = chosen.packageName,
                title = chosen.title!!,
                subtitle = chosen.subtitle,
                isPlaying = chosen.state == STATE_PLAYING || chosen.state == STATE_BUFFERING,
                positionMs = chosen.positionMs?.takeIf { it >= 0 },
            )
        }

        private fun String.clean(): String? = trim().takeIf { it.isNotEmpty() && it != "null" }

        private class Session(val packageName: String) {
            var active = false
            var state: Int? = null
            var positionMs: Long? = null
            var title: String? = null
            var subtitle: String? = null
        }

        private const val STATE_PAUSED = 2
        private const val STATE_PLAYING = 3
        private const val STATE_BUFFERING = 6
        private val LIVE_STATES = setOf(STATE_PAUSED, STATE_PLAYING, STATE_BUFFERING)

        private val WAKEFULNESS = Regex("""mWakefulness=(\w+)""")
        private val DISPLAY_STATE = Regex("""Display Power: state=(\w+)""")
        private val VOLUME_LINE = Regex("""volume is (\d+) in range \[(\d+)\.\.(\d+)\]""")

        /**
         * Android 13+ escribe `state=STATE_PLAYING(3)` y las versiones anteriores
         * `state=3`; la primera aparicion de `state=` en la linea es la del envoltorio
         * (`state=PlaybackState {`) y no lleva numero, asi que el patron la salta solo.
         */
        private val STATE_NUMBER = Regex("""state=(?:[A-Z_]+\()?(\d+)""")
        private val POSITION = Regex("""position=(-?\d+)""")
    }
}
