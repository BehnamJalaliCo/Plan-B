package com.behnamjalali.planb.core.nlp

import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.google.common.truth.Truth.assertWithMessage
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/** "Now" for every case unless it says otherwise: Sunday 4 Oct 2026 (12 Mehr 1405), 10:00. */
private val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 4, 10, 0)
private val TODAY: LocalDate = NOW.toLocalDate()
private fun d(days: Long): LocalDate = TODAY.plusDays(days)
private fun t(h: Int, m: Int = 0): LocalTime = LocalTime.of(h, m)

/**
 * Jalali dates used below, written out (the table is built before Robolectric starts, so it
 * cannot call ICU); [QuickAddParserTest] checks them against the Jalali engine.
 */
internal val JALALI_DATES: Map<Triple<Int, Int, Int>, LocalDate> = mapOf(
    Triple(1405, 7, 15) to LocalDate.of(2026, 10, 7),
    Triple(1405, 7, 20) to LocalDate.of(2026, 10, 12),
    Triple(1405, 7, 30) to LocalDate.of(2026, 10, 22),
    Triple(1405, 8, 1) to LocalDate.of(2026, 10, 23),
    Triple(1405, 8, 12) to LocalDate.of(2026, 11, 3),
    Triple(1405, 12, 20) to LocalDate.of(2027, 3, 11),
    Triple(1406, 1, 1) to LocalDate.of(2027, 3, 21),
    Triple(1406, 7, 10) to LocalDate.of(2027, 10, 2),
)

private fun jalali(y: Int, m: Int, day: Int): LocalDate = JALALI_DATES.getValue(Triple(y, m, day))

private val PROJECTS = listOf(
    QuickAddProject(1, "راه‌اندازی Plan-B"),
    QuickAddProject(2, "خانه"),
    QuickAddProject(3, "Home redesign"),
    QuickAddProject(4, "Thesis"),
)

private fun fa(frequency: RecurrenceFrequency, interval: Int = 1, days: Set<DayOfWeek> = emptySet(), until: LocalDate? = null) =
    RecurrenceRule(frequency, interval, days, CalendarSystem.JALALI, until)

private fun en(frequency: RecurrenceFrequency, interval: Int = 1, days: Set<DayOfWeek> = emptySet(), until: LocalDate? = null) =
    RecurrenceRule(frequency, interval, days, CalendarSystem.GREGORIAN, until)

/**
 * One table row: the text, the expected title and every field. Fields not given must stay
 * empty, so each row also checks that nothing else was read into the text.
 */
data class Case(
    val input: String,
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val recurrence: RecurrenceRule? = null,
    val priority: Priority? = null,
    val tags: List<String> = emptyList(),
    val project: Long? = null,
    val deadline: LocalDate? = null,
    val duration: Int? = null,
    val reminder: Int? = null,
    val english: Boolean = false,
    val now: LocalDateTime = NOW,
) {
    override fun toString(): String = (if (english) "en: " else "fa: ") + input + (if (now != NOW) " @" + now.toLocalTime() else "")
}

