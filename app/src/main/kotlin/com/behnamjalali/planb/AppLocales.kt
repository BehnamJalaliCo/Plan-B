package com.behnamjalali.planb

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Application language handling (Persian is the default on a fresh install). */
object AppLocales {
    const val DEFAULT_LANGUAGE = "fa"

    /** Applies Persian unless the user (or the system per-app setting) already chose a language. */
    fun applyDefaultIfUnset(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // AppCompatDelegate needs an attached activity on API 33+, which does not exist in
            // Application.onCreate, so talk to the platform LocaleManager directly.
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            if (manager.applicationLocales.isEmpty) manager.applicationLocales = LocaleList.forLanguageTags(DEFAULT_LANGUAGE)
        } else if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(DEFAULT_LANGUAGE))
        }
    }

    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty()
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
}
