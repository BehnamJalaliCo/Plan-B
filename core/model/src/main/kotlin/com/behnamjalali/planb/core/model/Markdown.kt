package com.behnamjalali.planb.core.model

import com.behnamjalali.planb.core.model.rich.ChartMapping
import com.behnamjalali.planb.core.model.rich.ColumnType
import com.behnamjalali.planb.core.model.rich.DbValues
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.model.rich.TableData

/**
 * A file a rich block refers to, as seen by a Markdown export. [path] is the relative link to
 * the file inside the export (the Markdown ZIP), or null when the files are not part of the
 * export (a single `.md` file); then the link names the file only.
 */
data class MarkdownAttachment(
    val path: String?,
    val displayName: String,
    val transcript: String? = null,
    val ocrText: String? = null,
)

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
 *
 * Plan-B Pro blocks: tables and databases become GFM tables, formulas `$$…$$` blocks, charts a
 * table with the title as caption, and images, scans, drawings (their PNG), files and recordings
 * links to their files (with a scan's text and a recording's transcript quoted below). GFM
 * tables and `$$` blocks are imported back as table and formula blocks; image links stay text.
 */
object Markdown {
    /**
     * [attachments] describes the files of rich blocks (Plan-B Pro); [linkTarget] gives the
     * relative path of the `.md` file of a linked note (Plan-B Pro #16) from its id and stored
     * title, or null to write the link as its title only.
     */
    fun export(
        title: String,
        document: NoteDocument,
        attachments: (Long) -> MarkdownAttachment? = { null },
        linkTarget: (EntityId, String) -> String? = { _, _ -> null },
    ): String = buildString {
        if (title.isNotBlank()) append("# ").append(title.trim()).append("\n\n")
        var number = 0
        var previous: BlockType? = null
        document.blocks.map { it.copy(text = linksToMarkdown(it.text, it.type, linkTarget)) }.forEach { block ->
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
                BlockType.TABLE -> append(gfmTable(RichBlocks.table(block)))
                BlockType.DATABASE -> append(databaseTable(block))
                BlockType.MATH -> append(MATH_FENCE).append('\n').append(block.text.trim()).append('\n').append(MATH_FENCE)
                BlockType.CHART -> {
                    val chart = RichBlocks.chart(block)
                    val points = ChartMapping.resolve(chart, document.blocks)
                    append(gfmTable(TableData(listOf(listOf("", "")) + points.map { listOf(it.label, formatNumber(it.value)) })))
                    if (chart.title.isNotBlank()) append("\n\n*").append(chart.title.trim()).append('*')
                }
                BlockType.IMAGE, BlockType.SCAN, BlockType.DRAWING -> {
                    val file = block.attachmentId?.let(attachments)
                    append("![").append(linkText(block.text)).append("](").append(fileLink(file)).append(')')
                    file?.ocrText?.takeIf { block.type == BlockType.SCAN && it.isNotBlank() }?.let { append("\n\n").append(quoted(it)) }
                }
                BlockType.FILE, BlockType.AUDIO -> {
                    val file = block.attachmentId?.let(attachments)
                    val label = block.text.ifBlank { file?.displayName.orEmpty() }.ifBlank { block.type.key }
                    append('[').append(linkText(label)).append("](").append(fileLink(file)).append(')')
                    file?.transcript?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(quoted(it)) }
                }
            }
            append('\n')
            previous = block.type
        }
    }.trimEnd() + "\n"

    private const val MATH_FENCE = "$$"

    private fun quoted(text: String) = text.trim().lines().joinToString("\n") { "> $it" }

    private fun linkText(text: String) = text.replace("\n", " ").replace("[", "\\[").replace("]", "\\]")

    /** A relative link; spaces and parentheses are percent-encoded so every reader keeps it whole. */
    private fun fileLink(file: MarkdownAttachment?): String {
        val raw = file?.path ?: file?.displayName.orEmpty()
        return raw.replace("%", "%25").replace(" ", "%20").replace("(", "%28").replace(")", "%29")
    }

    private fun formatNumber(value: Double): String =
        if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()

    private fun cell(text: String) = text.replace("|", "\\|").replace("\r", "").replace("\n", "<br>")

    /** A GFM table; without a header row the header line is left empty, as GFM needs one. */
    private fun gfmTable(table: TableData): String {
        val columns = table.columnCount.coerceAtLeast(1)
        val head = if (table.header) table.rows.first() else List(columns) { "" }
        val body = if (table.header) table.rows.drop(1) else table.rows
        fun line(cells: List<String>) = "| " + List(columns) { cell(cells.getOrElse(it) { "" }) }.joinToString(" | ") + " |"
        return (listOf(line(head), "|" + List(columns) { " --- " }.joinToString("|") + "|") + body.map(::line)).joinToString("\n")
    }

    /** All rows (the filter is a view setting) in the database's sort order; dates stay ISO. */
    private fun databaseTable(block: NoteBlock): String {
        val db = RichBlocks.database(block)
        val rows = db.copy(filter = null).visibleRows().map { row ->
            db.columns.map { column ->
                val value = row.cells[column.id].orEmpty()
                if (column.type == ColumnType.CHECKBOX) (if (DbValues.isTruthy(value)) "[x]" else "[ ]") else value
            }
        }
        return gfmTable(TableData(listOf(db.columns.map { it.name }) + rows, header = true))
    }

    /** The cells of a GFM table line (`\|` is a literal bar, `<br>` a line break). */
    private fun tableCells(line: String): List<String> {
        var body = line.trim().removePrefix("|")
        if (body.endsWith("|") && !body.endsWith("\\|")) body = body.dropLast(1)
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && body.getOrNull(i + 1) == '|' -> { current.append('|'); i++ }
                c == '|' -> { cells += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells.map { it.trim().replace("<br>", "\n") }
    }

    data class Imported(val title: String, val document: NoteDocument)

    /** Links become `[Title](<path>)`, or the title alone without a path or inside code. */
    private fun linksToMarkdown(text: String, type: BlockType, linkTarget: (EntityId, String) -> String?): String =
        NoteLinks.replace(text) { id, title ->
            val path = if (type == BlockType.CODE) null else linkTarget(id, title)?.replace(">", "%3E")
            if (path == null) title else "[${title.ifBlank { path }}](<$path>)"
        }

    private val checklist = Regex("""^\s*[-*+]\s+\[([ xX])]\s?(.*)$""")
    private val bullet = Regex("""^\s*[-*+]\s+(.*)$""")
    private val numbered = Regex("""^\s*[0-9\u06F0-\u06F9\u0660-\u0669]+[.)]\s+(.*)$""")
    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val tableSeparator = Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$""")
    private val divider = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private const val BULLET_PREFIX = "- "
    private const val ESCAPE = '\\'
    private const val FENCE = "```"

    /** True when [line] would be read as something other than paragraph text. */
    private fun isBlockMarker(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith(FENCE) || trimmed.startsWith(">") || trimmed.startsWith("|") || trimmed.startsWith(MATH_FENCE) ||
            divider.matches(line) || heading.matches(line) ||
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
            if (line.trimStart().startsWith(MATH_FENCE)) {
                flushParagraph(); flushQuote()
                val first = line.trim().removePrefix(MATH_FENCE)
                val formula = StringBuilder()
                i++
                if (first.endsWith(MATH_FENCE)) {
                    formula.append(first.removeSuffix(MATH_FENCE))
                } else {
                    formula.append(first)
                    while (i < lines.size && !lines[i].trim().endsWith(MATH_FENCE)) {
                        if (formula.isNotEmpty()) formula.append('\n')
                        formula.append(lines[i])
                        i++
                    }
                    if (i < lines.size) {
                        val last = lines[i].trim().removeSuffix(MATH_FENCE)
                        if (last.isNotBlank()) formula.append(if (formula.isEmpty()) "" else "\n").append(last)
                        i++
                    }
                }
                blocks += NoteBlock(newId(), BlockType.MATH, formula.toString().trim())
                continue
            }
            if (line.trimStart().startsWith("|") && lines.getOrNull(i + 1)?.let(tableSeparator::matches) == true) {
                flushParagraph(); flushQuote()
                val header = tableCells(line)
                val rows = mutableListOf(header)
                i += 2
                while (i < lines.size && lines[i].trimStart().startsWith("|")) rows += tableCells(lines[i++])
                val hasHeader = header.any { it.isNotBlank() }
                val table = TableData(if (hasHeader) rows else rows.drop(1).ifEmpty { listOf(header) }, header = hasHeader).normalized()
                blocks += NoteBlock(newId(), BlockType.TABLE, data = RichBlocks.encode(table))
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
    fun plainText(title: String, document: NoteDocument, titles: (EntityId) -> String? = { null }): String = buildString {
        if (title.isNotBlank()) append(title.trim()).append("\n\n")
        var number = 0
        var previous: BlockType? = null
        NoteLinks.plain(document, titles).blocks.forEach { b ->
            number = if (b.type == BlockType.NUMBERED) (if (previous == BlockType.NUMBERED) number + 1 else 1) else 0
            when (b.type) {
                BlockType.CHECKLIST -> append(if (b.checked) "☑ " else "☐ ").append(b.text)
                BlockType.BULLET -> append("• ").append(b.text)
                BlockType.NUMBERED -> append(number).append(". ").append(b.text)
                BlockType.DIVIDER -> append("————")
                BlockType.TABLE -> append(RichBlocks.table(b).rows.joinToString("\n") { it.joinToString("\t") })
                else -> append(RichBlocks.plainText(b))
            }
            append('\n')
            previous = b.type
        }
    }.trimEnd() + "\n"
}
