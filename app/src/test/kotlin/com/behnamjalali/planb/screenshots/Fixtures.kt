package com.behnamjalali.planb.screenshots

import com.behnamjalali.planb.core.data.repository.HabitWithHistory
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.EventOccurrence
import com.behnamjalali.planb.core.model.Goal
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitSchedule
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.ProjectSummary
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Screenshot fixtures (test-only). Content is localized so Persian screenshots
 * show realistic Persian/mixed text and English screenshots show English.
 */
class Fixtures(val language: AppLanguage) {
    val today: LocalDate = LocalDate.of(2026, 10, 4)
    private val fa = language == AppLanguage.PERSIAN
    private fun t(faText: String, enText: String) = if (fa) faText else enText
    private val now: Instant = Instant.parse("2026-10-04T06:00:00Z")

    val projects = listOf(
        Project(id = 1, title = t("راه‌اندازی Plan-B", "Plan-B launch"), color = AccentColor.LAVENDER, icon = PlannerIcon.ROCKET, dueDate = today.plusDays(20)),
        Project(id = 2, title = t("بازطراحی خانه", "Home redesign"), color = AccentColor.PEACH, icon = PlannerIcon.HOME, dueDate = today.plusDays(45)),
        Project(id = 3, title = t("دورهٔ یادگیری ماشین", "Machine learning course"), color = AccentColor.MINT, icon = PlannerIcon.SCHOOL),
    )

    val projectSummaries = listOf(
        ProjectSummary(projects[0], totalTasks = 12, completedTasks = 8, totalMilestones = 3, completedMilestones = 1),
        ProjectSummary(projects[1], totalTasks = 9, completedTasks = 3),
        ProjectSummary(projects[2], totalTasks = 20, completedTasks = 5),
    )

    private val work = Tag(1, t("کار", "work"), AccentColor.POWDER_BLUE)
    private val home = Tag(2, t("خانه", "home"), AccentColor.ROSE)

    val tasks = listOf(
        Task(id = 1, title = t("آماده‌سازی ارائهٔ فصلی", "Prepare quarterly presentation"), priority = Priority.HIGH, dueDate = today, dueTime = LocalTime.of(10, 30), projectId = 1, tags = listOf(work), subtaskCount = 4, completedSubtaskCount = 2, reminderOffsetMinutes = 15),
        Task(id = 2, title = t("تماس با Dr. Rahimi دربارهٔ API", "Call Dr. Rahimi about the API"), priority = Priority.MEDIUM, dueDate = today, dueTime = LocalTime.of(14, 0), projectId = 1),
        Task(id = 3, title = t("خرید میوه و نان", "Buy fruit and bread"), dueDate = today, tags = listOf(home)),
        Task(id = 4, title = t("مرور گزارش هفتگی", "Review weekly report"), dueDate = today.minusDays(1), priority = Priority.LOW),
        Task(id = 5, title = t("ورزش صبحگاهی", "Morning workout"), dueDate = today, status = TaskStatus.DONE, recurrence = RecurrenceRule(RecurrenceFrequency.DAILY)),
        Task(id = 6, title = t("پرداخت قبض اینترنت", "Pay the internet bill"), dueDate = today.plusDays(2), recurrence = RecurrenceRule(RecurrenceFrequency.MONTHLY)),
        Task(id = 7, title = t("نوشتن فصل ۳ پایان‌نامه", "Write thesis chapter 3"), dueDate = today.plusDays(4), priority = Priority.HIGH, projectId = 3),
    )

    val todayTasks = tasks.filter { it.dueDate != null && it.dueDate!! <= today && !it.isCompleted }
    val upcoming = tasks.filter { it.dueDate != null && it.dueDate!! > today }

    val events = listOf(
        CalendarEvent(id = 1, title = t("جلسهٔ تیم محصول", "Product team sync"), date = today, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), allDay = false, color = AccentColor.POWDER_BLUE),
        CalendarEvent(id = 2, title = t("ناهار با سارا", "Lunch with Sara"), date = today, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(14, 0), allDay = false, color = AccentColor.PEACH),
        CalendarEvent(id = 3, title = t("تولد مادر 🎂", "Mom's birthday 🎂"), date = today.plusDays(3), allDay = true, color = AccentColor.ROSE, recurrence = RecurrenceRule(RecurrenceFrequency.YEARLY)),
        CalendarEvent(id = 4, title = t("کلاس یوگا", "Yoga class"), date = today.plusDays(1), startTime = LocalTime.of(18, 0), endTime = LocalTime.of(19, 0), allDay = false, color = AccentColor.MINT, recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY)),
        CalendarEvent(id = 5, title = t("ددلاین گزارش مالی", "Finance report deadline"), date = today.plusDays(9), allDay = true, color = AccentColor.SAND),
    )

    fun occurrences(from: LocalDate, to: LocalDate): List<EventOccurrence> = events
        .filter { it.date in from..to }
        .map { EventOccurrence(it, it.date) }

    val habits: List<HabitWithHistory> = listOf(
        HabitWithHistory(
            Habit(id = 1, title = t("نوشیدن آب", "Drink water"), icon = PlannerIcon.WATER, color = AccentColor.POWDER_BLUE, target = 8, unit = t("لیوان", "glasses"), startDate = today.minusDays(60)),
            (0L..20L).associate { today.minusDays(it) to if (it == 0L) 5 else 8 },
        ),
        HabitWithHistory(
            Habit(id = 2, title = t("مطالعهٔ کتاب", "Read a book"), icon = PlannerIcon.BOOK, color = AccentColor.LAVENDER, target = 1, startDate = today.minusDays(60)),
            (0L..11L).associate { today.minusDays(it) to 1 },
        ),
        HabitWithHistory(
            Habit(id = 3, title = t("مدیتیشن", "Meditate"), icon = PlannerIcon.MEDITATION, color = AccentColor.MINT, schedule = HabitSchedule.Daily, startDate = today.minusDays(30)),
            (1L..3L).associate { today.minusDays(it) to 1 },
        ),
    )

    val notes = listOf(
        Note(
            id = 1, notebookId = 1, title = t("ایده‌های محصول", "Product ideas"), pinned = true,
            document = NoteDocument(blocks = listOf(NoteBlock("1", BlockType.TEXT, t("حالت تمرکز با Pomodoro و گزارش هفتگی", "Focus mode with Pomodoro and a weekly report")))),
            updatedAt = now,
        ),
        Note(
            id = 2, notebookId = 1, title = t("خلاصهٔ کتاب Atomic Habits", "Atomic Habits summary"), favorite = true,
            document = NoteDocument(blocks = listOf(NoteBlock("1", BlockType.TEXT, t("عادت‌های کوچک، نتایج بزرگ.", "Small habits, remarkable results.")))),
            updatedAt = now,
        ),
    )

    val goals = listOf(
        Goal(id = 1, title = t("خواندن ۲۴ کتاب", "Read 24 books"), target = 24.0, currentValue = 15.0, unit = t("کتاب", "books"), deadline = LocalDate.of(2027, 3, 20)),
        Goal(id = 2, title = t("پس‌انداز برای سفر", "Save for the trip"), target = 100.0, currentValue = 42.0, unit = "%"),
    )
}
