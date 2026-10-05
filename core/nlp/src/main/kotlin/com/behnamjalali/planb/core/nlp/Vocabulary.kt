package com.behnamjalali.planb.core.nlp

import com.behnamjalali.planb.core.model.Priority
import java.time.DayOfWeek

/**
 * Phrases of one or more words mapped to values. Persian phrases also match when their words
 * are written together, apart or with a half-space («پس‌فردا», «پس فردا», «پسفردا»).
 */
internal class Lexicon<T>(entries: List<Pair<String, T>>) {
    private val spaced = HashMap<String, T>()
    private val joined = HashMap<String, T>()
    private val maxWords: Int

    init {
        var longest = 1
        entries.forEach { (phrase, value) ->
            val words = phrase.split(' ').filter { it.isNotEmpty() }.map { QuickAddText.stem(QuickAddText.normalize(it)) }
            longest = maxOf(longest, words.size)
            spaced.putIfAbsent(words.joinToString(" "), value)
            if (words.any(QuickAddText::isPersian)) joined.putIfAbsent(words.joinToString(""), value)
        }
        // A one-word phrase may be typed as two words («یک شنبه»).
        maxWords = longest + 1
    }

    /** The longest phrase starting at token [from]: its value and the number of tokens it covers. */
    fun match(tokens: List<Token>, from: Int): Pair<T, Int>? {
        val most = minOf(maxWords, tokens.size - from)
        for (count in most downTo 1) {
            val words = (from until from + count).map { tokens[it].word }
            if (words.any { it.isEmpty() }) continue
            spaced[words.joinToString(" ")]?.let { return it to count }
            if (words.all { w -> QuickAddText.isPersian(w) && w.none(Char::isDigit) }) {
                joined[words.joinToString("")]?.let { return it to count }
            }
        }
        return null
    }

    fun contains(tokens: List<Token>, from: Int): Boolean = match(tokens, from) != null
}

internal enum class Span { MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }

/** A time-of-day word: «صبح», «عصر», "pm"… */
internal enum class Period { AM, PM, MORNING, NOON, AFTERNOON, LATE_AFTERNOON, EVENING, NIGHT, MIDNIGHT }

internal enum class Anchor { TODAY, TONIGHT, TOMORROW, DAY_AFTER_TOMORROW, WEEKEND, NEXT_WEEKEND, NEXT_WEEK, NEXT_MONTH, END_OF_MONTH, END_OF_NEXT_MONTH, NEXT_YEAR }

internal object Vocabulary {
    private fun <T> lex(vararg entries: Pair<String, T>) = Lexicon(entries.toList())

    val numberWords: Lexicon<Int> = lex(
        "یک" to 1, "یه" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "چار" to 4, "پنج" to 5, "شش" to 6, "شیش" to 6,
        "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10, "یازده" to 11, "دوازده" to 12, "پانزده" to 15, "پونزده" to 15,
        "بیست" to 20, "سی" to 30, "چهل" to 40, "چهل و پنج" to 45, "نود" to 90,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "forty-five" to 45, "ninety" to 90,
    )

    val articles: Lexicon<Int> = lex("a" to 1, "an" to 1)

    val units: Lexicon<Span> = lex(
        "دقیقه" to Span.MINUTE, "دقیقه‌ای" to Span.MINUTE, "دقه" to Span.MINUTE,
        "min" to Span.MINUTE, "mins" to Span.MINUTE, "minute" to Span.MINUTE, "minutes" to Span.MINUTE,
        "ساعت" to Span.HOUR, "ساعته" to Span.HOUR, "hour" to Span.HOUR, "hours" to Span.HOUR, "hr" to Span.HOUR, "hrs" to Span.HOUR,
        "روز" to Span.DAY, "day" to Span.DAY, "days" to Span.DAY,
        "هفته" to Span.WEEK, "week" to Span.WEEK, "weeks" to Span.WEEK,
        "ماه" to Span.MONTH, "month" to Span.MONTH, "months" to Span.MONTH,
        "سال" to Span.YEAR, "year" to Span.YEAR, "years" to Span.YEAR,
    )

