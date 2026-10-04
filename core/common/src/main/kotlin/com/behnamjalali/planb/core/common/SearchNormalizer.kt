package com.behnamjalali.planb.core.common

import java.text.Normalizer

/**
 * Normalizes text for *search indexing and matching only*. Stored originals are
 * never modified.
 *
 * - Arabic Yeh/Kaf/Teh-marbuta/Heh variants → Persian forms
 * - hamza seats fold to their base letter: أ/إ/آ → ا, ئ → ی, ؤ → و
 * - Persian/Arabic digits → ASCII digits
 * - ZWNJ (the Persian half-space) and other invisible joiners/marks are removed, so
 *   «کتاب‌ها» and «کتابها» are the same word; a zero-width space separates words
 * - tatweel and diacritics are removed
 * - case folding and whitespace collapsing
 *
 * Changing the output changes what is stored in the full-text index: bump [VERSION]
 * so existing installs rebuild their index once.
 *
 * Code points are written numerically because several of them are invisible.
 */
object SearchNormalizer {
    /** Version of the normalized form stored in the search index. */
    const val VERSION = 2

    private const val ZWNJ = '\u200C'

    private val replacements: Map<Char, Char> = mapOf(
        0x064A to 0x06CC, // Arabic Yeh → Persian Yeh
        0x0649 to 0x06CC, // Alef maksura → Persian Yeh
        0x0643 to 0x06A9, // Arabic Kaf → Persian Kaf
        0x0629 to 0x0647, // Teh marbuta → Heh
        0x06C0 to 0x0647, // Heh with Yeh above → Heh
        0x06D5 to 0x0647, // Arabic AE (NFKD of U+06C0) → Heh
        0x0623 to 0x0627, // Alef with hamza above → Alef
        0x0625 to 0x0627, // Alef with hamza below → Alef
        0x0622 to 0x0627, // Alef with madda → Alef
        0x0671 to 0x0627, // Alef wasla → Alef
        0x0624 to 0x0648, // Waw with hamza → Waw
        0x0626 to 0x06CC, // Yeh with hamza → Persian Yeh
    ).entries.associate { (from, to) -> from.toChar() to to.toChar() }

    /** Invisible characters inside words; dropped so the word stays one token. */
    private val joiners: Set<Char> = setOf(
        0x200C, // zero-width non-joiner (Persian half-space)
        0x200D, // zero-width joiner
        0x200E, // left-to-right mark
        0x200F, // right-to-left mark
        0x2060, // word joiner
        0xFEFF, // byte-order mark
    ).map { it.toChar() }.toSet()

    private const val ZERO_WIDTH_SPACE = '\u200B'

    private const val TATWEEL = 0x0640
    private const val SUPERSCRIPT_ALEF = 0x0670

    private fun isDiacritic(c: Char): Boolean =
        c.code in 0x064B..0x065F ||
            c.code == SUPERSCRIPT_ALEF ||
            c.code == TATWEEL ||
            Character.getType(c) == Character.NON_SPACING_MARK.toInt()

    fun normalize(input: String): String {
        if (input.isEmpty()) return input
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFKD)
        val sb = StringBuilder(decomposed.length)
        var lastWasSpace = true
        for (raw in decomposed) {
            if (isDiacritic(raw) || raw in joiners) continue
            val c = when {
                raw == ZERO_WIDTH_SPACE -> ' '
                raw.isWhitespace() -> ' '
                else -> replacements[raw] ?: raw
            }
            if (c == ' ') {
                if (!lastWasSpace) sb.append(' ')
                lastWasSpace = true
            } else {
                sb.append(c.lowercaseChar())
                lastWasSpace = false
            }
        }
        return Digits.toLatin(sb.toString().trimEnd())
    }

    /** Splits normalized text into search tokens (letters and digits only). */
    fun tokens(input: String): List<String> =
        normalize(input).split(' ')
            .map { token -> token.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }

    /**
     * Tokens stored in the index: the joined words from [tokens] plus, for words written
     * with a half-space, their parts, so «می‌خواهم» is found by «میخواهم» and «خواهم».
     */
    fun indexTokens(input: String): List<String> {
        val joined = tokens(input)
        if (ZWNJ !in input) return joined
        val parts = tokens(input.replace(ZWNJ, ' '))
        return joined + parts.filterNot { it in joined }.distinct()
    }
}
