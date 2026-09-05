package com.gabriel.tvmando.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Escala de espaciado en multiplos de 4.
 *
 * Uso: margen de pantalla [xl], relleno interior [lg], hueco entre controles del
 * mando [lg], rejilla de apps [md] en horizontal y [lg] en vertical, entre secciones
 * [xxl]. Hasta ahora estos valores se escribian sueltos en cada pantalla.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

/**
 * Escala de radios, con una regla: cuanto mas grande es la pieza, mas redonda.
 *
 * Asi una barra de 84 dp y un chip de 34 dp se sienten de la misma familia sin tener
 * el mismo radio. Y el radio del contenedor menos su relleno es el radio del hijo
 * (24 - 6 = 18 en la barra de pestanas): eso es lo que hace que las esquinas encajen.
 */
object Radius {
    /** Chips y distintivos pequenos. */
    val chip = 10.dp
    /** Botones sueltos y campos de texto. */
    val button = 14.dp
    /** Tarjetas y pastillas de navegacion. */
    val card = 20.dp
    /** Fichas de app y la barra de pestanas. */
    val tile = 24.dp
    /** Barras anchas: volumen. */
    val bar = 28.dp
}
