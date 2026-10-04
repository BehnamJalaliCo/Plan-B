package com.behnamjalali.planb.feature.pro

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.ProAccess
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate
import com.behnamjalali.planb.core.ui.ProGuard
import com.behnamjalali.planb.core.ui.rememberProGuard
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class ProGateTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun gate_showsContentToProUsers_andTheTeaserToOthers() {
        val opened = mutableListOf<ProFeature?>()
        compose.setContent { PlanBTheme {
            CompositionLocalProvider(LocalProAccess provides ProAccess(isPro = false) { opened += it }) {
                ProGate(ProFeature.EISENHOWER) { Text("matrix") }
            }
        } }
        compose.onNodeWithText("matrix").assertDoesNotExist()
        compose.onNodeWithText("Eisenhower matrix").assertExists()
        compose.onNodeWithText("Unlock with Pro").performClick()
        assertThat(opened).containsExactly(ProFeature.EISENHOWER)
    }

    @Test
    fun gate_proUser_seesContent() {
        compose.setContent { PlanBTheme {
            CompositionLocalProvider(LocalProAccess provides ProAccess(isPro = true) {}) {
                ProGate(ProFeature.EISENHOWER) { Text("matrix") }
            }
        } }
        compose.onNodeWithText("matrix").assertExists()
    }

    @Test
    fun guard_runsForPro_andOpensThePaywallOtherwise() {
        var pro = false
        val opened = mutableListOf<ProFeature?>()
        var ran = 0
        lateinit var guard: ProGuard
        compose.setContent { PlanBTheme {
            CompositionLocalProvider(LocalProAccess provides ProAccess(isPro = pro) { opened += it }) { guard = rememberProGuard() }
        } }
        compose.runOnIdle { guard.run(ProFeature.TIME_BLOCKING) { ran++ } }
        assertThat(ran).isEqualTo(0)
        assertThat(opened).containsExactly(ProFeature.TIME_BLOCKING)
    }

    @Test
    fun catalog_hasFortyNumberedFeaturesWithStableIds() {
        assertThat(ProFeature.entries.map { it.number }).isEqualTo((1..40).toList())
        assertThat(ProFeature.entries.map { it.id }.toSet()).hasSize(40)
        assertThat(ProFeature.fromId("journal")).isEqualTo(ProFeature.JOURNAL)
    }
}
