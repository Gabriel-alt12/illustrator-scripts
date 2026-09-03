package com.gabriel.tvmando.adb

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Par de claves RSA-2048 que identifica a este movil frente a adbd.
 *
 * Es el equivalente a ~/.android/adbkey del PC: la TV guarda la clave publica al
 * aceptar el dialogo de "Permitir depuracion USB" y a partir de ahi reconoce las
 * firmas sin volver a preguntar. Por eso la clave debe persistir entre arranques.
 *
 * La clave privada puede vivir en el AndroidKeyStore (no exportable), por eso aqui
 * solo se guarda una referencia a [PrivateKey] y se firma con "NONEwithRSA": el
 * AndroidKeyStore no permite Cipher.ENCRYPT_MODE con clave privada, pero si permite
 * firmar sin digest con relleno PKCS#1 v1.5, que es exactamente lo que hace adb.
 */
class AdbKeyPair(
    private val privateKey: PrivateKey,
    val publicKey: RSAPublicKey,
    /** Sufijo legible que la TV muestra junto a la clave, al estilo usuario@equipo. */
    val identity: String,
) {

    /**
     * Firma el token de 20 bytes (SHA-1) que envia adbd en el mensaje AUTH.
     *
     * adbd hace RSA_sign(NID_sha1, token), que internamente antepone el DigestInfo
     * ASN.1 de SHA-1 y aplica relleno PKCS#1 tipo 1. "NONEwithRSA" aplica ese mismo
     * relleno sobre los bytes que le damos, asi que reproducimos el DigestInfo a mano.
     */
    fun signToken(token: ByteArray): ByteArray {
        require(token.size == TOKEN_SIZE) { "Token AUTH inesperado: ${token.size} bytes" }
        val signature = Signature.getInstance("NONEwithRSA")
        signature.initSign(privateKey)
        signature.update(SHA1_DIGEST_INFO)
        signature.update(token)
        return signature.sign()
    }

    /** Clave publica en el formato propietario de ADB, lista para el mensaje AUTH. */
    fun adbPublicKey(): ByteArray = encodePublicKey(publicKey, identity)

    /**
     * Huella que la TV muestra en el dialogo de autorizacion, con el mismo algoritmo
     * que AdbDebuggingManager de AOSP: MD5 de la estructura binaria, en hex y con ":".
     * Permite confirmar en Ajustes que el dialogo corresponde a esta app.
     */
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("MD5").digest(encodeStruct(publicKey))
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /** Serializa la clave privada para guardarla cuando no hay AndroidKeyStore. */
    fun encodedPrivateKey(): ByteArray? = privateKey.encoded

    companion object {
        const val KEY_SIZE_BITS = 2048
        const val TOKEN_SIZE = 20

        private const val MODULUS_BYTES = KEY_SIZE_BITS / 8
        private const val MODULUS_WORDS = MODULUS_BYTES / 4

        /** 4 + 4 + modulo + rr + exponente, tal cual la struct RSAPublicKey de AOSP. */
        internal const val STRUCT_SIZE = 4 + 4 + MODULUS_BYTES + MODULUS_BYTES + 4

        private val PUBLIC_EXPONENT: BigInteger = BigInteger.valueOf(65537L)

        // BigInteger.TWO es Java 9+ / API 31: no sirve con minSdk 26.
        private val TWO: BigInteger = BigInteger.valueOf(2L)

        /** DigestInfo ASN.1 de SHA-1 (RFC 8017, seccion 9.2). */
        private val SHA1_DIGEST_INFO = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        )

        fun generate(identity: String): AdbKeyPair {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(KEY_SIZE_BITS)
            val pair = generator.generateKeyPair()
            return AdbKeyPair(pair.private, pair.public as RSAPublicKey, identity)
        }

        /** Reconstruye el par a partir de los bytes PKCS#8 / X.509 persistidos. */
        fun fromEncoded(pkcs8Private: ByteArray, x509Public: ByteArray, identity: String): AdbKeyPair {
            val factory = KeyFactory.getInstance("RSA")
            val private = factory.generatePrivate(PKCS8EncodedKeySpec(pkcs8Private))
            val public = factory.generatePublic(X509EncodedKeySpec(x509Public)) as RSAPublicKey
            return AdbKeyPair(private, public, identity)
        }

        /**
         * Codifica la clave publica en el formato que espera adbd:
         *
         *     struct RSAPublicKey {
         *         uint32_t modulus_size_words;  // 64
         *         uint32_t n0inv;               // -1 / n[0] mod 2^32
         *         uint8_t  modulus[256];        // little-endian
         *         uint8_t  rr[256];             // r^2 mod n, little-endian, r = 2^2048
         *         uint32_t exponent;            // 65537
         *     }
         *
         * ...en base64 y con " identidad" pegado detras.
         * Referencia: AOSP libcrypto_utils/android_pubkey.c
         */
        fun encodePublicKey(key: RSAPublicKey, identity: String): ByteArray {
            val base64 = Base64.getEncoder().encodeToString(encodeStruct(key))
            return "$base64 $identity".toByteArray(Charsets.US_ASCII)
        }

        internal fun encodeStruct(key: RSAPublicKey): ByteArray {
            val modulus = key.modulus
            require(modulus.bitLength() == KEY_SIZE_BITS) {
                "adbd solo acepta claves de $KEY_SIZE_BITS bits"
            }
            require(key.publicExponent == PUBLIC_EXPONENT) {
                "adbd solo acepta exponente 65537"
            }

            // n0inv = 2^32 - (n mod 2^32)^-1 mod 2^32
            val r32 = BigInteger.ZERO.setBit(32)
            val n0inv = r32.subtract(modulus.mod(r32).modInverse(r32))

            // rr = r^2 mod n, con r = 2^2048
            val rr = BigInteger.ZERO.setBit(KEY_SIZE_BITS).modPow(TWO, modulus)

            return ByteBuffer.allocate(STRUCT_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(MODULUS_WORDS)
                putInt(n0inv.toInt())
                put(toLittleEndian(modulus, MODULUS_BYTES))
                put(toLittleEndian(rr, MODULUS_BYTES))
                putInt(key.publicExponent.toInt())
            }.array()
        }

        /** Inverso de [encodePublicKey]. Se usa en los tests y para diagnostico. */
        fun decodePublicKey(encoded: ByteArray): RSAPublicKey {
            // El payload real es "<base64> <identidad>" y puede acabar en un nulo.
            val base64 = String(encoded, Charsets.US_ASCII)
                .takeWhile { it != ' ' && it != '\u0000' }
            val raw = Base64.getDecoder().decode(base64)
            require(raw.size == STRUCT_SIZE) { "Estructura de clave publica invalida" }

            val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            buffer.int // modulus_size_words
            buffer.int // n0inv
            val modulusLe = ByteArray(MODULUS_BYTES).also { buffer.get(it) }
            ByteArray(MODULUS_BYTES).also { buffer.get(it) } // rr
            val exponent = buffer.int

            val spec = RSAPublicKeySpec(
                BigInteger(1, modulusLe.reversedArray()),
                BigInteger.valueOf(exponent.toLong() and 0xFFFFFFFFL),
            )
            return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
        }

        /**
         * Pasa un entero positivo a little-endian de [size] bytes, quitando el byte de
         * signo que anade BigInteger y rellenando con ceros por arriba.
         */
        private fun toLittleEndian(value: BigInteger, size: Int): ByteArray {
            val bigEndian = value.toByteArray()
            val out = ByteArray(size)
            var src = bigEndian.size - 1
            var dst = 0
            while (src >= 0 && dst < size) {
                out[dst++] = bigEndian[src--]
            }
            while (src >= 0) {
                require(bigEndian[src--] == 0.toByte()) { "El entero no cabe en $size bytes" }
            }
            return out
        }
    }
}
