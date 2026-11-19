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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
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

    private val conversationHistory = mutableListOf<UnifiedTurn>()

    /** Set by the UI. Returns true if the user approves the sensitive tool call. */
    var confirmHandler: (suspend (toolName: String, args: JSONObject) -> Boolean)? = null

    private fun needsConfirmation(name: String, args: JSONObject): Boolean = when {
        name in setOf("run_shell_command", "send_sms", "send_whatsapp_message", "send_whatsapp_file", "workspace_delete_file") -> true
        name == "http_request" -> args.optString("method", "GET").uppercase() != "GET"
        name.startsWith("mcp_") -> true
        else -> false
    }

    private fun deniedResult() = AndroidTaskExecutor.ExecutionResult(
        JSONObject().put("error", "User denied this action."), "Denied by user"
    )

    private fun gatedExecute(name: String, args: JSONObject, allowScreen: Boolean): AndroidTaskExecutor.ExecutionResult {
        val approvalKey = if (name.startsWith("mcp_")) "mcp_*" else name
        val skip = settingsRepository.getApprovalSkipAll() || approvalKey in settingsRepository.getAlwaysAllowedTools()
        if (!skip && needsConfirmation(name, args)) {
            val handler = confirmHandler
            val ok = if (handler == null) false else runBlocking { withTimeoutOrNull(60_000L) { handler(name, args) } ?: false }
            if (!ok) return deniedResult()
        }
        return if (name.startsWith("mcp_")) McpClient.callTool(name, args)
        else toolExecutor.execute(name, args, allowScreenControl = allowScreen)
    }

    data class AgentStep(
        val finalText: String,
        val executedTools: List<Pair<String, String>>,
        val activeKeyIndex: Int,
        val keySwapMessage: String? = null,
        val generatedImageBase64: String? = null
    )

    fun clearHistory() {
        conversationHistory.clear()
    }

    suspend fun detectAndFetchModels(
        rawKey: String,
        overrideProvider: AiProvider? = null
    ): DetectedKeyResult = ApiKeyDetector.detectAndFetchModels(httpClient, rawKey, overrideProvider)

    suspend fun fetchAvailableModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        val entries = settingsRepository.getKeyEntries()
        if (entries.isEmpty()) return@withContext Result.failure(IllegalStateException("Add an API key first."))
        val activeIdx = settingsRepository.getActiveKeyIndex().coerceIn(0, entries.lastIndex)
        val activeProvider = entries[activeIdx].provider
        val candidateIndices = entries.indices.filter { entries[it].provider == activeProvider }
        val startPos = candidateIndices.indexOf(activeIdx).coerceAtLeast(0)

        for (offset in candidateIndices.indices) {
            val idx = candidateIndices[(startPos + offset) % candidateIndices.size]
            val fetched = ApiKeyDetector.fetchModelsForEntry(httpClient, entries[idx])
            if (fetched.isSuccess) {
                if (idx != activeIdx) settingsRepository.setActiveKeyIndex(idx)
                return@withContext fetched
            }
        }
        Result.success(activeProvider.defaultModels)
    }

    suspend fun sendUserPrompt(
        modelName: String,
        userText: String,
        attachment: AttachmentData?,
        mode: AgentMode,
        screenControlToggleOn: Boolean,
        onStreamDelta: (String) -> Unit = {}
    ): Result<AgentStep> = withContext(Dispatchers.IO) {
        val entries = settingsRepository.getKeyEntries()
        if (entries.isEmpty()) return@withContext Result.failure(IllegalStateException("No API keys configured."))

        val activeIdx = settingsRepository.getActiveKeyIndex().coerceIn(0, entries.lastIndex)
        val activeEntry = entries[activeIdx]
        val resolvedModel = if (ApiKeyDetector.isModelCompatible(modelName, activeEntry.provider)) {
            modelName
        } else {
            activeEntry.provider.defaultModel
        }

        val rollbackSize = conversationHistory.size
        conversationHistory.add(UnifiedTurn.User(userText, attachment))

        val executedTools = mutableListOf<Pair<String, String>>()
        val includeScreenTools = screenControlToggleOn && AssistantAccessibilityService.isConnected()
        val toggles = settingsRepository.getFeatureToggles()
        val mcpServers = settingsRepository.getMcpServers()
        if (mcpServers.isNotEmpty() && McpClient.getCachedTools().isEmpty()) McpClient.discoverAll(mcpServers)
        val maxRounds = if (mode == AgentMode.MAX) 100 else 40
        var swapMsg: String? = null

        val systemInstruction = buildSystemInstruction(mode)
        val decls = buildToolDeclarations(toggles, includeScreenTools, isSubagent = false)

        try {
            repeat(maxRounds) {
                val (output, swapNotice) = callWithKeyFailover(
                    entries = entries,
                    targetProvider = activeEntry.provider,
                    modelName = resolvedModel,
                    systemInstruction = systemInstruction,
                    history = conversationHistory,
                    decls = decls,
                    mode = mode,
                    onStreamDelta = onStreamDelta
                )
                if (swapNotice != null) swapMsg = swapNotice

                conversationHistory.add(
                    UnifiedTurn.Assistant(
                        text = output.text,
                        toolCalls = output.toolCalls,
                        rawGeminiContent = output.rawGeminiContent
                    )
                )

                if (output.toolCalls.isEmpty()) {
                    val finalReply = output.text.ifBlank {
                        if (output.generatedImageBase64 != null) "Generated image:" else "Done."
                    }
                    return@withContext Result.success(
                        AgentStep(
                            finalText = finalReply,
                            executedTools = executedTools,
                            activeKeyIndex = settingsRepository.getActiveKeyIndex(),
                            keySwapMessage = swapMsg,
                            generatedImageBase64 = output.generatedImageBase64
                        )
                    )
                }

                onStreamDelta("")
                val results = coroutineScope {
                    output.toolCalls.map { call ->
                        async(Dispatchers.IO) {
                            val res = when (call.name) {
                                "spawn_subagent" -> runSubagent(
                                    entries = entries,
                                    targetProvider = activeEntry.provider,
                                    modelName = resolvedModel,
                                    role = call.args.optString("role", "Specialist"),
                                    task = call.args.optString("task", ""),
                                    toggles = toggles,
                                    mode = mode
                                )
                                else -> gatedExecute(call.name, call.args, includeScreenTools)
                            }
                            if (call.name in setOf("open_app", "tap_on_screen", "scroll_screen", "press_navigation")) delay(650)
                            call to res
                        }
                    }.awaitAll()
                }

                val responseParts = results.map { (call, result) ->
                    executedTools.add(call.name to result.userSummary)
                    val respJson = result.responseJson
                    val inlineImg = respJson.optString("_inline_image_base64", "").takeIf { it.isNotBlank() }
                    if (inlineImg != null) respJson.remove("_inline_image_base64")
                    ToolResultPart(
                        toolCallId = call.id,
                        name = call.name,
                        responseJson = respJson,
                        inlineImageBase64 = inlineImg
                    )
                }
                conversationHistory.add(UnifiedTurn.ToolResponses(responseParts))
            }

            Result.success(
                AgentStep(
                    finalText = "Completed ${maxRounds}-step execution.",
                    executedTools = executedTools,
                    activeKeyIndex = settingsRepository.getActiveKeyIndex(),
                    keySwapMessage = swapMsg
                )
            )
        } catch (e: Exception) {
            while (conversationHistory.size > rollbackSize) {
                conversationHistory.removeAt(conversationHistory.lastIndex)
            }
            Result.failure(e)
        }
    }

    private fun runSubagent(
        entries: List<ApiKeyEntry>,
        targetProvider: AiProvider,
        modelName: String,
        role: String,
        task: String,
        toggles: Map<String, Boolean>,
        mode: AgentMode
    ): AndroidTaskExecutor.ExecutionResult {
        val subHistory = mutableListOf<UnifiedTurn>(
            UnifiedTurn.User("[Role: $role]\n$task")
        )
        val decls = TaskToolDeclarations.buildToolsArray(toggles, includeScreenControl = false, isSubagent = true)
            .getJSONObject(0)
            .getJSONArray("functionDeclarations")
        val subSystem = "You are a specialized subagent ($role). Complete the task using tools and return a concise summary."

        repeat(15) {
            val (out, _) = callWithKeyFailover(
                entries = entries,
                targetProvider = targetProvider,
                modelName = modelName,
                systemInstruction = subSystem,
                history = subHistory,
                decls = decls,
                mode = mode,
                onStreamDelta = {}
            )
            subHistory.add(UnifiedTurn.Assistant(out.text, out.toolCalls, out.rawGeminiContent))
            if (out.toolCalls.isEmpty()) {
                return AndroidTaskExecutor.ExecutionResult(
                    JSONObject().put("role", role).put("output", out.text),
                    "Subagent [$role] finished"
                )
            }
            val respParts = out.toolCalls.map { c ->
                val res = gatedExecute(c.name, c.args, false)
                val inlineImg = res.responseJson.optString("_inline_image_base64", "").takeIf { it.isNotBlank() }
                if (inlineImg != null) res.responseJson.remove("_inline_image_base64")
                ToolResultPart(c.id, c.name, res.responseJson, inlineImg)
            }
            subHistory.add(UnifiedTurn.ToolResponses(respParts))
        }
        return AndroidTaskExecutor.ExecutionResult(
            JSONObject().put("role", role).put("output", "Completed"),
            "Subagent [$role] finished"
        )
    }

    private fun buildSystemInstruction(mode: AgentMode): String {
        val customInstr = settingsRepository.getCustomInstructions()
        val memBlock = memoryRepository.buildContextPromptBlock()
        val modeInstr = when (mode) {
            AgentMode.PLAN -> "\n\n[PLAN MODE ACTIVE] Before writing code or executing changes: (1) Ask concise clarifying questions if requirements are ambiguous. (2) Once clear, call create_artifact with artifact_type='PLAN' containing a structured Markdown implementation plan, then STOP and ask the user to review & approve it in the Artifacts tab."
            AgentMode.MAX -> "\n\n[MAX MODE ACTIVE] Take your time for maximum quality: inspect existing files with grep_search/find_by_name, use workspace_edit_file/write_file, capture_webview_screenshot to visually verify HTML/JS UI and fix any console errors, and save a WALKTHROUGH artifact summarizing what you built."
            AgentMode.FAST -> ""
        }
        return buildString {
            append("You are Omni Studio for Android—a complete multi-provider AI assistant (Gemini, Claude, Kimi, ChatGPT), phone controller, and agentic dev tool. ")
            append("Support regular chat, device control, tasks, MCP external tools, and full-stack coding (TypeScript default, or any language). Follow a structured engineering loop for builds: (1) Discover existing files with workspace_list_files/grep_search, (2) Build cleanly across files, (3) Visually verify any HTML/JS UI using capture_webview_screenshot (including js_action tests) and fix any console or layout issues you see, (4) Save a concise WALKTHROUGH artifact summarizing verified changes. ")
            append(modeInstr)
            if (customInstr.isNotBlank()) append("\n\n[Custom Instructions]\n$customInstr")
            if (memBlock.isNotBlank()) append(memBlock)
        }
    }

    private fun buildToolDeclarations(
        toggles: Map<String, Boolean>,
        includeScreenTools: Boolean,
        isSubagent: Boolean
    ): JSONArray {
        val tools = TaskToolDeclarations.buildToolsArray(toggles, includeScreenControl = includeScreenTools, isSubagent = isSubagent)
        val decls = tools.getJSONObject(0).getJSONArray("functionDeclarations")
        if (!isSubagent) {
            McpClient.getCachedTools().forEach { mcp ->
                decls.put(JSONObject().apply {
                    put("name", mcp.qualifiedName)
                    put("description", mcp.description)
                    put("parameters", mcp.inputSchema)
                })
            }
        }
        return decls
    }

    private fun callWithKeyFailover(
        entries: List<ApiKeyEntry>,
        targetProvider: AiProvider,
        modelName: String,
        systemInstruction: String,
        history: List<UnifiedTurn>,
        decls: JSONArray,
        mode: AgentMode,
        onStreamDelta: (String) -> Unit
    ): Pair<ProviderTurnOutput, String?> {
        val startIndex = settingsRepository.getActiveKeyIndex().coerceIn(0, entries.lastIndex)
        val candidateIndices = entries.indices.filter { entries[it].provider == targetProvider }
        val startPos = candidateIndices.indexOf(startIndex).coerceAtLeast(0)
        val errors = mutableListOf<String>()

        for (offset in candidateIndices.indices) {
            val idx = candidateIndices[(startPos + offset) % candidateIndices.size]
            val entry = entries[idx]
            try {
                val out = ProviderChatAdapter.executeStreamingTurn(
                    httpClient = httpClient,
                    entry = entry,
                    modelName = modelName,
                    systemInstruction = systemInstruction,
                    history = history,
                    geminiFunctionDecls = decls,
                    mode = mode,
                    onStreamDelta = onStreamDelta
                )
                var notice: String? = null
                if (idx != startIndex) {
                    settingsRepository.setActiveKeyIndex(idx)
                    notice = "${targetProvider.displayName} Key #${startIndex + 1} hit error/quota -> swapped to Key #${idx + 1}"
                }
                return out to notice
            } catch (e: Exception) {
                errors.add("${targetProvider.displayName} Key #${idx + 1}: ${e.message}")
            }
        }
        throw IllegalStateException("All ${candidateIndices.size} ${targetProvider.displayName} key(s) failed.\n" + errors.joinToString("\n"))
    }
}
