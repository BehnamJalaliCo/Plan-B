package com.behnamjalali.planb.wear

import com.behnamjalali.planb.core.common.ApplicationScope
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Receives the watch's requests (Plan-B Pro #35). Google Play services binds this service only
 * on devices that have it, and the Data Layer only delivers messages from the app with the same
 * package name and signing key, so no other app can talk to it.
 */
@AndroidEntryPoint
class PhoneWearListenerService : WearableListenerService() {
    @Inject lateinit var sync: WearSync
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        val data = event.data
        scope.launch {
            if (path == WearProtocol.PATH_REFRESH) sync.publish() else sync.handle(path, data)
        }
    }
}
