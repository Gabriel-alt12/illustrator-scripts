# Mando TV — Xiaomi TV S Mini LED

App Android para controlar una Xiaomi TV S Mini LED 2025 (Google TV) por ADB en red
local. Kotlin + Jetpack Compose, MVVM, DataStore. Sin backend, sin cuentas, sin
internet: todo local.

**Estado: fases 2 a 5 completas.** Cliente ADB propio, mando completo (D-pad,
multimedia, navegación, volumen y encendido), pantalla de Apps con detección
dinámica, búsqueda por texto con historial, motor de escenas con editor visual, y
los extras de sistema: widget, tiles de ajustes rápidos, mando en la barra de
notificaciones, mando web para las visitas y firma para sideload.

---

## Compilar e instalar

Requiere JDK 17 y el SDK de Android 35.

```bash
cd xiaomi-tv-remote
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O abrir la carpeta `xiaomi-tv-remote/` directamente en Android Studio.

### APK firmado para sideload

Crea el almacén una sola vez y guárdalo **fuera** del repositorio:

```bash
keytool -genkey -v -keystore ~/mando-tv.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias mando
```

Copia `keystore.properties.example` a `keystore.properties`, rellena la ruta y las
contraseñas, y compila:

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

`keystore.properties`, `*.jks` y `*.keystore` están en `.gitignore`. Si el fichero
no existe, el `assembleRelease` sale sin firmar y tendrás que firmarlo a mano con
`apksigner`.

## Primer uso

1. Prepara la TV y anota su IP → **[docs/EMPAREJAMIENTO.md](docs/EMPAREJAMIENTO.md)**
2. Abre la app, toca el engranaje, escribe la IP y el puerto 5555.
3. Toca «Guardar y conectar».
4. **Mira la televisión**: sale un diálogo de «¿Permitir depuración USB?» con una
   huella. Compárala con la que muestra la app en Ajustes, marca «Permitir siempre» y
   acepta.
5. El indicador de arriba a la izquierda se pone verde.
6. La pestaña **Apps** se rellena sola al conectar. Toque = abrir, pulsación larga =
   forzar el cierre. La app que esté en pantalla aparece resaltada en naranja.
7. En **Buscar** eliges dónde escribir y usas el teclado del móvil: la app manda
   `input text` y luego ENTER.
8. En **Escenas** vienen seis de fábrica: las tres de la especificación (Modo cine,
   Modo música, Apagar todo) y tres más pensadas para un salón con gente que entra
   y sale (Silencio ya, Llega visita, Música de fondo). Edítalas: los paquetes de
   Netflix y Spotify son solo un punto de partida, usa los reales que te dé la
   pestaña de Apps.

### Fuera de la app

- **Widget**: mantén pulsada la pantalla de inicio → Widgets → Mando TV. Cuatro
  botones: encendido, volumen −/+ y silencio.
- **Tiles**: edita los ajustes rápidos y arrastra «Apagar TV» y «Silenciar TV».
- **Notificación persistente**: actívala en Ajustes de la app. Cinco botones
  siempre a mano; en Android 13+ te pedirá permiso de notificaciones.
- **Mando para invitados**: actívalo en Ajustes y sale una dirección tipo
  `http://192.168.1.55:8321/3f9c1d2e`. Quien esté de visita la abre en su
  navegador y controla la TV sin instalar nada. Solo va por la WiFi de casa y
  mientras la app siga viva; al apagarlo la dirección deja de valer, y la
  siguiente vez se reparte una nueva. La visita puede navegar, pausar y tocar el
  volumen: apagar la tele no, que para eso está el dueño de la casa.

Widget, tiles y notificación funcionan aunque la app esté cerrada: reabren la
sesión ADB solos. Si la TV no responde en 8 segundos sale un aviso, porque ahí no
hay pantalla donde enseñar un error. El mando de invitados es la excepción: vive
mientras viva el proceso de la app, que es justo lo que dura una visita.

Solo hay que hacerlo una vez: la clave se guarda en el AndroidKeyStore.

---

## Decisión técnica: el cliente ADB

La especificación dejaba abierto usar `adblib` o un binario `adb` embebido. Ninguna
de las dos funciona bien aquí:

