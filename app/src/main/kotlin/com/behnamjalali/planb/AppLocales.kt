package com.behnamjalali.planb

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.behnamjalali.planb.core.model.AppLanguage

/**
 * Application language handling. The stored preference (DataStore) is the source of truth;
 * the platform per-app locale only mirrors it. Persian is the default on a fresh install
 * because it is the preference's default.
 *
 * The platform list is "empty" in two situations that must not overwrite the preference:
 * before API 33 AppCompat has not loaded its stored locales yet in Application.onCreate, and
 * on API 33+ the user may pick "System default" in the system's per-app language settings.
 */
object AppLocales {
    /** At process start: makes the platform locale follow the stored [language] where needed. */
    fun applyStartupLanguage(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // AppCompatDelegate needs an attached activity on API 33+, which does not exist in
            // Application.onCreate, so talk to the platform LocaleManager directly. A language
            // chosen in the system settings is kept (MainActivity stores it); "System default"
            // (an empty list) falls back to the stored language.
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            if (manager.applicationLocales.isEmpty) manager.applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            // Before API 33 only the app can change its language, and every change is stored,
            // so the stored language is always right. AppCompat's own list cannot be read yet.
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }

    /** Applies [language] to the running app (activities are recreated) if it is not already active. */
    fun apply(context: Context, language: AppLanguage) {
        if (platformLanguage(context) == language) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }

    /** The language the platform currently applies to the app, or null when it set none. */
    fun platformLanguage(context: Context): AppLanguage? =
        current(context).split(',').firstOrNull()?.takeIf { it.isNotBlank() }?.let(AppLanguage::fromTag)

    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty()
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
}
