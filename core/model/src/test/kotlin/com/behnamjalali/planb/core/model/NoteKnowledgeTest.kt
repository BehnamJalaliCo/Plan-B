package com.behnamjalali.planb.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/** Links between notes, the version diff and word counting (Plan-B Pro #16, #24). */
class NoteKnowledgeTest {
    private val zwnj = Char(0x200C)

    // region links

    @Test
    fun linkTokens_parseAndRoundTrip() {
        val text = "See ${NoteLinks.token(12, "برنامهٔ سفر")} and ${NoteLinks.token(7, "Ideas | [draft]")}."
        val spans = NoteLinks.spans(text)
        assertThat(spans.map { it.noteId }).containsExactly(12L, 7L).inOrder()
        assertThat(spans[0].title).isEqualTo("برنامهٔ سفر")
        // Characters that would break the token are cleaned out of the title.
        assertThat(spans[1].title).isEqualTo("Ideas draft")
        assertThat(text.substring(spans[0].start, spans[0].end)).isEqualTo("[[note:12|برنامهٔ سفر]]")
        assertThat(NoteLinks.plain(text)).isEqualTo("See برنامهٔ سفر and Ideas draft.")
        assertThat(NoteLinks.plain(text) { id -> if (id == 7L) "Renamed" else null }).isEqualTo("See برنامهٔ سفر and Renamed.")
    }

    @Test
    fun brokenTokens_stayPlainText() {
        listOf("[[note:abc|x]]", "[[note:1|unclosed", "[[note:|x]]", "[note:1|x]", "[[note:1|a\nb]]").forEach { text ->
            assertThat(NoteLinks.spans(text)).isEmpty()
            assertThat(NoteLinks.plain(text)).isEqualTo(text)
        }
    }

    @Test
    fun targets_collectEveryLinkedNoteOnce() {
        val doc = NoteDocument(
            blocks = listOf(
                NoteBlock("a", BlockType.TEXT, "${NoteLinks.token(3, "A")} ${NoteLinks.token(4, "B")}"),
                NoteBlock("b", BlockType.BULLET, NoteLinks.token(3, "A again")),
            ),
        )
        assertThat(NoteLinks.targets(doc)).containsExactly(3L, 4L).inOrder()
        // Search and previews read links as their titles.
        assertThat(doc.plainText()).isEqualTo("A B\nA again")
    }

    @Test
    fun pendingQuery_findsTheSearchAfterDoubleBracket() {
        assertThat(NoteLinks.pendingQuery("Hello [[sa", 10)).isEqualTo(NoteLinks.Query(6, "sa"))
        assertThat(NoteLinks.pendingQuery("[[", 2)).isEqualTo(NoteLinks.Query(0, ""))
        assertThat(NoteLinks.pendingQuery("[[سفر", 5)).isEqualTo(NoteLinks.Query(0, "سفر"))
        // Closed, on another line, or inside a finished link: no search.
        assertThat(NoteLinks.pendingQuery("[[done]] x", 10)).isNull()
        assertThat(NoteLinks.pendingQuery("[[a\nb", 5)).isNull()
        val link = NoteLinks.token(5, "Five")
        assertThat(NoteLinks.pendingQuery("$link tail", link.length + 5)).isNull()
        assertThat(NoteLinks.pendingQuery("no brackets", 5)).isNull()
    }

    @Test
    fun insert_replacesTheSearchWithALink() {
        val query = NoteLinks.pendingQuery("Read [[tr later", 9)!!
        val (text, caret) = NoteLinks.insert("Read [[tr later", query, 9, 42, "Travel plan")
        assertThat(text).isEqualTo("Read [[note:42|Travel plan]] later")
        assertThat(caret).isEqualTo("Read [[note:42|Travel plan]]".length)
    }

    @Test
    fun repairDeletion_removesAWholeLinkWhenOneCharacterOfItIsDeleted() {
        val old = "a ${NoteLinks.token(9, "Nine")} b"
        // Backspace right after the link deletes its last "]".
        val damaged = old.removeRange(old.indexOf("]]") + 1, old.indexOf("]]") + 2)
        val (fixed, caret) = NoteLinks.repairDeletion(old, damaged)!!
        assertThat(fixed).isEqualTo("a  b")
        assertThat(caret).isEqualTo(2)
        // Deleting plain text, or a whole link, needs no repair.
        assertThat(NoteLinks.repairDeletion(old, old.drop(1))).isNull()
        assertThat(NoteLinks.repairDeletion(old, "a  b")).isNull()
    }

    @Test
    fun markdownExport_writesRelativeLinksAndPlainTextDegradesToTitles() {
        val doc = NoteDocument(blocks = listOf(NoteBlock("a", BlockType.TEXT, "Go to ${NoteLinks.token(2, "Trip")} now")))
        assertThat(Markdown.export("Index", doc) { _, title -> "$title.md" }).contains("Go to [Trip](<Trip.md>) now")
        assertThat(Markdown.export("Index", doc)).contains("Go to Trip now")
        assertThat(Markdown.plainText("Index", doc)).contains("Go to Trip now")
        assertThat(Markdown.plainText("Index", doc) { "Journey" }).contains("Go to Journey now")
    }

