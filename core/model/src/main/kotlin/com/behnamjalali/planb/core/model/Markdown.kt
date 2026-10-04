package com.behnamjalali.planb.core.model

/**
 * Practical Markdown conversion for notes. The block model is the source of
 * truth; Markdown is an interchange format. Supported: `#` headings, `- [ ]`
 * checklists, `-`/`*`/`+` bullets, `1.` numbered items, `>` quotes, `---`
 * dividers and fenced code blocks. Inline formatting is kept as literal text,
 * nested lists are flattened, and tables/images are imported as plain text.
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
                BlockType.TEXT -> append(block.text)
                BlockType.HEADING -> append("## ").append(block.text)
                BlockType.CHECKLIST -> append(if (block.checked) "- [x] " else "- [ ] ").append(block.text)
                BlockType.BULLET -> append("- ").append(block.text)
                BlockType.NUMBERED -> append(number).append(". ").append(block.text)
                BlockType.QUOTE -> append(block.text.lines().joinToString("\n") { "> $it" })
                BlockType.DIVIDER -> append("---")
                BlockType.CODE -> append("```\n").append(block.text).append("\n```")
            }
            append('\n')
            previous = block.type
        }
    }.trimEnd() + "\n"

    data class Imported(val title: String, val document: NoteDocument)

    private val checklist = Regex("""^\s*[-*+]\s+\[([ xX])]\s?(.*)$""")
    private val bullet = Regex("""^\s*[-*+]\s+(.*)$""")
    private val numbered = Regex("""^\s*\d+[.)]\s+(.*)$""")
    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val divider = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

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
            if (line.trimStart().startsWith("```")) {
                flushParagraph(); flushQuote()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
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
                bullet.matches(line) -> { flushParagraph(); blocks += NoteBlock(newId(), BlockType.BULLET, bullet.find(line)!!.groupValues[1]) }
                numbered.matches(line) -> { flushParagraph(); blocks += NoteBlock(newId(), BlockType.NUMBERED, numbered.find(line)!!.groupValues[1]) }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(line)
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
