package com.behnamjalali.planb.core.data

import android.content.Context
import androidx.room.withTransaction
import com.behnamjalali.planb.core.database.PlanBDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private storage for attachment files (`files/attachments/<name>`, see the
 * `attachments` table). Rows store only a flat [isSafeName] file name, never a path, so a
 * name read from a backup can never point outside this folder. Nothing here is shared with
 * other apps or uploaded.
 */
@Singleton
class AttachmentFiles(private val root: File) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.filesDir)

    val directory: File get() = File(root, DIR)

    /** The file for a stored name; rejects anything that is not a plain file name. */
    fun file(name: String): File {
        require(isSafeName(name)) { "Not an attachment file name" }
        return File(directory, name)
    }

    /** A new unique name with the given extension (letters and digits only, may be empty). */
    fun newFileName(extension: String = ""): String {
        val ext = extension.filter { it.isLetterOrDigit() && it.code < 128 }.take(10).lowercase()
        return UUID.randomUUID().toString() + if (ext.isEmpty()) "" else ".$ext"
    }

    /**
     * Deletes files that no row refers to and that are older than [minAgeMillis] (a file is
     * written before its row is inserted, so a fresh one may not be referenced yet). Returns
     * how many were removed.
     */
    fun deleteUnreferenced(referenced: Set<String>, minAgeMillis: Long = 60 * 60 * 1000L): Int {
        val cutoff = System.currentTimeMillis() - minAgeMillis
        return directory.listFiles().orEmpty().count { it.isFile && it.name !in referenced && it.lastModified() < cutoff && it.delete() }
    }

    fun deleteAll() {
        directory.listFiles().orEmpty().forEach { it.deleteRecursively() }
    }

    /** A fresh, empty folder for unpacking a backup's files before they replace the current ones. */
    fun newStagingDirectory(): File {
        val staging = File(root, STAGING_DIR)
        // Only one restore is prepared at a time; older, abandoned staging folders go.
        staging.listFiles().orEmpty().forEach { it.deleteRecursively() }
        return File(staging, UUID.randomUUID().toString()).apply { mkdirs() }
    }

    /**
     * Replaces the attachment folder with [staged] (or an empty folder) and returns how to undo
     * or finish it. The current folder is kept aside until [Replacement.commit], so a failed
     * database restore can put it back with [Replacement.rollback]. Renames stay on one file
     * system, so each step is atomic.
     */
    fun replaceWith(staged: File?): Replacement {
        val current = directory
        val previous = File(root, "$DIR-previous")
        previous.deleteRecursively()
        if (current.exists() && !current.renameTo(previous)) error("Cannot move the current attachments aside")
        val installed = if (staged != null && staged.isDirectory) staged.renameTo(current) else current.mkdirs()
        if (!installed) {
            current.deleteRecursively()
            previous.renameTo(current)
            error("Cannot install the restored attachments")
        }
        return Replacement(
            commit = { previous.deleteRecursively() },
            rollback = {
                current.deleteRecursively()
                if (previous.exists()) previous.renameTo(current)
            },
        )
    }

    class Replacement(val commit: () -> Unit, val rollback: () -> Unit)

    companion object {
        const val DIR = "attachments"
        private const val STAGING_DIR = "backup-staging"
        private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

        /** A flat file name: no separators, no `..`, ASCII letters, digits, dot, dash, underscore. */
        fun isSafeName(name: String): Boolean = SAFE_NAME.matches(name) && ".." !in name
    }
}

/** Keeps attachment rows and files consistent (rows of deleted owners, files without rows). */
class AttachmentMaintenance @Inject constructor(
    private val db: PlanBDatabase,
    private val files: AttachmentFiles,
    private val attachments: com.behnamjalali.planb.core.data.repository.AttachmentRepository,
) {
    /** Returns the number of files removed. Run off the main thread (app start). */
    suspend fun sweep(): Int {
        // Files of rich blocks the user deleted (and that no draft or kept version uses).
        val unused = attachments.deleteUnusedEverywhere()
        val referenced = db.withTransaction {
            db.attachmentDao().deleteOrphans()
            db.attachmentDao().allFileNames().toSet()
        }
        return unused + files.deleteUnreferenced(referenced)
    }
}
