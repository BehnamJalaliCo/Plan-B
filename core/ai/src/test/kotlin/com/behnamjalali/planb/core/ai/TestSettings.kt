package com.behnamjalali.planb.core.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** A settings repository on a temporary file with an in-memory cipher. */
internal class TestSettings : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("ai-settings").toFile()
    val repository = AiSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "ai.preferences_pb") }, TestCipher(), FakeTimeProvider())

    /** A complete, enabled configuration. */
    suspend fun configure() {
        repository.setProvider(AiProviders.DEEPSEEK)
        repository.setApiKey("sk-test")
        repository.enableWithConsent()
    }

    override fun close() {
        scope.cancel()
        dir.deleteRecursively()
    }
}
