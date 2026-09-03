package com.gabriel.tvmando.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Los comandos acaban en el shell de la TV a traves de sh, asi que lo que importa
 * es que la linea generada sea exactamente la del catalogo de la especificacion y
 * que el texto libre vaya entrecomillado.
 */
class TvCommandTest {

    @Test
    fun `las teclas usan el keycode del catalogo`() {
        assertEquals("input keyevent KEYCODE_POWER", PressKey(TvKey.POWER).shell)
        assertEquals("input keyevent KEYCODE_VOLUME_UP", PressKey(TvKey.VOLUME_UP).shell)
        assertEquals("input keyevent KEYCODE_DPAD_CENTER", PressKey(TvKey.DPAD_CENTER).shell)
        assertEquals("input keyevent KEYCODE_MEDIA_PLAY_PAUSE", PressKey(TvKey.MEDIA_PLAY_PAUSE).shell)
    }

    @Test
    fun `los digitos de canal se traducen a su keycode`() {
        assertEquals("input keyevent KEYCODE_0", PressDigit(0).shell)
        assertEquals("input keyevent KEYCODE_9", PressDigit(9).shell)
    }

    @Test
    fun `lanzar una app usa monkey con la categoria LAUNCHER`() {
        assertEquals(
            "monkey -p 'com.netflix.mediaclient' -c android.intent.category.LAUNCHER 1",
            LaunchApp("com.netflix.mediaclient").shell,
        )
    }

    @Test
    fun `el texto de busqueda va entrecomillado`() {
        assertEquals("input text 'el senor de los anillos'", TypeText("el senor de los anillos").shell)
    }

    @Test
    fun `una comilla simple no rompe el comando`() {
        // El truco clasico de sh: cerrar, escapar la comilla, volver a abrir.
        assertEquals("""input text 'rock '\''n'\'' roll'""", TypeText("rock 'n' roll").shell)
    }

    @Test
    fun `el brillo se recorta al rango valido`() {
        assertEquals("settings put system screen_brightness 255", SetBrightness(900).shell)
        assertEquals("settings put system screen_brightness 0", SetBrightness(-10).shell)
    }

    @Test
    fun `las consultas de diagnostico coinciden con la especificacion`() {
        assertEquals("pm list packages -3", TvQuery.THIRD_PARTY_PACKAGES.shell)
        assertEquals("pm list packages", TvQuery.ALL_PACKAGES.shell)
        assertEquals("getprop ro.product.model", TvQuery.MODEL.shell)
        assertEquals(
            "dumpsys activity activities | grep mResumedActivity",
            TvQuery.CURRENT_ACTIVITY.shell,
        )
    }

    @Test
    fun `force-stop entrecomilla el paquete`() {
        assertEquals("am force-stop 'com.disney.disneyplus'", ForceStopApp("com.disney.disneyplus").shell)
    }
}