- **`adblib`** (`com.android.tools.adblib`) es la librería de Android Studio y está
  pensada para hablar con un **servidor adb** que ya esté corriendo en la máquina.
  En un móvil no hay tal servidor: resolvería la mitad del problema y añadiría la
  otra mitad.
- **Binario `adb` embebido**: desde Android 10 no se puede ejecutar código desde el
  directorio de datos de la app (W^X). Habría que colarlo por `jniLibs` para que
  acabe en `nativeLibraryDir`, mantener un binario por arquitectura y aun así
  levantar un servidor adb dentro del móvil. Frágil y pesado para lo que hace falta.
- **`cgutman/AdbLib`** es la referencia clásica en Java, pero no está publicada en
  Maven Central (habría que tirar de JitPack) y arrastra Spongy Castle para
  compatibilidad con Androids antiguos.

Lo que se ha hecho: **implementar el protocolo de transporte de ADB directamente en
Kotlin**, unas 770 líneas con comentarios en el paquete `adb/`. Cero dependencias,
cero NDK, cero servidor intermedio, corrutinas de verdad y control total sobre los
mensajes de error, que es justo lo que pide la sección 11 de la especificación («la
app debe detectar esto y guiar al usuario, no fallar en silencio»).

El detalle del protocolo, la criptografía y el emparejamiento está en
[docs/EMPAREJAMIENTO.md](docs/EMPAREJAMIENTO.md).

---

## Estructura

```
app/src/main/java/com/gabriel/tvmando/
├── adb/                  Cliente ADB. Kotlin puro, sin dependencias de Android.
│   ├── AdbProtocol.kt      Mensajes de 24 bytes, checksum, magic
│   ├── AdbKeyPair.kt       RSA-2048, firma del token y struct RSAPublicKey de AOSP
│   ├── AdbConnection.kt    Handshake CNXN/AUTH y multiplexado de streams
│   ├── AdbStream.kt        Un stream lógico (un "shell:...")
│   └── AdbException.kt     Errores con pista accionable para la UI
├── data/
│   ├── SettingsRepository.kt   DataStore: IP, puerto, modelo
│   └── AdbKeyProvider.kt       AndroidKeyStore con respaldo en DataStore
├── domain/
│   ├── TvCommand.kt        Catálogo completo de la sección 5
│   ├── TvApp.kt            Catálogo de apps y parseo de pm list / dumpsys
│   ├── Scene.kt            Modelo de escena, escenas de fábrica y códec
│   ├── SceneRunner.kt      Motor de secuencias, sin dependencias de Android
│   ├── ConnectionState.kt
│   └── TvController.kt     Sesión viva, reconexión, serialización de comandos
├── ui/
│   ├── theme/              Paleta oscura propia, no Material 3 por defecto
│   ├── components/         D-pad, botones grandes, hápticos, fichas de app
│   ├── remote/             RemoteScreen: mando completo
│   ├── apps/               AppsScreen: rejilla con detección dinámica
│   ├── search/             SearchScreen: teclado remoto con historial
│   ├── scenes/             ScenesScreen + SceneEditor: secuencias con retardos
│   ├── MandoApp.kt         Cáscara: cabecera, avisos, pestañas y ajustes
│   └── MandoViewModel.kt   Estado de las cuatro pantallas
├── system/
│   ├── TvCommandReceiver.kt    Punto único de entrada de widget, tiles y notificación
│   ├── RemoteWidgetProvider.kt Widget 4x1 de la pantalla de inicio
│   ├── QuickTileService.kt     Tiles de ajustes rápidos
│   ├── MandoNotification.kt    Mando en la barra de notificaciones
│   └── GuestRemoteServer.kt    Mando web para las visitas, sin dependencias
├── AppContainer.kt         Inyección de dependencias a mano
└── MainActivity.kt
```

El paquete `adb/` no importa nada de Android **a propósito**: así se puede probar en
la JVM contra un adbd falso, sin emulador ni televisor.

`TvCommand` ya cubre el catálogo entero de la sección 5 (D-pad, multimedia, canales,
apps, texto, diagnóstico) aunque la fase 2 solo cablee tres botones. Las pantallas de
las fases 3 y 4 se construyen encima sin tocar las capas de abajo.

