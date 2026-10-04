package com.behnamjalali.planb.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.behnamjalali.planb.core.data.platform.AppIconSwitcher
import com.behnamjalali.planb.core.model.AppIcon
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alternate launcher icons (Plan-B Pro #33) through the activity aliases in the manifest
 * (`.LauncherClassic`, `.LauncherOcean`, …). Exactly one alias is enabled. Components are
 * switched with [PackageManager.DONT_KILL_APP] so the running app is not stopped; the new
 * alias is enabled before the old one is disabled, so the app always has a launcher entry.
 */
@Singleton
class LauncherIconSwitcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val afterSwitch: IconSwitchHook,
) : AppIconSwitcher {
    private val pm: PackageManager get() = context.packageManager

    override fun current(): AppIcon = AppIcon.entries.firstOrNull { icon ->
        when (pm.getComponentEnabledSetting(component(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            // Untouched components keep their manifest state: only the classic alias is enabled.
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.CLASSIC
            else -> false
        }
    } ?: AppIcon.CLASSIC

    override fun apply(icon: AppIcon) {
        if (icon == current()) return
        setEnabled(icon, true)
        AppIcon.entries.filter { it != icon }.forEach { setEnabled(it, false) }
        // Dynamic shortcuts belong to the launcher entry; publish them again for the new one.
        afterSwitch.onSwitched()
    }

    private fun setEnabled(icon: AppIcon, enabled: Boolean) {
        val state = when {
            // Back to the manifest default for the classic alias, so a reinstall looks the same.
            icon == AppIcon.CLASSIC && enabled -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        pm.setComponentEnabledSetting(component(icon), state, PackageManager.DONT_KILL_APP)
    }

    private fun component(icon: AppIcon) = ComponentName(context.packageName, aliasClass(icon))

    companion object {
        /** Alias names are relative to the namespace, not the (debug-suffixed) application id. */
        fun aliasClass(icon: AppIcon): String = "com.behnamjalali.planb.Launcher" + when (icon) {
            AppIcon.CLASSIC -> "Classic"
            AppIcon.OCEAN -> "Ocean"
            AppIcon.SUNSET -> "Sunset"
            AppIcon.FOREST -> "Forest"
            AppIcon.MIDNIGHT -> "Midnight"
        }
    }
}

/** Runs after the launcher entry changed (re-publishes the dynamic launcher shortcuts). */
fun interface IconSwitchHook {
    fun onSwitched()
}
