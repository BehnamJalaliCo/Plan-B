package com.behnamjalali.planb

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Application language handling (Persian is the default on a fresh install). */
object AppLocales {
    const val DEFAULT_LANGUAGE = "fa"

    /** Applies Persian unless the user (or the system per-app setting) already chose a language. */
    fun applyDefaultIfUnset() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(DEFAULT_LANGUAGE))
        }
    }
}
