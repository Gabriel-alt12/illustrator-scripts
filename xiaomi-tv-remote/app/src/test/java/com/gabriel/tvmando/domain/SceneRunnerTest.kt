package com.gabriel.tvmando.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El motor de escenas se prueba entero con esperas falsas: lo que importa es el
 * orden de los comandos, que se respeten los retardos y que un fallo corte la
 * secuencia en lugar de seguir dejando la TV a medias.
 */
class SceneRunnerTest {

    private val ejecutados = mutableListOf<String>()
    private val esperas = mutableListOf<Long>()

    private fun runner(failOn: String? = null) = SceneRunner(
        execute = { shell ->
            ejecutados += shell
            if (shell == failOn) Result.failure(IllegalStateException("la TV no responde"))
            else Result.success("")
        },
        wait = { millis -> esperas += millis },
    )

    @Test
    fun `ejecuta los pasos en orden y respeta los retardos`() = runBlocking {
        val escena = Scene(
            id = "test",
            name = "Prueba",
            steps = listOf(
                SceneStep.of(PressKey(TvKey.POWER), delayMs = 5_000),
                SceneStep.of(LaunchApp("com.netflix.mediaclient"), delayMs = 2_000),
                SceneStep.of(SetVolume(8)),
            ),
        )

        val outcome = runner().run(escena)

        assertTrue(outcome is SceneOutcome.Completed)
        assertEquals(
            listOf(
                "input keyevent KEYCODE_POWER",
                LaunchApp("com.netflix.mediaclient").shell,
                "media volume --stream 3 --set 8",
            ),
            ejecutados,
        )
        assertEquals(listOf(5_000L, 2_000L), esperas)
    }

    @Test
    fun `un paso de solo espera no ejecuta nada`() = runBlocking {
        val escena = Scene(
            id = "test",
            name = "Prueba",
            steps = listOf(
                SceneStep.wait(3_000),
                SceneStep.of(PressKey(TvKey.HOME)),
            ),
        )

        runner().run(escena)

        assertEquals(listOf("input keyevent KEYCODE_HOME"), ejecutados)
        assertEquals(listOf(3_000L), esperas)
    }

    @Test
    fun `si un paso falla la escena se corta ahi`() = runBlocking {
        val escena = Scene(
            id = "test",
            name = "Prueba",
            steps = listOf(
                SceneStep.of(PressKey(TvKey.POWER), delayMs = 1_000),
                SceneStep.of(PressKey(TvKey.HOME), delayMs = 1_000),
                SceneStep.of(PressKey(TvKey.BACK)),
            ),
        )

        val outcome = runner(failOn = "input keyevent KEYCODE_HOME").run(escena)

        assertTrue("esperaba Failed, llego $outcome", outcome is SceneOutcome.Failed)
        outcome as SceneOutcome.Failed
        assertEquals(1, outcome.stepIndex)
        assertEquals("la TV no responde", outcome.message)
        // El tercer paso no llega a ejecutarse ni se espera tras el que fallo.
        assertEquals(
            listOf("input keyevent KEYCODE_POWER", "input keyevent KEYCODE_HOME"),
            ejecutados,
        )
        assertEquals(listOf(1_000L), esperas)
    }

    @Test
    fun `informa del progreso paso a paso`() = runBlocking {
        val escena = Scene(
            id = "test",
            name = "Prueba",
            steps = listOf(
                SceneStep.of(PressKey(TvKey.POWER), delayMs = 1_000),
                SceneStep.of(PressKey(TvKey.HOME)),
            ),
        )

        val progreso = mutableListOf<Pair<Int, Boolean>>()
        runner().run(escena) { progreso += it.stepIndex to it.isWaiting }

        assertEquals(listOf(0 to false, 0 to true, 1 to false), progreso)
    }

    @Test
    fun `la escena de busqueda abre la app antes de escribir`() = runBlocking {
        val escena = SceneLibrary.search(
            query = "el senor de los anillos",
            target = SearchTarget.App("com.netflix.mediaclient", "Netflix"),
        )

        runner().run(escena)

        assertEquals(
            listOf(
                LaunchApp("com.netflix.mediaclient").shell,
                "input text 'el senor de los anillos'",
                "input keyevent KEYCODE_ENTER",
            ),
            ejecutados,
        )
    }

    @Test
    fun `la busqueda de Google TV empieza por el asistente`() = runBlocking {
        runner().run(SceneLibrary.search("dune", SearchTarget.GoogleTv))

        assertEquals(
            listOf(
                "input keyevent KEYCODE_ASSIST",
                "input text 'dune'",
                "input keyevent KEYCODE_ENTER",
            ),
            ejecutados,
        )
    }

    @Test
    fun `sin destino la busqueda escribe donde este el foco`() = runBlocking {
        runner().run(SceneLibrary.search("dune", SearchTarget.Focused))

        assertEquals(
            listOf("input text 'dune'", "input keyevent KEYCODE_ENTER"),
            ejecutados,
        )
    }
}