private val faCases = listOf(
    // Dates
    Case("امروز نان بخرم", "نان بخرم", date = d(0)),
    Case("فردا نان بخرم", "نان بخرم", date = d(1)),
    Case("نان بخرم فردا", "نان بخرم", date = d(1)),
    Case("پس‌فردا دندانپزشکی", "دندانپزشکی", date = d(2)),
    Case("پس فردا دندانپزشکی", "دندانپزشکی", date = d(2)),
    Case("پسفردا دندانپزشکی", "دندانپزشکی", date = d(2)),
    Case("پنجشنبه جلسه با تیم", "جلسه با تیم", date = d(4)),
    Case("پنج‌شنبه جلسه با تیم", "جلسه با تیم", date = d(4)),
    Case("پنج شنبه جلسه با تیم", "جلسه با تیم", date = d(4)),
    Case("پنجشنبهٔ بعد جلسه با تیم", "جلسه با تیم", date = d(11)),
    Case("پنجشنبه‌ی بعد جلسه با تیم", "جلسه با تیم", date = d(11)),
    Case("پنجشنبه آینده جلسه", "جلسه", date = d(11)),
    Case("جمعه کوه", "کوه", date = d(5)),
    Case("روز جمعه کوه", "کوه", date = d(5)),
    Case("شنبه کلاس زبان", "کلاس زبان", date = d(6)),
    Case("یکشنبه کلاس زبان", "کلاس زبان", date = d(7)),
    Case("یک‌شنبه کلاس زبان", "کلاس زبان", date = d(7)),
    Case("همین یکشنبه کلاس زبان", "کلاس زبان", date = d(0)),
    Case("دوشنبه بیمه", "بیمه", date = d(1)),
    Case("سه‌شنبه بیمه", "بیمه", date = d(2)),
    Case("سه شنبه بیمه", "بیمه", date = d(2)),
    Case("چهارشنبه بیمه", "بیمه", date = d(3)),
    Case("يكشنبه کلاس", "کلاس", date = d(7)),
    Case("۳ روز دیگه تمدید گواهینامه", "تمدید گواهینامه", date = d(3)),
    Case("۳ روز دیگر تمدید گواهینامه", "تمدید گواهینامه", date = d(3)),
    Case("٣ روز ديگه تمدید", "تمدید", date = d(3)),
    Case("سه روز بعد تمدید", "تمدید", date = d(3)),
    Case("۲ هفته دیگه سفر", "سفر", date = d(14)),
    Case("یک ماه دیگه چکاپ", "چکاپ", date = jalali(1405, 8, 12)),
    Case("آخر هفته خرید", "خرید", date = d(5)),
    Case("آخر هفته بعد خرید", "خرید", date = d(12)),
    Case("هفته بعد گزارش", "گزارش", date = d(6)),
    Case("هفته‌ی آینده گزارش", "گزارش", date = d(6)),
    Case("اول ماه بعد اجاره", "اجاره", date = jalali(1405, 8, 1)),
    Case("اول ماه اجاره", "اجاره", date = jalali(1405, 8, 1)),
    Case("آخر ماه اجاره", "اجاره", date = jalali(1405, 7, 30)),
    Case("سال بعد تمدید پاسپورت", "تمدید پاسپورت", date = jalali(1406, 1, 1)),
    Case("۱۵ مهر تولد سارا", "تولد سارا", date = jalali(1405, 7, 15)),
    Case("تولد سارا ۱۵ مهر", "تولد سارا", date = jalali(1405, 7, 15)),
    Case("۱۵ام مهر تولد", "تولد", date = jalali(1405, 7, 15)),
    Case("۱۵ مهر ماه تولد", "تولد", date = jalali(1405, 7, 15)),
    Case("۱۰ مهر سالگرد", "سالگرد", date = jalali(1406, 7, 10)),
    Case("۱ آبان سالگرد", "سالگرد", date = jalali(1405, 8, 1)),
    Case("۲۰ اسفند ۱۴۰۵ خرید عید", "خرید عید", date = jalali(1405, 12, 20)),
    Case("۲۵ دسامبر کریسمس", "کریسمس", date = LocalDate.of(2026, 12, 25)),
    Case("۳۱ مهر جلسه", "۳۱ مهر جلسه"),
    Case("۱۵/۷ قسط", "قسط", date = jalali(1405, 7, 15)),
    Case("قسط ۱۴۰۵/۸/۱", "قسط", date = jalali(1405, 8, 1)),
    Case("قسط ۲۰۲۶/۱۲/۲۵", "قسط", date = LocalDate.of(2026, 12, 25)),
    Case("برای فردا کیک بپزم", "کیک بپزم", date = d(1)),
    // Times
    Case("ساعت ۵ تماس با مادر", "تماس با مادر", date = d(0), time = t(17)),
    Case("ساعت ۱۱ تماس", "تماس", date = d(0), time = t(11)),
    Case("ساعت ۵ تماس", "تماس", date = d(1), time = t(5), now = LocalDateTime.of(2026, 10, 4, 18, 0)),
    Case("ساعت ۵ تماس", "تماس", date = d(0), time = t(5), now = LocalDateTime.of(2026, 10, 4, 3, 0)),
    Case("ساعت ۱۲ ناهار", "ناهار", date = d(0), time = t(12)),
    Case("۵ عصر تماس", "تماس", date = d(0), time = t(17)),
    Case("۵ بعدازظهر تماس", "تماس", date = d(0), time = t(17)),
    Case("۵ بعد از ظهر تماس", "تماس", date = d(0), time = t(17)),
    Case("۲ بعدازظهر جلسه", "جلسه", date = d(0), time = t(14)),
    Case("ساعت ۸ صبح دارو", "دارو", date = d(1), time = t(8)),
    Case("۸ صبح دارو", "دارو", date = d(1), time = t(8)),
    Case("صبح ساعت ۸ دارو", "دارو", date = d(1), time = t(8)),
    Case("۱۰ شب مسواک", "مسواک", date = d(0), time = t(22)),
    Case("شب ساعت ۱۰ مسواک", "مسواک", date = d(0), time = t(22)),
    Case("۱۲ ظهر ناهار", "ناهار", date = d(0), time = t(12)),
    Case("۱ ظهر ناهار", "ناهار", date = d(0), time = t(13)),
    Case("۱۷:۳۰ باشگاه", "باشگاه", date = d(0), time = t(17, 30)),
    Case("باشگاه ۱۷:۳۰", "باشگاه", date = d(0), time = t(17, 30)),
    Case("ساعت ۷:۴۵ باشگاه", "باشگاه", date = d(0), time = t(19, 45)),
    Case("ساعت ۰۸:۰۰ باشگاه", "باشگاه", date = d(1), time = t(8)),
    Case("ساعت ۵ و نیم عصر کلاس", "کلاس", date = d(0), time = t(17, 30)),
    Case("ساعت پنج عصر کلاس", "کلاس", date = d(0), time = t(17)),
    Case("ناهار ظهر", "ناهار", date = d(0), time = t(12)),
    Case("فیلم دیدن شب", "فیلم دیدن", date = d(0), time = t(20)),
    Case("فردا ظهر ناهار با مریم", "ناهار با مریم", date = d(1), time = t(12)),
    Case("فردا صبح دکتر", "دکتر", date = d(1), time = t(9)),
    Case("فردا عصر پیاده‌روی", "پیاده‌روی", date = d(1), time = t(17)),
    Case("امروز عصر پیاده‌روی", "پیاده‌روی", date = d(0), time = t(17)),
    Case("فردا ساعت ۵ جلسه", "جلسه", date = d(1), time = t(17)),
    Case("فردا ساعت ۹ جلسه", "جلسه", date = d(1), time = t(9)),
    Case("امشب ساعت ۹ فیلم", "فیلم", date = d(0), time = t(21)),
    Case("امشب فیلم", "فیلم", date = d(0)),
    Case("نیمه شب پشتیبان‌گیری", "پشتیبان‌گیری", date = d(1), time = t(0)),
    Case("۲ ساعت دیگه قرص", "قرص", date = d(0), time = t(12)),
    Case("نیم ساعت دیگه زنگ بزنم", "زنگ بزنم", date = d(0), time = t(10, 30)),
    Case("۲ ساعت دیگه قرص", "قرص", date = d(1), time = t(1, 30), now = LocalDateTime.of(2026, 10, 4, 23, 30)),
    // Recurrence
    Case("هر روز ورزش", "ورزش", date = d(0), recurrence = fa(RecurrenceFrequency.DAILY)),
    Case("ورزش هر روز", "ورزش", date = d(0), recurrence = fa(RecurrenceFrequency.DAILY)),
    Case("هر هفته جلسه", "جلسه", date = d(0), recurrence = fa(RecurrenceFrequency.WEEKLY)),
    Case("هر ماه قبض", "قبض", date = d(0), recurrence = fa(RecurrenceFrequency.MONTHLY)),
    Case("هر سال تمدید بیمه", "تمدید بیمه", date = d(0), recurrence = fa(RecurrenceFrequency.YEARLY)),
    Case("هر دوشنبه کلاس", "کلاس", date = d(1), recurrence = fa(RecurrenceFrequency.WEEKLY, days = setOf(MONDAY))),
    Case("هر دوشنبه و پنجشنبه کلاس", "کلاس", date = d(1), recurrence = fa(RecurrenceFrequency.WEEKLY, days = setOf(MONDAY, THURSDAY))),
    Case("هر ۳ روز آب دادن به گلدان", "آب دادن به گلدان", date = d(0), recurrence = fa(RecurrenceFrequency.DAILY, interval = 3)),
    Case("هر ۳ روز یکبار آب دادن", "آب دادن", date = d(0), recurrence = fa(RecurrenceFrequency.DAILY, interval = 3)),
    Case("هر ۲ هفته جلسه", "جلسه", date = d(0), recurrence = fa(RecurrenceFrequency.WEEKLY, interval = 2)),
    Case("یک روز در میان دویدن", "دویدن", date = d(0), recurrence = fa(RecurrenceFrequency.DAILY, interval = 2)),
    Case("روزهای کاری گزارش", "گزارش", date = d(0), recurrence = fa(RecurrenceFrequency.WEEKLY, days = setOf(SATURDAY, SUNDAY, MONDAY, TUESDAY, WEDNESDAY))),
    Case("هر روز تا آخر ماه دارو", "دارو", date = d(0), recurrence = fa(RecurrenceFrequency.DAILY, until = jalali(1405, 7, 30))),
    Case("هر جمعه ساعت ۸ صبح کوه", "کوه", date = d(5), time = t(8), recurrence = fa(RecurrenceFrequency.WEEKLY, days = setOf(FRIDAY))),
    Case("هر روز ساعت ۷ دارو", "دارو", date = d(0), time = t(19), recurrence = fa(RecurrenceFrequency.DAILY)),
    Case("ورزش روزانه ساعت ۷", "ورزش", date = d(0), time = t(19), recurrence = fa(RecurrenceFrequency.DAILY)),
    // Priority
    Case("فوری تمدید کارت", "تمدید کارت", priority = Priority.HIGH),
    Case("تمدید کارت فوری", "تمدید کارت", priority = Priority.HIGH),
    Case("تمدید کارت مهم", "تمدید کارت", priority = Priority.HIGH),
    Case("تمدید کارت !!", "تمدید کارت", priority = Priority.HIGH),
    Case("تمدید کارت !", "تمدید کارت", priority = Priority.MEDIUM),
    Case("تمدید کارت اولویت پایین", "تمدید کارت", priority = Priority.LOW),
    Case("تمدید کارت اولویت متوسط", "تمدید کارت", priority = Priority.MEDIUM),
    // Tags and projects
    Case("نوشتن گزارش #کار", "نوشتن گزارش", tags = listOf("کار")),
    Case("نوشتن گزارش #کار #فوری_مالی", "نوشتن گزارش", tags = listOf("کار", "فوری_مالی")),
    Case("#کار نوشتن گزارش", "نوشتن گزارش", tags = listOf("کار")),
    Case("خرید کاشی @خانه", "خرید کاشی", project = 2),
    Case("تست نسخهٔ بتا @راه‌اندازی Plan-B", "تست نسخهٔ بتا", project = 1),
    Case("تست نسخه @راه‌اندازی", "تست نسخه", project = 1),
    Case("خرید کاشی @باغ", "خرید کاشی @باغ"),
    // Deadlines
    Case("گزارش مالی تا جمعه", "گزارش مالی", deadline = d(5)),
    Case("گزارش مالی مهلت ۲۰ مهر", "گزارش مالی", deadline = jalali(1405, 7, 20)),
    Case("گزارش مالی مهلت: ۲۰ مهر", "گزارش مالی", deadline = jalali(1405, 7, 20)),
    Case("گزارش مالی ددلاین پنجشنبه", "گزارش مالی", deadline = d(4)),
    Case("فردا شروع گزارش تا آخر هفته", "شروع گزارش", date = d(1), deadline = d(5)),
    Case("گزارش تا ۳ روز دیگه", "گزارش", deadline = d(3)),
    // Duration
    Case("مطالعه ۴۵ دقیقه", "مطالعه", duration = 45),
    Case("مطالعه ۲ ساعت", "مطالعه", duration = 120),
    Case("مطالعه به مدت ۹۰ دقیقه", "مطالعه", duration = 90),
    Case("مطالعه یک ساعت و نیم", "مطالعه", duration = 90),
    Case("مطالعه نیم ساعت", "مطالعه", duration = 30),
    Case("مطالعه یه ربع", "مطالعه", duration = 15),
    Case("مطالعه ۱ ساعت و ۱۵ دقیقه", "مطالعه", duration = 75),
    // Reminders
    Case("یادم بنداز ۱۰ دقیقه قبل جلسه", "جلسه", reminder = 10),
    Case("جلسه یادم بنداز ۱ ساعت قبل", "جلسه", reminder = 60),
    Case("جلسه ۱۵ دقیقه قبل یادم بنداز", "جلسه", reminder = 15),
    Case("یادم بنداز فردا نان بخرم", "نان بخرم", date = d(1), reminder = 0),
    Case("یادآوری نیم ساعت قبل دکتر", "دکتر", reminder = 30),
    // Everything together
    Case(
        "فردا ساعت ۵ عصر جلسه با Dr. Rahimi #کار @راه‌اندازی Plan-B فوری ۴۵ دقیقه یادم بنداز ۱۰ دقیقه قبل",
        "جلسه با Dr. Rahimi", date = d(1), time = t(17), tags = listOf("کار"), project = 1, priority = Priority.HIGH, duration = 45, reminder = 10,
    ),
    Case("هر دوشنبه ساعت ۹ صبح جلسه تیم #کار ۳۰ دقیقه", "جلسه تیم", date = d(1), time = t(9), tags = listOf("کار"), duration = 30,
        recurrence = fa(RecurrenceFrequency.WEEKLY, days = setOf(MONDAY))),
    // Must not match
    Case("۳ کتاب بخرم", "۳ کتاب بخرم"),
    Case("خرید ۲ کیلو سیب", "خرید ۲ کیلو سیب"),
    Case("فصل ۳ پایان‌نامه", "فصل ۳ پایان‌نامه"),
    Case("ساعت مچی بخرم", "ساعت مچی بخرم"),
    Case("سه تا نان بخرم", "سه تا نان بخرم"),
    Case("مرور گزارش هفتگی", "مرور گزارش هفتگی"),
    Case("مهمانی شب یلدا", "مهمانی شب یلدا"),
    Case("پنج نفر مهمان", "پنج نفر مهمان"),
    Case("هر چی شد خبر بده", "هر چی شد خبر بده"),
    Case("کتاب مهر و ماه", "کتاب مهر و ماه"),
    Case("۳ روز مرخصی", "۳ روز مرخصی"),
    Case("اتاق ۱۲ طبقه ۳", "اتاق ۱۲ طبقه ۳"),
)

