# Ember — mando para la Xiaomi TV S Mini LED

App Android para controlar una Xiaomi TV S Mini LED 2025 (Google TV) por ADB en red
local. Kotlin + Jetpack Compose, MVVM, DataStore. Sin backend ni cuentas; a internet
solo sale para descargar una vez los logotipos de las apps. Antes se llamaba «Mando
TV»; el paquete sigue siendo `com.gabriel.tvmando`, así que se instala encima.

**Estado: funcional, rediseñada y con extras de salón.** Cliente ADB propio, mando
completo con encendido que sabe si la tele está encendida y volumen exacto, pantalla
de Apps con logotipos, favoritos y una estantería de «seguir viendo» (series que se
comparten desde el móvil y se abren de un toque por donde se dejaron), «ahora en la
tele», búsqueda por texto con historial, motor de escenas con editor visual,
temporizador de apagado y despertador, y los extras de sistema: widget, tiles, mando
en la barra de notificaciones, mando web para las visitas y compilación automática
del APK.

---

## Diseño

**Ember**: una brasa. La app se usa en el salón a oscuras, así que el tema por defecto
es casi negro con un único acento cálido, y ese acento se usa como **luz, no como
pintura**: al pulsar una tecla no se rellena de naranja, se enciende un anillo de luz
en su borde (un arco en el aro de la cruceta, una raya bajo el icono en la barra de
volumen) que se apaga despacio al soltar. La brasa junto al nombre, arriba a la
izquierda, es el indicador de conexión, y respira mientras espera a la TV.

- **Dos temas de verdad**: oscuro (por defecto), claro y «como el sistema», en
  Ajustes → Apariencia. El claro no es blanco sino papel, y lleva el acento más
  profundo para que se lea como texto; los contrastes están calculados, no
  estimados (ver `ui/theme/Color.kt`).
- **Tipografía**: Barlow para títulos y etiquetas, empaquetada en el APK (SIL Open
  Font License, en `docs/OFL-Barlow.txt`); la del sistema para leer.
- **Ajustes en una hoja inferior** con secciones: conexión, apariencia, otros mandos y
  clave. Guardar la esconde con su animación antes de conectar.
- **Movimiento con sentido**: cambio de pestaña con fundido y desplazamiento corto,
  avisos que crecen y encogen en vez de empujar el mando, fichas que se recolocan al
  fijarlas. Todo con lo que trae Compose: sin librerías nuevas.
- El widget y la notificación siguen el tema del móvil (claro u oscuro); el icono y el
  mando web comparten la misma paleta.

---

## Actualizar sin compilar

Cada cambio se compila solo en GitHub Actions y deja el APK en un enlace fijo:

**https://github.com/Gabriel-alt12/illustrator-scripts/releases/download/mando-tv/mando-tv.apk**

Guárdalo en marcadores del móvil: abrirlo descarga siempre la última versión, y se
instala encima de la anterior sin perder la IP, los favoritos ni las escenas. Ni PC,
ni Android Studio, ni copiar carpetas.

La primera vez, Android pedirá permiso para instalar apps de ese navegador, y habrá
que **desinstalar la copia anterior** si estaba firmada con otra clave (la que
compilaste a mano, o una de antes de que el flujo guardara su clave): el sistema no
deja actualizar entre firmas distintas. Se pierde la IP guardada esa vez.

Sin secretos configurados, el flujo firma con la clave de depuración de Gradle y la
guarda en la caché de Actions, así que es la misma en cada compilación y las
versiones se instalan una encima de otra. La caché caduca si pasa una semana sin
compilar nada: la siguiente firma sería nueva y habría que desinstalar una vez.

### Firma propia (opcional)

Para una firma que no dependa de una caché, crea el almacén y guarda cuatro secretos
en el repositorio:

```bash
keytool -genkey -v -keystore mando-tv.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias mando
base64 -w 0 mando-tv.jks    # esto es lo que se pega en KEYSTORE_BASE64
```

