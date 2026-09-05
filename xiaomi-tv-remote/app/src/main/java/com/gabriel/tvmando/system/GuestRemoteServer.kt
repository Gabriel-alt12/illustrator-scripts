package com.gabriel.tvmando.system

import com.gabriel.tvmando.domain.PressKey
import com.gabriel.tvmando.domain.TvController
import com.gabriel.tvmando.domain.TvKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.BindException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Collections

/** Lo que hay que enseñar en Ajustes sobre el mando de invitados. */
sealed interface GuestRemoteState {
    data object Stopped : GuestRemoteState

    /** [url] es lo que se le pasa a la visita. */
    data class Running(val url: String) : GuestRemoteState

    data class Failed(val message: String) : GuestRemoteState
}

/**
 * Mando para invitados: un servidor web minusculo dentro de la app.
 *
 * El caso de uso es el salon con gente que va y viene. Quien esta de visita abre una
 * direccion en el navegador de su movil y controla la tele sin instalarse nada, sin
 * cuenta y sin tocar el telefono del anfitrion. Los comandos salen por la misma sesion
 * ADB que el mando normal: esto no habla con la TV, habla con [TvController].
 *
 * Decisiones que importan:
 *
 *  - **No se persiste.** Encender esto es un gesto de "ahora hay gente en casa", no una
 *    configuracion. Al cerrarse el proceso el enlace muere, y cada vez que se enciende
 *    se genera una direccion nueva: un enlace viejo en el navegador de alguien no vale.
 *  - **Solo red local.** El socket escucha en la IP privada del movil. Para que
 *    llegase desde fuera de casa haria falta abrir el router a mano, que no es algo
 *    que pase por accidente.
 *  - **Teclas contadas** ([TvKey.GUEST]). La visita navega, sube el volumen y pausa; no
 *    apaga la tele ni la duerme. Es lo que se espera de un mando prestado.
 *  - **Lo mantiene vivo un servicio.** El bucle corre en el alcance de la sesion ADB,
 *    que por si solo no basta: desde Android 12 el sistema congela el proceso a los
 *    pocos segundos de irse a segundo plano y la visita se quedaria con una pagina
 *    muerta al bloquear el anfitrion la pantalla. De eso se encarga
 *    [GuestRemoteService], que es quien llama a [start] y [stop].
 *
 * Limitacion conocida: el socket queda atado a la IP que tuviera el movil al
 * encenderlo. Si se cambia de red con el mando puesto, deja de servir y Ajustes sigue
 * enseñando la direccion vieja hasta que se apaga y se vuelve a encender.
 */
