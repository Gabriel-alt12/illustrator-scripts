package com.gabriel.tvmando.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La deteccion dinamica de apps depende por completo de parsear bien la salida del
 * shell de la TV, que es texto suelto sin formato estable. Estos tests usan salidas
 * reales de `pm list packages -3` y de `dumpsys activity activities`.
 */
class TvAppTest {

    @Test
    fun `parsea la salida de pm list packages`() {
        val salida = """
            package:com.netflix.mediaclient
            package:com.spotify.tv.android
            package:com.ejemplo.rara
        """.trimIndent()

        val apps = AppCatalog.parseInstalledPackages(salida)

        assertEquals(3, apps.size)
        // Netflix va primero porque encabeza el catalogo, la desconocida al final.
        assertEquals("Netflix", apps[0].displayName)
        assertEquals("Spotify", apps[1].displayName)
        assertEquals("Rara", apps[2].displayName)
        assertTrue(apps[0].isKnown)
        assertFalse(apps[2].isKnown)
    }

    @Test
    fun `tolera retornos de carro, lineas vacias y duplicados`() {
        val salida = "package:com.netflix.mediaclient\r\n\r\npackage:com.netflix.mediaclient\r\n"
        assertEquals(
            listOf("com.netflix.mediaclient"),
            AppCatalog.parseInstalledPackages(salida).map { it.packageName },
        )
    }

    @Test
    fun `tolera el formato con ruta de pm list packages -f`() {
        val salida = "package:/data/app/~~ab==/base.apk=com.disney.disneyplus"
        val apps = AppCatalog.parseInstalledPackages(salida)
        assertEquals(listOf("com.disney.disneyplus"), apps.map { it.packageName })
        assertEquals("Disney+", apps.single().displayName)
    }

    @Test
    fun `descarta lineas que no son paquetes`() {
        val salida = """
            Error: Unknown option -3
            package:com.netflix.mediaclient
        """.trimIndent()
        assertEquals(
            listOf("com.netflix.mediaclient"),
            AppCatalog.parseInstalledPackages(salida).map { it.packageName },
        )
    }

    @Test
    fun `deduce un nombre razonable para paquetes desconocidos`() {
        assertEquals("Twitch", AppCatalog.describe("tv.twitch.android.app").displayName)
        assertEquals("Coolapp", AppCatalog.describe("com.ejemplo.coolapp").displayName)
        assertEquals("Atresplayer", AppCatalog.describe("es.otra.atresplayer.tv").displayName)
    }

    @Test
    fun `saca el paquete en primer plano del dumpsys`() {
        val salida =
            "  mResumedActivity: ActivityRecord{7f3a2b1 u0 com.netflix.mediaclient/.ui.MainActivity t42}"
        assertEquals("com.netflix.mediaclient", AppCatalog.parseForegroundPackage(salida))
    }

    @Test
    fun `devuelve null si el dumpsys no trae actividad`() {
        assertNull(AppCatalog.parseForegroundPackage(""))
        assertNull(AppCatalog.parseForegroundPackage("mResumedActivity: null"))
    }
}