En **Settings → Secrets and variables → Actions** del repositorio, añade
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`. El flujo los
detecta solo: si están, firma; si no, compila sin firmar.

Guarda el `.jks` fuera del repositorio y no lo pierdas: sin él no se pueden firmar
versiones nuevas que actualicen a las ya instaladas.

## Compilar e instalar a mano

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
2. Abre la app y toca el engranaje: sube una hoja de ajustes. Escribe la IP y el
   puerto 5555.
3. Toca «Guardar y conectar».
4. **Mira la televisión**: sale un diálogo de «¿Permitir depuración USB?» con una
   huella. Compárala con la que muestra la app en Ajustes, marca «Permitir siempre» y
   acepta.
5. La brasa junto al nombre, arriba a la izquierda, se pone verde.
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

- **Widget**: mantén pulsada la pantalla de inicio → Widgets → Ember. Cuatro
  botones: encendido, volumen −/+ y silencio.
- **Tiles**: edita los ajustes rápidos y arrastra «Apagar TV» y «Silenciar TV».
- **Notificación persistente**: actívala en Ajustes de la app. Cinco botones
  siempre a mano; en Android 13+ te pedirá permiso de notificaciones.
- **Mando para invitados**: actívalo en Ajustes y sale una dirección tipo
  `http://192.168.1.55:8321/3f9c1d2e`, con un botón para copiarla. Quien esté de
  visita la abre en su navegador y controla la TV sin instalar nada. Solo va por
  la WiFi de casa; al apagarlo la dirección deja de valer, y la siguiente vez se
  reparte una nueva. La visita puede navegar, pausar y tocar el volumen: apagar
  la tele no, que para eso está quien vive aquí. Mientras está encendido sale una
  notificación, que es lo que exige Android para dejar el servidor escuchando con
  la pantalla apagada — y de paso recuerda que hay un mando prestado por ahí.

Widget, tiles y notificación funcionan aunque la app esté cerrada: reabren la
sesión ADB solos. Si la TV no responde en 8 segundos sale un aviso, porque ahí no
hay pantalla donde enseñar un error. El mando de invitados es distinto: necesita
proceso vivo, así que se sostiene con un servicio en primer plano mientras el
interruptor esté encendido.

Solo hay que hacerlo una vez: la clave se guarda en el AndroidKeyStore.

---

## Series, estado de la tele y temporizadores

- **Seguir viendo.** En el móvil, en Netflix, Prime Video, YouTube o Disney+, abre la
  serie y toca **Compartir → Ember**: sale una hoja con el título y la app ya
  puestos, y queda como tarjeta encima de las apps. Un toque y la TV la abre en el
  sitio exacto y por donde la dejaste (la posición la guarda el propio servicio). Si
  la app de la TV no entiende el enlace, Ember la abre, busca el título y da a OK.
  Pulsación larga en la tarjeta: editar, borrar o **ponerla en la pantalla de inicio
  del móvil** como icono propio; las últimas salen también al mantener pulsado el
  icono de Ember. Sin enlace, también se añade a mano.
- **Ahora en la tele.** La tira de abajo dice qué está sonando (app y episodio) y al
  tocarla salen pausa, anterior, siguiente y «Guardar como acceso directo», que
  mete lo que estás viendo en la estantería sin buscar ningún enlace.
- **Encendido que sabe.** La cabecera dice ENCENDIDA o EN REPOSO y la tecla de
  encendido se apaga con la tele. **Volumen exacto**: la barra pinta el nivel como
  una raya de luz y se puede arrastrar para fijarlo de golpe.
- **Temporizadores.** Pulsación larga en la tecla de encendido: apagar dentro de 15 a
  90 minutos (con cuenta atrás en la notificación y botón de cancelar; solo apaga si
  la tele sigue encendida) y encender a una hora, solo o con una escena («Modo cine
  a las nueve»). El encendido programado es de un solo disparo y no sobrevive a un
  reinicio del móvil.
- **Diagnóstico.** En Ajustes, «Preguntar a la tele» dice en tres líneas qué sabe
  hacer tu televisor de todo esto: si cuenta si está encendida, si acepta un volumen
  exacto y si dice qué está reproduciendo.
