package com.behnamjalali.planb.core.model

/**
 * A small, sanitizing HTML → note blocks converter for the web clipper (Plan-B Pro #22).
 *
 * Only text survives: no markup, attribute or script ever reaches a note. Paragraphs and other
 * block elements become text blocks, `h1`–`h6` headings, `li` bullet or numbered items (by the
 * enclosing list), `blockquote` quotes, `pre` code, `hr` dividers and table rows text lines with
 * ` | ` between cells. A link keeps its text and, when it points elsewhere, its address in
 * parentheses, but only for `http`, `https` and `mailto` addresses. The content of `script`,
 * `style`, `head`, `template`, `iframe`, `object`, `svg`, `math`, form controls and the like is
 * dropped whole, as are comments and declarations. Entities are decoded; control and
 * bidirectional-override characters are removed. Malformed markup never throws: unknown or
 * unclosed tags are simply ignored.
 *
 * Limits keep a hostile page from exhausting memory: input beyond [MAX_INPUT] characters is cut,
 * at most [MAX_BLOCKS] blocks of at most [MAX_BLOCK_TEXT] characters are produced.
 */
object HtmlToBlocks {
    const val MAX_INPUT = 500_000
    const val MAX_BLOCKS = 2_000
    const val MAX_BLOCK_TEXT = 20_000

    private val skipped = setOf(
        "script", "style", "head", "title", "noscript", "template", "iframe", "frame", "frameset", "object", "embed",
        "applet", "svg", "math", "canvas", "audio", "video", "select", "textarea", "button", "form", "nav", "picture",
    )
    private val blocks = setOf(
        "p", "div", "section", "article", "header", "footer", "main", "aside", "figure", "figcaption", "address",
        "dl", "dt", "dd", "fieldset", "details", "summary", "center", "table", "thead", "tbody", "tfoot", "caption",
        "body", "html",
    )
    private val headings = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val rawText = setOf("script", "style", "textarea", "title")

    fun convert(html: String, newId: () -> String): List<NoteBlock> = Converter(html.take(MAX_INPUT), newId).run()

    private class Converter(private val src: String, private val newId: () -> String) {
        private val out = ArrayList<NoteBlock>()
        private val text = StringBuilder()
        private var type = BlockType.TEXT
        private val lists = ArrayDeque<Boolean>() // true = ordered
        private var quoteDepth = 0
        private var preDepth = 0
        private var linkHref: String? = null
        private var linkStart = 0
        private var cellInRow = 0
        private var pos = 0

        fun run(): List<NoteBlock> {
            while (pos < src.length && out.size < MAX_BLOCKS) {
                val lt = src.indexOf('<', pos)
                if (lt < 0) {
                    addText(src.substring(pos))
                    pos = src.length
                    break
                }
                if (lt > pos) addText(src.substring(pos, lt))
                pos = lt
                readMarkup()
            }
            flush()
            return out.take(MAX_BLOCKS)
        }

        /** At a '<': a comment, declaration, end tag or start tag; a lone '<' is text. */
        private fun readMarkup() {
            when {
                src.startsWith("<!--", pos) -> {
                    val end = src.indexOf("-->", pos + 4)
                    pos = if (end < 0) src.length else end + 3
                }
                src.startsWith("<!", pos) || src.startsWith("<?", pos) -> {
                    val end = src.indexOf('>', pos)
                    pos = if (end < 0) src.length else end + 1
                }
                src.startsWith("</", pos) -> {
                    val tag = readTag(pos + 2)
                    if (tag == null) {
                        addText("<")
                        pos++
                    } else {
                        endTag(tag.name)
                    }
                }
                else -> {
                    val tag = readTag(pos + 1)
                    if (tag == null) {
                        addText("<")
                        pos++
                    } else {
                        startTag(tag)
                    }
                }
            }
        }

        private class Tag(val name: String, val attributes: Map<String, String>, val selfClosing: Boolean)

