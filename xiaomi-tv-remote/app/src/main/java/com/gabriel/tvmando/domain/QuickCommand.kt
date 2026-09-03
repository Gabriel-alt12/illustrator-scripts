package com.gabriel.tvmando.domain

/**
 * Los comandos que se pueden disparar desde fuera de la app: widget de la pantalla
 * de inicio, tiles de ajustes rapidos y notificacion persistente.
 *
 * Todos esos sitios viven en procesos y componentes distintos, asi que lo unico que
 * viaja entre ellos es el [id]: una cadena estable que sobrevive a que el sistema
 * mate la app y reconstruya el widget. De ahi que no se serialice el TvCommand.
 */
enum class QuickCommand(
    val id: String,
    val label: String,
    val command: TvCommand,
) {
    POWER("power", "Encendido", PressKey(TvKey.POWER)),
    VOLUME_DOWN("volume_down", "Bajar volumen", PressKey(TvKey.VOLUME_DOWN)),
    VOLUME_UP("volume_up", "Subir volumen", PressKey(TvKey.VOLUME_UP)),
    MUTE("mute", "Silenciar", PressKey(TvKey.VOLUME_MUTE)),
    HOME("home", "Inicio", PressKey(TvKey.HOME)),
    PLAY_PAUSE("play_pause", "Play / Pausa", PressKey(TvKey.MEDIA_PLAY_PAUSE));

    companion object {
        fun fromId(id: String?): QuickCommand? = entries.firstOrNull { it.id == id }

        /** Los cuatro del widget, en el orden en que se pintan. */
        val widgetCommands: List<QuickCommand> = listOf(POWER, VOLUME_DOWN, VOLUME_UP, MUTE)

        /** Los cinco de la notificacion persistente. */
        val notificationCommands: List<QuickCommand> =
            listOf(POWER, VOLUME_DOWN, MUTE, VOLUME_UP, HOME)
    }
}
