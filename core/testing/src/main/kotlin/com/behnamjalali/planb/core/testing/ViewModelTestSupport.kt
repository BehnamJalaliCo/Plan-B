package com.behnamjalali.planb.core.testing

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces Dispatchers.Main with a real single-thread dispatcher. ViewModel flows
 * (debounce, todayFlow's minute ticker) then run on wall-clock time, which plays
 * well with Room's own query executors; tests wait with [awaitItem].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealMainDispatcherRule : TestWatcher() {
    private lateinit var executor: ExecutorService
    lateinit var dispatcher: CoroutineDispatcher
        private set

    /** Scope on the main dispatcher for keeping StateFlows subscribed during a test. */
    lateinit var scope: CoroutineScope
        private set

    override fun starting(description: Description) {
        executor = Executors.newSingleThreadExecutor { r -> Thread(r, "test-main").apply { isDaemon = true } }
        dispatcher = executor.asCoroutineDispatcher()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        scope.coroutineContext[Job]?.cancel()
        Dispatchers.resetMain()
        executor.shutdownNow()
    }

    /** Keeps [flow] collected (e.g. a WhileSubscribed StateFlow) until the test ends. */
    fun keepCollecting(flow: Flow<*>): Job = scope.launch { flow.collect {} }
}

/** Waits (real time) until [flow] emits a value matching [predicate]. */
suspend fun <T> Flow<T>.awaitItem(timeoutMillis: Long = 5_000, predicate: (T) -> Boolean): T =
    withTimeout(timeoutMillis) { first(predicate) }
