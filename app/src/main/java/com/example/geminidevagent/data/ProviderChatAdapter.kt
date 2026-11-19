package com.example.geminidevagent.data

import android.util.Base64
import com.example.geminidevagent.model.AttachmentData
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ToolCallPart(
    val id: String,
    val name: String,
    val args: JSONObject
)

data class ToolResultPart(
    val toolCallId: String,
    val name: String,
    val responseJson: JSONObject,
    val inlineImageBase64: String? = null
)

sealed class UnifiedTurn {
    data class User(val text: String, val attachment: AttachmentData? = null) : UnifiedTurn()
    data class Assistant(
        val text: String,
        val toolCalls: List<ToolCallPart>,
        val rawGeminiContent: JSONObject? = null
    ) : UnifiedTurn()
    data class ToolResponses(val results: List<ToolResultPart>) : UnifiedTurn()
}

data class ProviderTurnOutput(
    val text: String,
    val toolCalls: List<ToolCallPart>,
    val generatedImageBase64: String? = null,
    val rawGeminiContent: JSONObject? = null
)

object ProviderChatAdapter {

    fun executeStreamingTurn(
        httpClient: OkHttpClient,
        entry: ApiKeyEntry,
        modelName: String,
        systemInstruction: String,
        history: List<UnifiedTurn>,
        geminiFunctionDecls: JSONArray,
        mode: AgentMode,
        onStreamDelta: (String) -> Unit
    ): ProviderTurnOutput = when (entry.provider) {
        AiProvider.GEMINI -> executeGeminiTurn(
            httpClient, entry.key, modelName, systemInstruction, history, geminiFunctionDecls, mode, onStreamDelta
        )
        AiProvider.CLAUDE -> executeClaudeTurn(
            httpClient, entry.key, modelName, systemInstruction, history, geminiFunctionDecls, onStreamDelta
        )
        AiProvider.CHATGPT -> executeOpenAiCompatibleTurn(
            httpClient = httpClient,
            baseUrl = "https://api.openai.com/v1",
            apiKey = entry.key,
            modelName = modelName,
            systemInstruction = systemInstruction,
            history = history,
            geminiFunctionDecls = geminiFunctionDecls,
            allowVisionAttachments = true,
            onStreamDelta = onStreamDelta
        )
        AiProvider.KIMI -> executeOpenAiCompatibleTurn(
            httpClient = httpClient,
            baseUrl = entry.baseUrlOverride ?: "https://api.moonshot.ai/v1",
            apiKey = entry.key,
            modelName = modelName,
            systemInstruction = systemInstruction,
            history = history,
            geminiFunctionDecls = geminiFunctionDecls,
            allowVisionAttachments = modelName.contains("vision", true) ||
                modelName.contains("k2.5", true) ||
                modelName.contains("k3", true),
            onStreamDelta = onStreamDelta
        )
    }

