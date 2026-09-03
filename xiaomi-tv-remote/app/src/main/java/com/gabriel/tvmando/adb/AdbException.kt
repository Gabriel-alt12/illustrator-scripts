package com.gabriel.tvmando.adb

/**
 * Errores del cliente ADB.
 *
 * Cada uno lleva un [hint] pensado para mostrarse tal cual en la UI: la especificacion
 * pide que la app detecte los fallos de emparejamiento y guie al usuario en lugar de
 * fallar en silencio.
 */
sealed class AdbException(
    message: String,
    val hint: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Todavia no se ha dicho donde esta la TV. */
    class NotConfigured : AdbException(
        "Falta la IP de la TV",
        "Abre Ajustes e introduce la IP que aparece en la TV en Ajustes / Red / " +
            "Estado de la red, con el puerto 5555.",
    )

    /** No hay ruta hasta el endpoint: TV sin corriente, IP cambiada u otra red. */
    class Unreachable(endpoint: String, cause: Throwable? = null) : AdbException(
        "No se llega a $endpoint",
        "Comprueba que el movil y la TV estan en la misma WiFi. Si la IP cambio, " +
            "actualizala en Ajustes (mejor aun: reservala en el router).",
        cause,
    )

    /** El socket abrio pero adbd no contesto a tiempo. */
    class Timeout(what: String) : AdbException(
        "Tiempo de espera agotado: $what",
        "La TV puede estar en reposo profundo con el WiFi dormido. Enciendela con el " +
            "mando fisico y reintenta.",
    )

    /** El usuario no acepto el dialogo de autorizacion, o lo rechazo. */
    class AuthorizationRejected : AdbException(
        "La TV rechazo la clave de depuracion",
        "En la TV: Ajustes / Sistema / Opciones de desarrollador / Revocar autorizaciones " +
            "de depuracion USB. Reintenta y acepta el dialogo marcando Permitir siempre.",
    )

    /**
     * adbd pidio TLS (A_STLS). Es el modo "Depuracion inalambrica" de Android 11+,
     * que exige emparejamiento previo por codigo y no el flujo clasico del puerto 5555.
     */
    class TlsRequired : AdbException(
        "La TV exige depuracion inalambrica con TLS",
        "Activa Depuracion USB / Depuracion por red (puerto 5555) en Opciones de " +
            "desarrollador en lugar de Depuracion inalambrica, o empareja antes con " +
            "adb pair desde un PC.",
    )

    /** Rotura del protocolo: normalmente una version de adbd que no esperabamos. */
    class Protocol(message: String) : AdbException(message)

    /** adbd rechazo abrir el servicio (por ejemplo el shell). */
    class ServiceRefused(service: String) : AdbException(
        "La TV rechazo el servicio $service",
        "Suele significar que la autorizacion de depuracion se revoco. Reintenta la conexion.",
    )

    /** La conexion se perdio mientras habia comandos en vuelo. */
    class Disconnected(cause: Throwable? = null) : AdbException(
        "Se perdio la conexion ADB con la TV",
        "La app reintentara al enviar el proximo comando.",
        cause,
    )
}
