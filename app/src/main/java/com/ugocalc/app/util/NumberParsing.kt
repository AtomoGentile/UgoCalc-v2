package com.ugocalc.app.util

/**
 * Converte una stringa numerica in [Double] accettando sia la virgola italiana (`,`) sia il punto (`.`)
 * come separatore decimale, scartando valori non finiti (`NaN`, `Infinity`).
 */
fun String.parseLocaleDoubleOrNull(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/**
 * Come [parseLocaleDoubleOrNull], ma restituisce `null` se il numero è minore o uguale a zero.
 */
fun String.parsePositiveLocaleDoubleOrNull(): Double? =
    parseLocaleDoubleOrNull()?.takeIf { it > 0.0 }
