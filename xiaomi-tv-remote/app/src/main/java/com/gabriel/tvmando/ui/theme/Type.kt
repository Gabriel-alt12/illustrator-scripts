package com.gabriel.tvmando.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gabriel.tvmando.R

/**
 * Barlow, empaquetada en el APK (res/font, unos 330 KB en tres ficheros). Es una
 * grotesca de ingenieria que se parece a la rotulacion de un aparato, que es la
 * sensacion que busca un mando. Va dentro de la app y no se descarga: la app no
 * tiene internet ni lo quiere.
 *
 * Solo lleva los pesos que se usan. Pedir un peso que no esta (Black, por ejemplo)
 * haria que Android sintetizara uno feo, asi que la escala de abajo se cine a estos.
 * Licencia: SIL Open Font License 1.1, en docs/OFL-Barlow.txt.
 */
val Barlow = FontFamily(
    Font(R.font.barlow_bold, FontWeight.Bold),
    Font(R.font.barlow_extrabold, FontWeight.ExtraBold),
)

/** Para las etiquetas pequenas en mayusculas: condensada, aguanta el interletrado. */
val BarlowSemiCondensed = FontFamily(
    Font(R.font.barlow_semicondensed_bold, FontWeight.Bold),
)

/**
 * Jerarquia: Barlow para lo que tiene caracter (titulos y etiquetas), la sans del
 * sistema para leer (cuerpo) y la mono del sistema para datos (huellas, IPs).
 *
 *  - Titulares pesados y con interletrado negativo: compactos y con presencia.
 *  - Etiquetas pequenas en mayusculas con interletrado amplio: se leen de reojo y dan
 *    ese aire de panel de instrumentos que pide un mando.
 */
val MandoTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 36.sp,
        letterSpacing = (-1).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.sp,
    ),
)
