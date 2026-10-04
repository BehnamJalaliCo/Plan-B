package com.behnamjalali.planb.feature.reports

import android.content.res.Resources
import com.behnamjalali.planb.core.datetime.PlannerDateFormatter
import com.behnamjalali.planb.core.model.DateSpan
import com.behnamjalali.planb.core.model.PeriodStatistics
import com.behnamjalali.planb.core.model.StatsPeriod
import com.behnamjalali.planb.core.ui.pdf.PdfBar
import com.behnamjalali.planb.core.ui.pdf.PdfBlock
import com.behnamjalali.planb.core.ui.pdf.PdfMetric
import com.behnamjalali.planb.core.ui.pdf.PdfReport
import java.time.DayOfWeek
import java.time.LocalTime

/** Texts shared by the screens and the PDF export, so both say exactly the same. */
internal object ReportText {
    fun periodLabel(period: StatsPeriod, span: DateSpan, f: PlannerDateFormatter): String = when (period) {
        StatsPeriod.WEEK -> "${f.dayMonth(span.start)} – ${f.dayMonth(span.end)}"
        StatsPeriod.MONTH -> f.monthYear(f.monthOf(span.start))
        StatsPeriod.YEAR -> f.numbers.format(f.engine.toCalendarDate(span.start).year)
    }

    /** Short label under a chart bar, or null to keep a crowded axis readable. */
    fun axisLabel(period: StatsPeriod, bucket: DateSpan, f: PlannerDateFormatter): String? = when (period) {
        StatsPeriod.WEEK -> f.weekdayNarrow(bucket.start.dayOfWeek)
        StatsPeriod.MONTH -> f.engine.toCalendarDate(bucket.start).day.takeIf { it == 1 || it % 5 == 0 }?.let(f.numbers::format)
        StatsPeriod.YEAR -> f.numbers.format(f.engine.toCalendarDate(bucket.start).month)
    }

    /** Full name of a bucket for accessibility and PDF rows. */
    fun bucketName(period: StatsPeriod, bucket: DateSpan, f: PlannerDateFormatter): String = when (period) {
        StatsPeriod.WEEK -> f.weekdayName(bucket.start.dayOfWeek)
        StatsPeriod.MONTH -> f.dayMonth(bucket.start)
        StatsPeriod.YEAR -> f.monthName(f.engine.toCalendarDate(bucket.start).month)
    }

    fun chartDescription(res: Resources, title: String, names: List<String>, values: List<Int>, f: PlannerDateFormatter): String {
        val separator = if (f.numbers.persianDigits) "، " else ", "
        val items = names.zip(values).joinToString(separator) { (n, v) -> "$n ${f.numbers.format(v)}" }
        return res.getString(R.string.reports_chart_description, title, items)
    }

    /** Weekdays in the user's week order. */
    fun weekOrder(f: PlannerDateFormatter): List<DayOfWeek> = f.weekdays()

    fun hourLabel(hour: Int, f: PlannerDateFormatter): String = f.time(LocalTime.of(hour, 0))

    fun completionRate(res: Resources, s: PeriodStatistics, f: PlannerDateFormatter): String =
        s.completionRate?.let(f.numbers::percent) ?: res.getString(R.string.reports_none_yet)

    fun busiestDay(res: Resources, s: PeriodStatistics, f: PlannerDateFormatter): String =
        s.busiestWeekday?.let(f::weekdayName) ?: res.getString(R.string.reports_none_yet)

    fun busiestHour(res: Resources, s: PeriodStatistics, f: PlannerDateFormatter): String =
        s.busiestHour?.let { hourLabel(it, f) } ?: res.getString(R.string.reports_none_yet)

    private fun bars(values: List<Int>, names: List<String>, f: PlannerDateFormatter): PdfBlock.Bars {
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        return PdfBlock.Bars(names.zip(values).map { (n, v) -> PdfBar(n, v.toFloat() / max, f.numbers.format(v)) })
    }

