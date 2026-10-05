package com.behnamjalali.planb.e2e

import com.behnamjalali.planb.core.ai.AiApi
import com.behnamjalali.planb.core.ai.AiApiModule
import com.behnamjalali.planb.core.ai.AiEndpoint
import com.behnamjalali.planb.core.ai.AiMessage
import com.behnamjalali.planb.core.ai.AiModule
import com.behnamjalali.planb.core.ai.AiResult
import com.behnamjalali.planb.core.ai.AiStreamEvent
import com.behnamjalali.planb.core.ai.SecretCipher
import com.behnamjalali.planb.core.speech.FakeVoiceDictation
import com.behnamjalali.planb.core.speech.SpeechModule
import com.behnamjalali.planb.core.speech.VoiceDictation
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Robolectric has no Android Keystore: AES-GCM with a fixed in-memory key. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AiModule::class])
object TestAiCipherModule {
    @Provides
    fun provideCipher(): SecretCipher = object : SecretCipher {
        private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        override fun encrypt(plain: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
            return c.iv + c.doFinal(plain)
        }
        override fun decrypt(sealed: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12)) }
            return c.doFinal(sealed, 12, sealed.size - 12)
        }
    }
}

/** JVM app tests never reach a network: a provider that answers from a script. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AiApiModule::class])
object TestAiApiModule {
    @Provides
    @Singleton
    fun provideFake(): FakeAiApi = FakeAiApi()

    @Provides
    fun provideApi(fake: FakeAiApi): AiApi = fake
}

/** No speech service under Robolectric: a scripted recognizer tests can configure. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SpeechModule::class])
object TestSpeechModule {
    @Provides
    @Singleton
    fun provideFake(): FakeVoiceDictation = FakeVoiceDictation()

    @Provides
    fun provideDictation(fake: FakeVoiceDictation): VoiceDictation = fake
}

/**
 * Answers like a provider would, in the language of the request, recognizing what is asked by
 * the prompt's own instructions. Replies are streamed in a few pieces.
 */
class FakeAiApi : AiApi {
    /** Every request's messages, newest last (never anything but what the app chose to send). */
    val requests = mutableListOf<List<AiMessage>>()

    override suspend fun chat(endpoint: AiEndpoint, messages: List<AiMessage>, maxTokens: Int): AiResult<String> {
        requests += messages
        return AiResult.Success(reply(messages))
    }

    override fun stream(endpoint: AiEndpoint, messages: List<AiMessage>, maxTokens: Int): Flow<AiStreamEvent> = flow {
        requests += messages
        reply(messages).chunked(24).forEach { emit(AiStreamEvent.Delta(it)) }
        emit(AiStreamEvent.Done)
    }

    override suspend fun testConnection(endpoint: AiEndpoint): AiResult<Unit> = AiResult.Success(Unit)

    override suspend fun listModels(endpoint: AiEndpoint): AiResult<List<String>> = AiResult.Success(listOf("gpt-4o-mini", "deepseek-chat", "gemini-2.5-flash"))

    private fun reply(messages: List<AiMessage>): String {
        val prompt = messages.last().text
        val persian = messages.first().text.contains("Reply in Persian")
        fun t(fa: String, en: String) = if (persian) fa else en
        return when {
            "\"plan\"" in prompt -> {
                val ids = Regex("^- (\\d+) \\|", RegexOption.MULTILINE).findAll(prompt).map { it.groupValues[1] }.toList()
                val day = Regex("Days that may be planned: (\\S+?)[,\\n]").find(prompt)?.groupValues?.get(1).orEmpty()
                val slots = listOf("11:15" to "12:00", "15:00" to "15:45", "16:00" to "16:30", "16:45" to "17:15")
                val why = listOf(t("مهلتش نزدیک است", "deadline is close"), t("اولویت بالا", "high priority"), t("کار کوتاه", "a short task"), t("بعدازظهر آرام", "a calm afternoon"))
                val items = ids.take(slots.size).mapIndexed { i, id ->
                    """{"id": $id, "date": "$day", "start": "${slots[i].first}", "end": "${slots[i].second}", "why": "${why[i]}"}"""
                }
                "```json\n{\"plan\": [${items.joinToString(", ")}]}\n```"
            }
            "Break this task" in prompt -> t(
                """{"items": ["فهرست مهمان‌ها را بنویس", "سالن را رزرو کن", "دعوت‌نامه‌ها را بفرست", "منوی پذیرایی را نهایی کن"]}""",
                """{"items": ["Write the guest list", "Book the venue", "Send the invitations", "Settle the menu"]}""",
            )
            "action items" in prompt -> t(
                """{"items": ["حالت تمرکز با پومودورو را طراحی کن", "قالب گزارش هفتگی را بنویس", "از کاربران بازخورد بگیر"]}""",
                """{"items": ["Design the Pomodoro focus mode", "Draft the weekly report layout", "Ask users for feedback"]}""",
            )
            "Suggest 4 short" in prompt -> t(
                """{"items": ["ایده‌های حالت تمرکز", "تمرکز و گزارش هفتگی", "نقشهٔ راه محصول", "ایده‌های تازه"]}""",
                """{"items": ["Focus mode ideas", "Focus and weekly reports", "Product roadmap", "Fresh ideas"]}""",
            )
            "Summarize this" in prompt -> t(
                "- یک حالت تمرکز با تایمر پومودورو\n- گزارش هفتگی از پیشرفت کاربر",
                "- A focus mode with a Pomodoro timer\n- A weekly report of the user's progress",
            )
            "Translate this" in prompt -> t("Translated text.", "متن ترجمه‌شده.")
            "Rewrite this" in prompt || "Continue writing" in prompt -> t("متنی روان‌تر و کوتاه‌تر.", "A clearer, shorter text.")
            else -> t(
                "اول «آماده‌سازی ارائهٔ فصلی» را تمام کنید؛ اولویتش بالاست و دو زیرکارش مانده. بعد از ناهار با سارا، «تماس با Dr. Rahimi دربارهٔ API» را انجام دهید. «مرور گزارش هفتگی» از دیروز مانده و می‌تواند تا فردا صبر کند.",
                "Finish \"Prepare quarterly presentation\" first: it is high priority and two subtasks are left. After lunch with Sara, make the call to Dr. Rahimi. \"Review weekly report\" is from yesterday and can wait until tomorrow.",
            )
        }
    }
}
