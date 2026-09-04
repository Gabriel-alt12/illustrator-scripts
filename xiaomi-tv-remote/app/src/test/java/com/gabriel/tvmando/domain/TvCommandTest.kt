package com.gabriel.tvmando.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `lanzar una app pide las dos categorias de lanzador`() {
        // Sin LEANBACK_LAUNCHER, monkey no encuentra actividad en las apps que solo
        // existen para television, que son justo las que mas se abren desde aqui.
        assertEquals(
            "monkey -p 'com.netflix.mediaclient' " +
                "-c android.intent.category.LEANBACK_LAUNCHER " +
                "-c android.intent.category.LAUNCHER 1",
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
        // La consulta del lanzador pregunta por las dos categorias: Google TV usa
        // LEANBACK_LAUNCHER, pero una app de movil por sideload solo trae LAUNCHER.
        assertTrue(TvQuery.LAUNCHER_ACTIVITIES.shell.contains("LEANBACK_LAUNCHER"))
        assertTrue(TvQuery.LAUNCHER_ACTIVITIES.shell.contains("category.LAUNCHER"))
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

    @Test
    fun `escribir tecla a tecla manda un keyevent por letra en una sola llamada`() {
        assertEquals(
            "input keyevent KEYCODE_H KEYCODE_O KEYCODE_L KEYCODE_A",
            TypeKeys("hola").shell,
        )
    }

    @Test
    fun `al teclear se quitan tildes y se ignoran mayusculas`() {
        // Los buscadores de las teles no distinguen, y no hay keycode para "n con ene".
        assertEquals(
            "input keyevent KEYCODE_E KEYCODE_L KEYCODE_SPACE KEYCODE_S KEYCODE_E " +
                "KEYCODE_N KEYCODE_O KEYCODE_R",
            TypeKeys("El Señor").shell,
        )
    }

    @Test
    fun `los digitos y el espacio tienen su tecla`() {
        assertEquals("input keyevent KEYCODE_2 KEYCODE_SPACE KEYCODE_A", TypeKeys("2 a").shell)
    }

    @Test
    fun `lo que no se puede teclear se tira en vez de mandarse como basura`() {
        assertEquals(listOf("KEYCODE_O", "KEYCODE_K"), TypeKeys("¿o.k?!").keycodes)
    }

    @Test
    fun `sin nada que teclear no se manda un keyevent vacio`() {
        // "input keyevent" a secas da error en la TV: mejor un comando que no hace nada.
        assertEquals("true", TypeKeys("!!!").shell)
        assertEquals("true", TypeKeys("").shell)
    }

    @Test
    fun `la captura se pide en base64 para no traer binario por el shell`() {
        assertEquals("screencap -p | base64", TvQuery.SCREENSHOT.shell)
    }

    @Test
    fun `la captura se decodifica aunque venga partida en lineas`() {
        // "base64" de la TV corta la salida cada pocas decenas de caracteres.
        val partido = "aG9sYSBt\ndW5kbyBl\nbiBiYXNlNjQ=\n"
        assertEquals("hola mundo en base64", String(decodeScreenshot(partido)!!))
    }

    @Test
    fun `un error del shell en vez de una imagen no revienta`() {
        assertNull(decodeScreenshot("screencap: permission denied"))
        assertNull(decodeScreenshot(""))
        assertNull(decodeScreenshot("   "))
    }

    @Test
    fun `lo que no es un PNG se descarta aunque el base64 lo acepte`() {
        // El decodificador MIME ignora lo que no es base64 en vez de fallar, asi que
        // un mensaje de error del shell sale como bytes sueltos: sin comprobar la
        // firma, el dialogo se quedaria mudo en vez de contar lo que dijo la TV.
        assertNull(decodeScreenshot("/system/bin/sh: base64: not found"))
        assertNull(decodeScreenshot("Error: Activity not started"))
    }
}