    private fun executeGeminiTurn(
        httpClient: OkHttpClient,
        apiKey: String,
        modelName: String,
        systemInstruction: String,
        history: List<UnifiedTurn>,
        geminiFunctionDecls: JSONArray,
        mode: AgentMode,
        onStreamDelta: (String) -> Unit
    ): ProviderTurnOutput {
        val contents = JSONArray()
        val cutoff = (history.size - 4).coerceAtLeast(0)
        history.forEachIndexed { idx, turn ->
            val includeImages = idx >= cutoff
            when (turn) {
                is UnifiedTurn.User -> {
                    val parts = JSONArray()
                    val att = turn.attachment
                    if (att != null && includeImages) {
                        parts.put(
                            JSONObject().put(
                                "inlineData",
                                JSONObject().put("mimeType", att.mimeType).put("data", att.base64)
                            )
                        )
                    } else if (att != null) {
                        parts.put(JSONObject().put("text", "[attachment omitted: ${att.name}]"))
                    }
                    if (turn.text.isNotBlank()) parts.put(JSONObject().put("text", turn.text))
                    contents.put(JSONObject().put("role", "user").put("parts", parts))
                }
                is UnifiedTurn.Assistant -> {
                    if (turn.rawGeminiContent != null) {
                        contents.put(turn.rawGeminiContent)
                    } else {
                        val parts = JSONArray()
                        if (turn.text.isNotBlank()) parts.put(JSONObject().put("text", turn.text))
                        turn.toolCalls.forEach { tc ->
                            parts.put(
                                JSONObject().put(
                                    "functionCall",
                                    JSONObject().put("name", tc.name).put("args", tc.args)
                                )
                            )
                        }
                        contents.put(JSONObject().put("role", "model").put("parts", parts))
                    }
                }
                is UnifiedTurn.ToolResponses -> {
                    val parts = JSONArray()
                    turn.results.forEach { res ->
                        if (!res.inlineImageBase64.isNullOrBlank() && includeImages) {
                            parts.put(
                                JSONObject().put(
                                    "inlineData",
                                    JSONObject().put("mimeType", "image/jpeg").put("data", res.inlineImageBase64)
                                )
                            )
                        }
                        parts.put(
                            JSONObject().put(
                                "functionResponse",
                                JSONObject().put("name", res.name).put("response", res.responseJson)
                            )
                        )
                    }
                    contents.put(JSONObject().put("role", "user").put("parts", parts))
                }
            }
        }

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            put("contents", contents)
            if (geminiFunctionDecls.length() > 0) {
                put("tools", JSONArray().put(JSONObject().put("functionDeclarations", geminiFunctionDecls)))
            }
            if (modelName.contains("gemini-4")) {
                val lvl = when (mode) {
                    AgentMode.MAX -> "XHIGH"
                    AgentMode.FAST -> "LOW"
                    AgentMode.PLAN -> "HIGH"
                }
                put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingLevel", lvl)))
            }
        }

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?alt=sse")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                val msg = runCatching { JSONObject(errBody).getJSONObject("error").getString("message") }
                    .getOrElse { "HTTP ${resp.code}" }
                val reason = if (resp.code == 429) "Quota 429" else "HTTP ${resp.code}"
                throw IllegalStateException("$reason: $msg")
            }

            val source = resp.body?.source() ?: throw IllegalStateException("Empty stream body")
            val accumulatedParts = JSONArray()
            val textBuilder = StringBuilder()
            val toolCalls = mutableListOf<ToolCallPart>()
            var generatedImageBase64: String? = null

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val jsonLine = line.removePrefix("data:").trim()
                if (jsonLine.isEmpty()) continue
                val chunk = runCatching { JSONObject(jsonLine) }.getOrNull() ?: continue
                val cParts = chunk.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts") ?: continue
                for (i in 0 until cParts.length()) {
                    val p = cParts.getJSONObject(i)
                    when {
                        p.has("text") -> {
                            val delta = p.getString("text")
                            textBuilder.append(delta)
                            onStreamDelta(textBuilder.toString())
                        }
                        p.has("functionCall") -> {
                            accumulatedParts.put(p)
                            val fc = p.getJSONObject("functionCall")
                            toolCalls.add(
                                ToolCallPart(
                                    id = "gem_${UUID.randomUUID().toString().take(8)}",
                                    name = fc.getString("name"),
                                    args = fc.optJSONObject("args") ?: JSONObject()
                                )
                            )
                        }
                        p.has("inlineData") -> {
                            accumulatedParts.put(p)
                            val inline = p.getJSONObject("inlineData")
                            if (inline.optString("mimeType").startsWith("image/")) {
                                generatedImageBase64 = inline.optString("data")
                            }
                        }
                        else -> accumulatedParts.put(p)
                    }
                }
            }

            if (textBuilder.isNotEmpty()) {
                accumulatedParts.put(JSONObject().put("text", textBuilder.toString()))
            }

            val rawModelContent = JSONObject()
                .put("role", "model")
                .put("parts", accumulatedParts)

            return ProviderTurnOutput(
                text = textBuilder.toString(),
                toolCalls = toolCalls,
                generatedImageBase64 = generatedImageBase64,
                rawGeminiContent = rawModelContent
            )
        }
    }

    private fun executeClaudeTurn(
        httpClient: OkHttpClient,
        apiKey: String,
        modelName: String,
        systemInstruction: String,
        history: List<UnifiedTurn>,
        geminiFunctionDecls: JSONArray,
        onStreamDelta: (String) -> Unit
    ): ProviderTurnOutput {
        val messages = JSONArray()
        val cutoff = (history.size - 4).coerceAtLeast(0)
        history.forEachIndexed { idx, turn ->
            val includeImages = idx >= cutoff
            when (turn) {
                is UnifiedTurn.User -> {
                    val blocks = JSONArray()
                    val att = turn.attachment
                    if (att != null) {
                        when {
                            att.mimeType.startsWith("image/") && includeImages -> {
                                blocks.put(JSONObject().apply {
                                    put("type", "image")
                                    put("source", JSONObject().apply {
                                        put("type", "base64")
                                        put("media_type", att.mimeType)
                                        put("data", att.base64)
                                    })
                                })
                            }
                            att.mimeType == "application/pdf" && includeImages -> {
                                blocks.put(JSONObject().apply {
                                    put("type", "document")
                                    put("source", JSONObject().apply {
                                        put("type", "base64")
                                        put("media_type", "application/pdf")
                                        put("data", att.base64)
                                    })
                                })
                            }
                            else -> {
                                val textSnippet = decodeAttachmentSnippet(att)
                                blocks.put(JSONObject().put("type", "text").put("text", textSnippet))
                            }
                        }
                    }
                    if (turn.text.isNotBlank()) {
                        blocks.put(JSONObject().put("type", "text").put("text", turn.text))
                    }
                    messages.put(JSONObject().put("role", "user").put("content", blocks))
                }
                is UnifiedTurn.Assistant -> {
                    val blocks = JSONArray()
                    if (turn.text.isNotBlank()) {
                        blocks.put(JSONObject().put("type", "text").put("text", turn.text))
                    }
                    turn.toolCalls.forEach { tc ->
                        blocks.put(JSONObject().apply {
                            put("type", "tool_use")
                            put("id", tc.id)
                            put("name", tc.name)
                            put("input", tc.args)
                        })
                    }
                    if (blocks.length() > 0) {
                        messages.put(JSONObject().put("role", "assistant").put("content", blocks))
                    }
                }
                is UnifiedTurn.ToolResponses -> {
                    val blocks = JSONArray()
                    turn.results.forEach { res ->
                        val toolContent = if (!res.inlineImageBase64.isNullOrBlank() && includeImages) {
                            JSONArray().apply {
                                put(JSONObject().put("type", "text").put("text", res.responseJson.toString()))
                                put(JSONObject().apply {
                                    put("type", "image")
                                    put("source", JSONObject().apply {
                                        put("type", "base64")
                                        put("media_type", "image/jpeg")
                                        put("data", res.inlineImageBase64)
                                    })
                                })
                            }
                        } else {
                            res.responseJson.toString()
                        }
                        blocks.put(JSONObject().apply {
                            put("type", "tool_result")
                            put("tool_use_id", res.toolCallId)
                            put("content", toolContent)
                        })
                    }
                    messages.put(JSONObject().put("role", "user").put("content", blocks))
                }
            }
        }

        val tools = JSONArray()
        for (i in 0 until geminiFunctionDecls.length()) {
            val decl = geminiFunctionDecls.getJSONObject(i)
            tools.put(JSONObject().apply {
                put("name", decl.getString("name"))
                put("description", decl.optString("description"))
                put("input_schema", normalizeJsonSchema(decl.optJSONObject("parameters") ?: JSONObject().put("type", "object")))
            })
        }

        val payload = JSONObject().apply {
            put("model", modelName)
            put("max_tokens", 8192)
            put("system", systemInstruction)
            put("messages", messages)
            if (tools.length() > 0) put("tools", tools)
            put("stream", true)
        }

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                val msg = runCatching { JSONObject(errBody).getJSONObject("error").getString("message") }
                    .getOrElse { "HTTP ${resp.code}" }
                val reason = if (resp.code == 429) "Quota 429" else "HTTP ${resp.code}"
                throw IllegalStateException("$reason: $msg")
            }

            val source = resp.body?.source() ?: throw IllegalStateException("Empty stream body")
            val textBuilder = StringBuilder()
            val toolBlocks = linkedMapOf<Int, Triple<String, String, StringBuilder>>()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val jsonLine = line.removePrefix("data:").trim()
                if (jsonLine.isEmpty()) continue
                val ev = runCatching { JSONObject(jsonLine) }.getOrNull() ?: continue
                when (ev.optString("type")) {
                    "content_block_start" -> {
                        val idx = ev.optInt("index", -1)
                        val block = ev.optJSONObject("content_block") ?: continue
                        if (block.optString("type") == "tool_use") {
                            val id = block.optString("id").ifBlank { "tool_${UUID.randomUUID().toString().take(8)}" }
                            val name = block.optString("name")
                            val initialInput = block.optJSONObject("input")?.takeIf { it.length() > 0 }?.toString().orEmpty()
                            toolBlocks[idx] = Triple(id, name, StringBuilder(initialInput))
                        }
                    }
                    "content_block_delta" -> {
                        val idx = ev.optInt("index", -1)
                        val delta = ev.optJSONObject("delta") ?: continue
                        when (delta.optString("type")) {
                            "text_delta" -> {
                                val t = delta.optString("text")
                                if (t.isNotEmpty()) {
                                    textBuilder.append(t)
                                    onStreamDelta(textBuilder.toString())
                                }
                            }
                            "input_json_delta" -> {
                                val partial = delta.optString("partial_json")
                                toolBlocks[idx]?.third?.append(partial)
                            }
                        }
                    }
                    "error" -> {
                        val errMsg = ev.optJSONObject("error")?.optString("message") ?: "Claude stream error"
                        throw IllegalStateException(errMsg)
                    }
                }
            }

            val calls = toolBlocks.values.mapNotNull { (id, name, jsonSb) ->
                if (name.isBlank()) null
                else {
                    val args = runCatching { JSONObject(jsonSb.toString().ifBlank { "{}" }) }.getOrDefault(JSONObject())
                    ToolCallPart(id, name, args)
                }
            }

            return ProviderTurnOutput(text = textBuilder.toString(), toolCalls = calls)
        }
    }

    private fun executeOpenAiCompatibleTurn(
        httpClient: OkHttpClient,
        baseUrl: String,
        apiKey: String,
        modelName: String,
        systemInstruction: String,
        history: List<UnifiedTurn>,
        geminiFunctionDecls: JSONArray,
        allowVisionAttachments: Boolean,
        onStreamDelta: (String) -> Unit
    ): ProviderTurnOutput {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemInstruction))
        val cutoff = (history.size - 4).coerceAtLeast(0)

        history.forEachIndexed { idx, turn ->
            val includeImages = idx >= cutoff && allowVisionAttachments
            when (turn) {
                is UnifiedTurn.User -> {
                    val att = turn.attachment
                    if (att != null && att.mimeType.startsWith("image/") && includeImages) {
                        val contentArr = JSONArray()
                        if (turn.text.isNotBlank()) contentArr.put(JSONObject().put("type", "text").put("text", turn.text))
                        contentArr.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", "data:${att.mimeType};base64,${att.base64}"))
                        })
                        messages.put(JSONObject().put("role", "user").put("content", contentArr))
                    } else if (att != null) {
                        val combined = buildString {
                            append(decodeAttachmentSnippet(att))
                            if (turn.text.isNotBlank()) {
                                append("\n\n")
                                append(turn.text)
                            }
                        }
                        messages.put(JSONObject().put("role", "user").put("content", combined))
                    } else {
                        messages.put(JSONObject().put("role", "user").put("content", turn.text))
                    }
                }
                is UnifiedTurn.Assistant -> {
                    val msg = JSONObject().put("role", "assistant")
                    msg.put("content", if (turn.text.isNotBlank()) turn.text else JSONObject.NULL)
                    if (turn.toolCalls.isNotEmpty()) {
                        val tcArr = JSONArray()
                        turn.toolCalls.forEach { tc ->
                            tcArr.put(JSONObject().apply {
                                put("id", tc.id)
                                put("type", "function")
                                put("function", JSONObject().put("name", tc.name).put("arguments", tc.args.toString()))
                            })
                        }
                        msg.put("tool_calls", tcArr)
                    }
                    messages.put(msg)
                }
                is UnifiedTurn.ToolResponses -> {
                    turn.results.forEach { res ->
                        messages.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", res.toolCallId)
                            put("content", res.responseJson.toString())
                        })
                    }
                    val imageResults = turn.results.filter { !it.inlineImageBase64.isNullOrBlank() && includeImages }
                    if (imageResults.isNotEmpty()) {
                        val imgArr = JSONArray()
                        imgArr.put(JSONObject().put("type", "text").put("text", "Visual screenshot from tool output:"))
                        imageResults.forEach { r ->
                            imgArr.put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${r.inlineImageBase64}"))
                            })
                        }
                        messages.put(JSONObject().put("role", "user").put("content", imgArr))
                    }
                }
            }
        }

        val tools = JSONArray()
        for (i in 0 until geminiFunctionDecls.length()) {
            val decl = geminiFunctionDecls.getJSONObject(i)
            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", decl.getString("name"))
                    put("description", decl.optString("description"))
                    put("parameters", normalizeJsonSchema(decl.optJSONObject("parameters") ?: JSONObject().put("type", "object")))
                })
            })
        }

        val payload = JSONObject().apply {
            put("model", modelName)
            put("messages", messages)
            if (tools.length() > 0) put("tools", tools)
            put("stream", true)
        }

        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                val msg = runCatching { JSONObject(errBody).getJSONObject("error").getString("message") }
                    .getOrElse { "HTTP ${resp.code}" }
                val reason = if (resp.code == 429) "Quota 429" else "HTTP ${resp.code}"
                throw IllegalStateException("$reason: $msg")
            }

            val source = resp.body?.source() ?: throw IllegalStateException("Empty stream body")
            val textBuilder = StringBuilder()
            val toolAcc = linkedMapOf<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val jsonLine = line.removePrefix("data:").trim()
                if (jsonLine.isEmpty() || jsonLine == "[DONE]") continue
                val chunk = runCatching { JSONObject(jsonLine) }.getOrNull() ?: continue
                val choice = chunk.optJSONArray("choices")?.optJSONObject(0) ?: continue
                val delta = choice.optJSONObject("delta") ?: continue

                if (delta.has("content") && !delta.isNull("content")) {
                    val t = delta.optString("content")
                    if (t.isNotEmpty()) {
                        textBuilder.append(t)
                        onStreamDelta(textBuilder.toString())
                    }
                }

                val tcArray = delta.optJSONArray("tool_calls")
                if (tcArray != null) {
                    for (i in 0 until tcArray.length()) {
                        val tc = tcArray.getJSONObject(i)
                        val idx = tc.optInt("index", i)
                        val entry = toolAcc.getOrPut(idx) { Triple(StringBuilder(), StringBuilder(), StringBuilder()) }
                        tc.optString("id").takeIf { it.isNotBlank() }?.let {
                            if (entry.first.isEmpty()) entry.first.append(it)
                        }
                        val fn = tc.optJSONObject("function")
                        if (fn != null) {
                            fn.optString("name").takeIf { it.isNotEmpty() }?.let { entry.second.append(it) }
                            fn.optString("arguments").takeIf { it.isNotEmpty() }?.let { entry.third.append(it) }
                        }
                    }
                }
            }

            val calls = toolAcc.values.mapNotNull { (idSb, nameSb, argsSb) ->
                val name = nameSb.toString().trim()
                if (name.isEmpty()) null
                else {
                    val id = idSb.toString().ifBlank { "call_${UUID.randomUUID().toString().take(8)}" }
                    val args = runCatching { JSONObject(argsSb.toString().ifBlank { "{}" }) }.getOrDefault(JSONObject())
                    ToolCallPart(id, name, args)
                }
            }

            return ProviderTurnOutput(text = textBuilder.toString(), toolCalls = calls)
        }
    }

    private fun decodeAttachmentSnippet(att: AttachmentData): String {
        val isLikelyText = att.mimeType.startsWith("text/") ||
            att.mimeType.contains("json") ||
            att.mimeType.contains("xml") ||
            att.mimeType.contains("javascript") ||
            att.mimeType.contains("typescript")
        if (!isLikelyText) return "[Attached file: ${att.name} (${att.mimeType})]"
        val decoded = runCatching {
            String(Base64.decode(att.base64, Base64.DEFAULT), Charsets.UTF_8).take(12_000)
        }.getOrNull() ?: return "[Attached file: ${att.name}]"
        return "[Attached file: ${att.name}]\n$decoded"
    }

    private fun normalizeJsonSchema(schema: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = schema.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = schema.get(k)
            when {
                k == "type" && v is String -> out.put("type", v.lowercase())
                v is JSONObject -> out.put(k, normalizeJsonSchema(v))
                v is JSONArray -> {
                    val arr = JSONArray()
                    for (i in 0 until v.length()) {
                        val item = v.get(i)
                        arr.put(if (item is JSONObject) normalizeJsonSchema(item) else item)
                    }
                    out.put(k, arr)
                }
                else -> out.put(k, v)
            }
        }
        return out
    }
}
