package com.gabriel.tvmando.adb

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import javax.crypto.Cipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La codificacion de la clave publica es el punto mas facil de romper de todo el
 * cliente: es una struct de C en little-endian con dos campos derivados (n0inv y rr)
 * que adbd usa para su aritmetica de Montgomery. Si uno esta mal, la TV rechaza la
 * clave sin dar ninguna pista.
 */
class AdbKeyPairTest {

    private val keyPair = AdbKeyPair.generate("tvmando@test")

    @Test
    fun `la struct tiene la forma que espera adbd`() {
        val payload = String(keyPair.adbPublicKey(), Charsets.US_ASCII)
        val parts = payload.split(" ")
        assertEquals("esperaba '<base64> <identidad>'", 2, parts.size)
        assertEquals("tvmando@test", parts[1])

        val raw = Base64.getDecoder().decode(parts[0])
        assertEquals(524, raw.size)

        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val words = buffer.int
        val n0inv = buffer.int.toLong() and 0xFFFFFFFFL
        val modulusLe = ByteArray(256).also { buffer.get(it) }
        val rrLe = ByteArray(256).also { buffer.get(it) }
        val exponent = buffer.int

        assertEquals(64, words)
        assertEquals(65537, exponent)

        val modulus = BigInteger(1, modulusLe.reversedArray())
        assertEquals("el modulo debe viajar en little-endian", keyPair.publicKey.modulus, modulus)

        // n0inv * n[0] tiene que valer -1 modulo 2^32.
        val r32 = BigInteger.ZERO.setBit(32)
        val product = BigInteger.valueOf(n0inv).multiply(modulus.mod(r32)).mod(r32)
        assertEquals(r32.subtract(BigInteger.ONE), product)

        // rr tiene que ser r^2 mod n, con r = 2^2048.
        val rr = BigInteger(1, rrLe.reversedArray())
        assertEquals(
            BigInteger.ZERO.setBit(2048).modPow(BigInteger.valueOf(2), modulus),
            rr,
        )
    }

    @Test
    fun `la firma es la que valida RSA_verify con SHA1`() {
        val token = ByteArray(20) { (it * 7).toByte() }
        val signature = keyPair.signToken(token)
        assertEquals(256, signature.size)

        // Deshacemos el relleno PKCS#1 con la clave publica: debe aparecer el
        // DigestInfo de SHA-1 seguido del token, que es lo que firma adb.
        val recovered = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
            init(Cipher.DECRYPT_MODE, keyPair.publicKey)
            doFinal(signature)
        }
        val digestInfoSha1 = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        )
        assertArrayEquals(digestInfoSha1 + token, recovered)
    }

    @Test
    fun `la clave sobrevive a serializarse y volver`() {
        val recuperada = AdbKeyPair.fromEncoded(
            keyPair.encodedPrivateKey()!!,
            keyPair.publicKey.encoded,
            "tvmando@test",
        )
        assertArrayEquals(keyPair.adbPublicKey(), recuperada.adbPublicKey())

        val token = ByteArray(20) { it.toByte() }
        assertArrayEquals(keyPair.signToken(token), recuperada.signToken(token))
    }

    @Test
    fun `la huella usa el mismo formato que el dialogo de la TV`() {
        assertTrue(
            keyPair.fingerprint(),
            Regex("^([0-9A-F]{2}:){15}[0-9A-F]{2}$").matches(keyPair.fingerprint()),
        )
    }

    @Test
    fun `decodePublicKey deshace encodePublicKey`() {
        val decoded = AdbKeyPair.decodePublicKey(keyPair.adbPublicKey())
        assertEquals(keyPair.publicKey.modulus, decoded.modulus)
        assertEquals(keyPair.publicKey.publicExponent, decoded.publicExponent)
    }
}
