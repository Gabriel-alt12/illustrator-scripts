package com.gabriel.tvmando.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El modo se guarda en el DataStore como texto y se lee en el arranque, antes de
 * pintar nada: un valor raro ahi no puede tirar la app, tiene que caer en oscuro.
 */
class ThemeModeTest {

    @Test
    fun `cada modo se recupera de su propio nombre`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.from(mode.name))
        }
    }

    @Test
    fun `sin nada guardado se arranca en oscuro`() {
        assertEquals(ThemeMode.DARK, ThemeMode.from(null))
    }

    @Test
    fun `un valor desconocido cae en oscuro en vez de reventar`() {
        assertEquals(ThemeMode.DARK, ThemeMode.from("sepia"))
        assertEquals(ThemeMode.DARK, ThemeMode.from(""))
        assertEquals(ThemeMode.DARK, ThemeMode.from("light"))
    }
}
