package com.behnamjalali.planb.core.focus

import android.content.Context
import android.content.Intent
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.FocusSessionEffects
import com.behnamjalali.planb.core.model.AmbientSound
import com.behnamjalali.planb.core.model.FocusSession
import com.behnamjalali.planb.core.model.FocusStatus
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the Focus screen needs from Focus Pro's platform side (#26); faked in tests. */
interface FocusProControls {
    /** Whether Plan-B may change Do Not Disturb. */
    fun hasDndAccess(): Boolean

    /** Opens the system screen where the user allows Do Not Disturb access. */
    fun dndAccessIntent(): Intent

    /** Brings sound and Do Not Disturb in line with the active session (after a permission change, at start). */
    suspend fun refresh()

    /** Plays [sound] for a few seconds so the user can hear it (null stops). */
    fun preview(sound: AmbientSound?, volume: Int)
}

/**
 * Runs on every change of the active focus session ([FocusSessionEffects]): strict mode's Do Not
 * Disturb ([StrictModeCoordinator]) and the ambient sound service ([FocusSoundService], started
 * when a running session has a sound; it stops itself otherwise). [refresh] does the same for the
 * session in the database, for app start and after the user granted Do Not Disturb access.
 */
@Singleton
class FocusProEffects @Inject constructor(
    @ApplicationContext private val context: Context,
    private val strict: StrictModeCoordinator,
    private val dnd: DndController,
    private val focus: Lazy<FocusRepository>,
) : FocusSessionEffects, FocusProControls {
    private val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val previewPlayer = AmbientPlayer()
    private val previewToken = AtomicInteger()

    override suspend fun onSessionChanged(active: FocusSession?) {
        strict.apply(active)
        if (active != null && active.status == FocusStatus.RUNNING && AmbientSound.fromId(active.soundId) != null) {
            previewPlayer.stop()
            FocusSoundService.start(context)
        }
    }

    override fun hasDndAccess(): Boolean = dnd.hasAccess()

    override fun dndAccessIntent(): Intent = SystemDndController.accessSettingsIntent()

    override suspend fun refresh() = onSessionChanged(focus.get().getActive())

    override fun preview(sound: AmbientSound?, volume: Int) {
        val token = previewToken.incrementAndGet()
        if (sound == null) {
            previewPlayer.stop()
            return
        }
        previewPlayer.play(sound, volume)
        previewScope.launch {
            delay(PREVIEW_MILLIS)
            if (previewToken.get() == token) previewPlayer.stop()
        }
    }

    private companion object {
        const val PREVIEW_MILLIS = 6_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FocusProModule {
    @Binds abstract fun effects(impl: FocusProEffects): FocusSessionEffects
    @Binds abstract fun controls(impl: FocusProEffects): FocusProControls
    @Binds abstract fun dnd(impl: SystemDndController): DndController
}
