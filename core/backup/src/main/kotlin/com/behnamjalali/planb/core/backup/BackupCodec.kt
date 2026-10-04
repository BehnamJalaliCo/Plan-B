package com.behnamjalali.planb.core.backup

import com.behnamjalali.planb.core.data.AttachmentFiles
import com.behnamjalali.planb.core.database.PlanBDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * A fully decoded (but not yet validated) backup. [attachmentFiles] maps an attachment's file
 * name to its bytes on disk: the live files when writing, the unpacked (staged) files after
 * reading with a staging folder.
 */
data class BackupArchive(
    val manifest: BackupManifest,
    val database: BackupDatabase,
    val preferences: Map<String, String>,
    val metadata: BackupMetadata,
    val attachmentFiles: Map<String, File> = emptyMap(),
    /** The folder holding [attachmentFiles] after reading; it replaces the attachment folder on restore. */
    val stagingDirectory: File? = null,
)

/**
 * Reads and writes the ZIP container. Pure stream code (no Android, no DB) so it
 * is fully unit-testable. Reading never trusts entry sizes or names.
 */
object BackupCodec {
    private val PREFS_SERIALIZER = MapSerializer(String.serializer(), String.serializer())

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun write(archive: BackupArchive, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put(BackupFormat.MANIFEST, json.encodeToString(BackupManifest.serializer(), archive.manifest))
            put(BackupFormat.DATABASE, json.encodeToString(BackupDatabase.serializer(), archive.database))
            put(BackupFormat.PREFERENCES, json.encodeToString(PREFS_SERIALIZER, archive.preferences))
            put(BackupFormat.METADATA, json.encodeToString(BackupMetadata.serializer(), archive.metadata))
            // Attachment files are streamed from disk, one entry per referenced file.
            archive.database.attachments.map { it.fileName }.distinct().forEach { name ->
                val file = archive.attachmentFiles[name] ?: return@forEach
                zip.putNextEntry(ZipEntry(BackupFormat.ATTACHMENTS_DIR + name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Decodes an archive. A backup whose database schema is newer than [maxSchemaVersion] is
     * rejected: this app cannot know what the newer fields mean. Running out of memory while
     * reading counts as a damaged archive rather than crashing the app.
     */
    /**
     * With [stagingDirectory], attachment files (`attachments/<name>`) are streamed into it,
     * within [BackupFormat.MAX_ATTACHMENT_BYTES] per file and [maxAttachmentBytes] in total;
     * without it they are skipped. Only names that pass [AttachmentFiles.isSafeName] are
     * written, so nothing can land outside the folder.
     */
    fun read(
        input: InputStream,
        maxTotalBytes: Long = BackupFormat.MAX_TOTAL_BYTES,
        maxSchemaVersion: Int = PlanBDatabase.VERSION,
        stagingDirectory: File? = null,
        maxAttachmentBytes: Long = BackupFormat.MAX_ATTACHMENTS_TOTAL_BYTES,
    ): BackupArchive = try {
        readUnchecked(input, maxTotalBytes, maxSchemaVersion, stagingDirectory, maxAttachmentBytes)
    } catch (e: OutOfMemoryError) {
        throw BackupException.Corrupt("too large to open")
    } catch (e: java.io.IOException) {
        // Includes a full disk while unpacking attachments.
        if (e is ZipException || e is java.io.EOFException) throw BackupException.Corrupt("invalid ZIP (${e.message})")
        throw BackupException.Corrupt("cannot be unpacked (${e.javaClass.simpleName})")
    }

    private fun readUnchecked(
        input: InputStream,
        maxTotalBytes: Long,
        maxSchemaVersion: Int,
        stagingDirectory: File?,
        maxAttachmentBytes: Long,
    ): BackupArchive {
        val entries = mutableMapOf<String, ByteArray>()
        val attachmentFiles = mutableMapOf<String, File>()
        try {
            ZipInputStream(input).use { zip ->
                var count = 0
                var totalBytes = 0L
                var attachmentBytes = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    count++
                    if (count > BackupFormat.MAX_ENTRIES) throw BackupException.Corrupt("too many entries")
                    val name = entry.name
                    if (!entry.isDirectory && name.startsWith(BackupFormat.ATTACHMENTS_DIR)) {
                        val fileName = name.removePrefix(BackupFormat.ATTACHMENTS_DIR)
                        if (stagingDirectory == null || !AttachmentFiles.isSafeName(fileName) || fileName in attachmentFiles) continue
                        if (attachmentFiles.size >= BackupFormat.MAX_ATTACHMENTS) throw BackupException.Corrupt("too many attachments")
                        val target = File(stagingDirectory, fileName)
                        val written = target.outputStream().use { out ->
                            copyLimited(zip, out, minOf(BackupFormat.MAX_ATTACHMENT_BYTES, maxAttachmentBytes - attachmentBytes))
                        }
                        attachmentBytes += written
                        attachmentFiles[fileName] = target
                        continue
                    }
                    // Only flat, known file names are read; anything else is ignored.
                    if (entry.isDirectory || name.contains('/') || name.contains('\\') || name.contains("..")) continue
                    if (name !in KNOWN) continue
                    val bytes = readLimited(zip, minOf(BackupFormat.MAX_ENTRY_BYTES, maxTotalBytes - totalBytes))
                    totalBytes += bytes.size
                    entries[name] = bytes
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: ZipException) {
            throw BackupException.Corrupt("invalid ZIP (${e.message})")
        } catch (e: java.io.EOFException) {
            throw BackupException.Corrupt("truncated archive")
        }
        if (entries.isEmpty()) throw BackupException.NotABackup("empty or not a ZIP archive")
        val manifestBytes = entries[BackupFormat.MANIFEST] ?: throw BackupException.NotABackup("manifest.json missing")
        val manifest = decode("manifest.json") { json.decodeFromString(BackupManifest.serializer(), manifestBytes.utf8()) }
        if (manifest.application != BackupFormat.APPLICATION_ID) throw BackupException.NotABackup("created by another application")
        if (manifest.backupFormatVersion > BackupFormat.CURRENT) throw BackupException.UnsupportedVersion(manifest.backupFormatVersion)
        if (manifest.backupFormatVersion < 1) throw BackupException.Corrupt("invalid format version ${manifest.backupFormatVersion}")
        if (manifest.databaseSchemaVersion > maxSchemaVersion) throw BackupException.NewerDatabase(manifest.databaseSchemaVersion)
        val dbBytes = entries[BackupFormat.DATABASE] ?: throw BackupException.Corrupt("database.json missing")
        val database = decode("database.json") { json.decodeFromString(BackupDatabase.serializer(), dbBytes.utf8()) }
        val preferences = entries[BackupFormat.PREFERENCES]?.let { bytes ->
            decode("preferences.json") {
                json.decodeFromString(
                    PREFS_SERIALIZER,
                    bytes.utf8(),
                )
            }
        } ?: emptyMap()
        val metadata = entries[BackupFormat.METADATA]?.let { bytes ->
            runCatching { json.decodeFromString(BackupMetadata.serializer(), bytes.utf8()) }.getOrDefault(BackupMetadata())
        } ?: BackupMetadata()
        return BackupArchive(manifest, database, preferences, metadata, attachmentFiles, stagingDirectory)
    }

    private val KNOWN = setOf(BackupFormat.MANIFEST, BackupFormat.DATABASE, BackupFormat.PREFERENCES, BackupFormat.METADATA)

    private fun ByteArray.utf8() = toString(Charsets.UTF_8)

    private inline fun <T> decode(name: String, block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw BackupException.Corrupt("$name is not valid (${e.message?.take(120)})")
    } catch (e: IllegalArgumentException) {
        throw BackupException.Corrupt("$name is not valid (${e.message?.take(120)})")
    }

    private fun copyLimited(input: InputStream, output: java.io.OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw BackupException.Corrupt("attachment too large")
            output.write(buffer, 0, n)
        }
        return total
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw BackupException.Corrupt("entry too large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}

/** Checks referential integrity and id uniqueness before anything is written. */
object BackupValidator {
    /** SQLite's NOCASE collation: only ASCII A-Z are folded. */
    private fun nocase(value: String): String = buildString(value.length) {
        value.forEach { c -> append(if (c in 'A'..'Z') c + ('a' - 'A') else c) }
    }

    /**
     * [attachmentFiles] are the files that came with the archive; when [requireAttachmentFiles]
     * is true every attachment row must have its file (a restore never creates rows without
     * their bytes).
     */
    fun validate(db: BackupDatabase, attachmentFiles: Set<String> = emptySet(), requireAttachmentFiles: Boolean = false) {
        fun <T> unique(name: String, items: List<T>, id: (T) -> Long): Set<Long> {
            val ids = items.map(id)
            val set = ids.toSet()
            if (set.size != ids.size) throw BackupException.Invalid("duplicate ids in $name")
            if (ids.any { it <= 0 }) throw BackupException.Invalid("invalid id in $name")
            return set
        }
        fun check(condition: Boolean, message: () -> String) { if (!condition) throw BackupException.Invalid(message()) }

        val tags = unique("tags", db.tags) { it.id }
        val projects = unique("projects", db.projects) { it.id }
        val tasks = unique("tasks", db.tasks) { it.id }
        val notebooks = unique("notebooks", db.notebooks) { it.id }
        val sections = unique("sections", db.sections) { it.id }
        val notes = unique("notes", db.notes) { it.id }
        val habits = unique("habits", db.habits) { it.id }
        val goals = unique("goals", db.goals) { it.id }
        unique("projectMilestones", db.projectMilestones) { it.id }
        unique("goalMilestones", db.goalMilestones) { it.id }
        unique("habitCompletions", db.habitCompletions) { it.id }
        unique("events", db.events) { it.id }
        unique("focusSessions", db.focusSessions) { it.id }
        unique("templates", db.templates) { it.id }

        db.tasks.forEach { t ->
            check(t.projectId == null || t.projectId in projects) { "task ${t.id} references missing project" }
            check(t.parentTaskId == null || (t.parentTaskId in tasks && t.parentTaskId != t.id)) { "task ${t.id} references missing parent" }
        }
        // Tag names are unique the way the database compares them (COLLATE NOCASE, which folds
        // ASCII letters only), and so are links.
        check(db.tags.map { nocase(it.name) }.let { it.toSet().size == it.size }) { "duplicate tag names" }
        for ((name, links) in listOf("task" to db.taskTags, "project" to db.projectTags, "note" to db.noteTags)) {
            check(links.map { it.a to it.b }.toSet().size == links.size) { "duplicate $name tag links" }
        }
        db.taskTags.forEach { check(it.a in tasks && it.b in tags) { "task tag references missing record" } }
        db.projectTags.forEach { check(it.a in projects && it.b in tags) { "project tag references missing record" } }
        db.noteTags.forEach { check(it.a in notes && it.b in tags) { "note tag references missing record" } }
        db.projectMilestones.forEach { check(it.parentId in projects) { "milestone ${it.id} references missing project" } }
        db.goalMilestones.forEach { check(it.parentId in goals) { "milestone ${it.id} references missing goal" } }
        db.sections.forEach { check(it.notebookId in notebooks) { "section ${it.id} references missing notebook" } }
        db.notes.forEach { n ->
            check(n.notebookId in notebooks) { "note ${n.id} references missing notebook" }
            check(n.sectionId == null || n.sectionId in sections) { "note ${n.id} references missing section" }
        }
        db.habitCompletions.forEach { check(it.habitId in habits) { "completion ${it.id} references missing habit" } }
        val perDay = db.habitCompletions.groupBy { it.habitId to it.date }
        check(perDay.values.all { it.size == 1 }) { "duplicate habit check-ins for the same day" }
        db.goals.forEach { check(it.projectId == null || it.projectId in projects) { "goal ${it.id} references missing project" } }
        db.focusSessions.forEach { check(it.linkedTaskId == null || it.linkedTaskId in tasks) { "focus session ${it.id} references missing task" } }
        validateV3(db, tasks, notes, habits, attachmentFiles, requireAttachmentFiles, ::check)
        // Parent tasks must not form cycles.
        val parents = db.tasks.associate { it.id to it.parentTaskId }
        db.tasks.forEach { start ->
            var current = start.parentTaskId
            var steps = 0
            while (current != null) {
                check(current != start.id && steps++ < db.tasks.size) { "task hierarchy contains a cycle" }
                current = parents[current]
            }
        }
    }
}

/** Format 2 (schema v3) tables: references, uniqueness the database enforces, and files. */
private fun validateV3(
    db: BackupDatabase,
    tasks: Set<Long>,
    notes: Set<Long>,
    habits: Set<Long>,
    attachmentFiles: Set<String>,
    requireAttachmentFiles: Boolean,
    check: (Boolean, () -> String) -> Unit,
) {
    fun <T> unique(name: String, items: List<T>, id: (T) -> Long): Set<Long> {
        val ids = items.map(id)
        check(ids.toSet().size == ids.size) { "duplicate ids in $name" }
        check(ids.all { it > 0 }) { "invalid id in $name" }
        return ids.toSet()
    }
    unique("taskReminders", db.taskReminders) { it.id }
    unique("savedFilters", db.savedFilters) { it.id }
    unique("noteVersions", db.noteVersions) { it.id }
    unique("attachments", db.attachments) { it.id }
    unique("journalEntries", db.journalEntries) { it.id }
    val moods = unique("moodEntries", db.moodEntries) { it.id }
    unique("challenges", db.challenges) { it.id }
    unique("badges", db.badges) { it.id }
    unique("activityLog", db.activityLog) { it.id }
    unique("calendarLinks", db.calendarLinks) { it.id }
    val events = db.events.map { it.id }.toSet()

    db.taskReminders.forEach { check(it.taskId in tasks) { "reminder ${it.id} references missing task" } }
    check(db.taskDependencies.map { it.a to it.b }.toSet().size == db.taskDependencies.size) { "duplicate task dependencies" }
    db.taskDependencies.forEach {
        check(it.a in tasks && it.b in tasks && it.a != it.b) { "task dependency references missing or same task" }
    }
    db.noteVersions.forEach { check(it.noteId in notes) { "note version ${it.id} references missing note" } }
    check(db.noteLinks.map { it.a to it.b }.toSet().size == db.noteLinks.size) { "duplicate note links" }
    db.noteLinks.forEach { check(it.a in notes && it.b in notes) { "note link references missing note" } }
    db.notes.forEach { n ->
        check(n.encryptedPayload == null || runCatching { java.util.Base64.getDecoder().decode(n.encryptedPayload) }.isSuccess) {
            "note ${n.id} has an unreadable encrypted body"
        }
    }
    check(db.attachments.map { it.fileName }.toSet().size == db.attachments.size) { "duplicate attachment files" }
    db.attachments.forEach { a ->
        check(AttachmentFiles.isSafeName(a.fileName)) { "attachment ${a.id} has an invalid file name" }
        val ownerExists = when (a.ownerType) {
            "TASK" -> a.ownerId in tasks
            "NOTE" -> a.ownerId in notes
            "EVENT" -> a.ownerId in events
            "MOOD" -> a.ownerId in moods
            else -> false
        }
        check(ownerExists) { "attachment ${a.id} references a missing owner" }
        check(!requireAttachmentFiles || a.fileName in attachmentFiles) { "attachment ${a.id} has no file in the backup" }
    }
    db.journalEntries.forEach { check(it.noteId in notes) { "journal entry ${it.id} references missing note" } }
    check(db.journalEntries.map { it.date }.toSet().size == db.journalEntries.size) { "two journal entries for the same day" }
    db.moodEntries.forEach { check(it.noteId == null || it.noteId in notes) { "mood entry ${it.id} references missing note" } }
    db.challenges.forEach { check(it.habitId == null || it.habitId in habits) { "challenge ${it.id} references missing habit" } }
    check(db.badges.map { it.key }.toSet().size == db.badges.size) { "duplicate badges" }
    check(db.calendarLinks.map { it.localType to it.localId }.toSet().size == db.calendarLinks.size) { "duplicate calendar links" }
    check(db.calendarLinks.map { it.calendarId to it.externalEventId }.toSet().size == db.calendarLinks.size) { "duplicate calendar links" }
}
