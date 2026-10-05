package com.behnamjalali.planb.clipper

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.MainUiState
import com.behnamjalali.planb.MainViewModel
import com.behnamjalali.planb.ProStatusViewModel
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.ProAccess
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.feature.notebooks.clipper.ClipInput
import com.behnamjalali.planb.feature.notebooks.clipper.ClipIntentInput
import com.behnamjalali.planb.feature.notebooks.clipper.ClipperSheet
import com.behnamjalali.planb.feature.security.AppLockGate
import com.behnamjalali.planb.quick.QuickLinks
import com.behnamjalali.planb.ui.PlanBProviders
import dagger.hilt.android.AndroidEntryPoint

/**
 * "Save to Plan-B" in Android's share sheet (Plan-B Pro #22): a sheet over the sharing app that
 * saves shared text, HTML or a link as a note. Exported for `ACTION_SEND` of text only; it
 * reads the text extras (bounded) and never a stream, fetches nothing, and stays behind the
 * app lock like the rest of Plan-B. Without Pro the sheet explains the feature.
 */
@AndroidEntryPoint
class ClipperActivity : AppCompatActivity() {
    private val main: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val input = readInput(intent)
        setContent {
            val state by main.uiState.collectAsStateWithLifecycle()
            val today by main.today.collectAsStateWithLifecycle()
            val proStatus: ProStatusViewModel = hiltViewModel()
            val isPro by proStatus.isPro.collectAsStateWithLifecycle()
            val settings = (state as? MainUiState.Ready)?.settings ?: return@setContent
            val access = remember(isPro) { ProAccess(isPro) { feature -> openInApp(QuickLinks.pro((feature ?: ProFeature.WEB_CLIPPER).id)) } }
            PlanBProviders(settings, today, isPro) {
                CompositionLocalProvider(LocalProAccess provides access) {
                    AppLockGate {
                        ClipperSheet(
                            input = input,
                            onOpenNote = { id -> openInApp("planb://open/note/$id".toUri()) },
                            onFinish = ::finish,
                        )
                    }
                }
            }
        }
    }

    private fun openInApp(uri: android.net.Uri) {
        startActivity(QuickLinks.intent(this, uri))
        finish()
    }

    /** Reads the share's text extras; a malformed or foreign intent reads as "nothing shared". */
    private fun readInput(intent: Intent?): ClipInput? = try {
        intent?.let {
            ClipIntentInput.from(
                action = it.action,
                type = it.type,
                text = it.getCharSequenceExtra(Intent.EXTRA_TEXT),
                html = it.getStringExtra(Intent.EXTRA_HTML_TEXT),
                subject = it.getStringExtra(Intent.EXTRA_SUBJECT),
                title = it.getStringExtra(Intent.EXTRA_TITLE),
            )
        }
    } catch (e: RuntimeException) {
        // A broken parcel from another app must not crash Plan-B.
        Log.w(TAG, "Unreadable share (${e.javaClass.simpleName})")
        null
    }

    private companion object {
        const val TAG = "PlanBClipper"
    }
}