    private fun common(res: Resources, period: StatsPeriod, s: PeriodStatistics, f: PlannerDateFormatter): List<PdfBlock> = buildList {
        val names = s.buckets.map { bucketName(period, it, f) }
        // Days of a month are many: keep the PDF to the days that had something.
        fun rows(values: List<Int>) = if (period == StatsPeriod.MONTH) {
            val kept = values.indices.filter { values[it] > 0 }
            bars(kept.map(values::get), kept.map(names::get), f)
        } else {
            bars(values, names, f)
        }
        add(PdfBlock.Heading(res.getString(R.string.reports_completed_chart)))
        add(rows(s.completedPerBucket))
        add(PdfBlock.Heading(res.getString(R.string.reports_on_time_title)))
        add(
            PdfBlock.Paragraph(
                if (s.onTime + s.late == 0) res.getString(R.string.reports_no_due)
                else res.getString(R.string.reports_on_time_description, f.numbers.format(s.onTime), f.numbers.format(s.late)),
            ),
        )
        add(PdfBlock.Heading(res.getString(R.string.reports_busiest_day)))
        val week = weekOrder(f)
        add(bars(week.map { s.completedPerWeekday[it.value - 1] }, week.map(f::weekdayName), f))
        add(PdfBlock.Paragraph(res.getString(R.string.reports_busiest_hour) + ": " + busiestHour(res, s, f)))
        if (s.focusMinutes > 0) {
            add(PdfBlock.Heading(res.getString(R.string.reports_focus_chart)))
            add(rows(s.focusMinutesPerBucket))
        }
        if (s.habits.isNotEmpty()) {
            add(PdfBlock.Heading(res.getString(R.string.reports_habits)))
            add(PdfBlock.Bars(s.habits.map { PdfBar(it.habit.title, it.rate, f.numbers.percent(it.rate)) }))
        }
        if (s.projects.isNotEmpty()) {
            add(PdfBlock.Heading(res.getString(R.string.reports_projects)))
            add(PdfBlock.Bars(s.projects.map { PdfBar(it.project.title, it.progress, f.numbers.percent(it.progress)) }))
        }
        if (s.topTags.isNotEmpty()) {
            add(PdfBlock.Heading(res.getString(R.string.reports_top_tags)))
            val max = s.topTags.first().count.toFloat()
            add(PdfBlock.Bars(s.topTags.map { PdfBar(it.tag.name, it.count / max, f.numbers.format(it.count)) }))
        }
        if (s.topProjects.isNotEmpty()) {
            add(PdfBlock.Heading(res.getString(R.string.reports_top_projects)))
            val max = s.topProjects.first().count.toFloat()
            add(PdfBlock.Bars(s.topProjects.map { PdfBar(it.project.title, it.count / max, f.numbers.format(it.count)) }))
        }
    }

    private fun metrics(res: Resources, s: PeriodStatistics, f: PlannerDateFormatter) = PdfBlock.Metrics(
        listOf(
            PdfMetric(res.getString(R.string.reports_completed), f.numbers.format(s.completedTotal)),
            PdfMetric(res.getString(R.string.reports_completion_rate), completionRate(res, s, f)),
            PdfMetric(res.getString(R.string.reports_focus), f.duration(s.focusMinutes)),
            PdfMetric(res.getString(R.string.reports_notes), f.numbers.format(s.notesWritten)),
            PdfMetric(res.getString(R.string.reports_habit_success), s.habitSuccessRate?.let(f.numbers::percent) ?: res.getString(R.string.reports_none_yet)),
            PdfMetric(res.getString(R.string.reports_busiest_day), busiestDay(res, s, f)),
        ),
    )

    fun statisticsPdf(res: Resources, period: StatsPeriod, s: PeriodStatistics, f: PlannerDateFormatter, rtl: Boolean) = PdfReport(
        title = res.getString(R.string.reports_title),
        subtitle = periodLabel(period, s.span, f),
        rtl = rtl,
        blocks = listOf(metrics(res, s, f)) + common(res, period, s, f),
        footer = res.getString(R.string.reports_pdf_footer),
        pageNumber = f.numbers::format,
    )

    fun yearPdf(res: Resources, year: Int, s: PeriodStatistics, f: PlannerDateFormatter, rtl: Boolean): PdfReport {
        val story = buildList {
            add(PdfBlock.Paragraph(res.getString(R.string.year_hero_sub)))
            add(metrics(res, s, f))
            s.bestBucket?.let { best ->
                add(PdfBlock.Paragraph(res.getString(R.string.year_best_month) + ": " + res.getString(R.string.year_best_month_value, bucketName(StatsPeriod.YEAR, s.buckets[best], f), f.numbers.format(s.completedPerBucket[best]))))
            }
            if (s.longestStreak > 0) {
                add(PdfBlock.Paragraph(res.getString(R.string.year_streak) + ": " + res.getString(R.string.year_streak_value, f.numbers.format(s.longestStreak))))
            }
        }
        return PdfReport(
            title = res.getString(R.string.year_hero, f.numbers.format(year)),
            subtitle = "${f.mediumDate(s.span.start)} – ${f.mediumDate(s.span.end)}",
            rtl = rtl,
            blocks = story + common(res, StatsPeriod.YEAR, s, f) + PdfBlock.Paragraph(res.getString(R.string.year_closing)),
            footer = res.getString(R.string.reports_pdf_footer),
            pageNumber = f.numbers::format,
        )
    }
}
