package com.behnamjalali.planb.feature.notebooks.rich

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.Attachment
import com.behnamjalali.planb.core.model.AttachmentKind
import com.behnamjalali.planb.core.model.BlockType
import com.behnamjalali.planb.core.model.rich.ChartData
import com.behnamjalali.planb.core.model.rich.ChartPoint
import com.behnamjalali.planb.core.model.rich.ColumnType
import com.behnamjalali.planb.core.model.rich.DatabaseData
import com.behnamjalali.planb.core.model.rich.DbColumn
import com.behnamjalali.planb.core.model.rich.DbRow
import com.behnamjalali.planb.core.model.rich.Drawing
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.model.rich.TableData
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.rememberProGuard
import com.behnamjalali.planb.feature.notebooks.NoteEditorViewModel
import com.behnamjalali.planb.feature.notebooks.R
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** The drawing editor is open for [blockId] (null: a new drawing) starting from [drawing]. */
data class DrawingRequest(val blockId: String?, val drawing: Drawing)

/** Which explanation about the microphone is showing. */
enum class MicDialog { RATIONALE, SETTINGS }

/**
 * The UI side of rich blocks: pickers, camera, scanner and microphone launchers, the open
 * dialogs, and the actions blocks call. Created once per editor screen by [rememberRichUi].
 */
@Stable
class RichUi internal constructor(
    internal val editor: RichEditor,
    private val context: Context,
    private val scope: CoroutineScope,
) {
    internal var launchGallery: () -> Unit = {}
    internal var launchCamera: (AttachmentKind) -> Unit = {}
    internal var launchFile: () -> Unit = {}
    internal var launchScanner: () -> Unit = {}
    internal var launchMicPermission: () -> Unit = {}

    var drawingRequest by mutableStateOf<DrawingRequest?>(null)
    var recorderOpen by mutableStateOf(false)
    var cropFile by mutableStateOf<File?>(null)
    var micDialog by mutableStateOf<MicDialog?>(null)

    /** Live dictation is about to start for (block, language): its hint is showing. */
    var dictation by mutableStateOf<Pair<String, String>?>(null)

    /** What to do once the microphone permission is granted (record, or dictate a transcript). */
    private var afterMicPermission: () -> Unit = { recorderOpen = true }

    fun update(id: String, data: JsonObject? = null, text: String? = null) = editor.update(id, data, text)

    fun file(attachment: Attachment): File? = editor.file(attachment)?.takeIf { it.isFile }

    fun insertTable() = editor.insert(BlockType.TABLE, RichBlocks.encode(TableData()))

    fun insertDatabase() = editor.insert(
        BlockType.DATABASE,
        RichBlocks.encode(
            DatabaseData(
                columns = listOf(
                    DbColumn("c${UUID.randomUUID().toString().take(8)}", context.getString(R.string.rich_db_default_name)),
                    DbColumn("c${UUID.randomUUID().toString().take(8)}", context.getString(R.string.rich_db_default_amount), ColumnType.NUMBER),
                    DbColumn("c${UUID.randomUUID().toString().take(8)}", context.getString(R.string.rich_db_default_done), ColumnType.CHECKBOX),
                ),
                rows = List(2) { DbRow(UUID.randomUUID().toString()) },
            ),
        ),
    )

    fun insertMath() = editor.insert(BlockType.MATH)

    fun insertChart() = editor.insert(
        BlockType.CHART,
        RichBlocks.encode(
            ChartData(
                points = listOf(
                    ChartPoint(context.getString(R.string.rich_chart_default_a), 3.0),
                    ChartPoint(context.getString(R.string.rich_chart_default_b), 5.0),
                    ChartPoint(context.getString(R.string.rich_chart_default_c), 2.0),
                ),
            ),
        ),
    )

    fun newDrawing() {
        drawingRequest = DrawingRequest(null, Drawing())
    }

    fun editDrawing(blockId: String) {
        scope.launch { drawingRequest = DrawingRequest(blockId, editor.readDrawing(blockId)) }
    }

    private fun withMicrophone(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            // Asked in context, after explaining why (only while recording or dictating).
            afterMicPermission = action
            micDialog = MicDialog.RATIONALE
        }
    }

    fun record() = withMicrophone { recorderOpen = true }

    /**
     * Transcribes a recording: the file itself on Android 13+, otherwise live dictation
     * (explained first, and only with the microphone permission).
     */
    fun transcribe(blockId: String, languageTag: String) {
        if (editor.canTranscribeFiles()) editor.transcribe(blockId, languageTag) else withMicrophone { dictation = blockId to languageTag }
    }

    internal fun onMicPermission(granted: Boolean, activity: Activity?) {
        if (granted) {
            afterMicPermission()
        } else if (activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            micDialog = MicDialog.SETTINGS
        }
    }

    fun openAppSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Opens an attachment with another app (read-only access to that one file). */
    fun open(attachment: Attachment) = launch(attachment) { uri ->
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mimeType)
    }

    fun share(attachment: Attachment) = launch(attachment) { uri ->
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType(attachment.mimeType).putExtra(Intent.EXTRA_STREAM, uri), null)
    }

    private fun launch(attachment: Attachment, intent: (Uri) -> Intent) {
        val file = file(attachment) ?: return toast(R.string.rich_missing_file)
        val uri = AttachmentUris.forFile(context, file, attachment.displayName)
        try {
            context.startActivity(intent(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.rich_error_no_app)
        }
    }

    internal fun toast(text: Int) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}

