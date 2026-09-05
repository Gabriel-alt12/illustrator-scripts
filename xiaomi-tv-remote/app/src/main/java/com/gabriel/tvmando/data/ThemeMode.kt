package com.gabriel.tvmando.data

/**
 * Como se pinta la app. Oscuro es el valor de siempre y tambien el que se usa si en
 * el DataStore hay algo que no se reconoce (una version futura, un dato corrupto):
 * mejor el tema de siempre que reventar al arrancar.
 */
enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM;

    companion object {
        fun from(raw: String?): ThemeMode = entries.firstOrNull { it.name == raw } ?: DARK
    }
}
