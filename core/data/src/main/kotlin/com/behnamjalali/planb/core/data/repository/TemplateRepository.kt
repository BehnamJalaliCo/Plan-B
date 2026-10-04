package com.behnamjalali.planb.core.data.repository

import android.content.Context
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.R
import com.behnamjalali.planb.core.data.toModel
import com.behnamjalali.planb.core.database.dao.TemplateDao
import com.behnamjalali.planb.core.database.entity.PlannerTemplateEntity
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitSchedule
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.PlannerIcon
import com.behnamjalali.planb.core.model.PlannerTemplate
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.ProjectMilestone
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TemplatePayload
import com.behnamjalali.planb.core.model.TemplateType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What applying a template created. */
data class TemplateResult(val type: TemplateType, val id: EntityId, val createdCount: Int = 1)

interface TemplateRepository {
    /** Built-in templates (localized to the current app language) followed by custom ones. */
    fun observeTemplates(): Flow<List<PlannerTemplate>>
    fun builtInTemplates(): List<PlannerTemplate>
    suspend fun saveCustom(title: String, type: TemplateType, payload: TemplatePayload, id: EntityId = 0): EntityId
    suspend fun saveNoteAsTemplate(note: Note): EntityId
    suspend fun rename(id: EntityId, title: String)
    suspend fun delete(id: EntityId)

    /**
     * Instantiates [template]. [dateLabel] replaces `{{date}}` and is supplied by
     * the UI so it uses the user's calendar and digits. [notebookTitle] (localized
     * by the UI) names the notebook created for note templates if none exists.
     */
    suspend fun apply(template: PlannerTemplate, dateLabel: String, notebookTitle: String): TemplateResult
}

@Serializable
private data class BuiltInFile(
    val title: String,
    val description: String = "",
    val type: TemplateType,
    val payload: TemplatePayload,
)

@Singleton
internal class OfflineTemplateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TemplateDao,
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val notes: NoteRepository,
    private val habits: HabitRepository,
    private val time: TimeProvider,
) : TemplateRepository {
    private val json = Json { ignoreUnknownKeys = true }

    private val builtIns = listOf(
        "daily_planner" to R.raw.template_daily_planner,
        "weekly_planner" to R.raw.template_weekly_planner,
        "monthly_planner" to R.raw.template_monthly_planner,
        "meeting_note" to R.raw.template_meeting_note,
        "project_plan" to R.raw.template_project_plan,
        "study_plan" to R.raw.template_study_plan,
        "personal_journal" to R.raw.template_personal_journal,
        "habit_plan" to R.raw.template_habit_plan,
    )

    /** Read on every call so a language change is reflected immediately. */
    override fun builtInTemplates(): List<PlannerTemplate> = builtIns.map { (key, res) ->
        val file = context.resources.openRawResource(res).bufferedReader().use { json.decodeFromString<BuiltInFile>(it.readText()) }
        PlannerTemplate(builtInKey = key, title = file.title, description = file.description, type = file.type, payload = file.payload)
    }

    override fun observeTemplates(): Flow<List<PlannerTemplate>> =
        dao.observeAll().map { custom -> builtInTemplates() + custom.mapNotNull { it.toModel() } }

    override suspend fun saveCustom(title: String, type: TemplateType, payload: TemplatePayload, id: EntityId): EntityId {
        require(title.isNotBlank() || payload.blocks.isNotEmpty()) { "Template title must not be blank" }
        val now = time.now()
        val entity = PlannerTemplateEntity(
            id = id, title = title.trim(), type = type.name, payload = payload.encode(), builtIn = false,
            createdAt = now, updatedAt = now,
        )
        return if (id == 0L) dao.insert(entity) else id.also { dao.update(entity.copy(createdAt = dao.get(id)?.createdAt ?: now)) }
    }

    override suspend fun saveNoteAsTemplate(note: Note): EntityId = saveCustom(
        title = note.title,
        type = TemplateType.NOTE,
        payload = TemplatePayload(noteTitle = note.title, blocks = note.document.blocks.map { if (it.type.name == "CHECKLIST") it.copy(checked = false) else it }),
    )

    override suspend fun rename(id: EntityId, title: String) {
        require(title.isNotBlank()) { "Template title must not be blank" }
        val entity = dao.get(id) ?: return
        dao.update(entity.copy(title = title.trim(), updatedAt = time.now()))
    }

    override suspend fun delete(id: EntityId) = dao.delete(id)

    override suspend fun apply(template: PlannerTemplate, dateLabel: String, notebookTitle: String): TemplateResult {
        val p = template.payload
        fun String.fill() = replace(TemplatePayload.DATE_PLACEHOLDER, dateLabel)
        val today = time.today()
        return when (template.type) {
            TemplateType.NOTE -> {
                val notebookId = notes.ensureDefaultNotebook(notebookTitle)
                val blocks = p.blocks.map { NoteBlock(UUID.randomUUID().toString(), it.type, it.text.fill(), it.checked) }
                val id = notes.saveNote(
                    Note(notebookId = notebookId, title = (p.noteTitle ?: template.title).fill(), document = NoteDocument(blocks = blocks)),
                )
                TemplateResult(TemplateType.NOTE, id)
            }
            TemplateType.PROJECT -> {
                val projectId = projects.save(
                    Project(
                        title = (p.projectTitle ?: template.title).fill(),
                        description = p.description.fill(),
                        startDate = today,
                        dueDate = p.milestones.mapNotNull { it.dayOffset }.maxOrNull()?.let { today.plusDays(it.toLong()) },
                    ),
                )
                p.tasks.forEach { t ->
                    tasks.save(
                        Task(
                            title = t.title.fill(),
                            projectId = projectId,
                            priority = Priority.fromWeight(t.priority),
                            dueDate = t.dayOffset?.let { today.plusDays(it.toLong()) },
                        ),
                    )
                }
                p.milestones.forEach { m ->
                    projects.saveMilestone(
                        ProjectMilestone(projectId = projectId, title = m.title.fill(), date = m.dayOffset?.let { today.plusDays(it.toLong()) }),
                    )
                }
                TemplateResult(TemplateType.PROJECT, projectId, 1 + p.tasks.size)
            }
            TemplateType.TASKS -> {
                val ids = p.tasks.map { t ->
                    tasks.save(
                        Task(
                            title = t.title.fill(),
                            priority = Priority.fromWeight(t.priority),
                            dueDate = t.dayOffset?.let { today.plusDays(it.toLong()) },
                        ),
                    )
                }
                TemplateResult(TemplateType.TASKS, ids.firstOrNull() ?: 0, ids.size)
            }
            TemplateType.HABITS -> {
                val ids = p.habits.map { h ->
                    habits.save(
                        Habit(
                            title = h.title.fill(),
                            schedule = HabitSchedule.decode(h.schedule),
                            target = h.target,
                            unit = h.unit,
                            icon = PlannerIcon.fromKey(h.icon),
                            color = AccentColor.fromKey(h.color),
                            startDate = today,
                        ),
                    )
                }
                TemplateResult(TemplateType.HABITS, ids.firstOrNull() ?: 0, ids.size)
            }
        }
    }
}
