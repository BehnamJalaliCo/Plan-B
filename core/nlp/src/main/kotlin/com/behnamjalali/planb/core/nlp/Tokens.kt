package com.behnamjalali.planb.core.nlp

/**
 * A word of the input. [start]/[end] cover the whole whitespace-separated chunk in the
 * original text (punctuation included), [text] is the chunk without surrounding punctuation
 * and [word] its matching form (see [QuickAddText.normalize] and [QuickAddText.stem]).
 */
internal class Token(val start: Int, val end: Int, val text: String, val word: String)

/** Text folding for matching only; the original text is never changed. */
internal object QuickAddText {
    private val replacements: Map<Char, Char> = mapOf(
        'ي' to 'ی', // Arabic Yeh → Persian Yeh
        'ى' to 'ی', // Alef maksura → Persian Yeh
        'ك' to 'ک', // Arabic Kaf → Persian Kaf
        'ة' to 'ه', // Teh marbuta → Heh
        'ۀ' to 'ه', // Heh with Yeh above (ezafe) → Heh
        'أ' to 'ا', // Alef with hamza above → Alef
        'إ' to 'ا', // Alef with hamza below → Alef
        'آ' to 'ا', // Alef with madda → Alef
        'ٱ' to 'ا', // Alef wasla → Alef
        'ؤ' to 'و', // Waw with hamza → Waw
        'ئ' to 'ی', // Yeh with hamza → Persian Yeh
        '٫' to '.', // Arabic decimal separator
        '：' to ':', // full-width colon
    )

    /** Invisible joiners and direction marks, dropped so «پس‌فردا» matches «پسفردا». */
    private val invisible = setOf('‌', '‍', '‎', '‏', '⁠', '﻿')

    private val leading = "\"'«“‘([{".toSet()
    private val trailing = "\"'»”’)]}.,،؛;:?؟!…".toSet()

    fun isSpace(c: Char): Boolean = c.isWhitespace() || c == '​' || c == ' ' || c == ' '

    private fun isDiacritic(c: Char): Boolean =
        c.code in 0x064B..0x065F || c.code == 0x0670 || c.code == 0x0640

    /** Persian/Arabic digits to ASCII, Arabic letters to Persian, no joiners or diacritics, lower case. */
    fun normalize(input: String): String = buildString(input.length) {
        for (c in input) {
            when {
                c in '۰'..'۹' -> append('0' + (c - '۰'))
                c in '٠'..'٩' -> append('0' + (c - '٠'))
                c in invisible || isDiacritic(c) -> Unit
                else -> append(replacements[c] ?: c.lowercaseChar())
            }
        }
    }

    private val ordinal = Regex("^(\\d+)(ام|م|th|st|nd|rd)$")

    /**
     * Matching form of a normalized word: the ezafe «ی» written after a final «ه» is dropped
     * («هفته‌ی» → «هفته»), and ordinal endings after digits («۱۵ام», "15th") too.
     */
    fun stem(word: String): String {
        ordinal.matchEntire(word)?.let { return it.groupValues[1] }
        if (word.length > 3 && word.endsWith("هی")) return word.dropLast(1)
        return word
    }

    fun key(input: String): String = input.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { stem(normalize(it)) }

    fun isPersian(word: String): Boolean = word.any { it in '؀'..'ۿ' }

    fun tokenize(text: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            if (isSpace(text[i])) {
                i++
                continue
            }
            val start = i
            while (i < text.length && !isSpace(text[i])) i++
            val chunk = text.substring(start, i)
            var from = 0
            var to = chunk.length
            // "!!" is a priority, not punctuation.
            if (chunk.any { it != '!' }) {
                while (from < to && chunk[from] in leading) from++
                while (to > from && chunk[to - 1] in trailing) to--
            }
            val core = chunk.substring(from, to)
            tokens += Token(start, i, core, stem(normalize(core)))
        }
        return tokens
    }
}
