package com.gabriel.tvmando.adb

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.math.BigInteger
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CountDownLatch
import javax.crypto.Cipher
import kotlin.concurrent.thread

/**
 * adbd de mentira: habla el mismo protocolo que la TV para poder verificar el
 * cliente sin tener un Google TV delante.
 *
 * Verifica de forma INDEPENDIENTE lo que manda el cliente: la firma se comprueba
 * deshaciendo el relleno PKCS#1 con Cipher (que es lo que hace RSA_verify de
 * OpenSSL en adbd), no reutilizando el mismo primitivo que usa el cliente.
 */
class FakeAdbd(
    private val knowsKey: Boolean = false,
    private val requireTls: Boolean = false,
    private val shell: (String) -> String = { "" },
) : Closeable {

    private val server = ServerSocket(0)
    val port: Int get() = server.localPort

    val findings = mutableListOf<String>()
    val servicesRequested = mutableListOf<String>()
    private val started = CountDownLatch(1)

    private val digestInfoSha1 = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
        0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
    )

    fun start() {
        thread(isDaemon = true, name = "fake-adbd") {
            started.countDown()
            try {
                server.accept().use { serve(it) }
            } catch (t: Throwable) {
                if (!server.isClosed) findings += "adbd falso murio: $t"
            }
        }
        started.await()
    }

    private fun serve(socket: Socket) {
        socket.tcpNoDelay = true
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())

        // 1. CNXN del cliente
        val connect = input.readMessage()
        check(connect.command == AdbProtocol.CMD_CNXN) { "esperaba CNXN, llego $connect" }
        if (connect.arg0 != 0x01000000) findings += "version inesperada: 0x%08x".format(connect.arg0)
        if (!connect.text().startsWith("host::")) findings += "banner inesperado: ${connect.text()}"

        if (requireTls) {
            output.writeMessage(AdbMessage(AdbProtocol.CMD_STLS, 0x01000000, 0))
            return
        }

        // 2. AUTH TOKEN
        val token = ByteArray(20).also { SecureRandom().nextBytes(it) }
        output.writeMessage(AdbMessage(AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_TOKEN, 0, token))

        // 3. El cliente firma
        val signed = input.readMessage()
        check(signed.command == AdbProtocol.CMD_AUTH) { "esperaba AUTH, llego $signed" }
        if (signed.arg0 != AdbProtocol.AUTH_SIGNATURE) findings += "esperaba AUTH_SIGNATURE, llego arg0=${signed.arg0}"
        val signature = signed.payload
        if (signature.size != 256) findings += "firma de ${signature.size} bytes, esperaba 256"

        if (!knowsKey) {
            // 4. Clave desconocida: repetimos el token y esperamos la clave publica.
            output.writeMessage(AdbMessage(AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_TOKEN, 0, token))
            val keyMessage = input.readMessage()
            check(keyMessage.command == AdbProtocol.CMD_AUTH) { "esperaba AUTH, llego $keyMessage" }
            if (keyMessage.arg0 != AdbProtocol.AUTH_RSAPUBLICKEY) {
                findings += "esperaba AUTH_RSAPUBLICKEY, llego arg0=${keyMessage.arg0}"
            }
            verifyPublicKey(keyMessage.payload, token, signature)
        }

        // 5. Conectados
        output.writeMessage(
            AdbMessage(
                AdbProtocol.CMD_CNXN,
                0x01000000,
                256 * 1024,
                AdbMessage.nullTerminated("device::ro.product.name=xiaomi_tv;ro.product.model=Xiaomi TV S Mini LED 55"),
            )
        )

        // 6. Bucle de streams
        var nextRemoteId = 100
        while (true) {
            val message = try { input.readMessage() } catch (e: Exception) { return }
            when (message.command) {
                AdbProtocol.CMD_OPEN -> {
                    val service = message.text()
                    servicesRequested += service
                    val localId = message.arg0
                    val remoteId = nextRemoteId++
                    output.writeMessage(AdbMessage(AdbProtocol.CMD_OKAY, remoteId, localId))

                    val result = shell(service.removePrefix("shell:"))
                    if (result.isNotEmpty()) {
                        // Partimos en dos trozos para ejercitar el control de flujo.
                        val bytes = result.toByteArray()
                        val half = (bytes.size + 1) / 2
                        for (part in listOf(bytes.copyOfRange(0, half), bytes.copyOfRange(half, bytes.size))) {
                            if (part.isEmpty()) continue
                            output.writeMessage(AdbMessage(AdbProtocol.CMD_WRTE, remoteId, localId, part))
                            val ack = input.readMessage()
                            if (ack.command != AdbProtocol.CMD_OKAY) {
                                findings += "el cliente no confirmo el WRTE: $ack"
                                return
                            }
                        }
                    }
                    output.writeMessage(AdbMessage(AdbProtocol.CMD_CLSE, remoteId, localId))
                }

                AdbProtocol.CMD_CLSE -> Unit
                AdbProtocol.CMD_OKAY -> Unit
                else -> findings += "mensaje inesperado del cliente: $message"
            }
        }
    }

    /**
     * Comprueba la struct RSAPublicKey de AOSP campo a campo y valida la firma
     * deshaciendo el relleno PKCS#1, igual que RSA_verify(NID_sha1, ...).
     */
    private fun verifyPublicKey(payload: ByteArray, token: ByteArray, signature: ByteArray) {
        val text = String(payload, Charsets.US_ASCII)
        if (!text.endsWith('\u0000')) findings += "la clave publica no acaba en nulo"
        val body = text.trimEnd('\u0000')
        val parts = body.split(" ")
        if (parts.size != 2) findings += "esperaba '<base64> <identidad>', llego: $body"
        if (parts.getOrNull(1).isNullOrBlank()) findings += "falta la identidad tras la clave"

        val raw = Base64.getDecoder().decode(parts[0])
        if (raw.size != 524) findings += "struct de ${raw.size} bytes, esperaba 524"

        val buffer = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val words = buffer.int
        val n0inv = buffer.int.toLong() and 0xFFFFFFFFL
        val modulusLe = ByteArray(256).also { buffer.get(it) }
        val rrLe = ByteArray(256).also { buffer.get(it) }
        val exponent = buffer.int

        if (words != 64) findings += "modulus_size_words=$words, esperaba 64"
        if (exponent != 65537) findings += "exponente=$exponent, esperaba 65537"

        val modulus = BigInteger(1, modulusLe.reversedArray())
        val rr = BigInteger(1, rrLe.reversedArray())

        if (modulus.bitLength() != 2048) findings += "modulo de ${modulus.bitLength()} bits"

        // n0inv debe cumplir: n0inv * n[0] == -1 (mod 2^32)
        val r32 = BigInteger.ZERO.setBit(32)
        val expectedN0inv = r32.subtract(modulus.mod(r32).modInverse(r32))
        if (expectedN0inv.toLong() != n0inv) findings += "n0inv incorrecto"
        val product = BigInteger.valueOf(n0inv).multiply(modulus.mod(r32)).mod(r32)
        if (product != r32.subtract(BigInteger.ONE)) findings += "n0inv no cumple n0inv*n[0] = -1 mod 2^32"

        // rr debe ser r^2 mod n con r = 2^2048
        val expectedRr = BigInteger.ZERO.setBit(2048).modPow(BigInteger.valueOf(2), modulus)
        if (rr != expectedRr) findings += "rr incorrecto"

        // La firma, deshaciendo PKCS#1 con la clave publica reconstruida.
        val publicKey = java.security.KeyFactory.getInstance("RSA").generatePublic(
            java.security.spec.RSAPublicKeySpec(modulus, BigInteger.valueOf(exponent.toLong()))
        )
        val recovered = try {
            Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, publicKey)
                doFinal(signature)
            }
        } catch (t: Throwable) {
            findings += "no se pudo deshacer el relleno de la firma: $t"
            return
        }
        if (!recovered.contentEquals(digestInfoSha1 + token)) {
            findings += "la firma no corresponde a DigestInfo(SHA1) || token"
        }
    }

    override fun close() {
        runCatching { server.close() }
    }
}
