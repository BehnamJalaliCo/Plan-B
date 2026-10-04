package com.behnamjalali.planb.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps [action] so it runs at most once for as long as the caller stays composed. Detail screens
 * use it for leaving: deleting an item both emits a Deleted event and makes the item missing, and
 * each of those would otherwise pop the back stack.
 */
@Composable
fun rememberOnce(action: () -> Unit): () -> Unit {
    val current by rememberUpdatedState(action)
    val done = remember { AtomicBoolean(false) }
    return remember { { if (done.compareAndSet(false, true)) current() } }
}
