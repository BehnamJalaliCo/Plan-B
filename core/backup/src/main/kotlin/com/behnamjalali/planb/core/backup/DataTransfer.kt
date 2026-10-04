package com.behnamjalali.planb.core.backup

import android.net.Uri
import com.behnamjalali.planb.core.common.Digits
import com.behnamjalali.planb.core.data.DocumentFiles
import com.behnamjalali.planb.core.data.repository.ProjectRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.database.dao.BackupDao
import com.behnamjalali.planb.core.model.EntityId
import com.behnamjalali.planb.core.model.Markdown
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Project
import com.behnamjalali.planb.core.model.Tag
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** RFC 4180 CSV with UTF-8 BOM so spreadsheet apps detect Persian text correctly. */
object Csv {
    private val BOM = 0xFEFF.toChar().toString()

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    fun write(header: List<String>, rows: List<List<String>>): String = buildString {
        append(BOM)
        append(header.joinToString(",") { escape(it) }).append("\r\n")
        rows.forEach { row -> append(row.joinToString(",") { escape(it) }).append("\r\n") }
    }

    fun parse(text: String): List<List<String>> {
        val input = text.removePrefix(BOM)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                quoted && c == '"' && i + 1 < input.length && input[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    row += field.toString(); field.clear()
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            if (row.any { it.isNotEmpty() }) rows += row
        }
        return rows
    }
}

/** Portable task record for JSON export/import (ISO-8601 Gregorian dates). */
@Serializable
data class TaskExport(
    val title: String,
    val description: String = "",
    val status: String = "TODO",
    val priority: String = "NONE",
    val startDate: String? = null,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val project: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val estimatedMinutes: Int? = null,
    /** Title of the parent task for subtasks; the parent is exported right before them. */
    val parent: String? = null,
)

@Serializable
data class NoteExport(val title: String, val notebook: String, val markdown: String, val pinned: Boolean = false, val favorite: Boolean = false)

data class ImportResult(val imported: Int, val skipped: Int)

class ImportException(message: String) : Exception(message)

