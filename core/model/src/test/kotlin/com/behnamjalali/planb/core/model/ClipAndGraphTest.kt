package com.behnamjalali.planb.core.model

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Test

/** The web clipper's HTML converter (#22), the graph layout (#21) and the journal rules (#25). */
class ClipAndGraphTest {
    private var counter = 0
    private val ids: () -> String = { "b${counter++}" }

    private fun convert(html: String) = HtmlToBlocks.convert(html, ids)

    // region HTML → blocks

    @Test
    fun html_paragraphsHeadingsListsQuotesAndCode() {
        val blocks = convert(
            """
            <html><head><title>T</title><style>p{color:red}</style></head><body>
            <h1>Title &amp; more</h1>
            <p>First   paragraph<br>second line</p>
            <ul><li>one</li><li>two</li></ul>
            <ol><li>first</li></ol>
            <blockquote><p>quoted</p></blockquote>
            <pre>val x = 1
              indented</pre>
            <hr>
            <p>Read <a href="https://example.com/a">this</a> and <a href="https://x.org">https://x.org</a></p>
            </body></html>
            """.trimIndent(),
        )
        assertThat(blocks.map { it.type to it.text }).containsExactly(
            BlockType.HEADING to "Title & more",
            BlockType.TEXT to "First paragraph\nsecond line",
            BlockType.BULLET to "one",
            BlockType.BULLET to "two",
            BlockType.NUMBERED to "first",
            BlockType.QUOTE to "quoted",
            BlockType.CODE to "val x = 1\n  indented",
            BlockType.DIVIDER to "",
            BlockType.TEXT to "Read this (https://example.com/a) and https://x.org",
        ).inOrder()
    }

    @Test
    fun html_maliciousInputIsReducedToText() {
        val blocks = convert(
            """
            <script>alert('x')</script><SCRIPT type="text/javascript">document.cookie</SCRIPT>
            <p onclick="steal()">Hi <a href="javascript:alert(1)">click</a> <a href=" JaVaScRiPt:evil">x</a>
            <a href="data:text/html,<script>1</script>">d</a></p>
            <iframe src="https://evil"><p>inside frame</p></iframe>
            <svg><script>1</script><text>svg text</text></svg>
            <style>body{}</style><!-- <p>comment</p> --><p>&#x202E;evil&#8238;txt&#0;&#xD800;</p>
            <img src=x onerror=alert(1) alt="photo">
            <p>a &lt;b&gt; &unknown; &#1601;</p>
            <form><input value="secret"><button>Send</button></form>
            """.trimIndent(),
        )
        val all = blocks.joinToString("\n") { it.text }
        listOf("alert", "cookie", "steal", "javascript", "evil(", "inside frame", "svg text", "comment", "secret", "Send", "onerror", "data:").forEach {
            assertThat(all).doesNotContain(it)
        }
        assertThat(all).contains("Hi click x")
        assertThat(all).contains("eviltxt")
        assertThat(all.any { it.code in 0x202A..0x202E || it.code == 0 }).isFalse()
        assertThat(all).contains("photo")
        assertThat(all).contains("a <b> &unknown; ف")
    }

    @Test
    fun html_malformedMarkupNeverThrows() {
        listOf("<", "<<p>>", "<p", "</", "<a href='x", "<!--", "<script>", "<p>unclosed <b>bold", "&#99999999;", "<ul><li>a<li>b").forEach { html ->
            convert(html)
        }
        assertThat(convert("<p>unclosed <b>bold").single().text).isEqualTo("unclosed bold")
        assertThat(convert("<ul><li>a<li>b").map { it.text }).containsExactly("a", "b").inOrder()
        assertThat(convert("2 < 3 and 5 > 4").single().text).isEqualTo("2 < 3 and 5 > 4")
    }