        /** Reads a tag name and attributes from [from] up to and including '>'; null when [from] does not start a name. */
        private fun readTag(from: Int): Tag? {
            var i = from
            while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '-' || src[i] == ':')) i++
            if (i == from || !src[from].isLetter()) return null
            val name = src.substring(from, i).lowercase()
            val attributes = HashMap<String, String>()
            var selfClosing = false
            while (i < src.length) {
                val c = src[i]
                when {
                    c == '>' -> {
                        i++
                        break
                    }
                    c == '/' -> {
                        selfClosing = true
                        i++
                    }
                    c.isWhitespace() -> i++
                    else -> {
                        val start = i
                        while (i < src.length && !src[i].isWhitespace() && src[i] != '=' && src[i] != '>' && src[i] != '/') i++
                        val key = src.substring(start, i).lowercase()
                        while (i < src.length && src[i].isWhitespace()) i++
                        var value = ""
                        if (i < src.length && src[i] == '=') {
                            i++
                            while (i < src.length && src[i].isWhitespace()) i++
                            if (i < src.length && (src[i] == '"' || src[i] == '\'')) {
                                val quote = src[i]
                                val end = src.indexOf(quote, i + 1)
                                value = src.substring(i + 1, if (end < 0) src.length else end)
                                i = if (end < 0) src.length else end + 1
                            } else {
                                val vStart = i
                                while (i < src.length && !src[i].isWhitespace() && src[i] != '>') i++
                                value = src.substring(vStart, i)
                            }
                        }
                        if (key.isNotEmpty() && attributes.size < MAX_ATTRIBUTES) attributes[key] = value
                        if (i == start) i++
                    }
                }
            }
            pos = i
            return Tag(name, attributes, selfClosing)
        }

        private fun startTag(tag: Tag) {
            val name = tag.name
            when {
                name in skipped -> if (!tag.selfClosing) skipElement(name)
                name == "br" -> text.append(if (preDepth > 0 || type == BlockType.TEXT || type == BlockType.QUOTE) '\n' else ' ')
                name == "hr" -> {
                    flush()
                    add(BlockType.DIVIDER, "")
                }
                name in headings -> {
                    flush()
                    type = BlockType.HEADING
                }
                name == "ul" || name == "ol" || name == "menu" -> {
                    flush()
                    lists.addLast(name == "ol")
                }
                name == "li" -> {
                    flush()
                    type = if (lists.lastOrNull() == true) BlockType.NUMBERED else BlockType.BULLET
                }
                name == "blockquote" -> {
                    flush()
                    quoteDepth++
                }
                name == "pre" -> {
                    flush()
                    preDepth++
                    type = BlockType.CODE
                }
                name == "tr" -> {
                    flush()
                    cellInRow = 0
                }
                name == "td" || name == "th" -> {
                    if (cellInRow++ > 0) text.append(" | ")
                }
                name == "a" -> {
                    linkHref = safeHref(tag.attributes["href"])
                    linkStart = text.length
                }
                name == "img" -> tag.attributes["alt"]?.let { alt -> if (alt.isNotBlank()) addText(" $alt ") }
                name in blocks -> flush()
            }
        }

        private fun endTag(name: String) {
            when {
                name in headings || name == "li" || name == "tr" || name in blocks -> flush()
                name == "ul" || name == "ol" || name == "menu" -> {
                    flush()
                    lists.removeLastOrNull()
                }
                name == "blockquote" -> {
                    flush()
                    if (quoteDepth > 0) quoteDepth--
                }
                name == "pre" -> {
                    flush()
                    if (preDepth > 0) preDepth--
                }
                name == "a" -> {
                    val href = linkHref
                    linkHref = null
                    if (href != null && linkStart <= text.length) {
                        val label = text.substring(linkStart).trim()
                        if (label.isEmpty()) {
                            text.append(href)
                        } else if (label != href && label.removeSuffix("/") != href.removeSuffix("/")) {
                            text.append(" (").append(href).append(')')
                        }
                    }
                }
            }
        }

        /** Drops everything up to the matching end tag (nested ones of the same name included). */
        private fun skipElement(name: String) {
            if (name in rawText) {
                val end = src.indexOf("</$name", pos, ignoreCase = true)
                if (end < 0) {
                    pos = src.length
                } else {
                    val close = src.indexOf('>', end)
                    pos = if (close < 0) src.length else close + 1
                }
                return
            }
            var depth = 1
            while (depth > 0 && pos < src.length) {
                val lt = src.indexOf('<', pos)
                if (lt < 0) {
                    pos = src.length
                    return
                }
                pos = lt
                when {
                    src.startsWith("<!--", pos) -> {
                        val end = src.indexOf("-->", pos + 4)
                        pos = if (end < 0) src.length else end + 3
                    }
                    src.startsWith("</", pos) -> {
                        val tag = readTag(pos + 2)
                        if (tag == null) pos++ else if (tag.name == name) depth--
                    }
                    else -> {
                        val tag = readTag(pos + 1)
                        if (tag == null) pos++ else if (tag.name == name && !tag.selfClosing) depth++
                    }
                }
            }
        }

        private fun addText(raw: String) {
            val decoded = clean(decodeEntities(raw))
            if (preDepth > 0) {
                text.append(decoded)
            } else {
                val collapsed = decoded.replace(whitespace, " ")
                if (collapsed == " " && (text.isEmpty() || text.last().isWhitespace())) return
                if (collapsed.startsWith(' ') && (text.isEmpty() || text.last().isWhitespace())) {
                    text.append(collapsed.trimStart())
                } else {
                    text.append(collapsed)
                }
            }
            if (text.length > MAX_BLOCK_TEXT * 2) flush()
        }

        private fun flush() {
            val value = if (type == BlockType.CODE) text.toString().trim('\n', '\r') else text.toString().lines().joinToString("\n") { it.trim() }.trim()
            text.clear()
            linkStart = 0
            val kind = if (quoteDepth > 0 && type == BlockType.TEXT) BlockType.QUOTE else type
            type = if (preDepth > 0) BlockType.CODE else BlockType.TEXT
            if (value.isBlank()) return
            add(kind, value.take(MAX_BLOCK_TEXT))
        }

        private fun add(kind: BlockType, value: String) {
            if (out.size < MAX_BLOCKS) out += NoteBlock(newId(), kind, value)
        }
    }

    private const val MAX_ATTRIBUTES = 32
    private val whitespace = Regex("\\s+")

    /** Only web and mail links are kept; anything else (javascript:, data:, file:, intent:) is dropped. */
    internal fun safeHref(raw: String?): String? {
        val href = raw?.let { clean(decodeEntities(it)) }?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2_048 } ?: return null
        if (href.any { it.isWhitespace() || it.code < 0x20 }) return null
        val scheme = href.substringBefore(':', "").lowercase()
        return if (scheme == "http" || scheme == "https" || scheme == "mailto") href else null
    }

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "laquo" to "«", "raquo" to "»",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "bull" to "•", "middot" to "·",
        "copy" to "©", "reg" to "®", "trade" to "™", "times" to "×", "deg" to "°",
        "zwnj" to Char(0x200C).toString(), "zwj" to Char(0x200D).toString(),
    )
    private val entity = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});")

    internal fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return entity.replace(text) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") -> codePoint(body.substring(2).toIntOrNull(16))
                body.startsWith("#") -> codePoint(body.substring(1).toIntOrNull())
                else -> named[body] ?: m.value
            }
        }
    }

    private fun codePoint(code: Int?): String {
        if (code == null || code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF) return ""
        return String(Character.toChars(code))
    }

    /** Removes control characters (but line breaks and tabs) and bidirectional overrides. */
    internal fun clean(text: String): String {
        if (text.none { isUnsafe(it) }) return text
        return text.filterNot { isUnsafe(it) }
    }

    private fun isUnsafe(c: Char): Boolean {
        val code = c.code
        return (code < 0x20 && c != '\n' && c != '\t' && c != '\r') || code == 0x7F || code in 0x80..0x9F ||
            code in 0x202A..0x202E || code in 0x2066..0x2069
    }
}
