package com.behnamjalali.planb.core.ui.pdf

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Writes [report] as a PDF to the document at [uri] (chosen by the user through the system picker). */
suspend fun writePdf(context: Context, uri: Uri, report: PdfReport): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@runCatching false
        stream.use { PdfReportWriter.write(context, report, it) }
        true
    }.getOrDefault(false)
}

/**
 * Saves a PDF through the Storage Access Framework (ACTION_CREATE_DOCUMENT): the user picks the
 * place, Plan-B needs no storage permission. Call the returned function with a suggested file
 * name; [report] is built when the user has chosen where to save it, and [onResult] reports
 * whether writing succeeded (it is not called when the user cancels the picker).
 */
@Composable
fun rememberPdfExport(report: () -> PdfReport?, onResult: (Boolean) -> Unit): (fileName: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentReport = rememberUpdatedState(report)
    val currentResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val built = currentReport.value()
            scope.launch {
                currentResult.value(built != null && writePdf(context.applicationContext, uri, built))
            }
        }
    }
    return remember(launcher) { { name -> runCatching { launcher.launch(name) }.onFailure { currentResult.value(false) } } }
}