    @Test
    fun html_limitsHoldForHugeInput() {
        val huge = "<p>x</p>".repeat(10_000)
        assertThat(convert(huge)).hasSize(HtmlToBlocks.MAX_BLOCKS)
        val long = "<p>" + "a".repeat(100_000) + "</p>"
        assertThat(convert(long).all { it.text.length <= HtmlToBlocks.MAX_BLOCK_TEXT }).isTrue()
        val deep = "<div>".repeat(50_000) + "deep" + "</div>".repeat(50_000)
        assertThat(convert(deep).map { it.text }).containsExactly("deep")
    }

    @Test
    fun html_tablesBecomeRows() {
        val blocks = convert("<table><tr><th>Name</th><th>Age</th></tr><tr><td>Sara</td><td>30</td></tr></table>")
        assertThat(blocks.map { it.text }).containsExactly("Name | Age", "Sara | 30").inOrder()
    }

    @Test
    fun safeHref_keepsOnlyWebAndMailLinks() {
        assertThat(HtmlToBlocks.safeHref("https://a.b/c?d=1")).isEqualTo("https://a.b/c?d=1")
        assertThat(HtmlToBlocks.safeHref("mailto:x@y.z")).isEqualTo("mailto:x@y.z")
        listOf("javascript:x", "data:text/html,1", "file:///etc/passwd", "intent://x", "/relative", "", null, "https://a b").forEach {
            assertThat(HtmlToBlocks.safeHref(it)).isNull()
        }
    }

    // endregion

    // region graph layout

    private fun ring(n: Int) = (0 until n).map { it to (it + 1) % n }

    @Test
    fun layout_isDeterministicForASeed() {
        val edges = ring(40) + listOf(0 to 20, 5 to 25)
        val a = GraphLayout.layout(45, edges, seed = 7)
        val b = GraphLayout.layout(45, edges, seed = 7)
        assertThat(a.x.toList()).isEqualTo(b.x.toList())
        assertThat(a.y.toList()).isEqualTo(b.y.toList())
        val c = GraphLayout.layout(45, edges, seed = 8)
        assertThat(c.x.toList()).isNotEqualTo(a.x.toList())
    }

    @Test
    fun layout_staysInBoundsAndKeepsLinkedNotesCloser() {
        val edges = (1 until 10).map { 0 to it } // a star of 10, plus 10 orphans
        val p = GraphLayout.layout(20, edges, seed = 1)
        (0 until 20).forEach {
            assertThat(abs(p.x[it])).isAtMost(1.0001f)
            assertThat(abs(p.y[it])).isAtMost(1.0001f)
            assertThat(p.x[it].isNaN() || p.y[it].isNaN()).isFalse()
        }
        fun d(a: Int, b: Int) = hypot(p.x[a] - p.x[b], p.y[a] - p.y[b])
        val linked = (1 until 10).map { d(0, it) }.average()
        val orphans = (10 until 20).map { d(0, it) }.average()
        assertThat(linked).isLessThan(orphans)
    }

    @Test
    fun layout_handlesEdgeCases() {
        assertThat(GraphLayout.layout(0, emptyList()).size).isEqualTo(0)
        assertThat(GraphLayout.layout(1, emptyList()).x.single()).isEqualTo(0f)
        // Self loops and out-of-range pairs are ignored.
        val p = GraphLayout.layout(3, listOf(0 to 0, 0 to 9, -1 to 2, 1 to 2))
        assertThat(p.size).isEqualTo(3)
    }

    @Test
    fun layout_ofTwoThousandNotesFinishesQuickly() {
        val random = Random(3)
        val edges = (0 until 3_000).map { random.nextInt(GraphLayout.MAX_NODES) to random.nextInt(GraphLayout.MAX_NODES) }
        GraphLayout.layout(200, edges.filter { it.first < 200 && it.second < 200 }) // warm-up
        val start = System.nanoTime()
        val p = GraphLayout.layout(GraphLayout.MAX_NODES, edges, seed = 1)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertThat(p.size).isEqualTo(GraphLayout.MAX_NODES)
        assertThat((0 until p.size).none { p.x[it].isNaN() || p.y[it].isNaN() }).isTrue()
        // Generous bound for slow CI machines; typically well under a second.
        assertThat(ms).isLessThan(8_000L)
    }

