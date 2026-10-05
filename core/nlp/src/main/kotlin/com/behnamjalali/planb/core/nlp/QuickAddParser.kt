package com.behnamjalali.planb.core.nlp

import com.behnamjalali.planb.core.datetime.CalendarEngine
import com.behnamjalali.planb.core.datetime.CalendarEngines
import com.behnamjalali.planb.core.datetime.GregorianEngine
import com.behnamjalali.planb.core.datetime.JalaliEngine
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * On-device natural-language quick add for Persian and English (Plan-B Pro #1). No network,
 * no AI: a deterministic, table-driven reading of the text. See docs/PRO.md for the rules.
 *
 * The text is read word by word from the start; at each word the first rule that matches
 * wins, so earlier rules take precedence (a deadline «تا جمعه» before a date «جمعه», a
 * relative time «۲ ساعت دیگه» before a duration «۲ ساعت»). Each kind is taken once (only
 * tags repeat); later mentions stay in the title. Interpretations whose
 * [QuickAddPart.key] is in `dismissed` are skipped, which keeps their words as text.
 */
object QuickAddParser {
    fun parse(text: String, context: QuickAddContext, dismissed: Set<String> = emptySet()): QuickAddResult =
        Parse(text, context, dismissed).run()
}

private class Hit(
    val kind: QuickAddKind,
    val count: Int,
    val fills: Set<QuickAddKind> = setOf(kind),
    val apply: Draft.() -> Unit,
)

private class Draft {
    var date: LocalDate? = null
    var time: LocalTime? = null
    var ambiguous = false
    var night = false
    var recurrence: RecurrenceRule? = null
    var priority: Priority? = null
    val tags = LinkedHashMap<String, String>()
    var project: QuickAddProject? = null
    var deadline: LocalDate? = null
    var duration: Int? = null
    var reminder: Int? = null
}

private class DateHit(val date: LocalDate, val count: Int, val night: Boolean = false)

private class ClockHit(val time: LocalTime, val ambiguous: Boolean, val count: Int)

private class Parse(private val text: String, private val ctx: QuickAddContext, private val dismissed: Set<String>) {
    private val v = Vocabulary
    private val tokens = QuickAddText.tokenize(text)
    private val today: LocalDate = ctx.now.toLocalDate()
    private val engine: CalendarEngine = CalendarEngines.of(ctx.calendar)
    private val consumed = arrayOfNulls<QuickAddKind>(tokens.size)
    private val draft = Draft()
    private val filled = HashSet<QuickAddKind>()

    private val rules: List<(Int) -> Hit?> = listOf(
        ::tag, ::project, ::prioritySymbol, ::reminder, ::deadline, ::recurrence, ::relativeTime, ::date, ::time, ::duration, ::priorityWord,
    )

    fun run(): QuickAddResult {
        val parts = ArrayList<QuickAddPart>()
        var i = 0
        while (i < tokens.size) {
            val hit = hitAt(i)
            if (hit == null) {
                i++
                continue
            }
            hit.apply(draft)
            if (hit.kind != QuickAddKind.TAG) filled += hit.fills
            val last = tokens[i + hit.count - 1]
            parts += QuickAddPart(hit.kind, tokens[i].start, last.end, text.substring(tokens[i].start, last.end), key(hit, i))
            for (k in i until i + hit.count) consumed[k] = hit.kind
            i += hit.count
        }
        return resolve(parts)
    }

    private fun key(hit: Hit, i: Int): String =
        hit.kind.name + ":" + (i until i + hit.count).joinToString(" ") { tokens[it].word }

    private fun hitAt(i: Int): Hit? {
        for (rule in rules) {
            val hit = rule(i) ?: continue
            if (hit.kind != QuickAddKind.TAG && hit.fills.any { it in filled }) continue
            if (key(hit, i) in dismissed) continue
            return hit
        }
        return null
    }

    /** Whether a part (other than a lone time-of-day word or repeat adjective) starts at [i]. */
    private fun startsPart(i: Int): Boolean {
        if (i >= tokens.size) return false
        strict = true
        try {
            return rules.any { it(i) != null }
        } finally {
            strict = false
        }
    }

    /** While true, rules that look at their neighbours (see [startsPart]) don't match. */
    private var strict = false

    /**
     * Words that may stand alone only next to another part, or at the end of the text when
     * [atEnd] (a time-of-day word, or an English repeat adverb: "water the plants weekly").
     */
    private fun acceptsAdverb(i: Int, count: Int, atEnd: Boolean = true): Boolean {
        if (strict) return false
        val previous = i - 1
        return (previous >= 0 && consumed[previous] != null) || (atEnd && i + count >= tokens.size) || startsPart(i + count)
    }

    // region rules

    private fun tag(i: Int): Hit? {
        val t = tokens[i].text
        if (!t.startsWith("#") || t.length < 2 || t.drop(1).all { it == '#' }) return null
        val name = t.trimStart('#')
        return Hit(QuickAddKind.TAG, 1) { tags.putIfAbsent(QuickAddText.normalize(name), name) }
    }

    private fun project(i: Int): Hit? {
        val t = tokens[i].text
        if (!t.startsWith("@") || t.length < 2 || ctx.projects.isEmpty()) return null
        fun squash(s: String) = QuickAddText.normalize(s).filterNot { QuickAddText.isSpace(it) || it == ' ' }
        val names = ctx.projects.map { it to squash(it.name) }
        val most = minOf(PROJECT_WORDS, tokens.size - i)
        for (count in most downTo 1) {
            val typed = squash(t.drop(1) + (i + 1 until i + count).joinToString("") { tokens[it].text })
            names.firstOrNull { it.second == typed }?.let { (p, _) -> return Hit(QuickAddKind.PROJECT, count) { project = p } }
        }
        val prefix = squash(t.drop(1))
        val candidates = names.filter { prefix.length >= 2 && it.second.startsWith(prefix) }
        return candidates.singleOrNull()?.let { (p, _) -> Hit(QuickAddKind.PROJECT, 1) { project = p } }
    }

    private fun prioritySymbol(i: Int): Hit? {
        val w = tokens[i].word
        val p = when {
            w.isNotEmpty() && w.all { it == '!' } -> if (w.length == 1) Priority.MEDIUM else Priority.HIGH
            w == "p1" -> Priority.HIGH
            w == "p2" -> Priority.MEDIUM
            w == "p3" -> Priority.LOW
            else -> return null
        }
        return Hit(QuickAddKind.PRIORITY, 1) { priority = p }
    }

    private fun priorityWord(i: Int): Hit? {
        val (p, count) = v.priorityWords.match(tokens, i) ?: return null
        return Hit(QuickAddKind.PRIORITY, count) { priority = p }
    }

    /** «یادم بنداز ۱۰ دقیقه قبل», «۱۰ دقیقه قبل یادم بنداز», "remind me 1 hour before". */
    private fun reminder(i: Int): Hit? {
        v.reminders.match(tokens, i)?.let { (_, k) ->
            v.onTime.match(tokens, i + k)?.let { (_, k2) -> return Hit(QuickAddKind.REMINDER, k + k2) { reminder = 0 } }
            val offset = duration(i + k, allowPrefix = false)?.let { (minutes, k2) ->
                v.before.match(tokens, i + k + k2)?.let { (_, k3) -> minutes to (k2 + k3) }
            }
            return if (offset != null) {
                Hit(QuickAddKind.REMINDER, k + offset.second) { reminder = offset.first }
            } else {
                Hit(QuickAddKind.REMINDER, k) { reminder = 0 }
            }
        }
        val (minutes, k) = duration(i, allowPrefix = false) ?: return null
        val (_, k2) = v.before.match(tokens, i + k) ?: return null
        val (_, k3) = v.reminders.match(tokens, i + k + k2) ?: return null
        return Hit(QuickAddKind.REMINDER, k + k2 + k3) { reminder = minutes }
    }

    /** «تا جمعه», «مهلت ۲۰ مهر», "by friday", "deadline oct 20". */
    private fun deadline(i: Int): Hit? {
        val (_, k) = v.deadlinePrefix.match(tokens, i) ?: return null
        val hit = dateExpr(i + k) ?: return null
        return Hit(QuickAddKind.DEADLINE, k + hit.count) { deadline = hit.date }
    }

    private fun recurrence(i: Int): Hit? {
        var count: Int
        var rule: RecurrenceRule
        val fixed = v.repeats.match(tokens, i)
        val adjective = if (fixed == null) v.repeatAdjectives.match(tokens, i) else null
        when {
            fixed != null -> {
                rule = repeatRule(fixed.first)
                count = fixed.second
            }
            adjective != null -> {
                // «گزارش هفتگی» is a weekly report, not a repeat: Persian adjectives need a neighbour part.
                if (!acceptsAdverb(i, adjective.second, atEnd = !QuickAddText.isPersian(tokens[i].word))) return null
                rule = repeatRule(adjective.first)
                count = adjective.second
            }
            else -> {
                val (_, k) = v.every.match(tokens, i) ?: return null
                val interval = amount(i + k, allowArticle = false)
                val unit = interval?.let { v.units.match(tokens, i + k + it.second) }
                if (interval != null && unit != null) {
                    val n = interval.first.toInt().takeIf { it >= 1 && interval.first == it.toDouble() && it <= MAX_INTERVAL } ?: return null
                    val frequency = when (unit.first) {
                        Span.DAY -> RecurrenceFrequency.DAILY
                        Span.WEEK -> RecurrenceFrequency.WEEKLY
                        Span.MONTH -> RecurrenceFrequency.MONTHLY
                        Span.YEAR -> RecurrenceFrequency.YEARLY
                        else -> return null
                    }
                    rule = RecurrenceRule(frequency, interval = n, calendarSystem = ctx.calendar)
                    count = k + interval.second + unit.second
                    v.once.match(tokens, i + count)?.let { count += it.second }
                } else {
                    val days = LinkedHashSet<DayOfWeek>()
                    var j = i + k
                    while (j < tokens.size) {
                        val day = v.weekdaysAfterMarker.match(tokens, j)
                        if (day != null) {
                            days += day.first
                            j += day.second
                            continue
                        }
                        val and = v.conjunctions.match(tokens, j)
                        if (days.isNotEmpty() && and != null && v.weekdaysAfterMarker.contains(tokens, j + and.second)) {
                            j += and.second
                            continue
                        }
                        break
                    }
                    if (days.isEmpty()) return null
                    rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, weekdays = days, calendarSystem = ctx.calendar)
                    count = j - i
                }
            }
        }
        // «هر روز تا آخر ماه», "every day until friday".
        v.until.match(tokens, i + count)?.let { (_, k) ->
            dateExpr(i + count + k)?.let { end ->
                rule = rule.copy(until = end.date)
                count += k + end.count
            }
        }
        val result = rule
        return Hit(QuickAddKind.RECURRENCE, count) { recurrence = result }
    }

    private fun repeatRule(repeat: Repeat): RecurrenceRule = when (repeat) {
        Repeat.DAILY -> RecurrenceRule(RecurrenceFrequency.DAILY, calendarSystem = ctx.calendar)
        Repeat.WEEKLY -> RecurrenceRule(RecurrenceFrequency.WEEKLY, calendarSystem = ctx.calendar)
        Repeat.MONTHLY -> RecurrenceRule(RecurrenceFrequency.MONTHLY, calendarSystem = ctx.calendar)
        Repeat.YEARLY -> RecurrenceRule(RecurrenceFrequency.YEARLY, calendarSystem = ctx.calendar)
        Repeat.OTHER_DAY -> RecurrenceRule(RecurrenceFrequency.DAILY, interval = 2, calendarSystem = ctx.calendar)
        Repeat.OTHER_WEEK -> RecurrenceRule(RecurrenceFrequency.WEEKLY, interval = 2, calendarSystem = ctx.calendar)
        Repeat.WORKDAYS -> RecurrenceRule(
            RecurrenceFrequency.WEEKLY,
            weekdays = if (ctx.firstDayOfWeek == DayOfWeek.SATURDAY) {
                setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)
            } else {
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
            },
            calendarSystem = ctx.calendar,
        )
    }

    /** «۲ ساعت دیگه», «نیم ساعت دیگه», "in 45 min", "2 hours from now": now plus that time. */
    private fun relativeTime(i: Int): Hit? {
        val prefix = v.inWord.match(tokens, i)?.second ?: 0
        val (minutes, k) = duration(i + prefix, allowPrefix = false) ?: return null
        val later = v.later.match(tokens, i + prefix + k)
        if (prefix == 0 && later == null) return null
        val at = ctx.now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(minutes.toLong())
        val count = prefix + k + (later?.second ?: 0)
        return Hit(QuickAddKind.TIME, count, setOf(QuickAddKind.TIME, QuickAddKind.DATE)) {
            date = at.toLocalDate()
            time = at.toLocalTime()
            ambiguous = false
        }
    }

    private fun date(i: Int): Hit? {
        var prefix = 0
        val hit = dateExpr(i) ?: v.datePrefix.match(tokens, i)?.let { (_, k) ->
            prefix = k
            dateExpr(i + k)
        } ?: return null
        return Hit(QuickAddKind.DATE, prefix + hit.count) {
            date = hit.date
            if (hit.night) night = true
        }
    }

    private fun time(i: Int): Hit? {
        // A time-of-day word first: «صبح ساعت ۸», «شب ساعت ۱۰».
        v.periods.match(tokens, i)?.let { (period, k) ->
            val marker = v.timePrefix.match(tokens, i + k)?.second ?: 0
            clock(i + k + marker, requireMarker = marker == 0, period = period)?.let { c ->
                return timeHit(k + marker + c.count, c.time, c.ambiguous)
            }
            if (period == Period.AM || period == Period.PM) return null
            val english = tokens[i].word in v.englishBarePeriods && k == 1
            val persianSingle = QuickAddText.isPersian(tokens[i].word) && k == 1
            if ((english || persianSingle) && !acceptsAdverb(i, k)) return null
            return timeHit(k, defaultTime(period), ambiguous = false)
        }
        val marker = v.timePrefix.match(tokens, i)?.second ?: 0
        val c = clock(i + marker, requireMarker = marker == 0, period = null) ?: return null
        return timeHit(marker + c.count, c.time, c.ambiguous)
    }

    private fun timeHit(count: Int, at: LocalTime, ambiguous: Boolean) = Hit(QuickAddKind.TIME, count) {
        time = at
        this.ambiguous = ambiguous
    }

    private fun duration(i: Int): Hit? {
        val (minutes, k) = duration(i, allowPrefix = true) ?: return null
        return Hit(QuickAddKind.DURATION, k) { duration = minutes }
    }

    // endregion

    // region building blocks

    /** A number: digits (any script) or a number word; "a"/"an" when [allowArticle]. */
    private fun amount(i: Int, allowArticle: Boolean): Pair<Double, Int>? {
        if (i >= tokens.size) return null
        tokens[i].word.toDoubleOrNull()?.takeIf { tokens[i].word.all { c -> c.isDigit() || c == '.' } }?.let { return it to 1 }
        v.numberWords.match(tokens, i)?.let { (n, k) -> return n.toDouble() to k }
        if (allowArticle) v.articles.match(tokens, i)?.let { (n, k) -> return n.toDouble() to k }
        return null
    }

    /** A length of time in minutes: «۴۵ دقیقه», «یک ساعت و نیم», «نیم ساعت», "for 2h", "90min". */
    private fun duration(i: Int, allowPrefix: Boolean): Pair<Int, Int>? {
        val prefix = if (allowPrefix) v.durationPrefix.match(tokens, i)?.second ?: 0 else 0
        val j = i + prefix
        if (j >= tokens.size) return null
        val found: Pair<Int, Int>? = v.durationWords.match(tokens, j)
            ?: compactDuration(tokens[j].word)?.let { it to 1 }
            ?: run {
                val (n, k) = amount(j, allowArticle = true) ?: return@run null
                val (unit, k2) = v.units.match(tokens, j + k) ?: return@run null
                val base = when (unit) {
                    Span.MINUTE -> n
                    Span.HOUR -> n * 60
                    else -> return@run null
                }
                var minutes = base
                var count = k + k2
                if (unit == Span.HOUR) {
                    // «یک ساعت و نیم», «۲ ساعت و ۱۵ دقیقه».
                    v.half.match(tokens, j + count)?.let { (extra, k3) ->
                        minutes += extra
                        count += k3
                    } ?: v.conjunctions.match(tokens, j + count)?.let { (_, k3) ->
                        val more = amount(j + count + k3, allowArticle = false)
                        val unit2 = more?.let { v.units.match(tokens, j + count + k3 + it.second) }
                        if (more != null && unit2?.first == Span.MINUTE) {
                            minutes += more.first
                            count += k3 + more.second + unit2.second
                        }
                    }
                }
                minutes.toInt().takeIf { minutes == it.toDouble() || unit == Span.HOUR }?.let { it to count }
            }
        val (minutes, count) = found ?: return null
        if (minutes !in 1..MAX_DURATION) return null
        return minutes to prefix + count
    }

    private val compactHours = Regex("^(\\d+(?:\\.\\d+)?)(h|hr|hrs)$")
    private val compactMinutes = Regex("^(\\d+)(m|min|mins)$")
    private val compactBoth = Regex("^(\\d+)h(\\d+)(m|min)?$")

    private fun compactDuration(word: String): Int? {
        compactBoth.matchEntire(word)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        compactHours.matchEntire(word)?.let { return (it.groupValues[1].toDouble() * 60).toInt() }
        compactMinutes.matchEntire(word)?.let { return it.groupValues[1].toInt() }
        return null
    }

    private val clockPattern = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm|a\\.m\\.?|p\\.m\\.?)?$")

    /**
     * A clock time at [i]: "17:30", "5:30pm", «۵ عصر», «ساعت ۸ صبح» (with [requireMarker] false
     * after «ساعت»/"at", a bare number counts). [period] is a time-of-day word read before it.
     */
    private fun clock(i: Int, requireMarker: Boolean, period: Period?): ClockHit? {
        if (i >= tokens.size) return null
        var hour: Int
        var minute = 0
        var count = 1
        var hasColon = false
        var leadingZero = false
        var suffix: Period? = null
        val match = clockPattern.matchEntire(tokens[i].word)
        if (match != null) {
            hour = match.groupValues[1].toInt()
            leadingZero = match.groupValues[1].length == 2 && match.groupValues[1].startsWith("0")
            if (match.groupValues[2].isNotEmpty()) {
                hasColon = true
                minute = match.groupValues[2].toInt()
            }
            suffix = when {
                match.groupValues[3].startsWith("a") -> Period.AM
                match.groupValues[3].startsWith("p") -> Period.PM
                else -> null
            }
        } else {
            // A spelled hour: «ساعت پنج», "at five".
            if (requireMarker) return null
            val (n, k) = v.numberWords.match(tokens, i) ?: return null
            hour = n
            count = k
        }
        if (!hasColon) {
            v.half.match(tokens, i + count)?.let { (extra, k) ->
                minute = extra
                count += k
            }
        }
        var after: Period? = null
        if (suffix == null && period == null) {
            v.periods.match(tokens, i + count)?.let { (p, k) ->
                after = p
                count += k
            }
        }
        val applied = suffix ?: period ?: after
        if (requireMarker && applied == null && !hasColon) return null
        if (hour > 23 || minute > 59) return null
        val finalHour = if (applied != null) applyPeriod(hour, applied) ?: return null else hour
        val ambiguous = applied == null && hour in 1..11 && !leadingZero
        return ClockHit(LocalTime.of(finalHour, minute), ambiguous, count)
    }

    private fun applyPeriod(hour: Int, period: Period): Int? {
        if (hour >= 13) return hour
        return when (period) {
            Period.AM, Period.MORNING -> if (hour == 12) 0 else hour
            Period.PM, Period.AFTERNOON, Period.LATE_AFTERNOON, Period.EVENING -> if (hour == 12) 12 else hour + 12
            Period.NOON -> if (hour in 11..12) hour else hour + 12
            Period.NIGHT, Period.MIDNIGHT -> when (hour) {
                12, 0 -> 0
                in 1..4 -> hour
                else -> hour + 12
            }
        }
    }

    private fun defaultTime(period: Period): LocalTime = when (period) {
        Period.AM, Period.MORNING -> LocalTime.of(9, 0)
        Period.NOON -> LocalTime.NOON
        Period.AFTERNOON, Period.PM -> LocalTime.of(15, 0)
        Period.LATE_AFTERNOON -> LocalTime.of(17, 0)
        Period.EVENING -> LocalTime.of(18, 0)
        Period.NIGHT -> LocalTime.of(20, 0)
        Period.MIDNIGHT -> LocalTime.MIDNIGHT
    }

    /** A date at [i]: anchors, weekdays, «۳ روز دیگه», «۱۵ مهر», «۱۵/۷», "oct 15", "in 2 weeks". */
    private fun dateExpr(i: Int): DateHit? {
        if (i >= tokens.size) return null
        v.anchors.match(tokens, i)?.let { (anchor, k) -> return DateHit(resolve(anchor), k, night = anchor == Anchor.TONIGHT) }
        weekday(i)?.let { return it }
        relativeDate(i)?.let { return it }
        dayMonth(i)?.let { return it }
        numericDate(i)?.let { return it }
        return null
    }

    private fun weekday(i: Int): DateHit? {
        val prefix = v.weekdayPrefix.match(tokens, i)
        val start = i + (prefix?.second ?: 0)
        val lexicon = if (prefix != null && tokens[i].word != "روز") v.weekdaysAfterMarker else v.weekdays
        val (day, k) = lexicon.match(tokens, start) ?: return null
        val nextMarker = v.weekdayNext.match(tokens, start + k)
        val count = (prefix?.second ?: 0) + k + (nextMarker?.second ?: 0)
        val word = prefix?.let { tokens[i].word }
        val date = when {
            prefix?.first == true || nextMarker != null -> weekStart().plusDays(7L + offsetInWeek(day))
            word == "this" || word == "این" || word == "همین" -> nextOnOrAfter(today, day)
            else -> nextOnOrAfter(today.plusDays(1), day)
        }
        return DateHit(date, count)
    }

    private fun relativeDate(i: Int): DateHit? {
        val prefix = v.inWord.match(tokens, i)?.second ?: 0
        val (n, k) = amount(i + prefix, allowArticle = prefix > 0) ?: return null
        val (unit, k2) = v.units.match(tokens, i + prefix + k) ?: return null
        val later = v.later.match(tokens, i + prefix + k + k2)
        if (prefix == 0 && later == null) return null
        val count = n.toInt().takeIf { it.toDouble() == n && it in 0..MAX_RELATIVE } ?: return null
        val date = when (unit) {
            Span.DAY -> today.plusDays(count.toLong())
            Span.WEEK -> today.plusWeeks(count.toLong())
            Span.MONTH -> engine.plusMonths(today, count)
            Span.YEAR -> engine.plusYears(today, count)
            else -> return null
        }
        return DateHit(date, prefix + k + k2 + (later?.second ?: 0))
    }

    /** «۱۵ مهر», «۱۵ام مهر ۱۴۰۶», "15 oct", "oct 15", "october 15th", "15th of october". */
    private fun dayMonth(i: Int): DateHit? {
        fun dayAt(j: Int): Int? = tokens.getOrNull(j)?.word?.toIntOrNull()?.takeIf { it in 1..31 }
        fun monthAt(j: Int): Triple<Int, CalendarEngine, Int>? {
            v.jalaliMonths.match(tokens, j)?.let { (m, k) -> return Triple(m, JalaliEngine, k + (v.monthWord.match(tokens, j + k)?.second ?: 0)) }
            v.gregorianMonths.match(tokens, j)?.let { (m, k) -> return Triple(m, GregorianEngine, k) }
            return null
        }
        fun yearAt(j: Int, calendar: CalendarEngine): Int? = tokens.getOrNull(j)?.word?.toIntOrNull()?.takeIf { plausibleYear(it, calendar) }

        val day = dayAt(i)
        if (day != null) {
            val of = v.ofWord.match(tokens, i + 1)?.second ?: 0
            val (month, calendar, k) = monthAt(i + 1 + of) ?: return null
            var count = 1 + of + k
            val year = yearAt(i + count, calendar)?.also { count++ }
            return calendarDate(calendar, year, month, day)?.let { DateHit(it, count) }
        }
        val (month, calendar, k) = monthAt(i) ?: return null
        // Month first is English only ("oct 15"); a Persian month name alone is not a date.
        if (calendar != GregorianEngine || QuickAddText.isPersian(tokens[i].word)) return null
        val d = dayAt(i + k) ?: return null
        var count = k + 1
        val year = yearAt(i + count, calendar)?.also { count++ }
        return calendarDate(calendar, year, month, d)?.let { DateHit(it, count) }
    }

    private val numericPattern = Regex("^(\\d{1,4})[/\\-](\\d{1,2})(?:[/\\-](\\d{1,4}))?$")

    /** «۱۵/۷» (day/month in the user's calendar), «۱۴۰۵/۷/۱۵», "2026-10-15", "7/15" (month first when it can't be a month). */
    private fun numericDate(i: Int): DateHit? {
        val m = numericPattern.matchEntire(tokens[i].word) ?: return null
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        val c = m.groupValues[3].toIntOrNull()
        val date = when {
            c == null -> {
                val (day, month) = if (b > 12 && a <= 12) b to a else a to b
                calendarDate(engine, null, month, day)
            }
            a >= 1000 -> calendarDate(engineForYear(a) ?: return null, a, b, c)
            c >= 1000 -> {
                val (day, month) = if (b > 12 && a <= 12) b to a else a to b
                calendarDate(engineForYear(c) ?: return null, c, month, day)
            }
            else -> null
        } ?: return null
        return DateHit(date, 1)
    }

    private fun engineForYear(year: Int): CalendarEngine? = when {
        plausibleYear(year, JalaliEngine) -> JalaliEngine
        plausibleYear(year, GregorianEngine) -> GregorianEngine
        else -> null
    }

    private fun plausibleYear(year: Int, calendar: CalendarEngine): Boolean =
        if (calendar.system == CalendarSystem.JALALI) year in 1300..1699 else year in 1900..2299

    /** The date, or null when the day doesn't exist; without a year the next such day from today. */
    private fun calendarDate(calendar: CalendarEngine, year: Int?, month: Int, day: Int): LocalDate? {
        if (month !in 1..12 || day < 1) return null
        fun at(y: Int): LocalDate? = if (day <= calendar.monthLength(y, month)) calendar.toLocalDate(y, month, day) else null
        if (year != null) return at(year)
        val current = calendar.toCalendarDate(today).year
        val thisYear = at(current)
        return if (thisYear != null && thisYear >= today) thisYear else at(current + 1) ?: at(current + 2)
    }

    private fun resolve(anchor: Anchor): LocalDate = when (anchor) {
        Anchor.TODAY, Anchor.TONIGHT -> today
        Anchor.TOMORROW -> today.plusDays(1)
        Anchor.DAY_AFTER_TOMORROW -> today.plusDays(2)
        Anchor.WEEKEND -> nextOnOrAfter(today, weekendDay())
        Anchor.NEXT_WEEKEND -> weekStart().plusDays(7L + offsetInWeek(weekendDay()))
        Anchor.NEXT_WEEK -> weekStart().plusDays(7)
        Anchor.NEXT_MONTH -> engine.firstDayOfMonth(engine.monthOf(today).plus(1))
        Anchor.END_OF_MONTH -> engine.lastDayOfMonth(engine.monthOf(today))
        Anchor.END_OF_NEXT_MONTH -> engine.lastDayOfMonth(engine.monthOf(today).plus(1))
        Anchor.NEXT_YEAR -> engine.toLocalDate(engine.toCalendarDate(today).year + 1, 1, 1)
    }

    /** The weekend day: Friday for weeks that start on Saturday, otherwise Saturday. */
    private fun weekendDay(): DayOfWeek = if (ctx.firstDayOfWeek == DayOfWeek.SATURDAY) DayOfWeek.FRIDAY else DayOfWeek.SATURDAY

    private fun weekStart(): LocalDate = today.minusDays(offsetInWeek(today.dayOfWeek).toLong())

    private fun offsetInWeek(day: DayOfWeek): Int = Math.floorMod(day.value - ctx.firstDayOfWeek.value, 7)

    private fun nextOnOrAfter(from: LocalDate, day: DayOfWeek): LocalDate =
        from.plusDays(Math.floorMod(day.value - from.dayOfWeek.value, 7).toLong())

    // endregion

    /** Applies the date/time rules and builds the title from the words that are left. */
    private fun resolve(parts: List<QuickAddPart>): QuickAddResult {
        val nowTime = ctx.now.toLocalTime()
        var date = draft.date
        var time = draft.time
        var ambiguous = draft.ambiguous
        val rule = draft.recurrence
        if (date == null && rule != null) {
            date = if (rule.weekdays.isNotEmpty()) (0L..6L).map { today.plusDays(it) }.first { it.dayOfWeek in rule.weekdays } else today
        }
        if (time != null && ambiguous && draft.night) {
            time = time.withHour(if (time.hour <= NIGHT_EARLY_HOURS) time.hour else time.hour + 12)
            ambiguous = false
        }
        if (time != null && ambiguous) {
            // Hours 1–11 without a time-of-day word: the next upcoming of h:mm and (h+12):mm.
            val morning = time
            val evening = time.plusHours(12)
            if (date == null || date == today) {
                when {
                    morning > nowTime -> time = morning
                    evening > nowTime -> time = evening
                    date == null -> {
                        date = today.plusDays(1)
                        time = morning
                    }
                    else -> time = evening
                }
                date = date ?: today
            } else {
                // Another day: 1–6 means the afternoon, 7–11 the morning.
                time = if (morning.hour <= AFTERNOON_HOURS) evening else morning
            }
        } else if (time != null && date == null) {
            date = if (time > nowTime) today else today.plusDays(1)
        }
        return QuickAddResult(
            title = title(),
            date = date,
            time = time,
            recurrence = rule,
            priority = draft.priority,
            tags = draft.tags.values.toList(),
            project = draft.project,
            deadline = draft.deadline,
            durationMinutes = draft.duration,
            reminderMinutesBefore = draft.reminder,
            parts = parts,
        )
    }

    private fun title(): String {
        val left = tokens.indices.filter { consumed[it] == null }.toMutableList()
        fun dangling(index: Int): Boolean {
            val t = tokens[index]
            return t.word in v.danglingWords || t.word.isEmpty() ||
                text.substring(t.start, t.end).all { it in Vocabulary.DANGLING_PUNCTUATION }
        }
        while (left.isNotEmpty() && dangling(left.first())) left.removeAt(0)
        while (left.isNotEmpty() && dangling(left.last())) left.removeAt(left.lastIndex)
        return left.joinToString(" ") { text.substring(tokens[it].start, tokens[it].end) }
            .trim { it.isWhitespace() || it in Vocabulary.DANGLING_PUNCTUATION }
    }

    private companion object {
        const val PROJECT_WORDS = 4
        const val MAX_INTERVAL = 365
        const val MAX_RELATIVE = 3650
        const val MAX_DURATION = 24 * 60
        const val NIGHT_EARLY_HOURS = 4
        const val AFTERNOON_HOURS = 6
    }
}
