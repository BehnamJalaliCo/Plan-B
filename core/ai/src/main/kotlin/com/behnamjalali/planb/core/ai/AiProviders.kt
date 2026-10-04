package com.behnamjalali.planb.core.ai

import androidx.annotation.StringRes

/** The HTTP API shape a provider speaks. */
enum class WireFormat {
    /** `POST {base}/chat/completions` with a Bearer key (OpenAI and compatible gateways). */
    OPENAI_CHAT,

    /** `POST {base}/v1/messages` with `x-api-key` (Anthropic Messages API). */
    ANTHROPIC_MESSAGES,
}

/**
 * A provider the user can pick for the AI assistant. The model is always a free-text field;
 * [suggestedModels] only pre-fills it (providers rename models often, so the assistant UI also
 * offers [AiClient.listModels]). [defaultBaseUrl] null means the user must enter it.
 * [keyUrl] is where the user creates a key. Brand names are not translated; the two generic
 * entries use [labelRes].
 */
data class AiProvider(
    val id: String,
    val wireFormat: WireFormat,
    val defaultBaseUrl: String?,
    val suggestedModels: List<String>,
    val keyUrl: String?,
    val brandName: String? = null,
    @StringRes val labelRes: Int? = null,
) {
    val requiresBaseUrl: Boolean get() = defaultBaseUrl == null
}

/**
 * The catalog, Iranian gateways first. Only base URLs confirmed in the provider's public
 * documentation are listed; any other gateway works through the generic entries.
 */
object AiProviders {
    val IRAN_GATEWAY = AiProvider(
        id = "iran_gateway",
        wireFormat = WireFormat.OPENAI_CHAT,
        defaultBaseUrl = null,
        suggestedModels = emptyList(),
        keyUrl = null,
        labelRes = R.string.ai_provider_iran_gateway,
    )

    /** AvalAI (avalai.ir): OpenAI-compatible, base URL from docs.avalai.ir. */
    val AVALAI = AiProvider(
        id = "avalai",
        wireFormat = WireFormat.OPENAI_CHAT,
        defaultBaseUrl = "https://api.avalai.ir/v1",
        suggestedModels = listOf("gpt-4o-mini", "deepseek-chat"),
        keyUrl = "https://avalai.ir",
        brandName = "AvalAI",
    )

    val DEEPSEEK = AiProvider(
        id = "deepseek",
        wireFormat = WireFormat.OPENAI_CHAT,
        defaultBaseUrl = "https://api.deepseek.com",
        suggestedModels = listOf("deepseek-chat", "deepseek-reasoner"),
        keyUrl = "https://platform.deepseek.com/api_keys",
        brandName = "DeepSeek",
    )

    /** Qwen through Alibaba Cloud Model Studio (DashScope), OpenAI-compatible mode. */
    val QWEN = AiProvider(
        id = "qwen",
        wireFormat = WireFormat.OPENAI_CHAT,
        defaultBaseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
        suggestedModels = listOf("qwen-plus", "qwen-turbo", "qwen-max"),
        keyUrl = "https://www.alibabacloud.com/help/en/model-studio/get-api-key",
        brandName = "Qwen (Alibaba Cloud)",
    )

    val OPENROUTER = AiProvider(
        id = "openrouter",
        wireFormat = WireFormat.OPENAI_CHAT,
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        suggestedModels = listOf("openrouter/auto"),
        keyUrl = "https://openrouter.ai/keys",
        brandName = "OpenRouter",
    )

    val GROQ = AiProvider(
        id = "groq",
        wireFormat = WireFormat.OPENAI_CHAT,
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        suggestedModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
        keyUrl = "https://console.groq.com/keys",
        brandName = "Groq",
    )

    val OPENAI = AiProvider(
        id = "openai",
        wireFormat = WireFormat.OPENAI_CHAT,
        defaultBaseUrl = "https://api.openai.com/v1",
        suggestedModels = listOf("gpt-4o-mini", "gpt-4.1-mini"),
        keyUrl = "https://platform.openai.com/api-keys",
        brandName = "OpenAI",
    )

    /** Anthropic's native Messages API; pick a model from the provider's model list. */
    val ANTHROPIC = AiProvider(
        id = "anthropic",
        wireFormat = WireFormat.ANTHROPIC_MESSAGES,
        defaultBaseUrl = "https://api.anthropic.com",
        suggestedModels = emptyList(),
        keyUrl = "https://console.anthropic.com/settings/keys",
        brandName = "Anthropic",
    )

    /** Google Gemini through its OpenAI-compatible endpoint. */
    val GEMINI = AiProvider(
        id = "gemini",
        wireFormat = WireFormat.OPENAI_CHAT,
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        suggestedModels = listOf("gemini-2.5-flash", "gemini-2.0-flash"),
        keyUrl = "https://aistudio.google.com/apikey",
        brandName = "Google Gemini",
    )

    val CUSTOM = AiProvider(
        id = "custom",
        wireFormat = WireFormat.OPENAI_CHAT,
        defaultBaseUrl = null,
        suggestedModels = emptyList(),
        keyUrl = null,
        labelRes = R.string.ai_provider_custom,
    )

    val all: List<AiProvider> = listOf(IRAN_GATEWAY, AVALAI, DEEPSEEK, QWEN, OPENROUTER, GROQ, OPENAI, ANTHROPIC, GEMINI, CUSTOM)

    fun byId(id: String?): AiProvider? = all.firstOrNull { it.id == id }
}
