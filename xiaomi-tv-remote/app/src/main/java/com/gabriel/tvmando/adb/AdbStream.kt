package com.gabriel.tvmando.adb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

/**
 * Un stream logico dentro de la conexion ADB (lo que abre un "OPEN shell:...").
 *
 * adbd multiplexa varios streams sobre el mismo socket identificandolos por
 * (local-id, remote-id); esta clase solo guarda el estado de uno de ellos y
 * entrega los datos por un [Channel] que el consumidor recorre hasta el cierre.
 */
internal class AdbStream(
    val localId: Int,
    private val service: String,
) {
    /** Id que asigna la TV; llega en el OKAY de apertura. */
    @Volatile
    var remoteId: Int = 0
        private set

    @Volatile
    var isClosed: Boolean = false
        private set

    private val ready = CompletableDeferred<Unit>()

    /** Trozos de salida del comando. Se cierra cuando la TV manda CLSE. */
    val incoming = Channel<ByteArray>(Channel.UNLIMITED)

    fun onOkay(remote: Int) {
        remoteId = remote
        ready.complete(Unit)
    }

    fun onData(data: ByteArray) {
        incoming.trySend(data)
    }

    /** Cierre limpio: el comando termino (o adbd rechazo el servicio). */
    fun onClose() {
        isClosed = true
        if (!ready.isCompleted) {
            ready.completeExceptionally(AdbException.ServiceRefused(service))
        }
        incoming.close()
    }

    /** Cierre por caida del socket: propaga la causa a quien este leyendo. */
    fun onFailure(cause: Throwable) {
        isClosed = true
        if (!ready.isCompleted) ready.completeExceptionally(cause)
        incoming.close(cause)
    }

    /** Espera al OKAY de apertura; lanza si la TV cierra el stream antes. */
    suspend fun awaitReady() = ready.await()
}
