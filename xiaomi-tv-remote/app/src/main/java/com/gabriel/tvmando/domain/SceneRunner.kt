package com.gabriel.tvmando.domain

import kotlinx.coroutines.delay

/** Por donde va una escena mientras se ejecuta. */
data class SceneProgress(
    val scene: Scene,
    val stepIndex: Int,
    val isWaiting: Boolean,
) {
    val step: SceneStep? get() = scene.steps.getOrNull(stepIndex)
    val total: Int get() = scene.steps.size
}

/** Como acabo una escena. */
sealed interface SceneOutcome {
    data class Completed(val scene: Scene) : SceneOutcome

    /** Un paso fallo; la escena se corta ahi para no dejar la TV a medias. */
    data class Failed(
        val scene: Scene,
        val stepIndex: Int,
        val message: String,
    ) : SceneOutcome
}

/**
 * Motor de escenas.
 *
 * No sabe nada de ADB ni de Android: recibe una funcion para ejecutar una linea de
 * shell y otra para esperar. Asi el motor se prueba entero en la JVM, con esperas
 * falsas, sin televisor ni relojes de verdad.
 *
 * Si un paso falla se para: encadenar el resto de una escena sobre una TV que no
 * responde solo sirve para dejarla en un estado raro.
 */
class SceneRunner(
    private val execute: suspend (String) -> Result<String>,
    private val wait: suspend (Long) -> Unit = { millis -> delay(millis) },
) {

    suspend fun run(
        scene: Scene,
        onProgress: (SceneProgress) -> Unit = {},
    ): SceneOutcome {
        scene.steps.forEachIndexed { index, step ->
            if (step.shell != null) {
                onProgress(SceneProgress(scene, index, isWaiting = false))
                val result = execute(step.shell)
                val error = result.exceptionOrNull()
                if (error != null) {
                    return SceneOutcome.Failed(
                        scene = scene,
                        stepIndex = index,
                        message = error.message ?: "Fallo en ${step.label}",
                    )
                }
            }
            if (step.delayMs > 0) {
                onProgress(SceneProgress(scene, index, isWaiting = true))
                wait(step.delayMs)
            }
        }
        return SceneOutcome.Completed(scene)
    }
}
