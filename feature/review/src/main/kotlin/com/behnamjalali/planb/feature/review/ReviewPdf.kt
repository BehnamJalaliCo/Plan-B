package com.behnamjalali.planb.feature.review

import android.content.res.Resources
import com.behnamjalali.planb.core.datetime.PlannerDateFormatter
import com.behnamjalali.planb.core.model.WeeklyReview
import com.behnamjalali.planb.core.ui.pdf.PdfBar
import com.behnamjalali.planb.core.ui.pdf.PdfBlock
import com.behnamjalali.planb.core.ui.pdf.PdfMetric
import com.behnamjalali.planb.core.ui.pdf.PdfReport

/** The weekly review as a printable PDF report (Plan-B Pro #31). */
internal object ReviewPdf {
    fun weekRange(r: WeeklyReview, f: PlannerDateFormatter): String = "${f.mediumDate(r.weekStart)} – ${f.mediumDate(r.weekEnd)}"

    fun build(res: Resources, r: WeeklyReview, f: PlannerDateFormatter, rtl: Boolean): PdfReport {
        val n = f.numbers
        val blocks = buildList {
            add(
                PdfBlock.Metrics(
                    listOf(
                        PdfMetric(res.getString(R.string.review_completed), n.format(r.completedTasks)),
                        PdfMetric(res.getString(R.string.review_focus), f.duration(r.focusMinutes)),
                        PdfMetric(res.getString(R.string.review_notes), n.format(r.notesCreated)),
                        PdfMetric(res.getString(R.string.review_habit_average), n.percent(r.habitCompletionRate)),
                    ),
                ),
            )
            add(PdfBlock.Heading(res.getString(R.string.review_completed)))
            val max = (r.completedPerDay.maxOrNull() ?: 0).coerceAtLeast(1)
            add(
                PdfBlock.Bars(
                    r.completedPerDay.mapIndexed { i, c ->
                        PdfBar(f.weekdayName(r.weekStart.plusDays(i.toLong()).dayOfWeek), c.toFloat() / max, n.format(c))
                    },
                ),
            )
            add(PdfBlock.Heading(res.getString(R.string.review_missed)))
            add(PdfBlock.Paragraph(if (r.missedTasks.isEmpty()) res.getString(R.string.review_missed_none) else r.missedTasks.joinToString("\n") { "• ${it.title}" }))
            if (r.habits.isNotEmpty()) {
                add(PdfBlock.Heading(res.getString(R.string.review_habits)))
                add(PdfBlock.Bars(r.habits.map { PdfBar(it.habit.title, it.rate, n.percent(it.rate)) }))
            }
            if (r.projects.isNotEmpty()) {
                add(PdfBlock.Heading(res.getString(R.string.review_projects)))
                add(PdfBlock.Bars(r.projects.map { PdfBar(it.project.title, it.progress, n.percent(it.progress)) }))
            }
            if (r.goals.isNotEmpty()) {
                add(PdfBlock.Heading(res.getString(R.string.review_goals)))
                add(PdfBlock.Bars(r.goals.map { PdfBar(it.title, it.progress, n.percent(it.progress)) }))
            }
            add(PdfBlock.Heading(res.getString(R.string.review_next)))
            add(PdfBlock.Paragraph(if (r.nextWeekPriorities.isEmpty()) res.getString(R.string.review_next_none) else r.nextWeekPriorities.joinToString("\n") { "• ${it.title}" }))
        }
        return PdfReport(
            title = res.getString(R.string.review_title),
            subtitle = weekRange(r, f),
            rtl = rtl,
            blocks = blocks,
            footer = res.getString(R.string.review_pdf_footer),
            pageNumber = n::format,
        )
    }
}