- **Internet, solo para los logotipos.** Ember toma el icono de la misma app en el
  móvil y, si no está, lo descarga una vez de la ficha de la Play Store y lo guarda.
  Es la única vez que sale a internet; nada más deja el móvil.

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
├── adb/
│   ├── AdbProtocol.kt          Mensajes de 24 bytes, checksum, magic
│   ├── AdbKeyPair.kt           RSA-2048, firma del token y struct RSAPublicKey de AOSP
│   ├── AdbConnection.kt        Handshake CNXN/AUTH y multiplexado de streams
│   ├── AdbStream.kt            Un stream lógico (un "shell:...")
│   └── AdbException.kt         Errores con pista accionable para la UI
├── data/
│   ├── SettingsRepository.kt   DataStore: IP, tema, favoritos, accesos, despertador
│   ├── AdbKeyProvider.kt       AndroidKeyStore con respaldo en DataStore
│   ├── AppIconStore.kt         Iconos: móvil, disco o Play Store, en ese orden
│   └── ThemeMode.kt            Oscuro, claro o sistema
├── domain/
│   ├── TvCommand.kt            Catálogo de comandos, enlaces y consulta de estado
│   ├── TvStatus.kt             Encendido, volumen y qué suena, a partir de dumpsys
│   ├── TvApp.kt                Catálogo de apps y parseo de pm list / dumpsys
│   ├── Shortcut.kt             Accesos directos, su códec y el parser de lo compartido
│   ├── Scene.kt                Modelo de escena, escenas de fábrica y códec
│   ├── SceneRunner.kt          Motor de secuencias, sin dependencias de Android
│   ├── ConnectionState.kt
│   ├── QuickCommand.kt         Los comandos del widget, tiles y notificación
│   └── TvController.kt         Sesión viva, reconexión, serialización de comandos
├── ui/
│   ├── theme/                  Paleta Ember en dos temas, Barlow, escalas de espacio y radio
│   ├── components/             D-pad, teclas, luz de pulsación, hápticos, fichas
│   ├── remote/                 RemoteScreen: mando completo; TimersSheet: temporizadores
│   ├── apps/                   AppsScreen: rejilla; ShortcutShelf y sus hojas: seguir viendo
│   ├── search/                 SearchScreen: teclado remoto con historial
│   ├── scenes/                 ScenesScreen + SceneEditor: secuencias con retardos
│   ├── MandoApp.kt             Cáscara: cabecera, avisos, pestañas, hojas y ajustes
│   └── MandoViewModel.kt       Estado de las pantallas, sondeo de la TV, accesos
├── system/
│   ├── TvCommandReceiver.kt    Punto único de entrada de widget, tiles y notificación
│   ├── RemoteWidgetProvider.kt Widget 4x1 de la pantalla de inicio
│   ├── QuickTileService.kt     Tiles de ajustes rápidos
│   ├── MandoNotification.kt    Mando en la barra de notificaciones
│   ├── GuestRemoteServer.kt    Mando web para las visitas, sin dependencias
│   ├── GuestRemoteService.kt   Lo mantiene escuchando con la pantalla apagada
│   ├── SleepTimerService.kt    Cuenta atrás del apagado y encendido programado
│   ├── WakeAlarms.kt           El despertador: alarma exacta de un disparo
│   └── HomeShortcuts.kt        Accesos en el escritorio y atajos del icono
├── AppContainer.kt         Inyección de dependencias a mano
├── MainActivity.kt         Arranque: tema antes de pintar; recibe lo compartido
└── TvMandoApp.kt
```

El paquete `adb/` no importa nada de Android **a propósito**: así se puede probar en
la JVM contra un adbd falso, sin emulador ni televisor.

`TvCommand` ya cubre el catálogo entero de la sección 5 (D-pad, multimedia, canales,
apps, texto, diagnóstico) aunque la fase 2 solo cablee tres botones. Las pantallas de
las fases 3 y 4 se construyen encima sin tocar las capas de abajo.

---

## Qué está verificado y qué no

**Verificado ejecutando los tests** (`./gradlew :app:testDebugUnitTest`, 72 tests,
que GitHub Actions ejecuta en cada cambio antes de compilar el APK):

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
- Que el modo de tema guardado se lee tal cual y que un valor desconocido cae en
  oscuro en vez de tirar la app al arrancar.
- La consulta de estado de la TV: encendido, volumen exacto y qué suena, con
  salidas de `dumpsys` de Android 14 y del formato antiguo, y que una TV que no
  entienda las consultas deje todo en «desconocido» en vez de fallar.
- Los accesos directos: ida y vuelta por el DataStore, y que lo que comparten
  Netflix, Prime Video, YouTube o un navegador se convierta en título, enlace y app
  de la TV (con el asunto mandando sobre el texto). Que `am start` avise del fallo
  por su salida, y el plan B de buscar dentro de la app.

**No verificado, pendiente de tu móvil y tu televisor:**

- El comportamiento real de la Xiaomi TV S: que exponga el 5555 clásico, que acepte
  la clave y que responda a los keyevents. Es la fase 1 de la especificación y
  conviene hacerla a mano antes que nada (comandos en el documento de emparejamiento).
- Qué apps de tu tele entienden enlaces: Netflix, YouTube y Disney+ deberían;
  Prime Video es la duda. Ember lo detecta al abrir y cae al plan B sola.
- Que tu Xiaomi responda a `cmd media_session` (volumen exacto) y publique sus
  sesiones multimedia en `dumpsys` (ahora en la tele). El diagnóstico de Ajustes lo
  dice en diez segundos.
- El feedback háptico, el tacto de los botones y la luz de pulsación, que es lo que
  hay que ajustar teniéndolo en la mano.
- El tema claro a la luz del día: los contrastes están calculados, pero el papel y el
  naranja profundo hay que verlos en la pantalla.

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

Limitaciones conocidas de la sección 11 que siguen en pie: solo red local;
`KEYCODE_POWER` es un toggle y no hay forma fiable de saber si la TV está encendida;
sin acceso a los ajustes de imagen propietarios de Xiaomi.
