package com.behnamjalali.planb.core.testing

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

    private val viewModels = ViewModelStore()
    private var viewModelCount = 0
    private val viewModelJobs = mutableListOf<Job>()

    /**
     * Registers [viewModel] so it is cleared (viewModelScope cancelled, onCleared run) by
     * [clearViewModels]; otherwise its coroutines would outlive the test and touch
     * Dispatchers.Main after it is reset.
     */
    fun <T : ViewModel> track(viewModel: T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = viewModel as V
        }
        viewModelJobs += viewModel.viewModelScope.coroutineContext.job
        return ViewModelProvider(viewModels, factory)["vm${viewModelCount++}", viewModel.javaClass]
    }

    /** Clears tracked ViewModels; call before closing the database in @After. */
    fun clearViewModels() = viewModels.clear()

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
        clearViewModels()
        val scopeJob = scope.coroutineContext.job
        scopeJob.cancel()
        // Cancelled coroutines still resume on Dispatchers.Main to finish; resetting it
        // while one does fails with "Dispatchers.Main is used concurrently with setting it".
        runBlocking { withTimeoutOrNull(10_000) { (viewModelJobs + scopeJob).joinAll() } }
        viewModelJobs.clear()
        Dispatchers.resetMain()
        executor.shutdownNow()
    }

    /** Keeps [flow] collected (e.g. a WhileSubscribed StateFlow) until the test ends. */
    fun keepCollecting(flow: Flow<*>): Job = scope.launch { flow.collect {} }
}

/** Waits (real time, generous for slow CI machines) until [flow] emits a value matching [predicate]. */
suspend fun <T> Flow<T>.awaitItem(timeoutMillis: Long = 20_000, predicate: (T) -> Boolean): T =
    withTimeout(timeoutMillis) { first(predicate) }
