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
    fun `rescata del catalogo completo las apps conocidas que -3 esconde`() {
        // Prime Video viene preinstalada de fabrica en la Xiaomi TV: Android la cuenta
        // como app de sistema y "pm list packages -3" no la trae.
        val terceros = "package:com.spotify.tv.android"
        val catalogoCompleto = """
            package:android
            package:com.amazon.amazonvideo.livingroom
            package:com.android.settings
            package:com.spotify.tv.android
        """.trimIndent()

        val apps = AppCatalog.parseInstalledPackages(terceros, catalogoCompleto)

        val nombres = apps.map { it.displayName }
        assertTrue(nombres.contains("Prime Video"))
        assertTrue(nombres.contains("Spotify"))
        // Las desconocidas del catalogo completo (android, com.android.settings) no
        // deben colarse: inundarian la rejilla de servicios internos sin nombre.
        assertEquals(2, apps.size)
    }

    @Test
    fun `saca las apps con icono de la salida de query-activities`() {
        // Salida real abreviada de "cmd package query-activities": bloques por
        // actividad, no lineas "package:...".
        val salida = """
            Activity #0:
              priority=0 preferredOrder=0 match=0x108000
              ActivityInfo:
                name=com.netflix.ninja.MainActivity
                packageName=com.netflix.ninja
                enabled=true exported=true
            Activity #1:
              ActivityInfo:
                name=com.amazon.avod.thirdpartyclient.LauncherActivity
                packageName=com.amazon.avod.thirdpartyclient
        """.trimIndent()

        val apps = AppCatalog.parseInstalledPackages("", launcherOutput = salida)

        assertEquals(
            listOf("Netflix", "Prime Video"),
            apps.map { it.displayName },
        )
        assertTrue(apps.all { it.isKnown })
    }

    @Test
    fun `una app con icono desconocida entra igual con nombre deducido`() {
        val salida = "        packageName=com.ejemplo.cosarara"
        val apps = AppCatalog.parseInstalledPackages("", launcherOutput = salida)
        assertEquals(listOf("com.ejemplo.cosarara"), apps.map { it.packageName })
        assertEquals("Cosarara", apps.single().displayName)
        assertFalse(apps.single().isKnown)
    }

    @Test
    fun `los sufijos internos de las apps de TV no acaban como nombre`() {
        // Sin limpiar estos sufijos saldrian "Thirdpartyclient", "Ninja" y
        // "Livingroom" en vez del segmento que de verdad dice algo.
        assertEquals("Otra", AppCatalog.describe("com.otra.avod.thirdpartyclient").displayName)
        assertEquals("Rara", AppCatalog.describe("com.rara.ninja").displayName)
        assertEquals("Cosa", AppCatalog.describe("com.cosa.livingroom").displayName)
    }

    @Test
    fun `ignora la salida del lanzador si la TV no entiende el comando`() {
        val error = "Unknown command: query-activities"
        val apps = AppCatalog.parseInstalledPackages(
            "package:com.netflix.ninja",
            launcherOutput = error,
        )
        assertEquals(listOf("com.netflix.ninja"), apps.map { it.packageName })
    }

    @Test
    fun `sin segunda pasada se comporta igual que antes`() {
        val salida = "package:com.netflix.mediaclient"
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