    // endregion

    // region diff

    @Test
    fun diff_marksChangedLinesOnly() {
        val old = NoteDocument(
            blocks = listOf(
                NoteBlock("1", BlockType.HEADING, "Plan"),
                NoteBlock("2", BlockType.TEXT, "keep this"),
                NoteBlock("3", BlockType.CHECKLIST, "buy milk"),
            ),
        )
        val new = old.copy(
            blocks = listOf(
                NoteBlock("1", BlockType.HEADING, "Plan"),
                NoteBlock("2", BlockType.TEXT, "keep this"),
                NoteBlock("3", BlockType.CHECKLIST, "buy milk", checked = true),
                NoteBlock("4", BlockType.TEXT, "new line"),
            ),
        )
        val diff = NoteDiff.diff("T", old, "T", new)
        assertThat(diff.filter { it.kind == NoteDiff.Kind.SAME }.map { it.text }).containsExactly("# T", "## Plan", "keep this").inOrder()
        assertThat(diff.filter { it.kind == NoteDiff.Kind.REMOVED }.map { it.text }).containsExactly("☐ buy milk")
        assertThat(diff.filter { it.kind == NoteDiff.Kind.ADDED }.map { it.text }).containsExactly("☑ buy milk", "new line").inOrder()
    }

    @Test
    fun diff_ofIdenticalNotesIsAllSame() {
        val doc = NoteDocument(blocks = listOf(NoteBlock("1", BlockType.TEXT, "a\nb")))
        assertThat(NoteDiff.diff("x", doc, "x", doc).all { it.kind == NoteDiff.Kind.SAME }).isTrue()
    }

    @Test
    fun diff_ofHugeNotesStaysBounded() {
        val a = (0 until 5_000).map { "line $it" }
        val b = (0 until 5_000).map { "row $it" }
        val diff = NoteDiff.diff(a, b)
        assertThat(diff).hasSize(10_000)
    }

    // endregion

    // region word count

    @Test
    fun words_persianHalfSpaceKeepsOneWord() {
        assertThat(WordCount.words("می${zwnj}روم به کتاب${zwnj}خانه")).isEqualTo(3)
        assertThat(WordCount.words("میروم به کتابخانه")).isEqualTo(3)
        assertThat(WordCount.words("سلام، دنیا! چطوری؟")).isEqualTo(3)
        assertThat(WordCount.words("سال ۱۴۰۵ آمد")).isEqualTo(3)
        assertThat(WordCount.words("")).isEqualTo(0)
        assertThat(WordCount.words("   ...   ")).isEqualTo(0)
    }

    @Test
    fun words_english() {
        assertThat(WordCount.words("Don't stop the e-mail, now.")).isEqualTo(5)
        assertThat(WordCount.words("one\ntwo\tthree")).isEqualTo(3)
        assertThat(WordCount.words("Mixed متن with English")).isEqualTo(4)
    }

    @Test
    fun characters_skipInvisibleMarks() {
        assertThat(WordCount.characters("می${zwnj}روم")).isEqualTo(5)
        assertThat(WordCount.characters("ab c")).isEqualTo(4)
        assertThat(WordCount.characters("😀")).isEqualTo(1)
    }

    @Test
    fun documentCounts_readLinksAsTitlesAndSkipDividers() {
        val doc = NoteDocument(
            blocks = listOf(
                NoteBlock("1", BlockType.TEXT, "see ${NoteLinks.token(1, "two words")}"),
                NoteBlock("2", BlockType.DIVIDER),
                NoteBlock("3", BlockType.BULLET, "یک"),
            ),
        )
        assertThat(WordCount.of(doc).words).isEqualTo(4)
    }

    @Test
    fun writingGoal_streakGrowsOncePerDayAndBreaksAfterAMissedDay() {
        val day = LocalDate.of(2026, 10, 5)
        var w = WritingSettings(dailyGoal = 100)
        w = w.addWords(day, 60)
        assertThat(w.wordsOn(day)).isEqualTo(60)
        assertThat(w.streakOn(day)).isEqualTo(0)
        w = w.addWords(day, 50)
        assertThat(w.streakOn(day)).isEqualTo(1)
        w = w.addWords(day, 500)
        assertThat(w.streakOn(day)).isEqualTo(1)
        w = w.addWords(day.plusDays(1), 100)
        assertThat(w.wordsOn(day.plusDays(1))).isEqualTo(100)
        assertThat(w.streakOn(day.plusDays(1))).isEqualTo(2)
        // A whole day without the goal breaks the streak.
        assertThat(w.streakOn(day.plusDays(3))).isEqualTo(0)
        w = w.addWords(day.plusDays(3), 100)
        assertThat(w.streakOn(day.plusDays(3))).isEqualTo(1)
    }

    // endregion
}