private val enCases = listOf(
    Case("Buy milk today", "Buy milk", date = d(0), english = true),
    Case("Buy milk tomorrow", "Buy milk", date = d(1), english = true),
    Case("tmrw buy milk", "buy milk", date = d(1), english = true),
    Case("Dentist day after tomorrow", "Dentist", date = d(2), english = true),
    Case("Call mom on friday", "Call mom", date = d(5), english = true),
    Case("Call mom friday", "Call mom", date = d(5), english = true),
    Case("Call mom on fri", "Call mom", date = d(5), english = true),
    Case("Call mom next friday", "Call mom", date = d(5), english = true),
    Case("Call mom next monday", "Call mom", date = d(1), english = true),
    Case("Call mom sunday", "Call mom", date = d(7), english = true),
    Case("Call mom this sunday", "Call mom", date = d(0), english = true),
    Case("Renew license in 3 days", "Renew license", date = d(3), english = true),
    Case("Renew license 3 days from now", "Renew license", date = d(3), english = true),
    Case("Trip in 2 weeks", "Trip", date = d(14), english = true),
    Case("Trip in a week", "Trip", date = d(7), english = true),
    Case("Checkup in 1 month", "Checkup", date = LocalDate.of(2026, 11, 4), english = true),
    Case("Report next week", "Report", date = d(1), english = true),
    Case("Rent next month", "Rent", date = LocalDate.of(2026, 11, 1), english = true),
    Case("Rent end of month", "Rent", date = LocalDate.of(2026, 10, 31), english = true),
    Case("Groceries this weekend", "Groceries", date = d(6), english = true),
    Case("Party oct 15", "Party", date = LocalDate.of(2026, 10, 15), english = true),
    Case("Party 15 oct", "Party", date = LocalDate.of(2026, 10, 15), english = true),
    Case("Party october 15th", "Party", date = LocalDate.of(2026, 10, 15), english = true),
    Case("Party on the 15th of october", "Party", date = LocalDate.of(2026, 10, 15), english = true),
    Case("Party oct 1", "Party", date = LocalDate.of(2027, 10, 1), english = true),
    Case("Christmas dinner 25 december 2026", "Christmas dinner", date = LocalDate.of(2026, 12, 25), english = true),
    Case("Invoice 15/10", "Invoice", date = LocalDate.of(2026, 10, 15), english = true),
    Case("Invoice 10/15", "Invoice", date = LocalDate.of(2026, 10, 15), english = true),
    Case("Invoice 2026-12-01", "Invoice", date = LocalDate.of(2026, 12, 1), english = true),
    Case("Call mom 5pm", "Call mom", date = d(0), time = t(17), english = true),
    Case("Call mom at 5", "Call mom", date = d(0), time = t(17), english = true),
    Case("Call mom at 5:30pm", "Call mom", date = d(0), time = t(17, 30), english = true),
    Case("Call mom at 17:30", "Call mom", date = d(0), time = t(17, 30), english = true),
    Case("Call mom 9am", "Call mom", date = d(1), time = t(9), english = true),
    Case("Call mom 9 am", "Call mom", date = d(1), time = t(9), english = true),
    Case("Call mom at noon", "Call mom", date = d(0), time = t(12), english = true),
    Case("Call mom tomorrow 5pm", "Call mom", date = d(1), time = t(17), english = true),
    Case("Call mom tomorrow at 5", "Call mom", date = d(1), time = t(17), english = true),
    Case("Call mom tomorrow morning", "Call mom", date = d(1), time = t(9), english = true),
    Case("Call mom this evening", "Call mom", date = d(0), time = t(18), english = true),
    Case("Call mom in the afternoon", "Call mom", date = d(0), time = t(15), english = true),
    Case("Call mom tonight at 9", "Call mom", date = d(0), time = t(21), english = true),
    Case("Take pills in 2 hours", "Take pills", date = d(0), time = t(12), english = true),
    Case("Take pills in 45 min", "Take pills", date = d(0), time = t(10, 45), english = true),
    Case("Standup every day", "Standup", date = d(0), recurrence = en(RecurrenceFrequency.DAILY), english = true),
    Case("Standup daily", "Standup", date = d(0), recurrence = en(RecurrenceFrequency.DAILY), english = true),
    Case("Water plants every monday", "Water plants", date = d(1), recurrence = en(RecurrenceFrequency.WEEKLY, days = setOf(MONDAY)), english = true),
    Case("Water plants every mon and thu", "Water plants", date = d(1), recurrence = en(RecurrenceFrequency.WEEKLY, days = setOf(MONDAY, THURSDAY)), english = true),
    Case("Water plants every 3 days", "Water plants", date = d(0), recurrence = en(RecurrenceFrequency.DAILY, interval = 3), english = true),
    Case("Water plants every other day", "Water plants", date = d(0), recurrence = en(RecurrenceFrequency.DAILY, interval = 2), english = true),
    Case("Report every weekday", "Report", date = d(1), recurrence = en(RecurrenceFrequency.WEEKLY, days = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)), english = true),
    Case("Rent every month", "Rent", date = d(0), recurrence = en(RecurrenceFrequency.MONTHLY), english = true),
    Case("Water plants weekly", "Water plants", date = d(0), recurrence = en(RecurrenceFrequency.WEEKLY), english = true),
    Case("Pills every day until friday", "Pills", date = d(0), recurrence = en(RecurrenceFrequency.DAILY, until = d(5)), english = true),
    Case("Gym every monday at 7am", "Gym", date = d(1), time = t(7), recurrence = en(RecurrenceFrequency.WEEKLY, days = setOf(MONDAY)), english = true),
    Case("Pay taxes !!", "Pay taxes", priority = Priority.HIGH, english = true),
    Case("Pay taxes !!!", "Pay taxes", priority = Priority.HIGH, english = true),
    Case("Pay taxes urgent", "Pay taxes", priority = Priority.HIGH, english = true),
    Case("Pay taxes p2", "Pay taxes", priority = Priority.MEDIUM, english = true),
    Case("Pay taxes low priority", "Pay taxes", priority = Priority.LOW, english = true),
    Case("Write report #work", "Write report", tags = listOf("work"), english = true),
    Case("Buy paint @Home redesign", "Buy paint", project = 3, english = true),
    Case("Buy paint @home", "Buy paint", project = 3, english = true),
    Case("Chapter 3 @thesis", "Chapter 3", project = 4, english = true),
    Case("Taxes by friday", "Taxes", deadline = d(5), english = true),
    Case("Taxes deadline oct 20", "Taxes", deadline = LocalDate.of(2026, 10, 20), english = true),
    Case("Read for 45 min", "Read", duration = 45, english = true),
    Case("Read 2 hours", "Read", duration = 120, english = true),
    Case("Read 1.5h", "Read", duration = 90, english = true),
    Case("Read for an hour", "Read", duration = 60, english = true),
    Case("Read half an hour", "Read", duration = 30, english = true),
    Case("Meeting remind me 10 min before", "Meeting", reminder = 10, english = true),
    Case("Meeting tomorrow 3pm remind me 1 hour before", "Meeting", date = d(1), time = t(15), reminder = 60, english = true),
    Case(
        "Review PR tomorrow at 10am #work @thesis !! for 30 min",
        "Review PR", date = d(1), time = t(10), tags = listOf("work"), project = 4, priority = Priority.HIGH, duration = 30, english = true,
    ),
    // Must not match
    Case("Read 3 books", "Read 3 books", english = true),
    Case("Meet at the park", "Meet at the park", english = true),
    Case("Buy sun cream", "Buy sun cream", english = true),
    Case("Morning workout", "Morning workout", english = true),
    Case("Review weekly report", "Review weekly report", english = true),
    Case("Order 5 chairs", "Order 5 chairs", english = true),
    Case("Call may", "Call may", english = true),
    Case("Plan the week", "Plan the week", english = true),
    Case("Buy 2 tickets for the show", "Buy 2 tickets for the show", english = true),
)