    /** "…later": «۳ روز دیگه», «۲ هفته بعد», "3 days from now" ("in 3 days" is handled by "in"). */
    val later: Lexicon<Boolean> = lex("دیگه" to true, "دیگر" to true, "بعد" to true, "آینده" to true, "later" to true, "from now" to true)

    val inWord: Lexicon<Boolean> = lex("in" to true, "within" to true)

    /** «۱۰ دقیقه قبل» (for reminders). */
    val before: Lexicon<Boolean> = lex(
        "قبل" to true, "قبلش" to true, "قبل از اون" to true, "زودتر" to true, "پیش" to true, "before" to true, "earlier" to true, "ahead" to true,
        "before it" to true,
    )

    val half: Lexicon<Int> = lex("و نیم" to 30, "و ربع" to 15, "and a half" to 30)

    val anchors: Lexicon<Anchor> = lex(
        "امروز" to Anchor.TODAY, "today" to Anchor.TODAY,
        "امشب" to Anchor.TONIGHT, "tonight" to Anchor.TONIGHT,
        "فردا" to Anchor.TOMORROW, "tomorrow" to Anchor.TOMORROW, "tmrw" to Anchor.TOMORROW, "tmr" to Anchor.TOMORROW,
        "پس‌فردا" to Anchor.DAY_AFTER_TOMORROW, "پسون فردا" to Anchor.DAY_AFTER_TOMORROW,
        "day after tomorrow" to Anchor.DAY_AFTER_TOMORROW, "the day after tomorrow" to Anchor.DAY_AFTER_TOMORROW,
        "آخر هفته" to Anchor.WEEKEND, "آخر این هفته" to Anchor.WEEKEND, "weekend" to Anchor.WEEKEND,
        "this weekend" to Anchor.WEEKEND, "end of the week" to Anchor.WEEKEND, "end of week" to Anchor.WEEKEND,
        "آخر هفته بعد" to Anchor.NEXT_WEEKEND, "آخر هفته آینده" to Anchor.NEXT_WEEKEND, "آخر هفته دیگه" to Anchor.NEXT_WEEKEND,
        "next weekend" to Anchor.NEXT_WEEKEND,
        "هفته بعد" to Anchor.NEXT_WEEK, "هفته آینده" to Anchor.NEXT_WEEK, "هفته دیگه" to Anchor.NEXT_WEEK, "هفته دیگر" to Anchor.NEXT_WEEK,
        "اول هفته" to Anchor.NEXT_WEEK, "اول هفته بعد" to Anchor.NEXT_WEEK, "اول هفته آینده" to Anchor.NEXT_WEEK, "اول هفته دیگه" to Anchor.NEXT_WEEK,
        "next week" to Anchor.NEXT_WEEK, "start of next week" to Anchor.NEXT_WEEK, "beginning of next week" to Anchor.NEXT_WEEK,
        "ماه بعد" to Anchor.NEXT_MONTH, "ماه آینده" to Anchor.NEXT_MONTH, "ماه دیگه" to Anchor.NEXT_MONTH, "ماه دیگر" to Anchor.NEXT_MONTH,
        "اول ماه" to Anchor.NEXT_MONTH, "اول ماه بعد" to Anchor.NEXT_MONTH, "اول ماه آینده" to Anchor.NEXT_MONTH, "اول ماه دیگه" to Anchor.NEXT_MONTH,
        "next month" to Anchor.NEXT_MONTH, "start of next month" to Anchor.NEXT_MONTH, "beginning of next month" to Anchor.NEXT_MONTH,
        "آخر ماه" to Anchor.END_OF_MONTH, "آخر این ماه" to Anchor.END_OF_MONTH, "end of month" to Anchor.END_OF_MONTH,
        "end of the month" to Anchor.END_OF_MONTH,
        "آخر ماه بعد" to Anchor.END_OF_NEXT_MONTH, "آخر ماه آینده" to Anchor.END_OF_NEXT_MONTH, "end of next month" to Anchor.END_OF_NEXT_MONTH,
        "سال بعد" to Anchor.NEXT_YEAR, "سال آینده" to Anchor.NEXT_YEAR, "سال دیگه" to Anchor.NEXT_YEAR, "next year" to Anchor.NEXT_YEAR,
    )

