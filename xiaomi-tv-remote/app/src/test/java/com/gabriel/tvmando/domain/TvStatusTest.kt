package com.gabriel.tvmando.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La consulta de estado se lanza cada pocos segundos y su salida cambia entre
 * versiones de Android: el parser tiene que sacar lo que pueda de cada tramo y no
 * reventar nunca con lo que no entienda.
 */
class TvStatusTest {

    private val android14 = """
        #power
        mWakefulness=Awake
        #volume
        volume is 8 in range [0..15]
        #media
        MEDIA SESSION SERVICE (dumpsys media_session)

        User Records:
        Record for full_user 0
          Media button session is com.netflix.ninja/Netflix (userId=0)
          Sessions Stack - have 2 sessions:
            Netflix com.android.server.media.MediaSessionRecord@1a2b3c
              ownerPid=4321, ownerUid=10105, userId=0
              package=com.netflix.ninja
              launchIntent=null
              mediaButtonReceiver=null
              active=true
              flags=3
              rating type=0
              controllers: 2
              state=PlaybackState {state=STATE_PLAYING(3), position=2467384, buffered position=0, speed=1.0, updated=1234, actions=516, custom actions=[], active item id=-1, error=null}
              audioAttrs=AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MOVIE flags=0x0 tags= bundle=null
              volumeType=1, controlType=2, max=0, current=0
              metadata: size=7, description=Reacher, T2:E5 Lo que pasa en Atlantic City, null
              queueTitle=null, size=0
            YouTube com.android.server.media.MediaSessionRecord@4d5e6f
              package=com.google.android.youtube.tv
              active=false
              state=PlaybackState {state=STATE_STOPPED(1), position=0, buffered position=0, speed=0.0, updated=99, actions=0, custom actions=[], active item id=-1, error=null}
              metadata: size=0, description=null
    """.trimIndent()

    @Test
    fun `saca encendido, volumen y lo que suena de la salida de Android 14`() {
        val status = TvStatus.parse(android14)

        assertEquals(PowerState.AWAKE, status.power)
        assertEquals(VolumeLevel(current = 8, max = 15), status.volume)
        assertTrue(status.mediaAvailable)
        val playing = status.nowPlaying!!
        assertEquals("com.netflix.ninja", playing.packageName)
        assertEquals("Reacher", playing.title)
        assertEquals("T2:E5 Lo que pasa en Atlantic City", playing.subtitle)
        assertTrue(playing.isPlaying)
        assertEquals(2_467_384L, playing.positionMs)
    }

    @Test
    fun `entiende el formato antiguo del estado y la pausa`() {
        val raw = """
            #power
            Display Power: state=ON
            #volume
            #media
              Sessions Stack - have 1 sessions:
                com.amazon.avod.thirdpartyclient/AmazonVideo (userId=0)
                  package=com.amazon.avod.thirdpartyclient
                  active=true
                  state=PlaybackState {state=2, position=51000, buffered position=0, speed=0.0, updated=1, actions=0, custom actions=[], active item id=-1, error=null}
                  metadata: size=3, description=Reacher, null, null
        """.trimIndent()

        val status = TvStatus.parse(raw)

        assertEquals(PowerState.AWAKE, status.power)
        assertNull(status.volume)
        val playing = status.nowPlaying!!
        assertEquals("Reacher", playing.title)
        assertNull(playing.subtitle)
        assertFalse(playing.isPlaying)
        assertEquals(51_000L, playing.positionMs)
    }

    @Test
    fun `en reposo y sin nada sonando`() {
        val raw = """
            #power
            mWakefulness=Asleep
            #volume
            volume is 0 in range [0..15]
            #media
              Sessions Stack - have 1 sessions:
                  package=com.google.android.tvlauncher
                  active=true
                  state=null
                  metadata: size=0, description=null
        """.trimIndent()

        val status = TvStatus.parse(raw)

        assertEquals(PowerState.ASLEEP, status.power)
        assertEquals(VolumeLevel(0, 15), status.volume)
        assertTrue(status.mediaAvailable)
        assertNull(status.nowPlaying)
    }

    @Test
    fun `una TV que no entiende las consultas deja todo en desconocido`() {
        val raw = """
            #power
            #volume
            /system/bin/sh: cmd: inaccessible or not found
            #media
        """.trimIndent()

        val status = TvStatus.parse(raw)

        assertEquals(PowerState.UNKNOWN, status.power)
        assertNull(status.volume)
        assertNull(status.nowPlaying)
        assertFalse(status.mediaAvailable)
        assertEquals(TvStatus(), TvStatus.parse(""))
    }

    @Test
    fun `una sesion parada no cuenta como algo sonando`() {
        val raw = """
            #media
              package=com.netflix.ninja
              active=true
              state=PlaybackState {state=STATE_STOPPED(1), position=0}
              metadata: size=2, description=Reacher, null, null
        """.trimIndent()

        assertNull(TvStatus.parse(raw).nowPlaying)
    }

    @Test
    fun `la consulta de estado pregunta las tres cosas de una vez`() {
        val shell = TvQuery.STATUS.shell
        assertTrue(shell.contains("#power") && shell.contains("dumpsys power"))
        assertTrue(shell.contains("#volume") && shell.contains("cmd media_session volume"))
        assertTrue(shell.contains("#media") && shell.contains("dumpsys media_session"))
    }

    @Test
    fun `el volumen exacto se fija por cmd media_session`() {
        assertEquals("cmd media_session volume --stream 3 --set 8", SetVolume(8).shell)
        assertEquals("cmd media_session volume --stream 3 --set 100", SetVolume(900).shell)
    }
}
