package com.behnamjalali.planb.feature.notebooks.clipper

import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.HtmlToBlocks
import com.behnamjalali.planb.core.model.Markdown
import com.behnamjalali.planb.core.model.NoteBlock

/** What another app shared (Android's `ACTION_SEND` extras), untrusted and unvalidated. */
data class ClipInput(
    val text: String? = null,
    val html: String? = null,
    val subject: String? = null,
    val title: String? = null,
)

/** A note ready to save: [url] is the shared page's address, if any. */
data class ClipRequest(
    val title: String,
    val url: String?,
    val blocks: List<NoteBlock>,
    /** The shared text was longer than the limits and only its beginning is kept. */
    val truncated: Boolean,
)

/**
 * Turns shared text, HTML and links into a note for the web clipper (Plan-B Pro #22), offline:
 * no page is fetched. Everything is treated as untrusted: sizes are capped before parsing,
 * control and bidirectional-override characters are removed, HTML goes through the sanitizing
 * [HtmlToBlocks], and only `http`/`https` addresses are kept as the source link.
 */
object ClipParser {
    const val MAX_TEXT = 200_000
    const val MAX_HTML = HtmlToBlocks.MAX_INPUT
    const val MAX_TITLE = 200
    const val MAX_URL = 2_048

    private val urlPattern = Regex("""https?://[^\s<>"'\p{Cntrl}]+""", RegexOption.IGNORE_CASE)

    /** Null when nothing worth saving was shared. */
    fun parse(input: ClipInput, newId: () -> String, defaultTitle: String): ClipRequest? {
        val rawText = input.text.orEmpty()
        val rawHtml = input.html.orEmpty()
        var truncated = rawText.length > MAX_TEXT || rawHtml.length > MAX_HTML
        val text = HtmlToBlocks.clean(rawText.take(MAX_TEXT)).replace("\r\n", "\n").trim()
        val html = rawHtml.take(MAX_HTML)
        val url = urlPattern.find(text)?.value?.trimEnd('.', ',', ')', ']', '»', '"', '\'')?.takeIf { it.length <= MAX_URL }
        // A share of just a link: the note is the link (with the page title the app gave, if any).
        val onlyUrl = url != null && text == url
        val content: List<NoteBlock> = when {
            html.isNotBlank() -> HtmlToBlocks.convert(html, newId)
            onlyUrl -> emptyList()
            text.isNotEmpty() -> Markdown.import(text, "", newId).document.blocks
            else -> emptyList()
        }.filter { it.type == BlockType.DIVIDER || it.text.isNotBlank() }
        if (content.size >= HtmlToBlocks.MAX_BLOCKS) truncated = true
        if (content.isEmpty() && url == null) return null
        val subject = cleanLine(input.title) ?: cleanLine(input.subject)
        val importedTitle = if (html.isBlank() && !onlyUrl && text.isNotEmpty()) cleanLine(Markdown.import(text, "", newId).title) else null
        val firstLine = content.firstOrNull { it.type != BlockType.DIVIDER }?.text?.lineSequence()?.firstOrNull()?.let(::cleanLine)
        val title = subject
            ?: importedTitle
            ?: firstLine?.takeIf { it.length <= TITLE_FROM_TEXT }
            ?: url?.let(::host)
            ?: defaultTitle
        val blocks = buildList {
            // The source link stays in the note (and is searchable); it is not opened or fetched.
            if (url != null && content.none { it.text.contains(url) }) add(NoteBlock(newId(), BlockType.TEXT, url))
            addAll(content)
        }
        return ClipRequest(title.take(MAX_TITLE), url, blocks, truncated)
    }

    private const val TITLE_FROM_TEXT = 80

    private fun cleanLine(value: String?): String? =
        value?.let(HtmlToBlocks::clean)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TITLE)

    private fun host(url: String): String? = url.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.").takeIf { it.isNotBlank() }
}
