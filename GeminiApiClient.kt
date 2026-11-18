package com.example.geminidevagent.data

import com.example.geminidevagent.accessibility.AssistantAccessibilityService
import com.example.geminidevagent.model.AttachmentData
import com.example.geminidevagent.tools.AndroidTaskExecutor
import com.example.geminidevagent.tools.McpClient
import com.example.geminidevagent.tools.TaskToolDeclarations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class AgentMode {
    FAST, PLAN, MAX
}

class GeminiApiClient(
    private val toolExecutor: AndroidTaskExecutor,
    private val settingsRepository: SettingsRepository,
    private val memoryRepository: MemoryRepository
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = JSONArray()

    data class AgentStep(
        val finalText: String,
        val executedTools: List<Pair<String, String>>,
        val activeKeyIndex: Int,
        val keySwapMessage: String? = null,
        val generatedImageBase64: String? = null
    )

    fun clearHistory() {
        while (conversationHistory.length() > 0) conversationHistory.remove(0)
    }

    suspend fun fetchAvailableModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        val keys = settingsRepository.getApiKeys()
        if (keys.isEmpty()) return@withContext Result.failure(IllegalStateException("Add an API key first."))
        val startIndex = settingsRepository.getActiveKeyIndex().coerceIn(0, keys.lastIndex)
        for (offset in keys.indices) {
            val idx = (startIndex + offset) % keys.size
            val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models").addHeader("x-goog-api-key", keys[idx]).get().build()
            try {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) continue
                    val models = JSONObject(resp.body?.string().orEmpty()).optJSONArray("models") ?: JSONArray()
                    val names = buildList {
                        for (i in 0 until models.length()) {
                            val m = models.getJSONObject(i)
                            if (m.optJSONArray("supportedGenerationMethods")?.toString()?.contains("generateContent") == true) {
                                add(m.getString("name").removePrefix("models/"))
                            }
                        }
                    }
                    if (idx != startIndex) settingsRepository.setActiveKeyIndex(idx)
                    return@withContext Result.success(names)
                }
            } catch (_: Exception) { continue }
        }
        Result.failure(IllegalStateException("Could not fetch models."))
    }

    suspend fun sendUserPrompt(
        modelName: String,
        userText: String,
        attachment: AttachmentData?,
        mode: AgentMode,
        screenControlToggleOn: Boolean,
        onStreamDelta: (String) -> Unit = {}
    ): Result<AgentStep> = withContext(Dispatchers.IO) {
        val keys = settingsRepository.getApiKeys()
        if (keys.isEmpty()) return@withContext Result.failure(IllegalStateException("No Gemini API keys configured."))

        val userParts = JSONArray()
        if (attachment != null) {
            userParts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", attachment.mimeType).put("data", attachment.base64)))
        }
        if (userText.isNotBlank()) userParts.put(JSONObject().put("text", userText))

        val rollbackIndex = conversationHistory.length()
        conversationHistory.put(JSONObject().put("role", "user").put("parts", userParts))

        val executedTools = mutableListOf<Pair<String, String>>()
        val includeScreenTools = screenControlToggleOn && AssistantAccessibilityService.isConnected()
        val toggles = settingsRepository.getFeatureToggles()
        val mcpServers = settingsRepository.getMcpServers()
        if (mcpServers.isNotEmpty() && McpClient.getCachedTools().isEmpty()) McpClient.discoverAll(mcpServers)
        val maxRounds = if (mode == AgentMode.MAX) 100 else 40
        var swapMsg: String? = null

        try {
            repeat(maxRounds) {
                val (responseJson, swapNotice) = callWithKeyFailover(keys, modelName, mode, toggles, includeScreenTools, onStreamDelta)
                if (swapNotice != null) swapMsg = swapNotice

                val candidates = responseJson.optJSONArray("candidates") ?: throw IllegalStateException("No candidates returned.")
                if (candidates.length() == 0) throw IllegalStateException("Empty candidates list.")

                val modelContent = candidates.getJSONObject(0).getJSONObject("content")
                conversationHistory.put(modelContent)

                val parts = modelContent.optJSONArray("parts") ?: JSONArray()
                val functionCalls = mutableListOf<JSONObject>()
                val textChunks = mutableListOf<String>()
                var generatedImageB64: String? = null

                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    when {
                        part.has("functionCall") -> functionCalls.add(part.getJSONObject("functionCall"))
                        part.has("text") -> textChunks.add(part.getString("text"))
                        part.has("inlineData") -> {
                            val inline = part.getJSONObject("inlineData")
                            if (inline.optString("mimeType").startsWith("image/")) generatedImageB64 = inline.optString("data")
                        }
                    }
                }

                if (functionCalls.isEmpty()) {
                    val finalReply = textChunks.joinToString("\n").ifBlank { if (generatedImageB64 != null) "Generated image:" else "Done." }
                    return@withContext Result.success(
                        AgentStep(finalReply, executedTools, settingsRepository.getActiveKeyIndex(), swapMsg, generatedImageB64)
                    )
                }

                onStreamDelta("")
                val results = coroutineScope {
                    functionCalls.map { call ->
                        async(Dispatchers.IO) {
                            val name = call.getString("name")
                            val args = call.optJSONObject("args") ?: JSONObject()
                            val res = when {
                                name == "spawn_subagent" -> runSubagent(keys, modelName, args.optString("role", "Specialist"), args.optString("task", ""), toggles)
                                name.startsWith("mcp_") -> McpClient.callTool(name, args)
                                else -> toolExecutor.execute(name, args, allowScreenControl = includeScreenTools)
                            }
                            if (name in setOf("open_app", "tap_on_screen", "scroll_screen", "press_navigation")) delay(650)
                            name to res
                        }
                    }.awaitAll()
                }
                val functionResponseParts = JSONArray()
                for ((name, result) in results) {
                    executedTools.add(name to result.userSummary)
                    val respJson = result.responseJson
                    val inlineImg = respJson.optString("_inline_image_base64", "")
                    if (inlineImg.isNotBlank()) {
                        respJson.remove("_inline_image_base64")
                        functionResponseParts.put(
                            JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", inlineImg))
                        )
                    }
                    functionResponseParts.put(
                        JSONObject().put("functionResponse", JSONObject().put("name", name).put("response", respJson))
                    )
                }
                pruneOldImages()
                conversationHistory.put(JSONObject().put("role", "user").put("parts", functionResponseParts))
            }

            Result.success(AgentStep("Completed ${maxRounds}-step execution.", executedTools, settingsRepository.getActiveKeyIndex(), swapMsg))
        } catch (e: Exception) {
            while (conversationHistory.length() > rollbackIndex) conversationHistory.remove(conversationHistory.length() - 1)
            Result.failure(e)
        }
    }


    private fun pruneOldImages() {
        val cutoff = (conversationHistory.length() - 4).coerceAtLeast(0)
        for (i in 0 until cutoff) {
            val turn = conversationHistory.optJSONObject(i) ?: continue
            val parts = turn.optJSONArray("parts") ?: continue
            val kept = JSONArray()
            for (j in 0 until parts.length()) {
                val p = parts.getJSONObject(j)
                if (!p.has("inlineData")) kept.put(p)
            }
            if (kept.length() == 0) kept.put(JSONObject().put("text", "[image omitted]"))
            turn.put("parts", kept)
        }
    }

    private fun runSubagent(
        keys: List<String>,
        modelName: String,
        role: String,
        task: String,
        toggles: Map<String, Boolean>
    ): AndroidTaskExecutor.ExecutionResult {
        val subHistory = JSONArray().put(
            JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "[Role: $role]\n$task")))
        )
        val tools = TaskToolDeclarations.buildToolsArray(toggles, includeScreenControl = false, isSubagent = true)
        repeat(15) {
            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "You are a specialized subagent ($role). Complete the task using tools and return a concise summary."))))
                put("contents", subHistory)
                if (tools.getJSONObject(0).getJSONArray("functionDeclarations").length() > 0) put("tools", tools)
            }
            val (resp, _) = callRawWithFailover(keys, modelName, payload)
            val content = resp.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            subHistory.put(content)
            val parts = content.optJSONArray("parts") ?: JSONArray()
            val calls = (0 until parts.length()).map { parts.getJSONObject(it) }.filter { it.has("functionCall") }.map { it.getJSONObject("functionCall") }
            if (calls.isEmpty()) {
                val txt = (0 until parts.length()).mapNotNull { parts.getJSONObject(it).optString("text").takeIf { s -> s.isNotBlank() } }.joinToString("\n")
                return AndroidTaskExecutor.ExecutionResult(JSONObject().put("role", role).put("output", txt), "Subagent [$role] finished")
            }
            val respParts = JSONArray()
            calls.forEach { c ->
                val res = toolExecutor.execute(c.getString("name"), c.optJSONObject("args") ?: JSONObject(), allowScreenControl = false)
                respParts.put(JSONObject().put("functionResponse", JSONObject().put("name", c.getString("name")).put("response", res.responseJson)))
            }
            subHistory.put(JSONObject().put("role", "user").put("parts", respParts))
        }
        return AndroidTaskExecutor.ExecutionResult(JSONObject().put("role", role).put("output", "Completed"), "Subagent [$role] finished")
    }

    private fun callWithKeyFailover(
        keys: List<String>,
        modelName: String,
        mode: AgentMode,
        toggles: Map<String, Boolean>,
        includeScreenTools: Boolean,
        onStreamDelta: (String) -> Unit
    ): Pair<JSONObject, String?> {
        val customInstr = settingsRepository.getCustomInstructions()
        val memBlock = memoryRepository.buildContextPromptBlock()
        val modeInstr = when (mode) {
            AgentMode.PLAN -> "\n\n[PLAN MODE ACTIVE] Before writing code or executing changes: (1) Ask concise clarifying questions if requirements are ambiguous. (2) Once clear, call create_artifact with artifact_type='PLAN' containing a structured Markdown implementation plan, then STOP and ask the user to review & approve it in the Artifacts tab."
            AgentMode.MAX -> "\n\n[MAX MODE ACTIVE] Take your time for maximum quality: inspect existing files with grep_search/find_by_name, use workspace_edit_file/write_file, capture_webview_screenshot to visually verify HTML/JS UI and fix any console errors, and save a WALKTHROUGH artifact summarizing what you built."
            AgentMode.FAST -> ""
        }
        val instructionText = buildString {
            append("You are Gemini Omni Studio for Android—a complete AI assistant, phone controller, and agentic dev tool. ")
            append("Support regular Gemini chat, device control, tasks, MCP external tools, and full-stack coding (TypeScript default, or any language). Follow a structured Antigravity-style engineering loop for builds: (1) Discover existing files with workspace_list_files/grep_search, (2) Build cleanly across files, (3) Visually verify any HTML/JS UI using capture_webview_screenshot (including js_action tests) and fix any console or layout issues you see, (4) Save a concise WALKTHROUGH artifact summarizing verified changes. ")
            append(modeInstr)
            if (customInstr.isNotBlank()) append("\n\n[Custom Instructions]\n$customInstr")
            if (memBlock.isNotBlank()) append(memBlock)
        }
        val tools = TaskToolDeclarations.buildToolsArray(toggles, includeScreenControl = includeScreenTools, isSubagent = false)
        val decls = tools.getJSONObject(0).getJSONArray("functionDeclarations")
        McpClient.getCachedTools().forEach { mcp ->
            decls.put(JSONObject().apply {
                put("name", mcp.qualifiedName)
                put("description", mcp.description)
                put("parameters", mcp.inputSchema)
            })
        }
        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instructionText))))
            put("contents", conversationHistory)
            if (tools.getJSONObject(0).getJSONArray("functionDeclarations").length() > 0) put("tools", tools)
            if (modelName.contains("gemini-4")) {
                val lvl = when (mode) {
                    AgentMode.MAX -> "XHIGH"
                    AgentMode.FAST -> "LOW"
                    AgentMode.PLAN -> "HIGH"
                }
                put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingLevel", lvl)))
            }
        }
        return callRawWithFailover(keys, modelName, payload, onStreamDelta)
    }

    private fun callRawWithFailover(keys: List<String>, modelName: String, payload: JSONObject, onStreamDelta: (String) -> Unit = {}): Pair<JSONObject, String?> {
        val startIndex = settingsRepository.getActiveKeyIndex().coerceIn(0, keys.lastIndex)
        val errors = mutableListOf<String>()
        for (offset in keys.indices) {
            val idx = (startIndex + offset) % keys.size
            try {
                val req = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?alt=sse")
                    .addHeader("x-goog-api-key", keys[idx])
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string().orEmpty()
                        val msg = runCatching { JSONObject(errBody).getJSONObject("error").getString("message") }.getOrElse { "HTTP ${resp.code}" }
                        val reason = if (resp.code == 429) "Quota 429" else "HTTP ${resp.code}"
                        throw IllegalStateException("$reason: $msg")
                    }
                    val source = resp.body?.source() ?: throw IllegalStateException("Empty stream body")
                    val accumulatedParts = JSONArray()
                    val textBuilder = StringBuilder()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val jsonLine = line.removePrefix("data:").trim()
                        if (jsonLine.isEmpty()) continue
                        val chunk = runCatching { JSONObject(jsonLine) }.getOrNull() ?: continue
                        val cParts = chunk.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                        for (i in 0 until cParts.length()) {
                            val p = cParts.getJSONObject(i)
                            if (p.has("text")) {
                                val delta = p.getString("text")
                                textBuilder.append(delta)
                                onStreamDelta(textBuilder.toString())
                            } else {
                                accumulatedParts.put(p)
                            }
                        }
                    }
                    if (textBuilder.isNotEmpty()) {
                        accumulatedParts.put(JSONObject().put("text", textBuilder.toString()))
                    }
                    val merged = JSONObject().put(
                        "candidates",
                        JSONArray().put(JSONObject().put("content", JSONObject().put("role", "model").put("parts", accumulatedParts)))
                    )
                    var notice: String? = null
                    if (idx != startIndex) {
                        settingsRepository.setActiveKeyIndex(idx)
                        notice = "Key #${startIndex + 1} hit error/quota -> swapped mid-run to Key #${idx + 1}"
                    }
                    return merged to notice
                }
            } catch (e: Exception) {
                errors.add("Key #${idx + 1}: ${e.message}")
            }
        }
        throw IllegalStateException("All ${keys.size} API key(s) failed.\n" + errors.joinToString("\n"))
    }
}
