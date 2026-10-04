package com.behnamjalali.planb.quick

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.billing.EntitlementRepository
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.ui.ProFeature
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Opens [uri] in Plan-B from a tile, collapsing the quick-settings panel. */
@SuppressLint("StartActivityAndCollapseDeprecated")
private fun TileService.openApp(uri: Uri) {
    val intent = QuickLinks.intent(this, uri)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startActivityAndCollapse(PendingIntent.getActivity(this, uri.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }
}

/**
 * Quick-settings tile "Quick add" (Plan-B Pro #34): opens Quick Capture. Anyone can add the
 * tile; without Pro, tapping it opens the Pro screen instead (only on the user's tap).
 */
@AndroidEntryPoint
class QuickAddTileService : TileService() {
    @Inject lateinit var entitlements: EntitlementRepository

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val pro = runBlocking { runCatching { entitlements.current().isPro }.getOrDefault(false) }
        openApp(if (pro) QuickLinks.CAPTURE else QuickLinks.pro(ProFeature.QUICK_TILES.id))
    }
}

/** Quick-settings tile "Focus" (Plan-B Pro #34): starts or pauses the focus timer. */
@AndroidEntryPoint
class FocusTileService : TileService() {
    @Inject lateinit var entitlements: EntitlementRepository
    @Inject lateinit var focus: FocusRepository
    @Inject lateinit var controls: FocusControls
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onStartListening() = refresh()

    private fun refresh() {
        val running = runBlocking { runCatching { focus.getActive()?.status == FocusStatus.RUNNING }.getOrDefault(false) }
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(if (running) R.string.tile_focus_running else R.string.tile_focus_idle)
            }
            updateTile()
        }
    }

    override fun onClick() {
        val pro = runBlocking { runCatching { entitlements.current().isPro }.getOrDefault(false) }
        if (!pro) {
            openApp(QuickLinks.pro(ProFeature.QUICK_TILES.id))
            return
        }
        scope.launch {
            runCatching { controls.toggle() }
            requestListeningState(this@FocusTileService, android.content.ComponentName(this@FocusTileService, FocusTileService::class.java))
        }
        // Reflect the change right away; the system re-binds for later updates.
        qsTile?.apply {
            state = if (state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }
}

/**
 * Handles actions from launcher shortcuts that do something before showing a screen
 * (currently "Start focus"). It has no UI of its own and finishes at once.
 */
@AndroidEntryPoint
class QuickActionActivity : androidx.activity.ComponentActivity() {
    @Inject lateinit var entitlements: EntitlementRepository
    @Inject lateinit var controls: FocusControls

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = when (intent?.data?.lastPathSegment) {
            START_FOCUS -> runBlocking {
                if (runCatching { entitlements.current().isPro }.getOrDefault(false)) {
                    runCatching { controls.startIfIdle() }
                    QuickLinks.FOCUS
                } else {
                    QuickLinks.pro(ProFeature.QUICK_TILES.id)
                }
            }
            else -> QuickLinks.TODAY
        }
        startActivity(QuickLinks.intent(this, target))
        finish()
    }

    companion object {
        const val START_FOCUS = "focus-start"
        val START_FOCUS_URI: Uri = "planb://quick/$START_FOCUS".toUri()
    }
}

/**
 * Launcher shortcuts (#34). "New task" and "New note" are static and free (res/xml/shortcuts.xml);
 * the dynamic ones ("Today", "Start focus") are Plan-B Pro and are removed when Pro ends.
 */
@Singleton
class ShortcutPublisher @Inject constructor(@ApplicationContext private val context: Context) {
    fun publish(isPro: Boolean) {
        runCatching {
            if (!isPro) {
                ShortcutManagerCompat.removeAllDynamicShortcuts(context)
                return
            }
            val today = ShortcutInfoCompat.Builder(context, ID_TODAY)
                .setShortLabel(context.getString(R.string.shortcut_today))
                .setLongLabel(context.getString(R.string.shortcut_today_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_today))
                .setIntent(QuickLinks.intent(context, QuickLinks.TODAY))
                .setRank(0)
                .build()
            val focus = ShortcutInfoCompat.Builder(context, ID_FOCUS)
                .setShortLabel(context.getString(R.string.shortcut_focus))
                .setLongLabel(context.getString(R.string.shortcut_focus_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_focus))
                .setIntent(Intent(Intent.ACTION_VIEW, QuickActionActivity.START_FOCUS_URI).setClass(context, QuickActionActivity::class.java))
                .setRank(1)
                .build()
            ShortcutManagerCompat.setDynamicShortcuts(context, listOf(today, focus))
        }
    }

    private companion object {
        const val ID_TODAY = "today"
        const val ID_FOCUS = "start_focus"
    }
}
