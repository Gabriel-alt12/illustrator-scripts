package com.gabriel.tvmando.adb

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Constantes y (des)serializacion del protocolo de transporte de ADB.
 *
 * Cada mensaje son 24 bytes de cabecera en little-endian seguidos del payload:
 *
 *     u32 command        identificador de 4 caracteres ("CNXN", "OPEN"...)
 *     u32 arg0
 *     u32 arg1
 *     u32 data_length
 *     u32 data_checksum  suma de los bytes del payload (mod 2^32)
 *     u32 magic          command xor 0xFFFFFFFF
 *
 * Referencia: AOSP packages/modules/adb/protocol.txt
 */
internal object AdbProtocol {

    const val CMD_CNXN = 0x4e584e43 // "CNXN"
    const val CMD_AUTH = 0x48545541 // "AUTH"
    const val CMD_OPEN = 0x4e45504f // "OPEN"
    const val CMD_OKAY = 0x59414b4f // "OKAY"
    const val CMD_CLSE = 0x45534c43 // "CLSE"
    const val CMD_WRTE = 0x45545257 // "WRTE"
    const val CMD_STLS = 0x534c5453 // "STLS"

    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    /**
     * Version del protocolo sin negociacion de features. Con esta version el daemon
     * valida el checksum de cada mensaje, asi que siempre lo calculamos.
     */
    const val VERSION = 0x01000000

    const val MAX_PAYLOAD = 256 * 1024
    const val HEADER_SIZE = 24

    /** Banner que identifica al cliente. Sin features: usamos el shell clasico. */
    const val CONNECT_BANNER = "host::"

    fun commandName(command: Int): String {
        val bytes = ByteArray(4) { i -> ((command shr (i * 8)) and 0xFF).toByte() }
        val text = String(bytes, Charsets.US_ASCII)
        return if (text.all { it.isLetterOrDigit() }) text else "0x%08x".format(command)
    }

    fun checksum(payload: ByteArray): Int {
        var sum = 0
        for (b in payload) sum += (b.toInt() and 0xFF)
        return sum
    }
}

/** Un mensaje del protocolo ADB. */
internal class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val payload: ByteArray,
) {
    constructor(command: Int, arg0: Int, arg1: Int) : this(command, arg0, arg1, EMPTY)

    /** El payload como texto, descartando el terminador nulo que usa ADB. */
    fun text(): String = String(payload, Charsets.UTF_8).substringBefore('\u0000')

    override fun toString(): String =
        "${AdbProtocol.commandName(command)}(arg0=$arg0, arg1=$arg1, len=${payload.size})"

    companion object {
        val EMPTY = ByteArray(0)

        /** Payload de servicio/banner: texto UTF-8 terminado en nulo, como espera adbd. */
        fun nullTerminated(value: String): ByteArray = value.toByteArray(Charsets.UTF_8) + 0
    }
}

internal fun OutputStream.writeMessage(message: AdbMessage) {
    val header = ByteBuffer.allocate(AdbProtocol.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    header.putInt(message.command)
    header.putInt(message.arg0)
    header.putInt(message.arg1)
    header.putInt(message.payload.size)
    header.putInt(AdbProtocol.checksum(message.payload))
    header.putInt(message.command.inv())
    write(header.array())
    if (message.payload.isNotEmpty()) write(message.payload)
    flush()
}

internal fun InputStream.readMessage(): AdbMessage {
    val header = ByteArray(AdbProtocol.HEADER_SIZE)
    readFully(header)
    val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
    val command = buffer.int
    val arg0 = buffer.int
    val arg1 = buffer.int
    val length = buffer.int
    buffer.int // checksum: adbd no exige que el cliente lo verifique
    val magic = buffer.int

    if (command != magic.inv()) {
        throw AdbException.Protocol(
            "Cabecera ADB invalida (command=0x%08x, magic=0x%08x)".format(command, magic)
        )
    }
    if (length < 0 || length > AdbProtocol.MAX_PAYLOAD) {
        throw AdbException.Protocol("Payload ADB fuera de rango: $length bytes")
    }

    val payload = ByteArray(length)
    if (length > 0) readFully(payload)
    return AdbMessage(command, arg0, arg1, payload)
}

/** Lee exactamente [buffer].size bytes o lanza [EOFException]. */
internal fun InputStream.readFully(buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val read = read(buffer, offset, buffer.size - offset)
        if (read < 0) throw EOFException("La TV cerro la conexion ADB")
        offset += read
    }
}
