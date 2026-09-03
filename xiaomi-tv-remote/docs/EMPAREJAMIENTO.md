# Emparejamiento ADB y persistencia de la clave

Este documento responde a las dos preguntas del prompt de la fase 2: **cómo funciona
el emparejamiento inicial** y **cómo se persiste la clave** para no repetirlo.

---

## 1. Una corrección a la especificación

El punto 4.4 del documento de especificaciones dice:

> **Emparejamiento inicial:** requiere `adb pair IP:puerto` una sola vez, desde PC o
> desde una app puente ADB.

Esto **normalmente no es cierto en un Google TV**. Hay dos mecanismos distintos, y se
confunden con facilidad porque Google los nombra casi igual:

| | Depuración por red (clásico) | Depuración inalámbrica (Android 11+) |
|---|---|---|
| Puerto | 5555 fijo | aleatorio, cambia en cada arranque |
| Emparejar | No hace falta | `adb pair IP:puerto` con código de 6 dígitos |
| Autorización | Diálogo en pantalla con la huella de la clave | SPAKE2 sobre TLS |
| Transporte | TCP en claro, autenticación RSA | TLS con certificado de cliente |
| En el menú de la TV | «Depuración USB» / «Depuración por red» / «ADB por red» | «Depuración inalámbrica» |

Los televisores Android TV y Google TV, incluida la Xiaomi TV S, exponen el **flujo
clásico del puerto 5555**, que es el que usa todo el mundo con `adb connect`. Ese es
el que implementa esta app: no necesita `adb pair`, ni un PC, ni una app puente.

La app **detecta** el otro caso: si la TV contesta con un mensaje `A_STLS` (es decir,
exige TLS), lanza `AdbException.TlsRequired` con una explicación en pantalla en lugar
de quedarse colgada. Implementar el emparejamiento SPAKE2 queda fuera de la v1.

---

## 2. Preparar la TV (una sola vez)

1. **Ajustes → Sistema → Acerca de** → pulsar 7 veces sobre «Compilación».
2. **Ajustes → Sistema → Opciones de desarrollador** → activar **Depuración USB**.
   Si aparece una entrada aparte llamada «Depuración por red» o «ADB por red»,
   actívala también: es la que abre el puerto 5555.
3. **Ajustes → Red e Internet → (tu WiFi) → Estado** → anota la IP.
4. En el router, **reserva esa IP por DHCP** para la MAC de la TV. Si no, tarde o
   temprano cambia y la app deja de encontrarla.

> Si en Opciones de desarrollador solo aparece «Depuración inalámbrica» y ninguna
> opción de depuración por red, la TV está en el modo TLS y esta versión de la app no
> puede conectarse.

---

## 3. Qué pasa exactamente al conectar

El protocolo de transporte de ADB va sobre TCP y son mensajes de 24 bytes de cabecera
más payload. El intercambio completo, implementado en `AdbConnection.handshake()`:

```
Móvil                                              TV (adbd)
  |                                                    |
  |-- CNXN  version=0x01000000 maxdata=256K "host::" ->|
  |                                                    |
  |<------------------- AUTH  arg0=1 (TOKEN) 20 bytes -|   token aleatorio
  |                                                    |
  |-- AUTH  arg0=2 (SIGNATURE) firma RSA del token --->|
  |                                                    |
  |            ¿conoce la TV esta clave pública?       |
  |                                                    |
  |   SÍ:                                              |
  |<------------------- CNXN  "device::ro.product..." -|   conectados
  |                                                    |
  |   NO:                                              |
  |<------------------- AUTH  arg0=1 (TOKEN) ----------|   repite el token
  |-- AUTH  arg0=3 (RSAPUBLICKEY) clave + identidad -->|
  |                                                    |
  |          la TV muestra el diálogo en pantalla      |
  |          «¿Permitir depuración USB?»               |
  |          con la huella MD5 de la clave             |
  |                                                    |
  |<------------------- CNXN  (al aceptar) ------------|   conectados
```

Detalles que importan y que son fáciles de equivocar:

- **La firma no es `SHA1withRSA`.** El token que envía adbd *ya es* un digest de 20
  bytes. Lo que hace adbd es `RSA_sign(NID_sha1, token)`, que antepone el `DigestInfo`
  ASN.1 de SHA-1 y aplica relleno PKCS#1 tipo 1. En Java eso es `NONEwithRSA` sobre
  `DigestInfo || token`, con el prefijo puesto a mano
  (`AdbKeyPair.SHA1_DIGEST_INFO`).

- **La clave pública no va en formato X.509.** ADB usa una `struct` propia de AOSP
  (`libcrypto_utils/android_pubkey.c`) en little-endian, codificada en base64:

  ```c
  struct RSAPublicKey {
      uint32_t modulus_size_words;  // 64
      uint32_t n0inv;               // -1 / n[0] mod 2^32
      uint8_t  modulus[256];        // little-endian
      uint8_t  rr[256];             // r^2 mod n, con r = 2^2048
      uint32_t exponent;            // 65537
  };
  ```

  `n0inv` y `rr` son valores precalculados para la aritmética de Montgomery de adbd.
  Si alguno está mal, la TV rechaza la clave **sin dar ninguna pista**. Por eso hay
  un test (`AdbKeyPairTest`) que comprueba los dos invariantes:
  `n0inv · n[0] ≡ -1 (mod 2^32)` y `rr = 2^4096 mod n`.

