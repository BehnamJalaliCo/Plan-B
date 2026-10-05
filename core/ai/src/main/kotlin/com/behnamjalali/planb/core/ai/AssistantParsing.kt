package com.behnamjalali.planb.core.ai

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** One proposed time block as the assistant wrote it; [AiPlanItem]s are checked by the app before use. */
data class AiPlanItem(val taskId: Long, val date: String?, val start: String, val end: String, val why: String? = null)

/**
 * Reads structured answers tolerantly: models wrap JSON in Markdown fences, add a sentence
 * before or after it, use Persian digits, or answer with a plain list instead. Nothing here
 * throws; unusable answers give empty results.
 */
@OptIn(ExperimentalSerializationApi::class)
object AssistantParsing {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true; allowTrailingComma = true }

    /** The first JSON object or array in [text] (fenced or not), or null. */
    fun extractJson(text: String): JsonElement? {
        val candidates = buildList {
            FENCE.findAll(text).forEach { add(it.groupValues[1]) }
            add(text)
        }
        for (candidate in candidates) {
            var from = 0
            while (from < candidate.length) {
                val start = candidate.indexOfAny(charArrayOf('{', '['), from)
                if (start < 0) break
                val end = matchingBracket(candidate, start)
                if (end > start) {
                    val parsed = runCatching { json.parseToJsonElement(candidate.substring(start, end + 1)) }.getOrNull()
                    if (parsed != null) return parsed
                }
                from = start + 1
            }
        }
        return null
    }

    /** Index of the bracket closing the one at [start], respecting strings; -1 when unbalanced. */
    private fun matchingBracket(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    /**
     * A list of short texts (subtasks, extracted tasks, titles): `{"items": […]}` (or
     * `subtasks`/`tasks`/`titles`), a bare array, objects with a `title`, or — when there is no
     * JSON — bulleted or numbered lines. Trimmed, de-duplicated, at most [max] items.
     */
    fun parseList(text: String, max: Int = MAX_ITEMS): List<String> {
        val element = extractJson(text)
        // An empty bare array is more likely a "[ ]" checkbox in a plain list than an answer.
        val fromJson = element?.let(::listFrom)?.takeIf { it.isNotEmpty() || element is JsonObject }
        val raw = fromJson ?: bulletLines(text)
        val seen = HashSet<String>()
        return raw.asSequence()
            .map { clean(it) }
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
            .take(max)
            .toList()
    }

    private fun listFrom(element: JsonElement): List<String>? = when (element) {
        is JsonArray -> element.mapNotNull(::itemText)
        is JsonObject -> LIST_KEYS.firstNotNullOfOrNull { key -> (element[key] as? JsonArray)?.mapNotNull(::itemText) }
            ?: element.values.firstOrNull { it is JsonArray }?.let { (it as JsonArray).mapNotNull(::itemText) }
        else -> null
    }

    private fun itemText(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> listOf("title", "text", "name", "task").firstNotNullOfOrNull { (element[it] as? JsonPrimitive)?.contentOrNull }
        else -> null
    }

    private fun bulletLines(text: String): List<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("```") }
        val bulleted = lines.filter { BULLET.containsMatchIn(it) }
        return bulleted.ifEmpty { lines }.map { it.replace(BULLET, "") }
    }

    private fun clean(item: String): String = item
        .replace(BULLET, "")
        .replace(CHECKBOX, "")
        .trim()
        .trim('"', '“', '”', '«', '»', '*', '`')
        .trim()
        .take(MAX_ITEM_LENGTH)

    /**
     * Proposed time blocks: `{"plan": [{"id", "date", "start", "end", "why"}]}` or a bare array;
     * `task_id`/`taskId` and Persian digits are accepted. Items without an id or times are dropped.
     */
    fun parsePlan(text: String): List<AiPlanItem> {
        val element = extractJson(text) ?: return emptyList()
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> (element["plan"] ?: element["blocks"] ?: element["items"] ?: element.values.firstOrNull { it is JsonArray }) as? JsonArray
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val id = listOf("id", "task_id", "taskId").firstNotNullOfOrNull { key ->
                (o[key] as? JsonPrimitive)?.let { p -> p.longOrNull ?: p.contentOrNull?.let { latinDigits(it).trim().toLongOrNull() } }
            } ?: return@mapNotNull null
            val start = (o["start"] as? JsonPrimitive)?.contentOrNull?.let(::latinDigits)?.trim() ?: return@mapNotNull null
            val end = (o["end"] as? JsonPrimitive)?.contentOrNull?.let(::latinDigits)?.trim() ?: return@mapNotNull null
            val date = (o["date"] as? JsonPrimitive)?.contentOrNull?.let(::latinDigits)?.trim()
            val why = (o["why"] as? JsonPrimitive)?.contentOrNull?.trim()?.take(MAX_ITEM_LENGTH)
            AiPlanItem(id, date, start, end, why)
        }
    }

    /** Persian and Arabic-Indic digits to ASCII. */
    fun latinDigits(text: String): String = buildString(text.length) {
        text.forEach { c ->
            append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    else -> c
                },
            )
        }
    }

    /** Removes a Markdown fence around a whole answer (for plain-text actions such as a rewrite). */
    fun plainText(text: String): String {
        val trimmed = text.trim()
        val fenced = FENCE.matchEntire(trimmed)
        return (fenced?.groupValues?.get(1) ?: trimmed).trim()
    }

    const val MAX_ITEMS = 20
    private const val MAX_ITEM_LENGTH = 200
    private val LIST_KEYS = listOf("items", "subtasks", "tasks", "titles", "list")
    private val FENCE = Regex("```[a-zA-Z]*\\s*\\n?([\\s\\S]*?)```")
    private val BULLET = Regex("^\\s*(?:[-*•–]|[0-9۰-۹٠-٩]+[.)\\-]|[(][0-9۰-۹]+[)])\\s+")
    private val CHECKBOX = Regex("^\\[[ xX]?]\\s*")
}

/**
 * How much text is sent, and how it is cut to fit. Token counts are only an estimate shown to
 * the user ("about 1,200 tokens"): about 4 Latin characters, or 2 characters of other scripts
 * (Persian), per token.
 */
object AiContextBudget {
    /** At most this many characters of chosen context go into one request. */
    const val MAX_CONTEXT_CHARS = 12_000

    data class Cut(val text: String, val truncated: Boolean)

    fun approxTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var latin = 0
        var other = 0
        text.forEach { c -> if (c.code < 0x0250) latin++ else other++ }
        return ((latin + 3) / 4) + ((other + 1) / 2)
    }

    /**
     * Keeps the start of [text] up to [maxChars], ending at a paragraph, line or sentence break
     * near the limit when there is one, and marks the cut with an ellipsis line.
     */
    fun truncate(text: String, maxChars: Int = MAX_CONTEXT_CHARS): Cut {
        if (text.length <= maxChars) return Cut(text, false)
        val window = text.substring(0, maxChars)
        val minimum = (maxChars * 0.7).toInt()
        val breakAt = listOf(window.lastIndexOf("\n\n"), window.lastIndexOf('\n'), window.lastIndexOfAny(charArrayOf('.', '!', '?', '؟', '۔')))
            .firstOrNull { it >= minimum }
        val kept = if (breakAt != null) window.substring(0, breakAt + 1) else window
        return Cut(kept.trimEnd() + "\n…", true)
    }
}
