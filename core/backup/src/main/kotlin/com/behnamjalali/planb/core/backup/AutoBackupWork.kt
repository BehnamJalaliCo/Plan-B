package com.behnamjalali.planb.core.backup

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A SAF tree folder (local storage, SD card, Google Drive, ...) reached through DocumentsContract. */
internal class SafBackupFolder(private val context: Context, private val tree: Uri) : BackupFolder {
    private val resolver get() = context.contentResolver
    private val folderId: String = DocumentsContract.getTreeDocumentId(tree)
    private val folderUri: Uri = DocumentsContract.buildDocumentUriUsingTree(tree, folderId)

    private fun children(): List<Pair<String, Uri>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = resolver.query(childrenUri, projection, null, null, null) ?: error("Folder not readable")
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    add(name to DocumentsContract.buildDocumentUriUsingTree(tree, id))
                }
            }
        }
    }

    override fun list(): List<String> = children().map { it.first }

    override fun write(name: String, block: (OutputStream) -> Unit) {
        val file = DocumentsContract.createDocument(resolver, folderUri, BackupFormat.MIME, name) ?: error("Cannot create file")
        val stream = resolver.openOutputStream(file, "wt") ?: error("Cannot write file")
        stream.use(block)
    }

    override fun delete(name: String): Boolean {
        val uri = children().firstOrNull { it.first == name }?.second ?: return false
        return DocumentsContract.deleteDocument(resolver, uri)
    }

    companion object {
        /** Null when the permission to the folder is no longer held. */
        fun open(context: Context, uri: String): BackupFolder? {
            val tree = uri.toUri()
            val held = context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission && it.isReadPermission }
            return if (held) SafBackupFolder(context, tree) else null
        }
    }
}

/**
 * Takes a lasting permission to the folder the user picked (ACTION_OPEN_DOCUMENT_TREE) and
 * returns its display name; the permission to [previous] is released.
 */
fun adoptBackupFolder(context: Context, uri: Uri, previous: String?): String? {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    context.contentResolver.takePersistableUriPermission(uri, flags)
    previous?.toUri()?.takeIf { it != uri }?.let { old -> runCatching { context.contentResolver.releasePersistableUriPermission(old, flags) } }
    return runCatching {
        val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}

/** Schedules (or cancels) the periodic automatic backup. */
fun interface AutoBackupScheduler {
    fun apply(settings: AutoBackupSettings)
}

/** The periodic request for [settings], or null when automatic backups are off. */
object AutoBackupRequests {
    const val UNIQUE_NAME = "planb_auto_backup"

    fun request(settings: AutoBackupSettings): PeriodicWorkRequest? {
        if (!settings.enabled || settings.folderUri == null) return null
        val constraints = Constraints.Builder()
            .setRequiresCharging(settings.chargingOnly)
            .setRequiresStorageNotLow(true)
            .build()
        return PeriodicWorkRequestBuilder<AutoBackupWorker>(settings.frequency.days, TimeUnit.DAYS)
            .setConstraints(constraints)
            .addTag(UNIQUE_NAME)
            .build()
    }
}

@Singleton
class WorkManagerAutoBackupScheduler @Inject constructor(@ApplicationContext private val context: Context) : AutoBackupScheduler {
    override fun apply(settings: AutoBackupSettings) {
        // WorkManager may be unavailable (tests without its initializer); scheduling is best-effort.
        runCatching {
            val work = WorkManager.getInstance(context)
            val request = AutoBackupRequests.request(settings)
            if (request == null) {
                work.cancelUniqueWork(AutoBackupRequests.UNIQUE_NAME)
            } else {
                work.enqueueUniquePeriodicWork(AutoBackupRequests.UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
        }
    }
}

@dagger.Module
@InstallIn(SingletonComponent::class)
internal abstract class AutoBackupSchedulerModule {
    @dagger.Binds abstract fun scheduler(impl: WorkManagerAutoBackupScheduler): AutoBackupScheduler
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AutoBackupEntryPoint {
    fun runner(): AutoBackupRunner
}

/** Runs one automatic backup. Only a failure notifies the user; success stays quiet. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val runner = EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java).runner()
        val result = runner.run()
        if (result is AutoBackupResult.Failed) AutoBackupNotifier.notifyFailure(applicationContext)
        // A failure is shown in Settings and notified; the next period tries again.
        return Result.success()
    }
}

/** The single notification of automatic backups: "the last backup failed". */
object AutoBackupNotifier {
    private const val CHANNEL = "planb_backup"
    private const val ID = 0x0B4C

    // The permission is checked right below (Android 13+); without it nothing is shown.
    @SuppressLint("MissingPermission")
    fun notifyFailure(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.backup_auto_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val content = launch?.let { PendingIntent.getActivity(context, ID, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.backup_auto_failed_title))
            .setContentText(context.getString(R.string.backup_auto_failed_text))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, notification) }
    }
}
