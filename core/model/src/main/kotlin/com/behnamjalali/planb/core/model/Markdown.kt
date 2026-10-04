package com.behnamjalali.planb.core.model

/**
 * Practical Markdown conversion for notes. The block model is the source of
 * truth; Markdown is an interchange format. Supported: `#` headings, `- [ ]`
 * checklists, `-`/`*`/`+` bullets, `1.` numbered items, `>` quotes, `---`
 * dividers and fenced code blocks. Inline formatting is kept as literal text,
 * nested lists are flattened, and tables/images are imported as plain text.
 *
 * Text that would otherwise be read back as a different block (a paragraph starting
 * with "1. " or "> ", a bullet whose text is "--" or "[x] …") is exported with a
 * leading backslash, as in CommonMark, and the backslash is removed on import. Code
 * containing ``` lines gets a longer fence. Numbered items may use Persian or
 * Arabic-Indic digits ("۱. مورد").
 */
object Markdown {
    fun export(title: String, document: NoteDocument): String = buildString {
        if (title.isNotBlank()) append("# ").append(title.trim()).append("\n\n")
        var number = 0
        var previous: BlockType? = null
        document.blocks.forEach { block ->
            val listContinues = previous == block.type &&
                block.type in setOf(BlockType.BULLET, BlockType.CHECKLIST, BlockType.NUMBERED)
            if (previous != null && !listContinues) append('\n')
            number = if (block.type == BlockType.NUMBERED) (if (previous == BlockType.NUMBERED) number + 1 else 1) else 0
            when (block.type) {
                BlockType.TEXT -> append(block.text.lines().joinToString("\n") { escapeLine(it) })
                BlockType.HEADING -> append("## ").append(block.text)
                BlockType.CHECKLIST -> append(if (block.checked) "- [x] " else "- [ ] ").append(block.text)
                BlockType.BULLET -> append(BULLET_PREFIX).append(escapeItem(BULLET_PREFIX, block.text))
                BlockType.NUMBERED -> append(number).append(". ").append(block.text)
                BlockType.QUOTE -> append(block.text.lines().joinToString("\n") { "> $it" })
                BlockType.DIVIDER -> append("---")
                BlockType.CODE -> {
                    val fence = fenceFor(block.text)
                    append(fence).append('\n').append(block.text).append('\n').append(fence)
                }
            }
            append('\n')
            previous = block.type
        }
    }.trimEnd() + "\n"

    data class Imported(val title: String, val document: NoteDocument)

    private val checklist = Regex("""^\s*[-*+]\s+\[([ xX])]\s?(.*)$""")
    private val bullet = Regex("""^\s*[-*+]\s+(.*)$""")
    private val numbered = Regex("""^\s*[0-9\u06F0-\u06F9\u0660-\u0669]+[.)]\s+(.*)$""")
    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val divider = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private const val BULLET_PREFIX = "- "
    private const val ESCAPE = '\\'
    private const val FENCE = "```"

    /** True when [line] would be read as something other than paragraph text. */
    private fun isBlockMarker(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith(FENCE) || trimmed.startsWith(">") || divider.matches(line) || heading.matches(line) ||
            checklist.matches(line) || bullet.matches(line) || numbered.matches(line)
    }

    /**
     * Whether [text] (after [prefix]) must be escaped: it is misread as written, or it starts
     * with a backslash that the importer would otherwise remove.
     */
    private fun needsEscape(prefix: String, text: String, misread: (String) -> Boolean): Boolean {
        val rest = text.trimStart()
        val lead = text.substring(0, text.length - rest.length)
        return misread(prefix + text) ||
            (rest.startsWith(ESCAPE) && needsEscape(prefix, lead + rest.substring(1), misread))
    }

    private fun insertEscape(text: String): String {
        val rest = text.trimStart()
        return text.substring(0, text.length - rest.length) + ESCAPE + rest
    }

    private fun removeEscape(text: String): String {
        val rest = text.trimStart()
        return text.substring(0, text.length - rest.length) + rest.substring(1)
    }

    private fun escapeLine(line: String): String =
        if (needsEscape("", line, ::isBlockMarker)) insertEscape(line) else line

    private fun unescapeLine(line: String): String {
        if (!line.trimStart().startsWith(ESCAPE)) return line
        val candidate = removeEscape(line)
        return if (needsEscape("", candidate, ::isBlockMarker)) candidate else line
    }

