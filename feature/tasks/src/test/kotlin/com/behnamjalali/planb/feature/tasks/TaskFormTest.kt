package com.behnamjalali.planb.feature.tasks

import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.json.Json
import org.junit.Test

class TaskFormTest {
    @Test
    fun form_roundTripsThroughJson() {
        val task = Task(
            id = 7, title = "Draft task", priority = Priority.HIGH, dueDate = LocalDate.of(2026, 10, 4), dueTime = LocalTime.of(9, 30),
            reminderOffsetMinutes = 10, recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY), tags = listOf(Tag(1, "work")),
            estimatedMinutes = 30,
        )
        val form = TaskForm.from(task)
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(TaskForm.serializer(), json.encodeToString(TaskForm.serializer(), form))
        assertThat(decoded).isEqualTo(form)
        assertThat(decoded.toTask().title).isEqualTo("Draft task")
        assertThat(decoded.toTask().recurrence).isEqualTo(task.recurrence)
    }

    @Test
    fun persianDigits_inNumbers_areAccepted() {
        val form = TaskForm(title = "x", estimate = "۴۵")
        assertThat(form.estimateValid).isTrue()
        assertThat(form.toTask().estimatedMinutes).isEqualTo(45)
    }
}
