package com.behnamjalali.planb

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AppLocalesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(LocaleManager::class.java)

    @Test
    fun freshInstall_defaultsToPersian() {
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        AppLocales.applyDefaultIfUnset(context)
        assertThat(AppLocales.current(context)).isEqualTo("fa")
    }

    @Test
    fun userChoice_isNotOverridden() {
        manager.applicationLocales = LocaleList.forLanguageTags("en")
        AppLocales.applyDefaultIfUnset(context)
        assertThat(AppLocales.current(context)).isEqualTo("en")
    }
}
