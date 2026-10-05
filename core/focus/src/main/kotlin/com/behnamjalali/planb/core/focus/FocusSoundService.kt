package com.behnamjalali.planb.core.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.AmbientSound
import com.behnamjalali.planb.core.model.FocusStatus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Plays a focus session's ambient sound (Plan-B Pro #26) while the session runs, also with the
 * screen off: a foreground service of type `mediaPlayback` with a quiet notification (tap: open
 * Focus; "Stop sound": silence for this session). It follows the active session in the database
 * and stops itself as soon as there is no running session with a sound (paused, finished,
 * cancelled, completed by the end alarm). Started only from the foreground (see
 * [FocusSoundController]); the audio focus is respected (muted while another app plays).
 */
@AndroidEntryPoint
class FocusSoundService : Service() {
    @Inject lateinit var focus: FocusRepository
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = AmbientPlayer()
    private var follow: Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private var shown: AmbientSound? = null

    /** Resources in the app's language (before Android 13 a service does not get the per-app language). */
    private var localized: Context = this

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startInForeground(shown)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP_SOUND) scope.launch { runCatching { focus.setSound(null) } }
        if (follow == null) follow = scope.launch { followSession() }
        return START_STICKY
    }

    private suspend fun followSession() {
        combine(focus.observeActive(), settings.settings.map { it.focusPro.volume to it.language.tag }.distinctUntilChanged()) { session, (volume, language) ->
            val sound = AmbientSound.fromId(session?.soundId)?.takeIf { session?.status == FocusStatus.RUNNING }
            Triple(sound, volume, language)
        }.distinctUntilChanged().collect { (sound, volume, language) ->
            localized = localizedContext(language)
            if (sound == null) {
                player.stop()
                abandonFocus()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                requestFocus()
                player.play(sound, volume)
                if (sound != shown) startInForeground(sound)
            }
        }
    }

    private fun startInForeground(sound: AmbientSound?): Boolean = try {
        ensureChannel(localized)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(sound),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        shown = sound
        true
    } catch (e: RuntimeException) {
        // Not allowed from the background (Android 12+), or the type is missing: no sound then.
        Log.w(TAG, "Focus sound not started (${e.javaClass.simpleName})")
        false
    }

    private fun notification(sound: AmbientSound?): Notification {
        val open = Intent(Intent.ACTION_VIEW, FOCUS_URI.toUri()).setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val stop = Intent(this, FocusSoundService::class.java).setAction(ACTION_STOP_SOUND)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_focus_sound)
            .setContentTitle(localized.getString(R.string.focus_sound_notification_title))
            .setContentText(sound?.let { localized.getString(it.label()) } ?: localized.getString(R.string.focus_sound_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(PendingIntent.getActivity(this, NOTIFICATION_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(0, localized.getString(R.string.focus_sound_stop), PendingIntent.getService(this, NOTIFICATION_ID + 1, stop, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
    }

    // Both languages are always installed: the app bundle disables language splits.
    @android.annotation.SuppressLint("AppBundleLocaleChanges")
    private fun localizedContext(tag: String): Context = createConfigurationContext(
        android.content.res.Configuration(resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(tag)) },
    )

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        player.setMuted(change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val audio = getSystemService(AudioManager::class.java) ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(true)
            .build()
        focusRequest = request
        player.setMuted(audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_FAILED)
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request)
        focusRequest = null
    }

    override fun onDestroy() {
        player.stop()
        abandonFocus()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlanB"
        const val CHANNEL = "focus_sound"

        /** Slot 0 of id 4 in the `id × 8 + code` notification scheme (free; 8, 16, 24 are rituals and the journal). */
        const val NOTIFICATION_ID = 32
        private const val ACTION_STOP_SOUND = "com.behnamjalali.planb.focus.STOP_SOUND"
        private const val FOCUS_URI = "planb://open/focus"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.focus_sound_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.focus_sound_channel_desc)
                    setShowBadge(false)
                },
            )
        }

        /** Starts the service; false when the system does not allow it right now (app in the background). */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, FocusSoundService::class.java))
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Focus sound service not started (${e.javaClass.simpleName})")
            false
        }
    }
}

/** The name of an ambient sound. */
fun AmbientSound.label(): Int = when (this) {
    AmbientSound.RAIN -> R.string.focus_sound_rain
    AmbientSound.OCEAN -> R.string.focus_sound_ocean
    AmbientSound.BROWN -> R.string.focus_sound_brown
    AmbientSound.PINK -> R.string.focus_sound_pink
    AmbientSound.WHITE -> R.string.focus_sound_white
}
