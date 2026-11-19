package com.example.geminidevagent.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

enum class AiProvider(
    val displayName: String,
    val keyPortalUrl: String,
    val defaultModel: String,
    val defaultModels: List<String>
) {
    GEMINI(
        displayName = "Gemini",
        keyPortalUrl = "https://aistudio.google.com/apikey",
        defaultModel = "gemini-4-hydrogen",
        defaultModels = listOf(
            "gemini-4-hydrogen",
            "gemini-4-carbon",
            "gemini-4-argon",
            "gemini-flash-latest",
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-image"
        )
    ),
    CLAUDE(
        displayName = "Claude",
        keyPortalUrl = "https://console.anthropic.com/settings/keys",
        defaultModel = "claude-sonnet-5-5",
        defaultModels = listOf(
            "claude-sonnet-5-5",
            "claude-opus-5-5",
            "claude-haiku-5-5",
            "claude-fable-5-2",
            "claude-sonnet-4-5",
            "claude-opus-4-5"
        )
    ),
    KIMI(
        displayName = "Kimi",
        keyPortalUrl = "https://platform.moonshot.ai/console/api-keys",
        defaultModel = "kimi-k3.1-pro",
        defaultModels = listOf(
            "kimi-k3.1-pro",
            "kimi-k3",
            "kimi-k2-0905-preview",
            "kimi-k2-turbo-preview",
            "moonshot-v1-auto",
            "moonshot-v1-128k"
        )
    ),
    CHATGPT(
        displayName = "ChatGPT",
        keyPortalUrl = "https://platform.openai.com/api-keys",
        defaultModel = "gpt-6.2-sol",
        defaultModels = listOf(
            "gpt-6.2-sol",
            "gpt-6.2-astra",
            "gpt-6.1-terra",
            "gpt-6-luna",
            "chat-latest",
            "gpt-4o",
            "gpt-4.1",
            "o4-mini"
        )
    );

    companion object {
        fun fromNameOrNull(name: String?): AiProvider? =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
    }
}

data class ApiKeyEntry(
    val key: String,
    val provider: AiProvider,
    val baseUrlOverride: String? = null
)

data class DetectedKeyResult(
    val entry: ApiKeyEntry,
    val models: List<String>,
    val verifiedLive: Boolean,
    val note: String
)

object ApiKeyDetector {

    private val keyPatterns = listOf(
        Regex("sk-ant-[0-9A-Za-z_-]{20,}"),
        Regex("AIza[0-9A-Za-z_-]{30,}"),
        Regex("sk-proj-[0-9A-Za-z_-]{20,}"),
        Regex("sk-svcacct-[0-9A-Za-z_-]{20,}"),
        Regex("sk-admin-[0-9A-Za-z_-]{20,}"),
        Regex("sk-kimi-[0-9A-Za-z_-]{20,}"),
        Regex("sk-[0-9A-Za-z_-]{20,}")
    )

    fun extractKeyFromText(text: String): String? {
        for (pattern in keyPatterns) {
            val match = pattern.find(text)
            if (match != null) return match.value
        }
        return null
    }

    /**
     * Returns a definite provider when the key prefix is unambiguous, or null if it is a
     * generic `sk-...` key that could belong to either ChatGPT (OpenAI) or Kimi (Moonshot).
     */
    fun detectStatic(rawKey: String): AiProvider? {
        val k = rawKey.trim()
        return when {
            k.startsWith("AIza") -> AiProvider.GEMINI
            k.startsWith("sk-ant-") -> AiProvider.CLAUDE
            k.startsWith("sk-kimi-") -> AiProvider.KIMI
            k.startsWith("sk-proj-") || k.startsWith("sk-svcacct-") || k.startsWith("sk-admin-") -> AiProvider.CHATGPT
            k.startsWith("sk-") && k.contains("T3BlbkFJ") -> AiProvider.CHATGPT
            else -> null
        }
    }

    fun isAmbiguousSkKey(rawKey: String): Boolean {
        val k = rawKey.trim()
        return k.startsWith("sk-") && detectStatic(k) == null
    }

    fun inferProviderOrFallback(rawKey: String): AiProvider {
        val k = rawKey.trim()
        return detectStatic(k) ?: if (k.startsWith("sk-")) AiProvider.CHATGPT else AiProvider.GEMINI
    }

    fun inferProviderFromModel(modelName: String): AiProvider {
        val m = modelName.trim().lowercase()
        return when {
            m.startsWith("claude") -> AiProvider.CLAUDE
            m.startsWith("kimi") || m.startsWith("moonshot") -> AiProvider.KIMI
            m.startsWith("gpt") || m.startsWith("o1") || m.startsWith("o3") ||
                m.startsWith("o4") || m == "chat-latest" || m.startsWith("chatgpt") -> AiProvider.CHATGPT
            else -> AiProvider.GEMINI
        }
    }

    fun isModelCompatible(modelName: String, provider: AiProvider): Boolean {
        if (modelName.isBlank()) return false
        return inferProviderFromModel(modelName) == provider
    }

