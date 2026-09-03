package com.gabriel.tvmando.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los ids de [QuickCommand] viajan dentro de PendingIntents que el sistema guarda
 * y reconstruye despues de matar la app, incluso tras actualizarla. Si un id cambia
 * de nombre, los widgets ya colocados dejan de hacer nada en silencio: de ahi que
 * esten fijados en un test.
 */
class QuickCommandTest {

    @Test
    fun `los ids son estables`() {
        assertEquals(
            listOf("power", "volume_down", "volume_up", "mute", "home", "play_pause"),
            QuickCommand.entries.map { it.id },
        )
    }

    @Test
    fun `cada id resuelve a su comando`() {
        QuickCommand.entries.forEach { quick ->
            assertEquals(quick, QuickCommand.fromId(quick.id))
        }
        assertNull(QuickCommand.fromId("no-existe"))
        assertNull(QuickCommand.fromId(null))
    }

    @Test
    fun `los comandos son los keyevents del catalogo`() {
        assertEquals("input keyevent KEYCODE_POWER", QuickCommand.POWER.command.shell)
        assertEquals("input keyevent KEYCODE_VOLUME_MUTE", QuickCommand.MUTE.command.shell)
        assertEquals("input keyevent KEYCODE_HOME", QuickCommand.HOME.command.shell)
    }

    @Test
    fun `el widget tiene cuatro botones y la notificacion cinco`() {
        // Los layouts tienen ese numero exacto de ImageView cableados por indice.
        assertEquals(4, QuickCommand.widgetCommands.size)
        assertEquals(5, QuickCommand.notificationCommands.size)
        assertTrue(QuickCommand.widgetCommands.distinct().size == 4)
        assertTrue(QuickCommand.notificationCommands.distinct().size == 5)
    }
}