    // endregion

    // region journal

    @Test
    fun prompts_rotateThroughEveryPromptBeforeRepeating() {
        val start = LocalDate.of(2026, 10, 1)
        val custom = listOf("What made you smile?", "  ", "What made you smile?")
        val keys = JournalPrompts.keys(custom)
        assertThat(keys).hasSize(JournalPrompts.BUILT_IN + 1)
        val shown = (0 until keys.size).map { JournalPrompts.forDate(start.plusDays(it.toLong()), custom) }
        assertThat(shown.toSet()).isEqualTo(keys.toSet())
        // Neighbouring days never show neighbouring prompts of the list.
        val positions = shown.map { keys.indexOf(it) }
        assertThat(positions.zipWithNext().none { (a, b) -> abs(a - b) == 1 }).isTrue()
        // The same day always gets the same prompt; "another prompt" moves on.
        assertThat(JournalPrompts.forDate(start, custom)).isEqualTo(shown[0])
        assertThat(JournalPrompts.forDate(start, custom, shift = 1)).isEqualTo(shown[1])
        assertThat(JournalPrompts.customText(JournalPrompts.customKey("What made you smile?"), custom)).isEqualTo("What made you smile?")
        assertThat(JournalPrompts.builtInNumber("p07")).isEqualTo(7)
        assertThat(JournalPrompts.builtInNumber("custom:1")).isNull()
        assertThat(JournalPrompts.builtInNumber("p99")).isNull()
    }

    @Test
    fun moodCalendar_averagesPerDayAndWeekday() {
        val mon = LocalDate.of(2026, 10, 5) // a Monday
        val moods = listOf(
            MoodEntry(date = mon, mood = 4, energy = 2),
            MoodEntry(date = mon, mood = 5),
            MoodEntry(date = mon.plusDays(1), energy = 3),
            MoodEntry(date = mon.plusDays(7), mood = 2),
            MoodEntry(date = mon.plusDays(2), mood = 9), // out of range: ignored
        )
        val days = MoodCalendar.days(moods, setOf(mon, mon.plusDays(3)))
        assertThat(days.getValue(mon).mood).isEqualTo(4.5f)
        assertThat(days.getValue(mon).moodLevel).isEqualTo(5)
        assertThat(days.getValue(mon).energy).isEqualTo(2f)
        assertThat(days.getValue(mon).journaled).isTrue()
        assertThat(days.getValue(mon.plusDays(1)).mood).isNull()
        assertThat(days.getValue(mon.plusDays(3)).entries).isEqualTo(0)
        assertThat(days.getValue(mon.plusDays(2)).mood).isNull()
        val insights = MoodCalendar.insights(moods, setOf(mon, mon.plusDays(3)), mon.plusDays(7))
        assertThat(insights.moodByWeekday[DayOfWeek.MONDAY]).isWithin(0.001f).of(11f / 3)
        assertThat(insights.moodByWeekday[DayOfWeek.TUESDAY]).isNull()
        assertThat(insights.checkIns).isEqualTo(5)
    }

    @Test
    fun journalStreaks() {
        val today = LocalDate.of(2026, 10, 10)
        val dates = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(3), today.minusDays(6), today.minusDays(7))
        // Today has no page yet: the streak still counts up to yesterday.
        assertThat(MoodCalendar.currentStreak(dates, today)).isEqualTo(3)
        assertThat(MoodCalendar.currentStreak(dates + today, today)).isEqualTo(4)
        assertThat(MoodCalendar.currentStreak(dates, today.plusDays(2))).isEqualTo(0)
        assertThat(MoodCalendar.longestStreak(dates)).isEqualTo(3)
        assertThat(MoodCalendar.longestStreak(emptySet())).isEqualTo(0)
    }

    // endregion
}