    suspend fun detectAndFetchModels(
        httpClient: OkHttpClient,
        rawKey: String,
        overrideProvider: AiProvider? = null
    ): DetectedKeyResult = withContext(Dispatchers.IO) {
        val k = rawKey.trim()
        val staticProvider = overrideProvider ?: detectStatic(k)

        if (staticProvider != null) {
            val probe = probeProvider(httpClient, k, staticProvider)
            if (probe != null) {
                val (baseUrl, models) = probe
                val merged = (models + staticProvider.defaultModels).distinct()
                return@withContext DetectedKeyResult(
                    entry = ApiKeyEntry(k, staticProvider, baseUrl),
                    models = merged,
                    verifiedLive = true,
                    note = "Detected ${staticProvider.displayName} key (${models.size} live models)"
                )
            }
            return@withContext DetectedKeyResult(
                entry = ApiKeyEntry(k, staticProvider),
                models = staticProvider.defaultModels,
                verifiedLive = false,
                note = "Detected ${staticProvider.displayName} key format (offline defaults)"
            )
        }

        if (k.startsWith("sk-")) {
            // Ambiguous `sk-` key: probe OpenAI and Moonshot (Global & CN) to identify accurately
            probeProvider(httpClient, k, AiProvider.CHATGPT)?.let { (_, models) ->
                val merged = (models + AiProvider.CHATGPT.defaultModels).distinct()
                return@withContext DetectedKeyResult(
                    entry = ApiKeyEntry(k, AiProvider.CHATGPT),
                    models = merged,
                    verifiedLive = true,
                    note = "Verified ChatGPT (OpenAI) key (${models.size} live models)"
                )
            }
            probeProvider(httpClient, k, AiProvider.KIMI)?.let { (baseUrl, models) ->
                val merged = (models + AiProvider.KIMI.defaultModels).distinct()
                return@withContext DetectedKeyResult(
                    entry = ApiKeyEntry(k, AiProvider.KIMI, baseUrl),
                    models = merged,
                    verifiedLive = true,
                    note = "Verified Kimi (Moonshot) key (${models.size} live models)"
                )
            }
        }

        val fallback = inferProviderOrFallback(k)
        DetectedKeyResult(
            entry = ApiKeyEntry(k, fallback),
            models = fallback.defaultModels,
            verifiedLive = false,
            note = "Could not verify live; set to ${fallback.displayName} (tap chip to switch)"
        )
    }

    suspend fun fetchModelsForEntry(
        httpClient: OkHttpClient,
        entry: ApiKeyEntry
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val probe = probeProvider(httpClient, entry.key, entry.provider, entry.baseUrlOverride)
        if (probe != null && probe.second.isNotEmpty()) {
            Result.success((probe.second + entry.provider.defaultModels).distinct())
        } else {
            Result.failure(IllegalStateException("Could not fetch live models for ${entry.provider.displayName}."))
        }
    }

    private fun probeProvider(
        httpClient: OkHttpClient,
        key: String,
        provider: AiProvider,
        preferredBaseUrl: String? = null
    ): Pair<String?, List<String>>? = when (provider) {
        AiProvider.GEMINI -> probeGemini(httpClient, key)?.let { null to it }
        AiProvider.CLAUDE -> probeClaude(httpClient, key)?.let { null to it }
        AiProvider.CHATGPT -> probeOpenAi(httpClient, key)?.let { null to it }
        AiProvider.KIMI -> probeKimi(httpClient, key, preferredBaseUrl)
    }

    private fun probeGemini(httpClient: OkHttpClient, key: String): List<String>? {
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models")
            .addHeader("x-goog-api-key", key)
            .get()
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val models = JSONObject(resp.body?.string().orEmpty()).optJSONArray("models") ?: JSONArray()
                buildList<String> {
                    for (i in 0 until models.length()) {
                        val m = models.getJSONObject(i)
                        if (m.optJSONArray("supportedGenerationMethods")?.toString()?.contains("generateContent") == true) {
                            add(m.getString("name").removePrefix("models/"))
                        }
                    }
                }
            }
        }.getOrNull()
    }

    private fun probeClaude(httpClient: OkHttpClient, key: String): List<String>? {
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/models")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .get()
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val data = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data") ?: JSONArray()
                buildList<String> {
                    for (i in 0 until data.length()) {
                        val id = data.getJSONObject(i).optString("id")
                        if (id.isNotBlank()) add(id)
                    }
                }
            }
        }.getOrNull()
    }

    private fun probeOpenAi(httpClient: OkHttpClient, key: String): List<String>? {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .addHeader("Authorization", "Bearer $key")
            .get()
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val data = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data") ?: JSONArray()
                buildList {
                    for (i in 0 until data.length()) {
                        val id = data.getJSONObject(i).optString("id")
                        val lower = id.lowercase()
                        val isChatModel = (lower.startsWith("gpt-") || lower.startsWith("o1") ||
                            lower.startsWith("o3") || lower.startsWith("o4") ||
                            lower.startsWith("chatgpt-") || lower == "chat-latest") &&
                            !lower.contains("realtime") && !lower.contains("audio") &&
                            !lower.contains("transcribe") && !lower.contains("tts") &&
                            !lower.contains("image") && !lower.contains("instruct")
                        if (isChatModel) add(id)
                    }
                }.sortedDescending()
            }
        }.getOrNull()
    }

    private fun probeKimi(
        httpClient: OkHttpClient,
        key: String,
        preferredBaseUrl: String?
    ): Pair<String?, List<String>>? {
        val candidates = listOfNotNull(
            preferredBaseUrl,
            "https://api.moonshot.ai/v1",
            "https://api.moonshot.cn/v1"
        ).distinct()
        for (base in candidates) {
            val req = Request.Builder()
                .url("${base.trimEnd('/')}/models")
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()
            val list = runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val data = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data") ?: JSONArray()
                    buildList<String> {
                        for (i in 0 until data.length()) {
                            val id = data.getJSONObject(i).optString("id")
                            if (id.isNotBlank()) add(id)
                        }
                    }
                }
            }.getOrNull()
            if (list != null) return base to list
        }
        return null
    }
}
