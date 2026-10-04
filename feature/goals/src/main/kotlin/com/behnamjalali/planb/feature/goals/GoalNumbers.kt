package com.behnamjalali.planb.feature.goals

import com.behnamjalali.planb.core.common.Digits
import java.math.BigDecimal

/** Parsing and editing of goal amounts, shared by the goal editor and the progress dialog. */
object GoalNumbers {
    /** Largest amount accepted; anything bigger is a typo (and too big to format reliably). */
    const val MAX_VALUE = 1e12

    private val number = Regex("""\d+(\.\d*)?|\.\d+""")

    /**
     * Parses a non-negative amount typed in any of the app's number styles: Persian (۰-۹) and
     * Arabic-Indic (٠-٩) digits, ',' and the Arabic thousands separator '٬' as grouping
     * ("10,000" is ten thousand), '.' and the Arabic decimal separator '٫' as the decimal point.
     * Returns null for anything else, and for non-finite or absurdly large values.
     */
    fun parse(text: String): Double? {
        val normalized = Digits.toLatin(text.trim())
            .replace(",", "")
            .replace("٬", "")
            .replace('٫', '.')
        if (!number.matches(normalized)) return null
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it <= MAX_VALUE }
    }

    /** Editable text for a stored amount, without exponents or a trailing ".0". */
    fun format(value: Double): String =
        if (!value.isFinite()) "0" else BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
