package com.behnamjalali.planb.core.nlp

import com.behnamjalali.planb.core.model.CalendarSystem
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Parts, dismissals and context rules of the quick-add parser (the table is in [QuickAddParserCasesTest]). */
@RunWith(RobolectricTestRunner::class)
class QuickAddParserTest {
    private val now = LocalDateTime.of(2026, 10, 4, 10, 0)
    private val fa = QuickAddContext(now, CalendarSystem.JALALI, DayOfWeek.SATURDAY)
    private val en = QuickAddContext(now, CalendarSystem.GREGORIAN, DayOfWeek.MONDAY)

    @Test
    fun jalaliDatesOfTheTable_matchTheJalaliEngine() {
        JALALI_DATES.forEach { (ymd, date) ->
            assertThat(com.behnamjalali.planb.core.datetime.JalaliEngine.toLocalDate(ymd.first, ymd.second, ymd.third)).isEqualTo(date)
        }
    }

    @Test
    fun parts_coverTheOriginalText_inOrder() {
        val text = "فردا ساعت ۵ عصر جلسه #کار"
        val result = QuickAddParser.parse(text, fa)
        assertThat(result.parts.map { it.kind })
            .containsExactly(QuickAddKind.DATE, QuickAddKind.TIME, QuickAddKind.TAG).inOrder()
        result.parts.forEach { assertThat(text.substring(it.start, it.end)).isEqualTo(it.text) }
        assertThat(result.parts.map { it.text }).containsExactly("فردا", "ساعت ۵ عصر", "#کار").inOrder()
    }

    @Test
    fun dismissingAPart_keepsItsWordsInTheTitle() {
        val text = "فردا نان بخرم"
        val first = QuickAddParser.parse(text, fa)
        val key = first.parts.single().key
        val second = QuickAddParser.parse(text, fa, dismissed = setOf(key))
        assertThat(second.date).isNull()
        assertThat(second.title).isEqualTo("فردا نان بخرم")
        assertThat(second.parts).isEmpty()
    }

    @Test
    fun dismissedKey_isStableWhileTheRestOfTheTextChanges() {
        val key = QuickAddParser.parse("فردا", fa).parts.single().key
        val later = QuickAddParser.parse("خرید فردا و پنجشنبه", fa, dismissed = setOf(key))
        assertThat(later.title).isEqualTo("خرید فردا")
        // With «فردا» kept as text, «پنجشنبه» is the date.
        assertThat(later.date).isEqualTo(LocalDate.of(2026, 10, 8))
    }

    @Test
    fun dismissingTheTime_keepsTheDate() {
        val text = "Call mom tomorrow at 5"
        val time = QuickAddParser.parse(text, en).parts.first { it.kind == QuickAddKind.TIME }
        val result = QuickAddParser.parse(text, en, setOf(time.key))
        assertThat(result.time).isNull()
        assertThat(result.date).isEqualTo(LocalDate.of(2026, 10, 5))
        assertThat(result.title).isEqualTo("Call mom at 5")
    }

    @Test
    fun onlyTheFirstOfEachKindCounts_butTagsRepeat() {
        val result = QuickAddParser.parse("جمعه یا شنبه #الف #ب #الف", fa)
        assertThat(result.date).isEqualTo(LocalDate.of(2026, 10, 9))
        assertThat(result.title).isEqualTo("یا شنبه")
        assertThat(result.tags).containsExactly("الف", "ب").inOrder()
    }

    @Test
    fun onlyTheRequestedKindsAreRead() {
        val result = QuickAddParser.parse("فردا جلسه #کار فوری", fa, kinds = setOf(QuickAddKind.DATE, QuickAddKind.TIME))
        assertThat(result.date).isEqualTo(LocalDate.of(2026, 10, 5))
        assertThat(result.title).isEqualTo("جلسه #کار فوری")
        assertThat(result.tags).isEmpty()
        assertThat(result.priority).isNull()
    }

    @Test
    fun emptyAndBlankText() {
        assertThat(QuickAddParser.parse("", fa)).isEqualTo(QuickAddResult(title = ""))
        assertThat(QuickAddParser.parse("   ‌ ", fa).title).isEmpty()
    }

    @Test
    fun onlyParts_leaveAnEmptyTitle() {
        val result = QuickAddParser.parse("فردا ساعت ۵", fa)
        assertThat(result.title).isEmpty()
        assertThat(result.date).isEqualTo(LocalDate.of(2026, 10, 5))
        assertThat(result.time).isEqualTo(LocalTime.of(17, 0))
    }

