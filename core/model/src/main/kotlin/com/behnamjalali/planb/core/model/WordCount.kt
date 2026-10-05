package com.behnamjalali.planb.core.model

/**
 * Word and character counts for the writing mode (Plan-B Pro #24), Persian-aware:
 *
 * - A word is a run of letters, digits and in-word marks. The Persian half-space (ZWNJ,
 *   U+200C) and the zero-width joiner keep a word together, so «می‌روم» and «کتاب‌ها» are one
 *   word each, like «می‌روم» written without it.
 * - Apostrophes and hyphens between letters stay inside the word ("don't", "e-mail").
 * - Punctuation (Persian «،» «؛» «؟» too), spaces and symbols separate words; digits count
 *   (Persian, Arabic-Indic and Latin), so «۱۴۰۵» is a word.
 * - Links to notes count as their titles.
 *
 * Characters are the visible characters plus spaces: invisible joiners and marks are not
 * counted, and a surrogate pair (an emoji) counts once.
 */
object WordCount {
    data class Counts(val words: Int, val characters: Int)

    fun of(document: NoteDocument): Counts {
        var words = 0
        var characters = 0
        document.blocks.forEach { block ->
            if (block.type == BlockType.DIVIDER) return@forEach
            val text = NoteLinks.plain(block.text)
            words += words(text)
            characters += characters(text)
        }
        return Counts(words, characters)
    }

    fun words(text: String): Int {
        var count = 0
        var inWord = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val part = when {
                isWordChar(c) -> true
                // Joiners and in-word punctuation keep a word going only between word characters.
                inWord && (c in JOINERS || c in IN_WORD) && i + 1 < text.length && (isWordChar(text[i + 1]) || text[i + 1] in JOINERS) -> true
                inWord && Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> true
                else -> false
            }
            if (part && !inWord) count++
            inWord = part
            i++
        }
        return count
    }

    fun characters(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                    count++
                    i++
                }
                c in JOINERS || c in INVISIBLE -> Unit
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> Unit
                Character.isISOControl(c) && c != '\n' && c != '\t' -> Unit
                c == '\n' -> Unit
                else -> count++
            }
            i++
        }
        return count
    }

    private fun isWordChar(c: Char): Boolean = Character.isLetterOrDigit(c) && c.code != TATWEEL

    private const val TATWEEL = 0x0640
    private val JOINERS: Set<Char> = setOf(0x200C, 0x200D).map { it.toChar() }.toSet()
    private val INVISIBLE: Set<Char> = setOf(0x200B, 0x200E, 0x200F, 0x2060, 0xFEFF, 0x0640).map { it.toChar() }.toSet()
    private val IN_WORD: Set<Char> = setOf(0x27, 0x2019, 0x2D, 0x2011).map { it.toChar() }.toSet()
}