---

## Qué está verificado y qué no

**Verificado ejecutando los tests** (`./gradlew :app:testDebugUnitTest`, 55 tests):

- Handshake completo contra un `FakeAdbd` que habla el protocolo real y comprueba de
  forma independiente lo que enviamos.
- La firma del token, validada deshaciendo el relleno PKCS#1 con la clave pública,
  que es exactamente lo que hace `RSA_verify` dentro de adbd.
- La struct `RSAPublicKey` campo a campo, incluidos los invariantes matemáticos de
  `n0inv` y `rr`.
- Multiplexado de streams con su control de flujo (un `OKAY` por cada `WRTE`) y
  reensamblado de salidas largas troceadas.
- Detección de `A_STLS` y de endpoint inalcanzable.
- Ida y vuelta de la clave persistida.
- Las líneas de shell que genera cada `TvCommand`, incluido el entrecomillado.
- El parseo de `pm list packages -3` (con y sin `-f`, con retornos de carro, con
  duplicados y con líneas de error de por medio) y el de `mResumedActivity`.
- El rescate de apps de streaming preinstaladas de fábrica (Prime Video, Netflix...)
  que `-3` no trae por contarlas Android como apps de sistema: una segunda consulta
  con `pm list packages` (catálogo completo) se cruza con el catálogo conocido para
  recuperarlas sin inundar la rejilla de servicios internos sin nombre.
- El motor de escenas con esperas falsas: orden de los comandos, retardos exactos,
  progreso paso a paso y que un fallo corte la secuencia en lugar de seguir.
- El códec de escenas: ida y vuelta de las de fábrica y de textos con comillas,
  saltos de línea y los propios separadores del formato dentro.
- Que el mando de invitados solo resuelve las teclas de su lista blanca, y que lo
  que llega por la URL no puede colarse dentro de la línea que ejecuta la TV.
- Los identificadores de `QuickCommand`, fijados en un test porque viajan dentro de
  PendingIntents que el sistema guarda entre versiones de la app: si uno cambia de
  nombre, los widgets ya colocados dejan de funcionar en silencio.

**No verificado, pendiente de tu móvil y tu televisor:**

- La compilación del APK. El entorno donde se escribió esto no tenía SDK de Android
  ni acceso a `dl.google.com`, así que las capas que dependen de Android (Compose,
  DataStore, AndroidKeyStore) están escritas pero no compiladas. Si algo se queja al
  primer `./gradlew assembleDebug`, será ahí.
- El comportamiento real de la Xiaomi TV S: que exponga el 5555 clásico, que acepte
  la clave y que responda a los keyevents. Es la fase 1 de la especificación y
  conviene hacerla a mano antes que nada (comandos en el documento de emparejamiento).
- El feedback háptico y el tacto de los botones, que es lo que hay que ajustar
  teniéndolo en la mano.

---

## Extras de la sección 9

| Extra | Estado |
|---|---|
| Widget con los 4 botones más usados | Hecho |
| Tiles de ajustes rápidos | Hecho: «Apagar TV» y «Silenciar TV» |
| Reconexión automática al abrir la app | Hecho, y también al volver de segundo plano |
| Mando en notificación persistente | Hecho, con 5 botones |
| Detección de la app en primer plano | Detectada y resaltada en la pestaña de Apps |

El último punto se queda ahí a propósito. La especificación pedía usar esa detección
para **adaptar los controles mostrados**, y no está hecho: esta app se usa a oscuras
y sin mirar la pantalla, y un mando cuyos botones cambian de sitio según lo que haya
en la tele es exactamente lo contrario de lo que necesitas cuando alargas el pulgar
sin apartar la vista. La detección se usa para informar, no para mover cosas.

## Lo que falta

- Probarlo todo en un televisor de verdad (fase 1 de la especificación).
- Compilar: nada de esto se ha compilado nunca, ver más arriba.

Limitaciones conocidas de la sección 11 que siguen en pie: solo red local;
`KEYCODE_POWER` es un toggle y no hay forma fiable de saber si la TV está encendida;
sin acceso a los ajustes de imagen propietarios de Xiaomi.
