package com.gabriel.tvmando.adb

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Una sesion ADB viva contra la TV.
 *
 * Habla el protocolo de transporte directamente sobre TCP: no hay servidor adb ni
 * binario embebido de por medio, solo este socket. Tras el handshake queda una
 * corrutina leyendo el socket y repartiendo los mensajes entre los [AdbStream]
 * abiertos, porque adbd multiplexa todos los comandos sobre la misma conexion.
 */
class AdbConnection internal constructor(
    private val socket: Socket,
    private val input: InputStream,
    private val output: OutputStream,
    /** Banner que devuelve la TV, del estilo "device::ro.product.name=...". */
    val banner: String,
) : Closeable {

    private val writeLock = Mutex()
    private val streams = ConcurrentHashMap<Int, AdbStream>()
    private val nextLocalId = AtomicInteger(1)

    @Volatile
    private var failure: Throwable? = null

    private var readerJob: Job? = null

    val isConnected: Boolean
        get() = failure == null && !socket.isClosed

    /**
     * Ejecuta un comando en el shell de la TV y devuelve su salida.
     *
     * Usa el servicio "shell:" clasico, que mezcla stdout y stderr y no devuelve
     * codigo de salida. Para lo que hace la app (input keyevent, monkey, getprop)
     * es suficiente: si algo va mal, adbd escribe el error en esa misma salida.
     */
    suspend fun shell(command: String, timeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS): String {
        var stream: AdbStream? = null
        try {
            return withTimeout(timeoutMs) {
                val opened = open("shell:$command").also { stream = it }
                val text = StringBuilder()
                for (chunk in opened.incoming) {
                    text.append(String(chunk, Charsets.UTF_8))
                }
                text.toString()
            }
        } catch (timeout: TimeoutCancellationException) {
            throw AdbException.Timeout("ejecutando $command")
        } finally {
            stream?.let { open -> withContext(NonCancellable) { closeStream(open) } }
        }
    }

    /** Comprobacion barata de que la sesion sigue viva. */
    suspend fun ping(): Boolean = runCatching {
        shell("echo ok", timeoutMs = 4_000).contains("ok")
    }.getOrDefault(false)

    override fun close() {
        readerJob?.cancel()
        streams.values.forEach { it.onFailure(AdbException.Disconnected()) }
        streams.clear()
        runCatching { socket.close() }
    }

    // --- interno -----------------------------------------------------------

    internal suspend fun open(service: String): AdbStream {
        failure?.let { throw it.asAdbException() }

        val localId = nextLocalId.getAndIncrement()
        val stream = AdbStream(localId, service)
        streams[localId] = stream
        send(AdbMessage(AdbProtocol.CMD_OPEN, localId, 0, AdbMessage.nullTerminated(service)))
        try {
            stream.awaitReady()
        } catch (t: Throwable) {
            streams.remove(localId)
            throw t
        }
        return stream
    }

    private suspend fun closeStream(stream: AdbStream) {
        streams.remove(stream.localId)
        if (!stream.isClosed && isConnected) {
            runCatching {
                send(AdbMessage(AdbProtocol.CMD_CLSE, stream.localId, stream.remoteId))
            }
        }
        stream.incoming.close()
    }

    private suspend fun send(message: AdbMessage) = writeLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                output.writeMessage(message)
            } catch (e: IOException) {
                fail(e)
                throw AdbException.Disconnected(e)
            }
        }
    }

    private fun startReader(scope: CoroutineScope) {
        readerJob = scope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val message = input.readMessage()
                    when (message.command) {
                        AdbProtocol.CMD_OKAY -> streams[message.arg1]?.onOkay(message.arg0)

                        AdbProtocol.CMD_WRTE -> {
                            val stream = streams[message.arg1]
                            if (stream != null) {
                                stream.onData(message.payload)
                                // adbd espera un OKAY por cada WRTE antes de seguir enviando.
                                send(AdbMessage(AdbProtocol.CMD_OKAY, message.arg1, message.arg0))
                            } else {
                                send(AdbMessage(AdbProtocol.CMD_CLSE, message.arg1, message.arg0))
                            }
                        }

                        AdbProtocol.CMD_CLSE -> streams.remove(message.arg1)?.onClose()

                        AdbProtocol.CMD_CNXN ->
                            throw AdbException.Protocol("adbd reinicio la conexion")

                        else -> Unit // AUTH tardio u otros: irrelevantes una vez conectados
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    private fun fail(cause: Throwable) {
        if (failure == null) failure = cause
        val wrapped = cause.asAdbException()
        streams.values.forEach { it.onFailure(wrapped) }
        streams.clear()
        runCatching { socket.close() }
    }

    private fun Throwable.asAdbException(): AdbException =
        this as? AdbException ?: AdbException.Disconnected(this)

    companion object {
        const val DEFAULT_COMMAND_TIMEOUT_MS = 8_000L
        const val DEFAULT_CONNECT_TIMEOUT_MS = 6_000
        const val DEFAULT_AUTHORIZATION_TIMEOUT_MS = 90_000

        /**
         * Abre y autentica una sesion ADB.
         *
         * @param onAuthorizationRequired se invoca cuando hay que enviar la clave publica,
         *   es decir cuando la TV va a mostrar el dialogo "Permitir depuracion USB".
         *   La UI lo usa para pedirle al usuario que mire a la tele.
         */
        suspend fun connect(
            host: String,
            port: Int,
            keyPair: AdbKeyPair,
            scope: CoroutineScope,
            connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
            authorizationTimeoutMs: Int = DEFAULT_AUTHORIZATION_TIMEOUT_MS,
            onAuthorizationRequired: () -> Unit = {},
        ): AdbConnection = withContext(Dispatchers.IO) {
            val endpoint = "$host:$port"
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                try {
                    socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                } catch (e: SocketTimeoutException) {
                    throw AdbException.Unreachable(endpoint, e)
                } catch (e: IOException) {
                    throw AdbException.Unreachable(endpoint, e)
                }
                socket.soTimeout = connectTimeoutMs

                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val banner = handshake(
                    socket = socket,
                    input = input,
                    output = output,
                    keyPair = keyPair,
                    endpoint = endpoint,
                    authorizationTimeoutMs = authorizationTimeoutMs,
                    onAuthorizationRequired = onAuthorizationRequired,
                )

                // A partir de aqui manda la corrutina lectora: sin timeout de socket,
                // los limites de tiempo los pone withTimeout en cada comando.
                socket.soTimeout = 0
                AdbConnection(socket, input, output, banner).also { it.startReader(scope) }
            } catch (t: Throwable) {
                runCatching { socket.close() }
                throw t
            }
        }

        /**
         * Handshake CNXN/AUTH.
         *
         * 1. Enviamos CNXN con nuestro banner.
         * 2. adbd responde AUTH(TOKEN) con 20 bytes aleatorios.
         * 3. Firmamos el token con la clave privada. Si la TV ya conocia la clave,
         *    responde CNXN y estamos dentro.
         * 4. Si no la conocia, vuelve a mandar AUTH(TOKEN). Entonces enviamos la clave
         *    publica y la TV muestra el dialogo de autorizacion. Al aceptar, responde CNXN.
         */
        private fun handshake(
            socket: Socket,
            input: InputStream,
            output: OutputStream,
            keyPair: AdbKeyPair,
            endpoint: String,
            authorizationTimeoutMs: Int,
            onAuthorizationRequired: () -> Unit,
        ): String {
            output.writeMessage(
                AdbMessage(
                    AdbProtocol.CMD_CNXN,
                    AdbProtocol.VERSION,
                    AdbProtocol.MAX_PAYLOAD,
                    AdbMessage.nullTerminated(AdbProtocol.CONNECT_BANNER),
                )
            )

            var signatureSent = false
            var publicKeySent = false

            while (true) {
                val message = try {
                    input.readMessage()
                } catch (e: SocketTimeoutException) {
                    throw if (publicKeySent) {
                        AdbException.Timeout("esperando a que aceptes el dialogo en la TV")
                    } else {
                        AdbException.Timeout("negociando con adbd en $endpoint")
                    }
                }

                when (message.command) {
                    AdbProtocol.CMD_CNXN -> return message.text()

                    AdbProtocol.CMD_AUTH -> {
                        if (message.arg0 != AdbProtocol.AUTH_TOKEN) {
                            throw AdbException.Protocol("AUTH inesperado: arg0=${message.arg0}")
                        }
                        when {
                            !signatureSent -> {
                                signatureSent = true
                                output.writeMessage(
                                    AdbMessage(
                                        AdbProtocol.CMD_AUTH,
                                        AdbProtocol.AUTH_SIGNATURE,
                                        0,
                                        keyPair.signToken(message.payload),
                                    )
                                )
                            }

                            !publicKeySent -> {
                                publicKeySent = true
                                // El usuario tiene que levantarse a aceptar: mas margen.
                                socket.soTimeout = authorizationTimeoutMs
                                onAuthorizationRequired()
                                output.writeMessage(
                                    AdbMessage(
                                        AdbProtocol.CMD_AUTH,
                                        AdbProtocol.AUTH_RSAPUBLICKEY,
                                        0,
                                        keyPair.adbPublicKey() + 0,
                                    )
                                )
                            }

                            else -> throw AdbException.AuthorizationRejected()
                        }
                    }

                    // Depuracion inalambrica de Android 11+: exige emparejamiento previo.
                    AdbProtocol.CMD_STLS -> throw AdbException.TlsRequired()

                    AdbProtocol.CMD_CLSE -> throw AdbException.AuthorizationRejected()

                    else -> throw AdbException.Protocol("Mensaje inesperado en el handshake: $message")
                }
            }
        }
    }
}
