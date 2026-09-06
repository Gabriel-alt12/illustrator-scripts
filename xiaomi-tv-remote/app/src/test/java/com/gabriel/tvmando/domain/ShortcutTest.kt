package com.gabriel.tvmando.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los accesos directos llegan por "Compartir" desde apps que adornan el texto cada
 * una a su manera, y se guardan en el DataStore: lo que se pierda aqui es una serie
 * que hay que volver a buscar.
 */
class ShortcutTest {

    @Test
    fun `ida y vuelta por el codec, con lo feo dentro`() {
        val original = listOf(
            Shortcut("a1", "Reacher", "com.netflix.ninja", "https://www.netflix.com/title/80234304?s=a&trkid=1", autoOk = true, createdAt = 1_700_000_000_000),
            Shortcut("a2", "Rock 'n' roll; con \"comillas\", y punto.", null, null, autoOk = false, createdAt = 5),
            Shortcut("a3", "Loki", "com.disney.disneyplus", "https://www.disneyplus.com/series/loki/6pARMvILBGzF", createdAt = 0),
        )
        assertEquals(original, ShortcutCodec.decode(ShortcutCodec.encode(original)))
    }

    @Test
    fun `una linea corrupta no se lleva a las demas`() {
        val good = ShortcutCodec.encode(listOf(Shortcut("ok", "Reacher", null, null)))
        val mixed = "basura\n$good\n2;futuro;x;y;z;1;0\n1;sinTitulo;;;;0;0\n"

        val decoded = ShortcutCodec.decode(mixed)

        assertEquals(1, decoded.size)
        assertEquals("Reacher", decoded.single().title)
        assertEquals(emptyList<Shortcut>(), ShortcutCodec.decode(""))
    }

    @Test
    fun `entiende lo que comparte Netflix`() {
        val link = SharedLinkParser.parse(
            "Mira «Reacher» en Netflix https://www.netflix.com/title/80234304?s=a&trkid=13747225",
        )!!

        assertEquals("Reacher", link.title)
        assertEquals("https://www.netflix.com/title/80234304?s=a&trkid=13747225", link.url)
        assertEquals("com.netflix.ninja", link.packageName)
    }

    @Test
    fun `entiende lo que comparten YouTube y Prime Video`() {
        val video = SharedLinkParser.parse("Como hacer pan en casa\nhttps://youtu.be/abc123XYZ")!!
        assertEquals("Como hacer pan en casa", video.title)
        assertEquals("com.google.android.youtube.tv", video.packageName)

        val serie = SharedLinkParser.parse("Reacher - Temporada 2 https://www.primevideo.com/detail/0ABC/ref=atv_dp_share")!!
        assertEquals("Reacher - Temporada 2", serie.title)
        assertEquals("com.amazon.avod.thirdpartyclient", serie.packageName)

        val amazon = SharedLinkParser.parse("https://www.amazon.es/gp/video/detail/B0ABC/ref=share")!!
        assertEquals("com.amazon.avod.thirdpartyclient", amazon.packageName)
        assertEquals("Prime Video", amazon.title)
    }

    @Test
    fun `el asunto manda sobre el texto y un dominio desconocido no tiene app`() {
        val loki = SharedLinkParser.parse("https://www.disneyplus.com/series/loki/6pARMvILBGzF", subject = "Loki")!!
        assertEquals("Loki", loki.title)
        assertEquals("com.disney.disneyplus", loki.packageName)

        val raro = SharedLinkParser.parse("Un articulo https://ejemplo.org/cosa.")!!
        assertEquals("Un articulo", raro.title)
        assertEquals("https://ejemplo.org/cosa", raro.url)
        assertNull(raro.packageName)
    }

    @Test
    fun `sin enlace no hay acceso directo`() {
        assertNull(SharedLinkParser.parse("Reacher"))
        assertNull(SharedLinkParser.parse(null))
        assertNull(SharedLinkParser.parse("", subject = "Loki"))
    }

    @Test
    fun `abrir un enlace va por am start dentro de la app que toque`() {
        assertEquals(
            "am start -a android.intent.action.VIEW -d 'https://www.netflix.com/title/1' 'com.netflix.ninja'",
            OpenLink("https://www.netflix.com/title/1", "com.netflix.ninja").shell,
        )
        assertEquals(
            "am start -a android.intent.action.VIEW -d 'https://ejemplo.org/x'",
            OpenLink("https://ejemplo.org/x", null).shell,
        )
    }

    @Test
    fun `am start avisa del fallo por la salida, no por el codigo`() {
        assertTrue(OpenLink.failed("Error: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW }"))
        assertTrue(OpenLink.failed("Error: Activity class {com.x/.Y} does not exist."))
        assertFalse(OpenLink.failed("Starting: Intent { act=android.intent.action.VIEW dat=https://www.netflix.com/... pkg=com.netflix.ninja }"))
        assertFalse(OpenLink.failed(""))
    }

    @Test
    fun `el plan B busca dentro de la app y le da a OK dos veces`() {
        val scene = SceneLibrary.playFirstResult("Reacher", "com.amazon.avod.thirdpartyclient")
        val shells = scene.steps.map { it.shell }
        assertTrue(shells.first()!!.startsWith("monkey -p 'com.amazon.avod.thirdpartyclient'"))
        assertEquals("input keyevent KEYCODE_R KEYCODE_E KEYCODE_A KEYCODE_C KEYCODE_H KEYCODE_E KEYCODE_R", shells[1])
        assertEquals("input keyevent KEYCODE_ENTER", shells[2])
        assertEquals(2, shells.count { it == "input keyevent KEYCODE_DPAD_CENTER" })
    }
}
