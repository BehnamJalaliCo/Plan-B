package com.behnamjalali.planb.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Today's tasks (tap to complete) and habits (tap to check in), from the phone over the Data
 * Layer. All calls are wrapped: without the phone app or Play services the screen simply waits.
 */
class WearActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private val state = MutableStateFlow<WearState>(WearState.Waiting)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val current by state.collectAsStateWithLifecycle()
            MaterialTheme { WearApp(current, ::completeTask, ::toggleHabit) }
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching { Wearable.getDataClient(this).addListener(this) }
        lifecycleScope.launch {
            runCatching {
                val items = Wearable.getDataClient(this@WearActivity).getDataItems("wear://*${WearKeys.PATH_TODAY}".toUri()).await()
                try {
                    items.firstOrNull()?.let { state.value = parse(DataMapItem.fromDataItem(it).dataMap) }
                } finally {
                    items.release()
                }
            }
            send(WearKeys.PATH_REFRESH, "")
        }
    }

    override fun onPause() {
        runCatching { Wearable.getDataClient(this).removeListener(this) }
        super.onPause()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WearKeys.PATH_TODAY }
            .lastOrNull()
            ?.let { state.value = parse(DataMapItem.fromDataItem(it.dataItem).dataMap) }
    }

    private fun completeTask(id: Long) {
        (state.value as? WearState.Ready)?.let { state.value = it.completeTask(id) }
        lifecycleScope.launch { send(WearKeys.PATH_COMPLETE_TASK, id.toString()) }
    }

    private fun toggleHabit(id: Long) {
        (state.value as? WearState.Ready)?.let { state.value = it.toggleHabit(id) }
        lifecycleScope.launch { send(WearKeys.PATH_CHECK_HABIT, id.toString()) }
    }

    private suspend fun send(path: String, payload: String) {
        runCatching {
            val nodes = Wearable.getNodeClient(this).connectedNodes.await()
            val messages = Wearable.getMessageClient(this)
            nodes.forEach { messages.sendMessage(it.id, path, payload.toByteArray()).await() }
        }
    }

    private fun parse(map: DataMap): WearState {
        val rtl = map.getBoolean(WearKeys.RTL)
        if (!map.getBoolean(WearKeys.PRO)) return WearState.Locked(map.getString(WearKeys.LABEL_LOCKED).orEmpty(), rtl)
        fun items(key: String) = map.getDataMapArrayList(key).orEmpty().map {
            WearItem(it.getLong(WearKeys.ID), it.getString(WearKeys.TITLE).orEmpty(), it.getBoolean(WearKeys.DONE))
        }
        return WearState.Ready(
            tasks = items(WearKeys.TASKS),
            habits = items(WearKeys.HABITS),
            tasksLabel = map.getString(WearKeys.LABEL_TASKS).orEmpty(),
            habitsLabel = map.getString(WearKeys.LABEL_HABITS).orEmpty(),
            emptyLabel = map.getString(WearKeys.LABEL_EMPTY).orEmpty(),
            rtl = rtl,
        )
    }
}

@Composable
fun WearApp(state: WearState, onCompleteTask: (Long) -> Unit, onToggleHabit: (Long) -> Unit) {
    val rtl = when (state) {
        is WearState.Ready -> state.rtl
        is WearState.Locked -> state.rtl
        WearState.Waiting -> false
    }
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        ScalingLazyColumn(Modifier.fillMaxSize()) {
            when (state) {
                WearState.Waiting -> item { Message(stringResource(R.string.wear_waiting)) }
                is WearState.Locked -> item { Message(state.message) }
                is WearState.Ready -> {
                    item { ListHeader { Text(state.tasksLabel) } }
                    if (state.tasks.isEmpty()) item { Message(state.emptyLabel) }
                    items(state.tasks, key = { "t${it.id}" }) { task ->
                        CheckboxButton(
                            checked = false,
                            onCheckedChange = { onCompleteTask(task.id) },
                            label = { Text(task.title, maxLines = 2) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.habits.isNotEmpty()) {
                        item { ListHeader { Text(state.habitsLabel) } }
                        items(state.habits, key = { "h${it.id}" }) { habit ->
                            CheckboxButton(
                                checked = habit.done,
                                onCheckedChange = { onToggleHabit(habit.id) },
                                label = { Text(habit.title, maxLines = 2) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
}
