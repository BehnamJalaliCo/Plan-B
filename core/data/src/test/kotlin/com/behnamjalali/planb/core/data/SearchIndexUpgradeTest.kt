package com.behnamjalali.planb.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.behnamjalali.planb.core.common.SearchNormalizer
import com.behnamjalali.planb.core.data.repository.FtsSearchRepository
import com.behnamjalali.planb.core.data.repository.OfflineTaskRepository
import com.behnamjalali.planb.core.database.PlanBDatabase
import com.behnamjalali.planb.core.database.entity.SearchIndexEntity
import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import com.behnamjalali.planb.core.model.SearchEntityType
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchIndexUpgradeTest {
    private lateinit var db: PlanBDatabase
    private val time = FakeTimeProvider()
    private val dir: File = Files.createTempDirectory("prefs").toFile()

    @Before
    fun setUp() {
        db = TestDatabase.create()
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun outdatedIndex_isRebuiltOnce_withHalfSpaceWordsJoined() = runTest {
        val preferences = UserPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "test.preferences_pb") },
        )
        val tasks = OfflineTaskRepository(db, db.taskDao(), db.tagDao(), db.searchDao(), time, RecordingReminderScheduler())
        val search = FtsSearchRepository(db.searchDao(), db.taskDao(), db.projectDao(), db.noteDao(), db.habitDao(), db.goalDao(), db.eventDao())
        val id = tasks.save(Task(title = "خرید کتاب‌ها"))
        // Simulate a row written by the previous normalizer (half-space as a word break).
        db.searchDao().upsert(
            SearchIndexEntity(SearchIndexer.rowId(SearchEntityType.TASK, id), SearchEntityType.TASK.code, id, "خرید کتاب ها"),
        )
        assertThat(search.search("کتابها")).isEmpty()

        val upgrade = SearchIndexUpgrade(db, SearchIndexMaintenance(db.backupDao(), db.searchDao()), preferences)
        assertThat(upgrade.rebuildIfOutdated()).isTrue()
        assertThat(search.search("کتابها").map { it.id }).containsExactly(id)
        assertThat(search.search("کتاب‌ها").map { it.id }).containsExactly(id)
        assertThat(preferences.searchIndexVersion()).isEqualTo(SearchNormalizer.VERSION)
        assertThat(upgrade.rebuildIfOutdated()).isFalse()
    }
}
