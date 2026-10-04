package com.behnamjalali.planb.feature.goals

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.data.repository.GoalRepository
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Goal
import com.behnamjalali.planb.core.model.GoalMilestone
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class GoalNumbersTest {
    @Test
    fun groupingSeparators_areIgnored() {
        assertThat(GoalNumbers.parse("10,000")).isEqualTo(10_000.0)
        assertThat(GoalNumbers.parse("۱۰٬۰۰۰")).isEqualTo(10_000.0)
        assertThat(GoalNumbers.parse("١٠٬٠٠٠")).isEqualTo(10_000.0)
    }

    @Test
    fun decimalSeparators_areAccepted() {
        assertThat(GoalNumbers.parse("2.5")).isEqualTo(2.5)
        assertThat(GoalNumbers.parse("۲٫۵")).isEqualTo(2.5)
        assertThat(GoalNumbers.parse("1,234.5")).isEqualTo(1234.5)
    }

    @Test
    fun junkAndAbsurdValues_areRejected() {
        listOf("", " ", "abc", "Infinity", "NaN", "-5", "1e5", "1.2.3", "1e13", "9999999999999").forEach {
            assertThat(GoalNumbers.parse(it)).isNull()
        }
        assertThat(GoalNumbers.parse("1000000000000")).isEqualTo(1e12)
    }

    @Test
    fun format_isPlainAndRoundTrips() {
        assertThat(GoalNumbers.format(10_000.0)).isEqualTo("10000")
        assertThat(GoalNumbers.format(123_456_789.5)).isEqualTo("123456789.5")
        assertThat(GoalNumbers.parse(GoalNumbers.format(2.25))).isEqualTo(2.25)
    }

    @Test
    fun form_usesTheSharedParser() {
        val form = GoalForm(title = "Read", target = "10,000", current = "۱٬۵۰۰")
        assertThat(form.valid).isTrue()
        assertThat(form.toGoal().target).isEqualTo(10_000.0)
        assertThat(form.toGoal().currentValue).isEqualTo(1_500.0)
        assertThat(GoalForm(title = "x", target = "Infinity").valid).isFalse()
    }
}

@RunWith(RobolectricTestRunner::class)
class GoalDetailViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph

    @Before
    fun setUp() {
        graph = TestDataGraph()
    }

    /** In-memory goals whose writes take a little while, like a real database. */
    private class SlowGoals(goal: Goal) : GoalRepository {
        val goal = MutableStateFlow(goal)
        override fun observeGoals(archived: Boolean): Flow<List<Goal>> = goal.map { listOf(it) }
        override fun observeGoal(id: EntityId): Flow<Goal?> = goal
        override fun observeMilestones(goalId: EntityId): Flow<List<GoalMilestone>> = flowOf(emptyList())
        override suspend fun getGoal(id: EntityId): Goal {
            delay(10)
            return goal.value
        }
        override suspend fun save(goal: Goal): EntityId = goal.id
        override suspend fun updateProgress(id: EntityId, currentValue: Double) {
            delay(20)
            goal.value = goal.value.copy(currentValue = currentValue)
        }
        override suspend fun setArchived(id: EntityId, archived: Boolean) = Unit
        override suspend fun delete(id: EntityId) = Unit
        override suspend fun saveMilestone(milestone: GoalMilestone): EntityId = 0
        override suspend fun setMilestoneCompleted(milestone: GoalMilestone, completed: Boolean) = Unit
        override suspend fun deleteMilestone(id: EntityId) = Unit
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    @Test
    fun rapidPlusAndMinusTaps_allCount() = runBlocking<Unit> {
        val repo = SlowGoals(Goal(id = 1, title = "Run", target = 100.0, currentValue = 10.0))
        val vm = main.track(GoalDetailViewModel(SavedStateHandle(mapOf("goalId" to 1L)), repo, graph.projects, graph.time))
        repeat(5) { vm.adjustProgress(1.0) }
        vm.adjustProgress(-2.0)
        withTimeout(20_000) { repo.goal.first { it.currentValue == 13.0 } }

        // Never below zero.
        repeat(3) { vm.adjustProgress(-10.0) }
        withTimeout(20_000) { repo.goal.first { it.currentValue == 0.0 } }
    }
}
