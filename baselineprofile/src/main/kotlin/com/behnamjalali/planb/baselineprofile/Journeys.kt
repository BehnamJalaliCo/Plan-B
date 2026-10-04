package com.behnamjalali.planb.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE_NAME = "com.behnamjalali.planb"
private const val TIMEOUT_MS = 5_000L

/** Skips onboarding on a fresh install (no-op once it has been completed). */
fun MacrobenchmarkScope.skipOnboarding() {
    device.wait(Until.hasObject(By.res("onboarding_skip")), TIMEOUT_MS)
    device.findObject(By.res("onboarding_skip"))?.click()
    device.wait(Until.hasObject(By.res("nav_today")), TIMEOUT_MS)
}

private fun MacrobenchmarkScope.openTab(tag: String) {
    device.findObject(By.res(tag))?.click()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.flingMainList() {
    val list = device.findObject(By.scrollable(true)) ?: return
    // Keep the gesture away from the system navigation area.
    list.setGestureMargin(device.displayWidth / 5)
    list.fling(Direction.DOWN)
    device.waitForIdle()
    list.fling(Direction.UP)
    device.waitForIdle()
}

/** The critical user journeys: start-up, every top-level tab, list scrolling and capture. */
fun MacrobenchmarkScope.criticalJourneys() {
    skipOnboarding()
    flingMainList()
    for (tab in listOf("nav_calendar", "nav_tasks", "nav_notebooks", "nav_more", "nav_today")) {
        openTab(tab)
        flingMainList()
    }
    device.findObject(By.res("quick_capture"))?.let {
        it.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
}