    /** A bullet's text is misread when the whole line becomes a divider or a checklist item. */
    private fun misreadItem(line: String): Boolean = divider.matches(line) || checklist.matches(line)

    private fun escapeItem(prefix: String, text: String): String =
        if (needsEscape(prefix, text, ::misreadItem)) insertEscape(text) else text

    private fun unescapeItem(prefix: String, text: String): String {
        if (!text.trimStart().startsWith(ESCAPE)) return text
        val candidate = removeEscape(text)
        return if (needsEscape(prefix, candidate, ::misreadItem)) candidate else text
    }

    /** A fence longer than any backtick run that starts a line of [code]. */
    private fun fenceFor(code: String): String {
        val longest = code.lines().maxOfOrNull { line -> line.trimStart().takeWhile { it == '`' }.length } ?: 0
        return "`".repeat(maxOf(3, longest + 1))
    }

    fun import(markdown: String, fallbackTitle: String, newId: () -> String): Imported {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<NoteBlock>()
        var title: String? = null
        val paragraph = StringBuilder()
        val quote = mutableListOf<String>()
        var i = 0

        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks += NoteBlock(newId(), BlockType.TEXT, paragraph.toString().trim())
            paragraph.clear()
        }
        fun flushQuote() {
            if (quote.isNotEmpty()) blocks += NoteBlock(newId(), BlockType.QUOTE, quote.joinToString("\n"))
            quote.clear()
        }

        while (i < lines.size) {
            val line = lines[i]
            if (line.trimStart().startsWith(FENCE)) {
                flushParagraph(); flushQuote()
                // The closing fence is a line of at least as many backticks (and nothing else).
                val fenceLength = line.trimStart().takeWhile { it == '`' }.length
                fun isClosing(candidate: String): Boolean {
                    val t = candidate.trim()
                    return t.length >= fenceLength && t.all { it == '`' }
                }
                val code = StringBuilder()
                i++
                while (i < lines.size && !isClosing(lines[i])) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                blocks += NoteBlock(newId(), BlockType.CODE, code.toString())
                i++
                continue
            }
            if (line.trimStart().startsWith(">")) {
                flushParagraph()
                quote += line.trimStart().removePrefix(">").removePrefix(" ")
                i++
                continue
            }
            flushQuote()
            when {
                line.isBlank() -> flushParagraph()
                divider.matches(line) -> { flushParagraph(); blocks += NoteBlock(newId(), BlockType.DIVIDER) }
                heading.matches(line) -> {
                    flushParagraph()
                    val (hashes, text) = heading.find(line)!!.destructured
                    if (hashes.length == 1 && title == null && blocks.isEmpty()) title = text.trim()
                    else blocks += NoteBlock(newId(), BlockType.HEADING, text.trim())
                }
                checklist.matches(line) -> {
                    flushParagraph()
                    val (mark, text) = checklist.find(line)!!.destructured
                    blocks += NoteBlock(newId(), BlockType.CHECKLIST, text, checked = mark.equals("x", ignoreCase = true))
                }
                bullet.matches(line) -> {
                    flushParagraph()
                    blocks += NoteBlock(newId(), BlockType.BULLET, unescapeItem(BULLET_PREFIX, bullet.find(line)!!.groupValues[1]))
                }
                numbered.matches(line) -> { flushParagraph(); blocks += NoteBlock(newId(), BlockType.NUMBERED, numbered.find(line)!!.groupValues[1]) }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(unescapeLine(line))
                }
            }
            i++
        }
        flushParagraph(); flushQuote()
        return Imported(title ?: fallbackTitle, NoteDocument(blocks = blocks))
    }

    /** Plain-text export (no markup). */
    fun plainText(title: String, document: NoteDocument): String = buildString {
        if (title.isNotBlank()) append(title.trim()).append("\n\n")
        var number = 0
        var previous: BlockType? = null
        document.blocks.forEach { b ->
            number = if (b.type == BlockType.NUMBERED) (if (previous == BlockType.NUMBERED) number + 1 else 1) else 0
            when (b.type) {
                BlockType.CHECKLIST -> append(if (b.checked) "☑ " else "☐ ").append(b.text)
                BlockType.BULLET -> append("• ").append(b.text)
                BlockType.NUMBERED -> append(number).append(". ").append(b.text)
                BlockType.DIVIDER -> append("————")
                else -> append(b.text)
            }
            append('\n')
            previous = b.type
        }
    }.trimEnd() + "\n"
}
