package com.behnamjalali.planb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerHeroSurface
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.ui.PlannerLocals
import kotlinx.coroutines.launch

private data class OnboardingPage(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: Int, val body: Int)

private val pages = listOf(
    OnboardingPage(Icons.Rounded.AutoAwesome, R.string.onboarding_1_title, R.string.onboarding_1_body),
    OnboardingPage(Icons.Rounded.Shield, R.string.onboarding_2_title, R.string.onboarding_2_body),
    OnboardingPage(Icons.Rounded.NotificationsActive, R.string.onboarding_3_title, R.string.onboarding_3_body),
)

/** Three short pages; skippable at any time. No account or permissions are requested here. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val numbers = PlannerLocals.numbers
    Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(Spacing.screen)) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDone, modifier = Modifier.testTag("onboarding_skip")) { Text(stringResource(R.string.onboarding_skip)) }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                val page = pages[index]
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    PlannerHeroSurface(Modifier.size(180.dp)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(page.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.hero))
                        }
                    }
                    Spacer(Modifier.height(Spacing.xxxl))
                    Text(stringResource(page.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Spacing.md))
                    Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val pageLabel = stringResource(R.string.onboarding_page, numbers.format(pager.currentPage + 1), numbers.format(pages.size))
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.lg).semantics { contentDescription = pageLabel },
                horizontalArrangement = Arrangement.Center,
            ) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier.padding(Spacing.xs).size(if (i == pager.currentPage) 10.dp else 8.dp)
                            .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                }
            }
            val last = pager.currentPage == pages.lastIndex
            PlannerButton(
                stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next),
                { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                Modifier.fillMaxWidth(),
            )
        }
    }
}
