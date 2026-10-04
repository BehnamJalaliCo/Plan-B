package com.behnamjalali.planb.core.data.platform

import com.behnamjalali.planb.core.model.AppIcon

/**
 * Switches the launcher icon (Plan-B Pro #33). Implemented in the app with activity aliases;
 * the enabled alias is the source of truth, so nothing is stored or exported.
 */
interface AppIconSwitcher {
    fun current(): AppIcon

    /** Enables [icon]'s launcher entry and disables the others, without killing the app. */
    fun apply(icon: AppIcon)
}

/**
 * Refreshes home-screen widgets (Plan-B Pro #32) after data changes. Implemented in the app
 * (Glance); [com.behnamjalali.planb.core.data.platform.DataChangeWatcher] calls it whenever a
 * table shown by a widget changes, whatever repository wrote it (including restores).
 */
fun interface WidgetUpdater {
    fun requestUpdate()
}
