package com.behnamjalali.planb.navigation

import android.content.Context
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Serializable internal data object StartScreen
@Serializable internal data object ListScreen
@Serializable internal data object DetailScreen

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ScreenNavigatorTest {
    private val navController = NavHostController(ApplicationProvider.getApplicationContext<Context>()).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = StartScreen) {
            composable<StartScreen> {}
            composable<ListScreen> {}
            composable<DetailScreen> {}
        }
    }

    private fun routes() = navController.currentBackStack.value.mapNotNull { entry ->
        when {
            entry.destination.hasRoute<StartScreen>() -> "start"
            entry.destination.hasRoute<ListScreen>() -> "list"
            entry.destination.hasRoute<DetailScreen>() -> "detail"
            else -> null
        }
    }
    private fun current() = ScreenNavigator(navController, navController.currentBackStackEntry!!)

    @Test
    fun doubleTap_pushesTheDestinationOnce() {
        val start = current()
        start.navigate(ListScreen)
        start.navigate(ListScreen)
        assertThat(routes()).containsExactly("start", "list").inOrder()
    }

    @Test
    fun doubleBack_popsOnlyTheScreenItCameFrom() {
        navController.navigate(ListScreen)
        current().navigate(DetailScreen)
        val detail = current()
        detail.back()
        detail.back()
        assertThat(routes()).containsExactly("start", "list").inOrder()
    }

    @Test
    fun back_neverPopsTheStartDestination() {
        navController.navigate(ListScreen)
        val list = current()
        list.back()
        current().back()
        current().back()
        assertThat(routes()).containsExactly("start")
    }
}
