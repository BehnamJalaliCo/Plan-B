package com.behnamjalali.planb.core.common

/**
 * Central digit conversion. Only apply to *display* strings — never to stored
 * identifiers, URLs or file names.
 */
object Digits {
    private const val PERSIAN_ZERO = '۰'
    private const val ARABIC_ZERO = '٠'

    fun toPersian(input: String): String {
        if (input.none { it in '0'..'9' }) return input
        val out = CharArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            out[i] = if (c in '0'..'9') PERSIAN_ZERO + (c - '0') else c
        }
        return String(out)
    }

    /** Converts Persian (۰-۹) and Arabic-Indic (٠-٩) digits to ASCII. */
    fun toLatin(input: String): String {
        if (input.none { it.isNonLatinDigit() }) return input
        val out = CharArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            out[i] = when (c) {
                in PERSIAN_ZERO..PERSIAN_ZERO + 9 -> '0' + (c - PERSIAN_ZERO)
                in ARABIC_ZERO..ARABIC_ZERO + 9 -> '0' + (c - ARABIC_ZERO)
                else -> c
            }
        }
        return String(out)
    }

    fun localize(input: String, persian: Boolean): String = if (persian) toPersian(input) else input

    private fun Char.isNonLatinDigit() =
        this in PERSIAN_ZERO..PERSIAN_ZERO + 9 || this in ARABIC_ZERO..ARABIC_ZERO + 9
}

/** Formats numbers for display according to the user's digit preference. */
class NumberFormatter(val persianDigits: Boolean) {
    fun format(value: Int): String = Digits.localize(value.toString(), persianDigits)

    fun format(value: Long): String = Digits.localize(value.toString(), persianDigits)

    /** Formats with at most [maxFractionDigits] decimals, trimming trailing zeros. */
    fun format(value: Double, maxFractionDigits: Int = 1): String {
        val rounded = java.math.BigDecimal(value)
            .setScale(maxFractionDigits, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        val text = if (persianDigits) rounded.replace('.', '٫') else rounded
        return Digits.localize(text, persianDigits)
    }

    fun percent(fraction: Float): String {
        val pct = (fraction.coerceIn(0f, 1f) * 100).toInt()
        return if (persianDigits) "${format(pct)}٪" else "${format(pct)}%"
    }

    fun twoDigits(value: Int): String = Digits.localize(value.toString().padStart(2, '0'), persianDigits)

    fun localize(text: String): String = Digits.localize(text, persianDigits)
}
