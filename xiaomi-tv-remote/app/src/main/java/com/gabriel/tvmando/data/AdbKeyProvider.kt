package com.gabriel.tvmando.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.gabriel.tvmando.adb.AdbKeyPair
import java.math.BigInteger
import java.security.KeyStore
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import javax.security.auth.x500.X500Principal
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Entrega el par de claves ADB de la app, creandolo la primera vez y reutilizandolo
 * despues. Es lo que hace que solo haya que aceptar el dialogo de la TV UNA vez.
 *
 * Estrategia en dos niveles:
 *
 *  1. AndroidKeyStore. La clave privada se genera dentro del almacen del sistema y
 *     nunca sale de ahi: ni siquiera esta app puede exportarla. Se firma con
 *     "NONEwithRSA" + relleno PKCS#1, que es justo el primitivo que necesita ADB.
 *  2. Respaldo en DataStore. Si el fabricante no soporta DIGEST_NONE para RSA (raro,
 *     pero no queremos que la app quede inservible), se genera una clave por software
 *     y se guarda en el DataStore privado de la app.
 *
 * En ambos casos la clave sobrevive a reinicios y actualizaciones, y se pierde al
 * desinstalar: entonces habra que volver a aceptar el dialogo en la TV.
 */
class AdbKeyProvider(private val settings: SettingsRepository) {

    private val mutex = Mutex()

    @Volatile
    private var cached: AdbKeyPair? = null

    /** Identidad que la TV muestra junto a la clave, al estilo "usuario@equipo". */
    private val identity: String = "tvmando@" + Build.MODEL.replace(' ', '-').ifBlank { "android" }

    suspend fun keyPair(): AdbKeyPair = mutex.withLock {
        cached ?: load().also { cached = it }
    }

    /**
     * Tira la clave actual y genera otra. Obliga a repetir la autorizacion en la TV,
     * asi que solo se usa desde el boton de "reemparejar" de Ajustes.
     */
    suspend fun reset(): AdbKeyPair = mutex.withLock {
        cached = null
        runCatching { androidKeyStore()?.deleteEntry(KEYSTORE_ALIAS) }
        settings.clearKeyPair()
        load().also { cached = it }
    }

    /** Huella que debe coincidir con la que muestra el dialogo de la TV. */
    suspend fun fingerprint(): String = keyPair().fingerprint()

    /** true si la clave privada esta respaldada por hardware/AndroidKeyStore. */
    suspend fun isKeyStoreBacked(): Boolean {
        keyPair()
        return keyStoreAvailable
    }

    @Volatile
    private var keyStoreAvailable = false

    private suspend fun load(): AdbKeyPair {
        loadFromKeyStore()?.let { keyStoreAvailable = true; return it }
        createInKeyStore()?.let { keyStoreAvailable = true; return it }
        keyStoreAvailable = false
        loadFromSettings()?.let { return it }
        return createInSettings()
    }

    private fun androidKeyStore(): KeyStore? = runCatching {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }.getOrNull()

    private fun loadFromKeyStore(): AdbKeyPair? = runCatching {
        val store = androidKeyStore() ?: return null
        val entry = store.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
        val public = entry.certificate.publicKey as? RSAPublicKey ?: return null
        AdbKeyPair(entry.privateKey, public, identity)
    }.getOrNull()

    private fun createInKeyStore(): AdbKeyPair? = runCatching {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE,
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setKeySize(AdbKeyPair.KEY_SIZE_BITS)
                // ADB firma un digest ya calculado: necesitamos firmar sin digest.
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA1)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=$identity"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        val pair = generator.generateKeyPair()
        val keyPair = AdbKeyPair(pair.private, pair.public as RSAPublicKey, identity)
        // Comprobamos que este proveedor sabe firmar de verdad antes de fiarnos.
        keyPair.signToken(ByteArray(AdbKeyPair.TOKEN_SIZE))
        keyPair
    }.getOrNull()

    private suspend fun loadFromSettings(): AdbKeyPair? {
        val (privateKey, publicKey) = settings.storedKeyPair() ?: return null
        return runCatching {
            AdbKeyPair.fromEncoded(
                Base64.getDecoder().decode(privateKey),
                Base64.getDecoder().decode(publicKey),
                identity,
            )
        }.getOrNull()
    }

    private suspend fun createInSettings(): AdbKeyPair {
        val keyPair = AdbKeyPair.generate(identity)
        val privateKey = keyPair.encodedPrivateKey()
        if (privateKey != null) {
            settings.storeKeyPair(
                Base64.getEncoder().encodeToString(privateKey),
                Base64.getEncoder().encodeToString(keyPair.publicKey.encoded),
            )
        }
        return keyPair
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEYSTORE_ALIAS = "tvmando_adb_key"
    }
}
