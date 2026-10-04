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

    /**
     * Formats with at most [maxFractionDigits] decimals, trimming trailing zeros. Uses the
     * shortest decimal form of [value] (BigDecimal.valueOf), so 0.15 rounds to 0.2, not 0.1.
     * Infinite values render as ∞ and NaN as a dash instead of throwing.
     */
    fun format(value: Double, maxFractionDigits: Int = 1): String {
        if (value.isNaN()) return "–"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        val rounded = java.math.BigDecimal.valueOf(value)
            .setScale(maxFractionDigits, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        val text = if (persianDigits) rounded.replace('.', '٫') else rounded
        return Digits.localize(text, persianDigits)
    }

    /**
     * Whole percent, rounded down so an unfinished value never reads 100%. A tiny epsilon
     * absorbs float error (0.53f * 100 = 52.99999…) before flooring.
     */
    fun percent(fraction: Float): String {
        val clamped = fraction.coerceIn(0f, 1f)
        var pct = kotlin.math.floor(clamped.toDouble() * 100 + PERCENT_EPSILON).toInt()
        if (clamped < 1f) pct = pct.coerceAtMost(99)
        return if (persianDigits) "${format(pct)}٪" else "${format(pct)}%"
    }

    fun twoDigits(value: Int): String = Digits.localize(value.toString().padStart(2, '0'), persianDigits)

    fun localize(text: String): String = Digits.localize(text, persianDigits)

    private companion object {
        const val PERCENT_EPSILON = 1e-4
    }
}