class GuestRemoteServer(
    private val controller: TvController,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<GuestRemoteState>(GuestRemoteState.Stopped)
    val state: StateFlow<GuestRemoteState> = _state.asStateFlow()

    private var server: ServerSocket? = null
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return

        val address = localIpAddress()
        if (address == null) {
            _state.value = GuestRemoteState.Failed(
                "El movil no esta conectado a la WiFi de casa.",
            )
            return
        }

        val opened = try {
            openSocket(address)
        } catch (error: IOException) {
            _state.value = GuestRemoteState.Failed(explain(error))
            return
        }

        val token = newToken()
        server = opened
        _state.value = GuestRemoteState.Running("http://$address:$PORT/$token")
        job = scope.launch { acceptLoop(opened, token) }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        // Cerrar el socket es lo que despierta al accept() que esta bloqueado.
        runCatching { server?.close() }
        server = null
        _state.value = GuestRemoteState.Stopped
    }

    // --- servidor ----------------------------------------------------------

    /**
     * Extension de [CoroutineScope] para que las peticiones sean hijas de este bucle:
     * asi al cancelarlo se van con el y no quedan sockets a medias.
     *
     * `accept()` es bloqueante y no atiende a cancelaciones, de ahi que [stop] cierre
     * ademas el socket: eso es lo que lo despierta.
     */
    private suspend fun CoroutineScope.acceptLoop(socket: ServerSocket, token: String) {
        try {
            while (isActive && !socket.isClosed) {
                val client = socket.accept()
                // Cada peticion en su corrutina: un navegador lento no bloquea al resto.
                launch { serve(client, token) }
            }
        } catch (error: IOException) {
            // stop() cierra el socket y accept() salta por aqui: esa es la salida
            // normal. Cualquier otro fallo si hay que contarlo, porque el enlace deja
            // de contestar y desde Ajustes no habria forma de saberlo.
            if (!socket.isClosed) {
                _state.value = GuestRemoteState.Failed(
                    "El mando de invitados se ha caido: vuelve a encenderlo.",
                )
            }
        } finally {
            onLoopFinished(socket)
        }
    }

    /**
     * Cierre ordenado del bucle. Va sincronizado y comprobando identidad porque un
     * apagar-y-encender rapido deja dos bucles vivos un instante: sin esto, el viejo
     * al terminar borraba el estado del nuevo y Ajustes se quedaba sin enseñar la
     * direccion mientras el servidor seguia sirviendo.
     */
    @Synchronized
    private fun onLoopFinished(socket: ServerSocket) {
        runCatching { socket.close() }
        if (server !== socket) return
        server = null
        job = null
        if (_state.value is GuestRemoteState.Running) {
            _state.value = GuestRemoteState.Stopped
        }
    }

    private suspend fun serve(client: Socket, token: String) {
        try {
            client.soTimeout = REQUEST_TIMEOUT_MS
            val reader = client.getInputStream().bufferedReader()
            val requestLine = reader.readLine().orEmpty()
            // Las cabeceras no se usan, pero hay que consumirlas hasta la linea vacia.
            while (true) {
                val line = reader.readLine()
                if (line == null || line.isEmpty()) break
            }
            route(client, path = requestLine.split(' ').getOrNull(1).orEmpty(), token = token)
        } catch (error: IOException) {
            // Un navegador que corta la conexion no es motivo para tirar el servidor.
        } finally {
            runCatching { client.close() }
        }
    }

    private suspend fun route(client: Socket, path: String, token: String) {
        val prefix = "/$token"
        when {
            path == prefix || path == "$prefix/" ->
                respond(client, "200 OK", "text/html; charset=utf-8", page(token))

            path.startsWith("$prefix/press?") -> {
                val name = path.substringAfter("k=", "").substringBefore('&')
                val key = TvKey.GUEST.firstOrNull { it.name == name }
                if (key == null) {
                    respond(client, "400 Bad Request")
                } else {
                    // El resultado importa: la pagina avisa a la visita de que la TV
                    // no responde, y con un 204 fijo ese aviso no saldria nunca.
                    val done = controller.run(PressKey(key)).isSuccess
                    respond(client, if (done) "204 No Content" else "502 Bad Gateway")
                }
            }

            // Sin la direccion completa no hay nada que ver, ni siquiera un indicio de
            // que aqui haya un mando: la misma respuesta para todo lo demas.
            else -> respond(client, "404 Not Found")
        }
    }

    private fun respond(
        client: Socket,
        status: String,
        contentType: String? = null,
        body: String? = null,
    ) {
        val bytes = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val head = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            if (contentType != null) append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val output = client.getOutputStream()
        output.write(head.toByteArray(Charsets.US_ASCII))
        if (bytes.isNotEmpty()) output.write(bytes)
        output.flush()
    }

    private fun page(token: String): String = PAGE.replace(TOKEN_SLOT, token)

    // --- utilidades --------------------------------------------------------

    /**
     * Abre el socket atado a la IP de la WiFi, y no a 0.0.0.0: si el movil tiene
     * ademas datos moviles, el mando no tiene por que asomar tambien por ahi.
     *
     * Reintenta un par de veces porque cerrar no libera el puerto del todo mientras
     * quede un hilo dentro de `accept()`: un apagar-y-encender seguido se encuentra el
     * puerto tomado por su propio socket anterior durante uno o dos milisegundos.
     */
    private fun openSocket(address: String): ServerSocket {
        val bound = InetAddress.getByName(address)
        var last: IOException? = null
        repeat(BIND_ATTEMPTS) { attempt ->
            try {
                return ServerSocket(PORT, BACKLOG, bound)
            } catch (busy: BindException) {
                last = busy
                if (attempt < BIND_ATTEMPTS - 1) Thread.sleep(BIND_RETRY_MS)
            }
        }
        throw last ?: BindException("No se pudo abrir el puerto $PORT")
    }

    /**
     * Todos los fallos de apertura acababan diciendo "lo tiene otra app", que manda al
     * usuario a buscar un culpable que muchas veces no existe. Aqui cada motivo dice
     * lo suyo.
     */
    private fun explain(error: IOException): String = when (error) {
        is BindException -> "El puerto $PORT sigue ocupado. Vuelve a encenderlo."
        else -> "No se pudo abrir el mando de invitados: ${error.message ?: "error de red"}"
    }

    /**
     * Direccion del movil en la red de casa. Se descarta loopback y se exige que sea
     * privada (192.168.x.x y compania): si el movil solo tiene datos moviles no hay
     * nada que ofrecer y mas vale decirlo que levantar un servidor inalcanzable.
     */
    private fun localIpAddress(): String? = runCatching {
        for (nif in Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nif.isUp || nif.isLoopback) continue
            for (address in Collections.list(nif.inetAddresses)) {
                if (address is Inet4Address &&
                    !address.isLoopbackAddress &&
                    address.isSiteLocalAddress
                ) {
                    return@runCatching address.hostAddress
                }
            }
        }
        null
    }.getOrNull()

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val PORT = 8321
        const val BACKLOG = 50
        const val BIND_ATTEMPTS = 3
        const val BIND_RETRY_MS = 30L
        const val REQUEST_TIMEOUT_MS = 5_000
        const val TOKEN_BYTES = 4
        const val TOKEN_SLOT = "__TOKEN__"


        /**
         * La pagina entera, sin nada externo: la visita puede no tener internet, solo
         * la WiFi de casa. Los simbolos van como entidades HTML para que este fichero
         * sea ASCII puro y no dependa de como viaje la codificacion.
         */
        val PAGE = """
            <!doctype html>
            <html lang="es">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
            <title>Ember</title>
            <style>
              /* La misma paleta que la app, por papeles: ink es el fondo y chalk el
                 texto sean del color que sean. Sigue al tema del movil de la visita. */
              :root {
                color-scheme: light dark;
                --ink: #08090C; --raised: #11131A; --high: #1A1D26; --hair: #272B36;
                --chalk: #F2F3F5; --muted: #858C9B;
                --ember: #FF5A1F; --glow: rgba(255, 90, 31, .45);
              }
              @media (prefers-color-scheme: light) {
                :root {
                  --ink: #F3EFE8; --raised: #FFFFFF; --high: #EAE5DC; --hair: #DAD3C8;
                  --chalk: #17181C; --muted: #5F646F;
                  --ember: #C93E0E; --glow: rgba(201, 62, 14, .35);
                }
              }
              * { box-sizing: border-box; }
              body {
                margin: 0; min-height: 100vh; background: var(--ink); color: var(--chalk);
                font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
                display: flex; flex-direction: column; align-items: center;
                gap: 16px; padding: 22px 18px 34px;
                -webkit-user-select: none; user-select: none;
                -webkit-tap-highlight-color: transparent;
              }
              h1 {
                margin: 0; font-size: 26px; font-weight: 800; letter-spacing: -.02em;
                line-height: 1; text-align: center;
              }
              h1 small {
                display: block; margin-top: 6px; font-size: 11px; font-weight: 700;
                letter-spacing: .2em; text-transform: uppercase; color: var(--muted);
              }
              .dial {
                position: relative; width: 258px; height: 258px; border-radius: 50%;
                background: var(--raised); border: 1px solid var(--hair);
              }
              button {
                color: var(--chalk); cursor: pointer;
                /* Luz, no pintura: al pulsar se enciende un anillo con halo y al
                   soltar se apaga despacio, como en la app. */
                transition: box-shadow .45s ease-out, color .45s ease-out;
              }
              .dial button {
                position: absolute; width: 72px; height: 72px; border-radius: 50%;
                background: none; border: 0; font-size: 24px;
              }
              .up { top: 8px; left: 93px; }
              .down { bottom: 8px; left: 93px; }
              .left { left: 8px; top: 93px; }
              .right { right: 8px; top: 93px; }
              .ok {
                left: 84px; top: 84px; width: 90px; height: 90px;
                background: var(--high); border: 2px solid var(--hair);
                font-size: 14px; font-weight: 700; letter-spacing: .12em;
              }
              .row { display: flex; gap: 12px; width: 100%; max-width: 300px; }
              .row button {
                flex: 1; height: 60px; border-radius: 20px; background: var(--raised);
                border: 1px solid var(--hair); font-size: 20px;
              }
              button:active {
                color: var(--ember); transition: none;
                box-shadow: 0 0 0 2px var(--ember), 0 0 18px 4px var(--glow);
              }
              .note {
                font-size: 11px; letter-spacing: .12em; text-transform: uppercase;
                color: var(--muted); min-height: 14px;
              }
            </style>
            </head>
            <body>
              <h1>Ember<small>Mando invitado</small></h1>
              <div class="dial">
                <button class="up" data-k="DPAD_UP">&#9650;</button>
                <button class="down" data-k="DPAD_DOWN">&#9660;</button>
                <button class="left" data-k="DPAD_LEFT">&#9664;</button>
                <button class="right" data-k="DPAD_RIGHT">&#9654;</button>
                <button class="ok" data-k="DPAD_CENTER">OK</button>
              </div>
              <div class="row">
                <button data-k="BACK">&#8617;</button>
                <button data-k="HOME">&#8962;</button>
                <button data-k="MEDIA_PLAY_PAUSE">&#9199;</button>
              </div>
              <div class="row">
                <button data-k="VOLUME_DOWN">&#8722;</button>
                <button data-k="VOLUME_MUTE">&#128263;</button>
                <button data-k="VOLUME_UP">+</button>
              </div>
              <div class="note" id="note"></div>
            <script>
              var TOKEN = "__TOKEN__";
              var note = document.getElementById("note");
              function press(key) {
                fetch("/" + TOKEN + "/press?k=" + key, { method: "POST" })
                  .then(function (r) { note.textContent = r.ok ? "" : "la tele no responde"; })
                  .catch(function () { note.textContent = "sin conexion con el movil"; });
              }
              document.querySelectorAll("button[data-k]").forEach(function (boton) {
                boton.addEventListener("click", function () {
                  if (navigator.vibrate) navigator.vibrate(10);
                  press(boton.getAttribute("data-k"));
                });
              });
            </script>
            </body>
            </html>
        """.trimIndent()
    }
}
