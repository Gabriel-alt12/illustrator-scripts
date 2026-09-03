package com.gabriel.tvmando.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las escenas se guardan en el DataStore como texto, asi que el codec es lo unico
 * que separa al usuario de perder su "Modo cine" al reiniciar. Los casos feos
 * (comillas, saltos de linea, acentos, separadores del propio formato) van dentro.
 */
class SceneCodecTest {

    @Test
    fun `ida y vuelta de las escenas de fabrica`() {
        val original = SceneLibrary.defaults()
        assertEquals(original, SceneCodec.decode(SceneCodec.encode(original)))
    }

    @Test
    fun `sobrevive a textos con separadores del formato dentro`() {
        val escena = Scene(
            id = "rara",
            name = "Escena ; con , separadores : dentro",
            steps = listOf(
                SceneStep.of(TypeText("rock 'n' roll, 50%; \"comillas\"")),
                SceneStep.of(TypeText("dos\nlineas"), delayMs = 1_500),
                SceneStep.wait(500),
            ),
        )

        val recuperada = SceneCodec.decode(SceneCodec.encode(listOf(escena))).single()

        assertEquals(escena, recuperada)
        assertEquals("rock 'n' roll, 50%; \"comillas\"", recuperada.steps[0].shell?.let {
            it.removePrefix("input text '").removeSuffix("'").replace("'\\''", "'")
        })
    }

    @Test
    fun `un paso de solo espera vuelve sin comando`() {
        val escena = Scene("e", "E", listOf(SceneStep.wait(2_000)))
        val recuperado = SceneCodec.decode(SceneCodec.encode(listOf(escena))).single().steps.single()

        assertTrue(recuperado.isWaitOnly)
        assertEquals(2_000L, recuperado.delayMs)
    }

    @Test
    fun `una escena sin pasos se conserva`() {
        val escena = Scene("vacia", "Vacia", emptyList())
        assertEquals(listOf(escena), SceneCodec.decode(SceneCodec.encode(listOf(escena))))
    }

    @Test
    fun `descarta lineas corruptas sin perder las demas`() {
        val bueno = SceneCodec.encode(listOf(SceneLibrary.defaults().first()))
        val mezcla = "basura sin formato\n$bueno\n2;version;futura;\n"

        val escenas = SceneCodec.decode(mezcla)

        assertEquals(1, escenas.size)
        assertEquals("Modo cine", escenas.single().name)
    }

    @Test
    fun `un texto vacio no da escenas`() {
        assertEquals(emptyList<Scene>(), SceneCodec.decode(""))
        assertEquals(emptyList<Scene>(), SceneCodec.decode("\n\n"))
    }

    @Test
    fun `las escenas de fabrica son las de la especificacion mas las del salon`() {
        assertEquals(
            listOf(
                "Modo cine",
                "Modo musica",
                "Apagar todo",
                "Silencio ya",
                "Llega visita",
                "Musica de fondo",
            ),
            SceneLibrary.defaults().map { it.name },
        )
    }

    @Test
    fun `silencio ya no tiene esperas, que para eso esta`() {
        val escena = SceneLibrary.defaults().single { it.id == "silencio" }
        assertEquals(0L, escena.totalDurationMs)
    }
}
