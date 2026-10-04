package com.behnamjalali.planb.core.backup

import java.io.ByteArrayOutputStream
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

/** A fully decoded (but not yet validated) backup. */
data class BackupArchive(
    val manifest: BackupManifest,
    val database: BackupDatabase,
    val preferences: Map<String, String>,
    val metadata: BackupMetadata,
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
        }
    }

    fun read(input: InputStream, maxTotalBytes: Long = BackupFormat.MAX_TOTAL_BYTES): BackupArchive {
        val entries = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(input).use { zip ->
                var count = 0
                var totalBytes = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    count++
                    if (count > BackupFormat.MAX_ENTRIES) throw BackupException.Corrupt("too many entries")
                    val name = entry.name
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
        return BackupArchive(manifest, database, preferences, metadata)
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
    fun validate(db: BackupDatabase) {
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
        // Tag names are unique (the database has a unique index on the exact name), and so are links.
        check(db.tags.map { it.name }.let { it.toSet().size == it.size }) { "duplicate tag names" }
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
