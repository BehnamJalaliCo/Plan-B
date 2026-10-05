package com.behnamjalali.planb.core.model

/**
 * A line diff between two versions of a note (Plan-B Pro #16, "compare"). Every block becomes
 * one or more lines with a short marker for its kind (so a paragraph turned into a list item is
 * a change too); links read as their titles. Lines are matched with a longest common
 * subsequence, so moved text shows as removed and added.
 */
object NoteDiff {
    enum class Kind { SAME, ADDED, REMOVED }

    data class Line(val kind: Kind, val text: String)

    /** Above this many lines on either side, the comparison is "everything changed" (no quadratic work). */
    const val MAX_LINES = 1_500

    fun lines(title: String, document: NoteDocument): List<String> = buildList {
        if (title.isNotBlank()) add("# ${title.trim()}")
        var number = 0
        var previous: BlockType? = null
        document.blocks.forEach { block ->
            number = if (block.type == BlockType.NUMBERED) (if (previous == BlockType.NUMBERED) number + 1 else 1) else 0
            val text = NoteLinks.plain(block.text)
            val prefix = when (block.type) {
                BlockType.HEADING -> "## "
                BlockType.CHECKLIST -> if (block.checked) "☑ " else "☐ "
                BlockType.BULLET -> "• "
                BlockType.NUMBERED -> "$number. "
                BlockType.QUOTE -> "> "
                else -> ""
            }
            if (block.type == BlockType.DIVIDER) {
                add("———")
            } else {
                text.split('\n').forEachIndexed { i, line -> add(if (i == 0) prefix + line else line) }
            }
            previous = block.type
        }
    }

    fun diff(oldTitle: String, old: NoteDocument, newTitle: String, new: NoteDocument): List<Line> =
        diff(lines(oldTitle, old), lines(newTitle, new))

    fun diff(a: List<String>, b: List<String>): List<Line> {
        // Common head and tail first: most edits touch a few lines.
        var head = 0
        while (head < a.size && head < b.size && a[head] == b[head]) head++
        var tail = 0
        while (tail < a.size - head && tail < b.size - head && a[a.size - 1 - tail] == b[b.size - 1 - tail]) tail++
        val midA = a.subList(head, a.size - tail)
        val midB = b.subList(head, b.size - tail)
        val middle = if (midA.size > MAX_LINES || midB.size > MAX_LINES) {
            midA.map { Line(Kind.REMOVED, it) } + midB.map { Line(Kind.ADDED, it) }
        } else {
            lcs(midA, midB)
        }
        return a.subList(0, head).map { Line(Kind.SAME, it) } + middle + a.subList(a.size - tail, a.size).map { Line(Kind.SAME, it) }
    }

    private fun lcs(a: List<String>, b: List<String>): List<Line> {
        val n = a.size
        val m = b.size
        if (n == 0) return b.map { Line(Kind.ADDED, it) }
        if (m == 0) return a.map { Line(Kind.REMOVED, it) }
        // lengths[i][j] = LCS of a[i..] and b[j..], in one flat array.
        val width = m + 1
        val lengths = IntArray((n + 1) * width)
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lengths[i * width + j] = if (a[i] == b[j]) {
                    lengths[(i + 1) * width + j + 1] + 1
                } else {
                    maxOf(lengths[(i + 1) * width + j], lengths[i * width + j + 1])
                }
            }
        }
        val out = ArrayList<Line>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    out += Line(Kind.SAME, a[i]); i++; j++
                }
                lengths[(i + 1) * width + j] >= lengths[i * width + j + 1] -> {
                    out += Line(Kind.REMOVED, a[i]); i++
                }
                else -> {
                    out += Line(Kind.ADDED, b[j]); j++
                }
            }
        }
        while (i < n) out += Line(Kind.REMOVED, a[i++])
        while (j < m) out += Line(Kind.ADDED, b[j++])
        return out
    }
}
