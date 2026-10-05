package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.SmartFilterEvaluator
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.SmartDateRange
import com.behnamjalali.planb.core.model.SmartFilter
import com.behnamjalali.planb.core.model.SmartFilterCodec
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskSort
import com.behnamjalali.planb.core.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

/** Smart list (Plan-B Pro #10) filter evaluation. */
class SmartFilterEvaluatorTest {
    private val today = LocalDate.of(2026, 10, 4)
    private val work = Tag(1, "work")
    private val home = Tag(2, "home")

    private val tasks = listOf(
        Task(id = 1, title = "Quarterly report", priority = Priority.HIGH, projectId = 10, tags = listOf(work), dueDate = today, sortOrder = 5),
        Task(id = 2, title = "Buy bread", projectId = null, tags = listOf(home), dueDate = today.plusDays(3), sortOrder = 4),
        Task(id = 3, title = "Late invoice", priority = Priority.MEDIUM, projectId = 11, dueDate = today.minusDays(2), sortOrder = 3),
        Task(id = 4, title = "Someday idea", priority = Priority.LOW, sortOrder = 2),
        Task(id = 5, title = "Contract", projectId = 10, dueDate = today.plusDays(20), deadline = today.plusDays(1), sortOrder = 1),
        Task(id = 6, title = "Finished report", status = TaskStatus.DONE, projectId = 10, dueDate = today, sortOrder = 0),
        Task(id = 7, title = "گزارش مالی", status = TaskStatus.IN_PROGRESS, dueDate = today.plusDays(7), sortOrder = 6, createdAt = Instant.ofEpochSecond(9)),
    )

    private fun ids(filter: SmartFilter) = SmartFilterEvaluator.apply(tasks, filter, today).map { it.id }

    @Test
    fun emptyFilter_isOpenTasks() {
        assertThat(ids(SmartFilter(sort = TaskSort.MANUAL))).containsExactly(5L, 4L, 3L, 2L, 1L, 7L).inOrder()
    }

    @Test
    fun projectsTagsPriorityStatus() {
        assertThat(ids(SmartFilter(projectIds = setOf(10)))).containsExactly(1L, 5L)
        assertThat(ids(SmartFilter(projectIds = setOf(10), noProject = true))).containsExactly(1L, 2L, 4L, 5L, 7L)
        assertThat(ids(SmartFilter(noProject = true))).containsExactly(2L, 4L, 7L)
        assertThat(ids(SmartFilter(tagIds = setOf(1, 2)))).containsExactly(1L, 2L)
        assertThat(ids(SmartFilter(priorities = setOf(Priority.HIGH, Priority.MEDIUM)))).containsExactly(1L, 3L)
        assertThat(ids(SmartFilter(statuses = setOf(TaskStatus.DONE)))).containsExactly(6L)
        assertThat(ids(SmartFilter(statuses = setOf(TaskStatus.IN_PROGRESS)))).containsExactly(7L)
        assertThat(ids(SmartFilter(statuses = TaskStatus.entries.toSet()))).hasSize(7)
    }

    @Test
    fun dateRanges() {
        assertThat(ids(SmartFilter(dateRange = SmartDateRange.TODAY))).containsExactly(1L)
        // Overdue follows the deadline when there is one.
        assertThat(ids(SmartFilter(dateRange = SmartDateRange.OVERDUE))).containsExactly(3L)
        assertThat(ids(SmartFilter(dateRange = SmartDateRange.NEXT_7_DAYS))).containsExactly(1L, 2L, 7L)
        assertThat(ids(SmartFilter(dateRange = SmartDateRange.NO_DATE))).containsExactly(4L)
        assertThat(ids(SmartFilter(dateRange = SmartDateRange.CUSTOM, from = today.plusDays(1), to = today.plusDays(20)))).containsExactly(2L, 5L, 7L)
        assertThat(ids(SmartFilter(dateRange = SmartDateRange.CUSTOM, from = today.plusDays(8)))).containsExactly(5L)
        assertThat(ids(SmartFilter(dateRange = SmartDateRange.CUSTOM, to = today.minusDays(1)))).containsExactly(3L)
    }

    @Test
    fun deadlineAndText() {
        assertThat(ids(SmartFilter(hasDeadline = true))).containsExactly(5L)
        assertThat(ids(SmartFilter(hasDeadline = false))).doesNotContain(5L)
        assertThat(ids(SmartFilter(text = "REPORT"))).containsExactly(1L)
        // Arabic Kaf/Yeh typed on an Arabic keyboard still find Persian text.
        assertThat(ids(SmartFilter(text = "گزارش مالي"))).containsExactly(7L)
    }

    @Test
    fun sorts() {
        assertThat(ids(SmartFilter(sort = TaskSort.DUE_DATE))).containsExactly(3L, 1L, 2L, 7L, 5L, 4L).inOrder()
        assertThat(ids(SmartFilter(sort = TaskSort.PRIORITY)).take(3)).containsExactly(1L, 3L, 4L).inOrder()
        assertThat(ids(SmartFilter(sort = TaskSort.DEADLINE)).first()).isEqualTo(5L)
        assertThat(ids(SmartFilter(sort = TaskSort.TITLE)).take(2)).containsExactly(2L, 5L).inOrder()
        assertThat(ids(SmartFilter(sort = TaskSort.CREATED)).first()).isEqualTo(7L)
    }

    @Test
    fun combinedConditions_afterJsonRoundTrip() {
        val filter = SmartFilter(projectIds = setOf(10), priorities = setOf(Priority.HIGH), dateRange = SmartDateRange.TODAY, text = "quarterly")
        assertThat(ids(SmartFilterCodec.decode(SmartFilterCodec.encode(filter)))).containsExactly(1L)
        assertThat(ids(filter.copy(priorities = setOf(Priority.LOW)))).isEmpty()
    }

    @Test
    fun archivedAndTrashedNeverMatch() {
        val hidden = listOf(
            Task(id = 8, title = "Archived", archived = true),
            Task(id = 9, title = "Trashed", deletedAt = Instant.EPOCH),
        )
        assertThat(SmartFilterEvaluator.apply(hidden, SmartFilter(statuses = TaskStatus.entries.toSet()), today)).isEmpty()
    }
}