- **Al payload de la clave pública hay que añadirle un byte nulo** al final, y el
  formato es `"<base64> <identidad>"`. La identidad es lo que la TV enseña junto a la
  huella.

- **Espera larga tras enviar la clave.** El usuario tiene que levantarse y aceptar el
  diálogo, así que el `soTimeout` del socket sube a 90 s justo en ese momento y se
  avisa a la UI por el callback `onAuthorizationRequired`.

### La huella

El diálogo de la TV enseña una huella tipo `E4:3B:F7:...`. Se calcula igual que en
`AdbDebuggingManager` de AOSP: **MD5 de los 524 bytes de la struct**, en hexadecimal
y separado por dos puntos. La app la muestra en Ajustes para poder cotejarla: si no
coincide, alguien más está pidiendo acceso a tu televisión.

---

## 4. Persistencia de la clave

Es lo que hace que el diálogo salga **una sola vez**. Dos niveles, en
`data/AdbKeyProvider.kt`:

### Nivel 1 — AndroidKeyStore (lo normal)

La clave RSA-2048 se genera **dentro del almacén del sistema** con `PURPOSE_SIGN`,
`DIGEST_NONE` y relleno `PKCS1`. La privada nunca sale de ahí: ni siquiera esta app
puede exportarla, solo pedirle firmas.

`DIGEST_NONE` es imprescindible: sin él, el AndroidKeyStore insistiría en hacer él
mismo el hash y el token que firma ADB ya viene hasheado.

Al generar la clave se hace una firma de prueba inmediatamente. Si el proveedor del
fabricante no soporta esa combinación, se descarta el nivel 1 sin dejar la app rota.

### Nivel 2 — respaldo en DataStore

Si el AndroidKeyStore falla, se genera una clave por software y se guarda en el
DataStore privado de la app, en base64 (PKCS#8 para la privada, X.509 para la
pública). Sigue estando dentro del sandbox de la app, pero es exportable con root.

### Ciclo de vida

| Evento | ¿Sobrevive la clave? |
|---|---|
| Reiniciar el móvil | Sí |
| Actualizar la app | Sí |
| Borrar la caché | Sí |
| Borrar datos de la app | No → vuelve a salir el diálogo |
| Desinstalar | No → vuelve a salir el diálogo |
| Copia de seguridad y restauración en otro móvil | No (`allowBackup="false"` a propósito: una clave del KeyStore no se puede restaurar, y guardar una identidad de depuración en la nube no es buena idea) |
| Reset de fábrica de la TV | La clave sigue, pero la TV la olvidó → diálogo otra vez |
| «Revocar autorizaciones de depuración USB» en la TV | Ídem |

El botón **«Generar clave nueva y reemparejar»** de Ajustes borra la clave y crea
otra. Se usa cuando la TV tiene guardada una autorización antigua que ya no funciona.

---

## 5. Cuando se rompe

La sección 11 de la especificación avisa: ADB es frágil y las actualizaciones de
Google TV pueden romper el emparejamiento. Cada fallo de `AdbException` lleva un
`hint` que se muestra tal cual en pantalla:

| Error | Qué ha pasado | Qué hacer |
|---|---|---|
| `NotConfigured` | Falta la IP | Ponerla en Ajustes |
| `Unreachable` | No hay ruta al endpoint | Misma WiFi, IP correcta, TV enchufada |
| `Timeout` | El socket abrió pero adbd no contesta | La TV está en reposo profundo; encenderla con el mando físico |
| `AuthorizationRejected` | La TV rechazó la clave | Revocar autorizaciones en la TV y reintentar aceptando «Permitir siempre» |
| `TlsRequired` | La TV exige depuración inalámbrica | Activar la depuración por red clásica (5555) |
| `Disconnected` | Se cayó la sesión | La app reconecta sola al siguiente comando |

---

## 6. Validación manual (fase 1 de la especificación)

Antes de fiarse de la app conviene comprobar los comandos a mano desde un PC:

```bash
adb connect 192.168.1.42:5555      # acepta el diálogo en la TV
adb devices -l                     # debe decir "device", no "unauthorized"

adb shell input keyevent KEYCODE_VOLUME_UP
adb shell input keyevent KEYCODE_POWER
adb shell getprop ro.product.model
adb shell pm list packages -3      # nombres de paquete reales de TU televisor
```

Ese último es importante: los nombres de paquete de las apps de streaming cambian
entre versiones y regiones, y por eso la pantalla de Apps de la fase 3 los detecta en
lugar de llevarlos escritos a fuego.