@Singleton
class DataTransfer @Inject constructor(
    private val dao: BackupDao,
    private val tasks: TaskRepository,
    private val projects: ProjectRepository,
    private val files: DocumentFiles,
) {
    private val json = BackupCodec.json

    private suspend fun taskExports(): List<TaskExport> {
        val projects = dao.projects().associate { it.id to it.title }
        val tags = dao.tags().associate { it.id to it.name }
        val tagsByTask = dao.taskTags().groupBy({ it.taskId }, { tags[it.tagId].orEmpty() })
        val all = dao.tasks()
        val children = all.filter { it.parentTaskId != null }.groupBy { it.parentTaskId }
        val titles = all.associate { it.id to it.title }
        // Each top-level task is followed by its subtasks, so an import can re-link them.
        val ordered = all.filter { it.parentTaskId == null }.flatMap { listOf(it) + children[it.id].orEmpty() }
        return ordered.map { t ->
            TaskExport(
                title = t.title,
                description = t.description,
                status = t.status,
                priority = Priority.fromWeight(t.priority).name,
                startDate = t.startDate?.toString(),
                dueDate = t.dueDate?.toString(),
                dueTime = t.dueTime?.toString(),
                project = t.projectId?.let { projects[it] },
                tags = tagsByTask[t.id].orEmpty().filter { it.isNotBlank() },
                notes = t.notes,
                estimatedMinutes = t.estimatedMinutes,
                parent = t.parentTaskId?.let { titles[it] },
            )
        }
    }

    suspend fun exportTasksCsv(uri: Uri): Int {
        val rows = taskExports()
        val text = Csv.write(
            listOf("title", "description", "status", "priority", "start_date", "due_date", "due_time", "project", "tags", "notes", "estimated_minutes", "parent"),
            rows.map {
                listOf(it.title, it.description, it.status, it.priority, it.startDate.orEmpty(), it.dueDate.orEmpty(), it.dueTime.orEmpty(),
                    it.project.orEmpty(), it.tags.joinToString(";"), it.notes, it.estimatedMinutes?.toString().orEmpty(), it.parent.orEmpty())
            },
        )
        files.writeText(uri, text)
        return rows.size
    }

    suspend fun exportTasksJson(uri: Uri): Int {
        val rows = taskExports()
        files.writeText(uri, json.encodeToString(ListSerializer(TaskExport.serializer()), rows))
        return rows.size
    }

    /** One Markdown file per note inside a ZIP, grouped in notebook folders. */
    suspend fun exportNotesMarkdownZip(uri: Uri): Int {
        val notebooks = dao.notebooks().associate { it.id to it.title }
        val notes = dao.notes()
        files.output(uri) { out ->
            ZipOutputStream(out).use { zip ->
                val used = mutableSetOf<String>()
                notes.forEach { n ->
                    val folder = safeName(notebooks[n.notebookId] ?: "Notebook")
                    var base = "$folder/${safeName(n.title.ifBlank { "Note ${n.id}" })}"
                    if (!used.add(base)) base = "$base (${n.id})".also { used += it }
                    zip.putNextEntry(ZipEntry("$base.md"))
                    zip.write(Markdown.export(n.title, NoteDocument.decode(n.content)).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
        return notes.size
    }

    suspend fun exportNotesJson(uri: Uri): Int {
        val notebooks = dao.notebooks().associate { it.id to it.title }
        val notes = dao.notes().map { n ->
            NoteExport(n.title, notebooks[n.notebookId].orEmpty(), Markdown.export(n.title, NoteDocument.decode(n.content)), n.pinned, n.favorite)
        }
        files.writeText(uri, json.encodeToString(ListSerializer(NoteExport.serializer()), notes))
        return notes.size
    }

    /**
     * Imports tasks from CSV or JSON. Every valid row becomes a NEW task, so
     * existing records are never overwritten. Invalid rows are skipped and counted.
     */
    suspend fun importTasks(uri: Uri): ImportResult {
        val text = files.readText(uri)
        val trimmed = text.trimStart(0xFEFF.toChar(), ' ', '\n', '\r', '\t')
        val rows: List<TaskExport> = if (trimmed.startsWith("[")) {
            runCatching { json.decodeFromString(ListSerializer(TaskExport.serializer()), trimmed) }
                .getOrElse { throw ImportException("Invalid JSON") }
        } else {
            parseCsv(text)
        }
        if (rows.isEmpty()) throw ImportException("No tasks found")
        // Tasks are linked to an existing project with the same name (case-insensitive);
        // unknown project names create a new project. Existing data is never modified.
        val projectIds = dao.projects().associate { it.title.trim().lowercase() to it.id }.toMutableMap()
        // Top-level tasks imported in this run, by title, for re-linking subtasks.
        val importedByTitle = mutableMapOf<String, EntityId>()
        var imported = 0
        var skipped = 0
        rows.forEach { row ->
            val task = row.toTask()
            if (task == null) {
                skipped++
            } else {
                val projectName = row.project?.trim()?.take(200)?.takeIf { it.isNotEmpty() }
                val projectId = projectName?.let { name ->
                    projectIds.getOrPut(name.lowercase()) { projects.save(Project(title = name)) }
                }
                val parentId = row.parent?.trim()?.takeIf { it.isNotEmpty() }?.let { importedByTitle[it] }
                val id = tasks.save(task.copy(projectId = projectId, parentTaskId = parentId))
                if (parentId == null) importedByTitle[task.title] = id
                imported++
            }
        }
        return ImportResult(imported, skipped)
    }

    private fun parseCsv(text: String): List<TaskExport> {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        val titleIndex = header.indexOf("title")
        if (titleIndex < 0) throw ImportException("CSV needs a 'title' column")
        fun List<String>.col(name: String) = header.indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }
        return rows.drop(1).map { r ->
            TaskExport(
                title = r.getOrNull(titleIndex).orEmpty(),
                description = r.col("description").orEmpty(),
                status = r.col("status") ?: "TODO",
                priority = r.col("priority") ?: "NONE",
                startDate = r.col("start_date"),
                dueDate = r.col("due_date"),
                dueTime = r.col("due_time"),
                project = r.col("project"),
                parent = r.col("parent"),
                tags = r.col("tags")?.split(';', ',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                notes = r.col("notes").orEmpty(),
                estimatedMinutes = r.col("estimated_minutes")?.let { Digits.toLatin(it).toIntOrNull() },
            )
        }
    }

    private fun TaskExport.toTask(): Task? {
        if (title.isBlank()) return null
        val start = startDate?.let { parseDate(it) ?: return null }
        val due = dueDate?.let { parseDate(it) ?: return null }
        val time = dueTime?.let { runCatching { LocalTime.parse(Digits.toLatin(it)) }.getOrNull() ?: return null }
        val st = runCatching { TaskStatus.valueOf(status.uppercase()) }.getOrDefault(TaskStatus.TODO)
        return Task(
            title = title.trim().take(500),
            description = description,
            status = st,
            priority = runCatching { Priority.valueOf(priority.uppercase()) }.getOrDefault(Priority.NONE),
            startDate = start,
            dueDate = due,
            dueTime = if (due != null) time else null,
            notes = notes,
            estimatedMinutes = estimatedMinutes?.takeIf { it in 0..100_000 },
            tags = tags.map { Tag(name = it.removePrefix("#")) },
        )
    }

    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(Digits.toLatin(raw.trim())) }.getOrNull()

    private fun safeName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ").replace(Regex("\\s+"), " ").trim().take(60).ifBlank { "untitled" }
}