    @Test
    fun calendarSetting_decidesNumericDatesAndMonthArithmetic() {
        // «۱/۸» is 1 Aban in the Jalali calendar and 1 August in the Gregorian one.
        assertThat(QuickAddParser.parse("قسط ۱/۸", fa).date).isEqualTo(LocalDate.of(2026, 10, 23))
        assertThat(QuickAddParser.parse("قسط ۱/۸", fa.copy(calendar = CalendarSystem.GREGORIAN)).date).isEqualTo(LocalDate.of(2027, 8, 1))
        // Month names choose their own calendar whatever the setting.
        assertThat(QuickAddParser.parse("۱ آبان", fa.copy(calendar = CalendarSystem.GREGORIAN)).date).isEqualTo(LocalDate.of(2026, 10, 23))
        assertThat(QuickAddParser.parse("rent end of month", en.copy(calendar = CalendarSystem.JALALI)).date).isEqualTo(LocalDate.of(2026, 10, 22))
    }

    @Test
    fun firstDayOfWeek_decidesNextWeekAndTheWeekend() {
        val sundayStart = en.copy(firstDayOfWeek = DayOfWeek.SUNDAY)
        // Week of Sunday 4 Oct: next week starts on 11 Oct.
        assertThat(QuickAddParser.parse("report next week", sundayStart).date).isEqualTo(LocalDate.of(2026, 10, 11))
        assertThat(QuickAddParser.parse("party next friday", sundayStart).date).isEqualTo(LocalDate.of(2026, 10, 16))
        // Weeks starting on Saturday end with a Friday weekend.
        assertThat(QuickAddParser.parse("groceries weekend", en.copy(firstDayOfWeek = DayOfWeek.SATURDAY)).date).isEqualTo(LocalDate.of(2026, 10, 9))
    }

    @Test
    fun ambiguousHours_followTheDocumentedRule() {
        fun at(h: Int, m: Int = 0) = fa.copy(now = now.withHour(h).withMinute(m))
        // Before both instances: the morning one.
        assertThat(QuickAddParser.parse("ساعت ۵", at(4)).time).isEqualTo(LocalTime.of(5, 0))
        // Between: the evening one, today.
        assertThat(QuickAddParser.parse("ساعت ۵", at(6)).time).isEqualTo(LocalTime.of(17, 0))
        // After both: tomorrow morning.
        val late = QuickAddParser.parse("ساعت ۵", at(18))
        assertThat(late.date).isEqualTo(LocalDate.of(2026, 10, 5))
        assertThat(late.time).isEqualTo(LocalTime.of(5, 0))
        // «امروز» keeps today even when both have passed.
        assertThat(QuickAddParser.parse("امروز ساعت ۵", at(18)).date).isEqualTo(LocalDate.of(2026, 10, 4))
        // An exact minute is in the future only when strictly later.
        assertThat(QuickAddParser.parse("ساعت ۱۰", at(10)).time).isEqualTo(LocalTime.of(22, 0))
    }

    @Test
    fun projectPrefix_mustBeUnique() {
        val projects = listOf(QuickAddProject(1, "Home redesign"), QuickAddProject(2, "Homework"))
        val context = en.copy(projects = projects)
        assertThat(QuickAddParser.parse("paint @home", context).project).isNull()
        assertThat(QuickAddParser.parse("paint @homew", context).project?.id).isEqualTo(2)
        assertThat(QuickAddParser.parse("paint @home redesign", context).project?.id).isEqualTo(1)
    }

    @Test
    fun withoutProjects_atWordsStayInTheTitle() {
        assertThat(QuickAddParser.parse("email @sara", en).title).isEqualTo("email @sara")
    }

    @Test
    fun punctuationAroundParts_isCleanedFromTheTitle() {
        assertThat(QuickAddParser.parse("خرید نان، فردا", fa).title).isEqualTo("خرید نان")
        assertThat(QuickAddParser.parse("Buy milk - tomorrow!", en).title).isEqualTo("Buy milk")
        assertThat(QuickAddParser.parse("«فردا» خرید", fa).date).isEqualTo(LocalDate.of(2026, 10, 5))
    }

    @Test
    fun mixedScriptsAndDigits() {
        val result = QuickAddParser.parse("Deploy v2 فردا 17:30 #release", fa)
        assertThat(result.title).isEqualTo("Deploy v2")
        assertThat(result.time).isEqualTo(LocalTime.of(17, 30))
        assertThat(result.tags).containsExactly("release")
    }

    @Test
    fun longText_isParsedQuickly() {
        val text = (1..400).joinToString(" ") { if (it % 50 == 0) "فردا" else "کلمه$it" }
        val start = System.nanoTime()
        val result = QuickAddParser.parse(text, fa)
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000)
        assertThat(result.date).isEqualTo(LocalDate.of(2026, 10, 5))
    }
}
