# Mando TV — Xiaomi TV S Mini LED

App Android para controlar una Xiaomi TV S Mini LED 2025 (Google TV) por ADB en red
local. Kotlin + Jetpack Compose, MVVM, DataStore. Sin backend, sin cuentas, sin
internet: todo local.

**Estado: fase 2 completa.** Una pantalla, cliente ADB funcionando y tres botones
(encendido, volumen + y volumen −) validando el circuito de punta a punta. La
estructura ya está montada para las pantallas de la sección 7 de la especificación.

---

## Compilar e instalar

Requiere JDK 17 y el SDK de Android 35.

```bash
cd xiaomi-tv-remote
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O abrir la carpeta `xiaomi-tv-remote/` directamente en Android Studio.

Para el APK de sideload firmado (fase 5) todavía hay que añadir la configuración de
firma; el `assembleDebug` vale de sobra para probar en tu propio móvil.

## Primer uso

1. Prepara la TV y anota su IP → **[docs/EMPAREJAMIENTO.md](docs/EMPAREJAMIENTO.md)**
2. Abre la app, toca el engranaje, escribe la IP y el puerto 5555.
3. Toca «Guardar y conectar».
4. **Mira la televisión**: sale un diálogo de «¿Permitir depuración USB?» con una
   huella. Compárala con la que muestra la app en Ajustes, marca «Permitir siempre» y
   acepta.
5. El indicador de arriba a la izquierda se pone verde.

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
│   ├── ConnectionState.kt
│   └── TvController.kt     Sesión viva, reconexión, serialización de comandos
├── ui/
│   ├── theme/              Paleta oscura propia, no Material 3 por defecto
│   ├── components/         Botones grandes, hápticos, animación de confirmación
│   └── remote/             RemoteScreen + RemoteViewModel
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

**Verificado ejecutando los tests** (`./gradlew :app:testDebugUnitTest`, 18 tests):

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

## Siguientes fases

- **Fase 3** — D-pad y multimedia sobre `TvCommand`, pantalla de Apps con detección
  dinámica vía `pm list packages -3` y cierre forzado con pulsación larga.
- **Fase 4** — Motor de escenas (secuencias con retardos), editor visual y envío de
  texto con historial.
- **Fase 5** — Widget, tiles de ajustes rápidos, notificación persistente, APK
  firmado.

Limitaciones conocidas de la sección 11 que siguen en pie: solo red local;
`KEYCODE_POWER` es un toggle y no hay forma fiable de saber si la TV está encendida;
sin acceso a los ajustes de imagen propietarios de Xiaomi.
