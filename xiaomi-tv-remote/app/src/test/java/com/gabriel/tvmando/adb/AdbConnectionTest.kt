package com.gabriel.tvmando.adb

import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el cliente ADB contra [FakeAdbd], que habla el protocolo real y comprueba
 * de forma independiente la clave publica y la firma que enviamos.
 *
 * Son tests de red sobre localhost, no unitarios puros, pero es la unica manera de
 * cubrir el handshake completo sin tener un Google TV conectado.
 */
class AdbConnectionTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keyPair = AdbKeyPair.generate("tvmando@test")

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `autoriza la primera vez y ejecuta comandos`() = runBlocking {
        val respuestas = mapOf(
            "getprop ro.product.model" to "Xiaomi TV S Mini LED 55\n",
            "input keyevent KEYCODE_POWER" to "",
        )
        FakeAdbd(knowsKey = false) { respuestas[it].orEmpty() }.use { adbd ->
            adbd.start()
            var autorizacionPedida = false

            val connection = AdbConnection.connect(
                host = LOCALHOST,
                port = adbd.port,
                keyPair = keyPair,
                scope = scope,
                onAuthorizationRequired = { autorizacionPedida = true },
            )

            assertEquals("el daemon encontro problemas", emptyList<String>(), adbd.findings)
            assertTrue("hay que avisar de que la TV pide autorizacion", autorizacionPedida)
            assertTrue(connection.banner.contains("Xiaomi TV S Mini LED 55"))
            assertEquals(
                "Xiaomi TV S Mini LED 55\n",
                connection.shell("getprop ro.product.model"),
            )
            assertEquals("", connection.shell("input keyevent KEYCODE_POWER"))
            assertTrue(connection.isConnected)
            connection.close()
        }
    }

    @Test
    fun `no pide autorizacion si la TV ya conoce la clave`() = runBlocking {
        FakeAdbd(knowsKey = true) { "ok\n" }.use { adbd ->
            adbd.start()
            var autorizacionPedida = false

            val connection = AdbConnection.connect(
                LOCALHOST, adbd.port, keyPair, scope,
                onAuthorizationRequired = { autorizacionPedida = true },
            )

            assertFalse(autorizacionPedida)
            assertEquals(emptyList<String>(), adbd.findings)
            assertTrue(connection.ping())
            connection.close()
        }
    }

    @Test
    fun `reensambla una salida repartida en varios WRTE`() = runBlocking {
        val paquetes = (1..400).joinToString("\n") { "package:com.ejemplo.app$it" } + "\n"
        FakeAdbd(knowsKey = true) { paquetes }.use { adbd ->
            adbd.start()
            val connection = AdbConnection.connect(LOCALHOST, adbd.port, keyPair, scope)
            assertEquals(paquetes, connection.shell("pm list packages -3"))
            connection.close()
        }
    }

    @Test
    fun `detecta que la TV exige depuracion inalambrica con TLS`() = runBlocking {
        FakeAdbd(requireTls = true).use { adbd ->
            adbd.start()
            val error = runCatching {
                AdbConnection.connect(LOCALHOST, adbd.port, keyPair, scope)
            }.exceptionOrNull()

            assertTrue("esperaba TlsRequired, llego $error", error is AdbException.TlsRequired)
            assertNotNull((error as AdbException).hint)
        }
    }

    @Test
    fun `informa de endpoint inalcanzable`() = runBlocking {
        val puertoCerrado = ServerSocket(0).use { it.localPort }
        val error = runCatching {
            AdbConnection.connect(LOCALHOST, puertoCerrado, keyPair, scope, connectTimeoutMs = 1500)
        }.exceptionOrNull()

        assertTrue("esperaba Unreachable, llego $error", error is AdbException.Unreachable)
    }

    private companion object {
        const val LOCALHOST = "127.0.0.1"
    }
}