@RunWith(ParameterizedRobolectricTestRunner::class)
class QuickAddParserCasesTest(private val case: Case) {
    @Test
    fun parses() {
        val context = if (case.english) {
            QuickAddContext(case.now, CalendarSystem.GREGORIAN, DayOfWeek.MONDAY, PROJECTS)
        } else {
            QuickAddContext(case.now, CalendarSystem.JALALI, DayOfWeek.SATURDAY, PROJECTS)
        }
        val r = QuickAddParser.parse(case.input, context)
        fun check(field: String, actual: Any?, expected: Any?) = assertWithMessage("$field of «${case.input}»").that(actual).isEqualTo(expected)
        check("title", r.title, case.title)
        check("date", r.date, case.date)
        check("time", r.time, case.time)
        check("recurrence", r.recurrence, case.recurrence)
        check("priority", r.priority, case.priority)
        check("tags", r.tags, case.tags)
        check("project", r.project?.id, case.project)
        check("deadline", r.deadline, case.deadline)
        check("duration", r.durationMinutes, case.duration)
        check("reminder", r.reminderMinutesBefore, case.reminder)
    }

    companion object {
        @JvmStatic
        // ASCII names: test reports become file names, and not every machine stores Persian ones.
        @ParameterizedRobolectricTestRunner.Parameters(name = "case {index}")
        fun cases(): List<Array<Any>> = (faCases + enCases).map { arrayOf(it) }
    }
}
