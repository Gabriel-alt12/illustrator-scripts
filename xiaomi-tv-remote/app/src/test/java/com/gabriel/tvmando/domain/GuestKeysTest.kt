package com.gabriel.tvmando.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El mando de invitados recibe la tecla por su nombre dentro de una URL y la busca
 * contra [TvKey.GUEST]. El servidor vive en la capa de Android y no se puede levantar
 * aqui, pero lo que hay que blindar es esta decision: que un nombre suelto llegado de
 * fuera no pueda convertirse en cualquier tecla del catalogo.
 */
class GuestKeysTest {

    /** La busqueda que hace el servidor con lo que venga en la URL. */
    private fun resolver(nombre: String): TvKey? = TvKey.GUEST.firstOrNull { it.name == nombre }

    @Test
    fun `una visita no puede apagar ni dormir la television`() {
        assertFalse(TvKey.GUEST.contains(TvKey.POWER))
        assertFalse(TvKey.GUEST.contains(TvKey.SLEEP))
        assertFalse(TvKey.GUEST.contains(TvKey.WAKEUP))
        assertNull(resolver("POWER"))
    }

    @Test
    fun `una visita si puede navegar, pausar y tocar el volumen`() {
        assertEquals(TvKey.DPAD_CENTER, resolver("DPAD_CENTER"))
        assertEquals(TvKey.BACK, resolver("BACK"))
        assertEquals(TvKey.VOLUME_UP, resolver("VOLUME_UP"))
        assertEquals(TvKey.MEDIA_PLAY_PAUSE, resolver("MEDIA_PLAY_PAUSE"))
    }

    @Test
    fun `un nombre que no casa exactamente no resuelve a nada`() {
        assertNull(resolver(""))
        assertNull(resolver("dpad_up"))
        assertNull(resolver("DPAD_UP; rm -rf /"))
        assertNull(resolver("KEYCODE_DPAD_UP"))
    }

    @Test
    fun `la tecla resuelta genera el keyevent de siempre, sin nada colado`() {
        // Aunque el nombre venga de fuera, el shell sale del catalogo tipado: no hay
        // forma de que un texto de la URL acabe dentro de la linea que ejecuta la TV.
        val key = resolver("VOLUME_UP")
        assertEquals("input keyevent KEYCODE_VOLUME_UP", PressKey(key!!).shell)
    }

    @Test
    fun `la lista de invitado no repite teclas`() {
        assertEquals(TvKey.GUEST.size, TvKey.GUEST.distinct().size)
    }
}
