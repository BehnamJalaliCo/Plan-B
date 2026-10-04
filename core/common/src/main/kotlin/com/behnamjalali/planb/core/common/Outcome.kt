package com.behnamjalali.planb.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and converts failures into [Result] without swallowing
 * coroutine cancellation.
 */
suspend inline fun <T> runCatchingSafely(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