    private val persianWeekdays = listOf(
        "شنبه" to DayOfWeek.SATURDAY, "یکشنبه" to DayOfWeek.SUNDAY, "دوشنبه" to DayOfWeek.MONDAY,
        "سه‌شنبه" to DayOfWeek.TUESDAY, "سشنبه" to DayOfWeek.TUESDAY, "چهارشنبه" to DayOfWeek.WEDNESDAY,
        "چارشنبه" to DayOfWeek.WEDNESDAY, "پنجشنبه" to DayOfWeek.THURSDAY, "جمعه" to DayOfWeek.FRIDAY,
    )
    private val englishWeekdays = listOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY,
    )
    private val englishShortWeekdays = listOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
        "mondays" to DayOfWeek.MONDAY, "tuesdays" to DayOfWeek.TUESDAY, "wednesdays" to DayOfWeek.WEDNESDAY,
        "thursdays" to DayOfWeek.THURSDAY, "fridays" to DayOfWeek.FRIDAY, "saturdays" to DayOfWeek.SATURDAY, "sundays" to DayOfWeek.SUNDAY,
    )

    /** Weekday names that are unambiguous on their own. */
    val weekdays: Lexicon<DayOfWeek> = Lexicon(persianWeekdays + englishWeekdays)

    /** Also short English names ("fri"), accepted only after "on", "next", "every", "by"… */
    val weekdaysAfterMarker: Lexicon<DayOfWeek> = Lexicon(persianWeekdays + englishWeekdays + englishShortWeekdays)

    /** Before a weekday: «روز جمعه», «همین جمعه», "on friday", "this friday". */
    val weekdayPrefix: Lexicon<Boolean> = lex("روز" to false, "این" to false, "همین" to false, "on" to false, "this" to false, "next" to true)

    /** After a weekday: «جمعهٔ بعد», «جمعه آینده», «جمعه دیگه» (the one of next week). */
    val weekdayNext: Lexicon<Boolean> = lex("بعد" to true, "آینده" to true, "دیگه" to true, "دیگر" to true, "بعدی" to true)

    /** Jalali month names (always the Jalali calendar). */
    val jalaliMonths: Lexicon<Int> = lex(
        "فروردین" to 1, "اردیبهشت" to 2, "خرداد" to 3, "تیر" to 4, "مرداد" to 5, "امرداد" to 5, "شهریور" to 6,
        "مهر" to 7, "آبان" to 8, "آذر" to 9, "دی" to 10, "بهمن" to 11, "اسفند" to 12,
    )

    /** Gregorian month names in Persian and English (always the Gregorian calendar). */
    val gregorianMonths: Lexicon<Int> = lex(
        "ژانویه" to 1, "فوریه" to 2, "مارس" to 3, "آوریل" to 4, "مه" to 5, "می" to 5, "ژوئن" to 6, "ژوئیه" to 7,
        "جولای" to 7, "اوت" to 8, "آگوست" to 8, "سپتامبر" to 9, "اکتبر" to 10, "نوامبر" to 11, "دسامبر" to 12,
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3, "april" to 4, "apr" to 4,
        "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7, "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9, "sept" to 9, "october" to 10, "oct" to 10, "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12,
    )

    /** Optional word after a Jalali month: «۱۵ مهر ماه». */
    val monthWord: Lexicon<Boolean> = lex("ماه" to true)

    /** "the 15th of october": the joining "of". */
    val ofWord: Lexicon<Boolean> = lex("of" to true)

    /** Before a date: «برای جمعه», "on friday", "for tomorrow". */
    val datePrefix: Lexicon<Boolean> = lex("برای" to true, "on" to true, "for" to true)

    /** Time-of-day words. */
    val periods: Lexicon<Period> = lex(
        "صبح" to Period.MORNING, "صبح زود" to Period.MORNING, "ظهر" to Period.NOON,
        "بعدازظهر" to Period.AFTERNOON, "بعد از ظهر" to Period.AFTERNOON, "بعدظهر" to Period.AFTERNOON, "بعد ظهر" to Period.AFTERNOON,
        "عصر" to Period.LATE_AFTERNOON, "غروب" to Period.EVENING, "شب" to Period.NIGHT, "نیمه شب" to Period.MIDNIGHT, "نصف شب" to Period.MIDNIGHT,
        "am" to Period.AM, "a.m" to Period.AM, "a.m." to Period.AM, "pm" to Period.PM, "p.m" to Period.PM, "p.m." to Period.PM,
        "morning" to Period.MORNING, "in the morning" to Period.MORNING, "this morning" to Period.MORNING,
        "noon" to Period.NOON, "at noon" to Period.NOON, "midday" to Period.NOON,
        "afternoon" to Period.AFTERNOON, "in the afternoon" to Period.AFTERNOON, "this afternoon" to Period.AFTERNOON,
        "evening" to Period.EVENING, "in the evening" to Period.EVENING, "this evening" to Period.EVENING,
        "night" to Period.NIGHT, "at night" to Period.NIGHT, "midnight" to Period.MIDNIGHT, "at midnight" to Period.MIDNIGHT,
    )

    /** English period words are too common in titles ("Morning run") to count on their own. */
    val englishBarePeriods: Set<String> = setOf("morning", "afternoon", "evening", "night", "noon", "midday", "midnight")

    /** «ساعت ۵», "at 5". */
    val timePrefix: Lexicon<Boolean> = lex("ساعت" to true, "سر ساعت" to true, "حدود ساعت" to true, "at" to true, "@" to true, "around" to true)

    val durationPrefix: Lexicon<Boolean> = lex("به مدت" to true, "حدود" to true, "حدودا" to true, "for" to true, "about" to true, "takes" to true)

    /** Whole durations: «نیم ساعت», «یک ربع», "half an hour". */
    val durationWords: Lexicon<Int> = lex(
        "نیم ساعت" to 30, "نیم ساعته" to 30, "یک ربع" to 15, "یه ربع" to 15, "ربع ساعت" to 15, "یک ربع ساعت" to 15,
        "half an hour" to 30, "half hour" to 30, "a half hour" to 30, "quarter of an hour" to 15, "a quarter hour" to 15,
        "an hour" to 60, "an hour and a half" to 90,
    )

    val reminders: Lexicon<Boolean> = lex(
        "یادم بنداز" to true, "یادم بینداز" to true, "یادم بیانداز" to true, "یادآوری کن" to true, "یادآوری" to true,
        "بهم یادآوری کن" to true, "یادآور" to true, "remind me" to true, "reminder" to true, "alert me" to true, "notify me" to true,
    )

    /** "On time" reminders. */
    val onTime: Lexicon<Boolean> = lex("سر وقت" to true, "سروقت" to true, "on time" to true, "at the time" to true)

    val deadlinePrefix: Lexicon<Boolean> = lex(
        "تا" to true, "تا قبل از" to true, "قبل از" to true, "مهلت" to true, "مهلتش" to true, "مهلت تا" to true,
        "ددلاین" to true, "ددلاین تا" to true, "حداکثر تا" to true, "نهایتا تا" to true, "نهایتاً تا" to true,
        "by" to true, "deadline" to true, "due by" to true, "no later than" to true, "before" to true,
    )

    val every: Lexicon<Boolean> = lex("هر" to true, "every" to true, "each" to true)

    /** Optional ending of «هر ۳ روز یکبار». */
    val once: Lexicon<Boolean> = lex("یکبار" to true, "یک بار" to true, "یه بار" to true, "بار" to true)

    val conjunctions: Lexicon<Boolean> = lex("و" to true, "and" to true, "&" to true)

    val until: Lexicon<Boolean> = lex("تا" to true, "until" to true, "till" to true)

    /** Fixed repeats; adjective forms ("weekly report") only count at the end or next to other parts. */
    val repeats: Lexicon<Repeat> = lex(
        "هر روز" to Repeat.DAILY, "every day" to Repeat.DAILY, "everyday" to Repeat.DAILY, "each day" to Repeat.DAILY,
        "هر هفته" to Repeat.WEEKLY, "every week" to Repeat.WEEKLY, "each week" to Repeat.WEEKLY,
        "هر ماه" to Repeat.MONTHLY, "every month" to Repeat.MONTHLY, "each month" to Repeat.MONTHLY,
        "هر سال" to Repeat.YEARLY, "every year" to Repeat.YEARLY, "each year" to Repeat.YEARLY,
        "یک روز در میان" to Repeat.OTHER_DAY, "یه روز در میون" to Repeat.OTHER_DAY, "یک روز در میون" to Repeat.OTHER_DAY,
        "every other day" to Repeat.OTHER_DAY,
        "هر دو هفته" to Repeat.OTHER_WEEK, "دو هفته یکبار" to Repeat.OTHER_WEEK, "every other week" to Repeat.OTHER_WEEK,
        "every two weeks" to Repeat.OTHER_WEEK, "biweekly" to Repeat.OTHER_WEEK,
        "هر روز کاری" to Repeat.WORKDAYS, "روزهای کاری" to Repeat.WORKDAYS, "روزای کاری" to Repeat.WORKDAYS,
        "every weekday" to Repeat.WORKDAYS, "weekdays" to Repeat.WORKDAYS, "on weekdays" to Repeat.WORKDAYS, "every workday" to Repeat.WORKDAYS,
    )

    val repeatAdjectives: Lexicon<Repeat> = lex(
        "روزانه" to Repeat.DAILY, "daily" to Repeat.DAILY, "هفتگی" to Repeat.WEEKLY, "weekly" to Repeat.WEEKLY,
        "ماهانه" to Repeat.MONTHLY, "ماهیانه" to Repeat.MONTHLY, "monthly" to Repeat.MONTHLY,
        "سالانه" to Repeat.YEARLY, "سالیانه" to Repeat.YEARLY, "yearly" to Repeat.YEARLY, "annually" to Repeat.YEARLY,
    )

    val priorityWords: Lexicon<Priority> = lex(
        "فوری" to Priority.HIGH, "خیلی مهم" to Priority.HIGH, "مهم" to Priority.HIGH, "اولویت بالا" to Priority.HIGH,
        "الویت بالا" to Priority.HIGH, "اولویت زیاد" to Priority.HIGH, "اولویت متوسط" to Priority.MEDIUM,
        "اولویت معمولی" to Priority.MEDIUM, "اولویت پایین" to Priority.LOW, "اولویت کم" to Priority.LOW,
        "urgent" to Priority.HIGH, "asap" to Priority.HIGH, "important" to Priority.HIGH, "high priority" to Priority.HIGH,
        "medium priority" to Priority.MEDIUM, "low priority" to Priority.LOW,
    )

    /** Leftover connectors removed from both ends of the title. */
    val danglingWords: Set<String> = setOf("و", "در", "برای", "تا", "از", "به", "and", "on", "at", "by", "in", "for", "every", "of", "the")
    const val DANGLING_PUNCTUATION = "-–—,،;؛:|/"
}

internal enum class Repeat { DAILY, WEEKLY, MONTHLY, YEARLY, OTHER_DAY, OTHER_WEEK, WORKDAYS }