/** Content URIs for attachments and camera captures (`<package>.attachments` FileProvider). */
internal object AttachmentUris {
    fun authority(context: Context) = "${context.packageName}.attachments"

    fun forFile(context: Context, file: File, displayName: String = ""): Uri =
        if (displayName.isBlank()) {
            FileProvider.getUriForFile(context, authority(context), file)
        } else {
            FileProvider.getUriForFile(context, authority(context), file, displayName)
        }

    /** A fresh private file for the camera to write into. */
    fun captureFile(context: Context): File =
        File(File(context.cacheDir, "capture").apply { mkdirs() }, "photo-${System.currentTimeMillis()}.jpg")
}

@Composable
internal fun rememberRichUi(viewModel: NoteEditorViewModel): RichUi {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val ui = remember(viewModel) { RichUi(viewModel.rich, context.applicationContext, scope) }
    var pendingCapture by remember { mutableStateOf<Pair<File, AttachmentKind>?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED)) { uris ->
        if (uris.isNotEmpty()) ui.editor.addImages(uris, AttachmentKind.IMAGE)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val (file, kind) = pendingCapture ?: return@rememberLauncherForActivityResult
        pendingCapture = null
        when {
            !taken || !file.isFile || file.length() == 0L -> file.delete()
            kind == AttachmentKind.SCAN -> ui.cropFile = file
            else -> ui.editor.addCaptured(file, AttachmentKind.IMAGE, file.name)
        }
    }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(ui.editor::addFile)
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty().map { it.imageUri }
            if (pages.isNotEmpty()) ui.editor.addImages(pages, AttachmentKind.SCAN)
        }
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ui.onMicPermission(granted, activity)
    }

    ui.launchGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    ui.launchFile = { document.launch(arrayOf("*/*")) }
    ui.launchMicPermission = { mic.launch(Manifest.permission.RECORD_AUDIO) }
    ui.launchCamera = { kind ->
        val file = AttachmentUris.captureFile(context)
        pendingCapture = file to kind
        try {
            camera.launch(AttachmentUris.forFile(context, file))
        } catch (e: ActivityNotFoundException) {
            pendingCapture = null
            ui.toast(R.string.rich_error_camera)
        }
    }
    ui.launchScanner = {
        // ML Kit's scanner lives in Google Play services; without them the camera and a manual crop do the job.
        val playServices = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
        if (!playServices || activity == null) {
            ui.launchCamera(AttachmentKind.SCAN)
        } else {
            val client: GmsDocumentScanner = GmsDocumentScanning.getClient(
                GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(MAX_SCAN_PAGES)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build(),
            )
            client.getStartScanIntent(activity)
                .addOnSuccessListener { sender -> scanner.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { ui.launchCamera(AttachmentKind.SCAN) }
        }
    }
    return ui
}

private const val MAX_PICKED = 10
private const val MAX_SCAN_PAGES = 10

/** The toolbar's "Insert" button: every Pro block, each opening the Pro screen for free users. */
@Composable
internal fun RichInsertButton(ui: RichUi?, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    val guard = rememberProGuard()
    PlannerIconButton(Icons.Rounded.AddCircleOutline, stringResource(R.string.rich_insert), { open = true }, enabled = enabled && ui != null)
    if (ui == null) return
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        listOf(
            Triple(R.string.rich_insert_gallery, Icons.Rounded.Image, ProFeature.RICH_NOTES) to ui.launchGallery,
            Triple(R.string.rich_insert_camera, Icons.Rounded.CameraAlt, ProFeature.RICH_NOTES) to { ui.launchCamera(AttachmentKind.IMAGE) },
            Triple(R.string.rich_insert_scan, Icons.Rounded.DocumentScanner, ProFeature.DOCUMENT_SCAN) to ui.launchScanner,
            Triple(R.string.rich_block_file, Icons.AutoMirrored.Rounded.InsertDriveFile, ProFeature.RICH_NOTES) to ui.launchFile,
            Triple(R.string.rich_block_table, Icons.Rounded.GridOn, ProFeature.RICH_NOTES) to { ui.insertTable(); Unit },
            Triple(R.string.rich_block_drawing, Icons.Rounded.Draw, ProFeature.RICH_NOTES) to ui::newDrawing,
            Triple(R.string.rich_insert_audio, Icons.Rounded.Mic, ProFeature.VOICE_NOTES) to ui::record,
            Triple(R.string.rich_block_database, Icons.Rounded.TableChart, ProFeature.NOTE_DATABASES) to { ui.insertDatabase(); Unit },
            Triple(R.string.rich_block_math, Icons.Rounded.Functions, ProFeature.MATH_CHARTS) to { ui.insertMath(); Unit },
            Triple(R.string.rich_block_chart, Icons.Rounded.BarChart, ProFeature.MATH_CHARTS) to { ui.insertChart(); Unit },
        ).forEach { (item, action) ->
            val (label, icon, feature) = item
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(label))
                        if (!guard.isPro) {
                            Spacer(Modifier.width(Spacing.sm))
                            ProBadge()
                        }
                    }
                },
                leadingIcon = { Icon(icon, contentDescription = null) },
                onClick = {
                    open = false
                    guard.run(feature, action)
                },
            )
        }
    }
}
