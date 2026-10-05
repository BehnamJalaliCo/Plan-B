package com.behnamjalali.planb.core.model

/**
 * Links between notes (Plan-B Pro #16), written inside block text as a markup token
 * `[[note:ID|Title]]`. The id is the source of truth; the title is the one the note had when
 * the link was made and is only a fallback (the editor shows the target's current title).
 * Exports degrade a link to its title (plain text) or to a relative `.md` link (Markdown).
 *
 * Titles inside a token never contain `[`, `]`, `|` or line breaks, so a token always parses
 * back unchanged; text that only looks similar (a broken token) stays plain text.
 */
object NoteLinks {
    /** A link token inside a text: [start, end) in that text. */
    data class Span(val start: Int, val end: Int, val noteId: EntityId, val title: String)

    /** "[[" typed and not closed yet: the text after it (the search) runs from [start] + 2 to the caret. */
    data class Query(val start: Int, val text: String)

    private val token = Regex("""\[\[note:(\d{1,18})\|([^\[\]|\n\r]{0,$MAX_TITLE})]]""")

    /** The longest title kept inside a token. */
    const val MAX_TITLE = 200

    /** The longest search typed after "[[" that still counts as a link search. */
    private const val MAX_QUERY = 60

    fun token(noteId: EntityId, title: String): String = "[[note:$noteId|${cleanTitle(title)}]]"

    /** A title safe inside a token. */
    fun cleanTitle(title: String): String =
        title.replace(Regex("""[\[\]|\n\r]+"""), " ").replace(Regex("\\s+"), " ").trim().take(MAX_TITLE)

    fun spans(text: String): List<Span> {
        if (!text.contains("[[note:")) return emptyList()
        return token.findAll(text).mapNotNull { m ->
            val id = m.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            Span(m.range.first, m.range.last + 1, id, m.groupValues[2])
        }.toList()
    }

    /** Ids of every note linked from [document], in order of first appearance. */
    fun targets(document: NoteDocument): Set<EntityId> = buildSet {
        document.blocks.forEach { block -> spans(block.text).forEach { add(it.noteId) } }
    }

    /**
     * Replaces each link with a label: [label] gets the id and the stored title and returns the
     * text to show (by default the stored title).
     */
    fun replace(text: String, label: (EntityId, String) -> String = { _, title -> title }): String {
        val found = spans(text)
        if (found.isEmpty()) return text
        return buildString {
            var at = 0
            found.forEach { span ->
                append(text, at, span.start)
                append(label(span.noteId, span.title))
                at = span.end
            }
            append(text, at, text.length)
        }
    }

    /** Plain text: every link becomes its title (as stored, or [titles] when given). */
    fun plain(text: String, titles: (EntityId) -> String? = { null }): String =
        replace(text) { id, title -> titles(id) ?: title }

    /** Every link in [document] as plain text. */
    fun plain(document: NoteDocument, titles: (EntityId) -> String? = { null }): NoteDocument =
        if (document.blocks.none { it.text.contains("[[note:") }) {
            document
        } else {
            document.copy(blocks = document.blocks.map { it.copy(text = plain(it.text, titles)) })
        }

    /**
     * A search typed after "[[" right before [cursor]: no line break, no closing "]]" and not
     * inside a finished link. Null when the caret is not in such a search.
     */
    fun pendingQuery(text: String, cursor: Int): Query? {
        if (cursor < 2 || cursor > text.length) return null
        val open = text.lastIndexOf("[[", cursor - 2)
        if (open < 0) return null
        val typed = text.substring(open + 2, cursor)
        if (typed.length > MAX_QUERY || typed.any { it == '\n' || it == '\r' || it == ']' || it == '[' || it == '|' }) return null
        if (spans(text).any { open >= it.start && open < it.end }) return null
        return Query(open, typed)
    }

    /**
     * Replaces the pending search [query] (from "[[" to [cursor]) with a link to [noteId]. Returns
     * the new text and the caret right after the link.
     */
    fun insert(text: String, query: Query, cursor: Int, noteId: EntityId, title: String): Pair<String, Int> {
        val link = token(noteId, title)
        val end = cursor.coerceIn(query.start, text.length)
        val updated = text.substring(0, query.start) + link + text.substring(end)
        return updated to query.start + link.length
    }

    /**
     * Keeps links whole while editing: when an edit that deleted text cut into a link (for
     * example Backspace right after it), the rest of that link goes too. Returns the repaired
     * text and the caret (where the link was), or null when nothing needed repairing.
     */
    fun repairDeletion(old: String, new: String): Pair<String, Int>? {
        if (new.length >= old.length) return null
        val links = spans(old)
        if (links.isEmpty()) return null
        var prefix = 0
        while (prefix < new.length && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < new.length - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        // The deleted range of the old text is [prefix, old.length - suffix).
        val delStart = prefix
        val delEnd = old.length - suffix
        if (delEnd - delStart != old.length - new.length) return null
        val cut = links.filter { it.start < delEnd && it.end > delStart && !(it.start >= delStart && it.end <= delEnd) }
        if (cut.isEmpty()) return null
        val start = minOf(delStart, cut.minOf { it.start })
        val end = maxOf(delEnd, cut.maxOf { it.end })
        val repaired = old.substring(0, start) + old.substring(end)
        return repaired to start
    }

    /** The link that contains [offset] (or ends right at it), if any. */
    fun spanAt(text: String, offset: Int): Span? = spans(text).firstOrNull { offset > it.start && offset <= it.end }
}
