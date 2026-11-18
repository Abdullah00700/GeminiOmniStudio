package com.example.geminidevagent.tools

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class McpServerConfig(
    val name: String,
    val url: String,
    val authHeader: String = ""
)

data class McpRemoteTool(
    val serverName: String,
    val serverUrl: String,
    val authHeader: String,
    val toolName: String,
    val qualifiedName: String,
    val description: String,
    val inputSchema: JSONObject
)

object McpClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedTools: List<McpRemoteTool> = emptyList()

    fun getCachedTools(): List<McpRemoteTool> = cachedTools

    fun discoverAll(servers: List<McpServerConfig>): List<McpRemoteTool> {
        val discovered = mutableListOf<McpRemoteTool>()
        servers.forEachIndexed { idx, srv ->
            if (srv.url.isBlank()) return@forEachIndexed
            runCatching {
                val rpc = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", idx + 1)
                    put("method", "tools/list")
                    put("params", JSONObject())
                }
                val resp = postJsonRpc(srv.url, srv.authHeader, rpc)
                val toolsArr = resp.optJSONObject("result")?.optJSONArray("tools") ?: JSONArray()
                for (i in 0 until toolsArr.length()) {
                    val t = toolsArr.getJSONObject(i)
                    val rawName = t.getString("name")
                    val safeServer = srv.name.replace(Regex("[^a-zA-Z0-9_]"), "_").ifEmpty { "srv$idx" }
                    val safeTool = rawName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val qualified = "mcp_${safeServer}__${safeTool}".take(64)
                    discovered.add(
                        McpRemoteTool(
                            serverName = srv.name,
                            serverUrl = srv.url,
                            authHeader = srv.authHeader,
                            toolName = rawName,
                            qualifiedName = qualified,
                            description = "[MCP: ${srv.name}] " + t.optString("description", rawName),
                            inputSchema = t.optJSONObject("inputSchema") ?: JSONObject().put("type", "OBJECT")
                        )
                    )
                }
            }
        }
        cachedTools = discovered
        return discovered
    }

    fun callTool(qualifiedName: String, args: JSONObject): AndroidTaskExecutor.ExecutionResult {
        val tool = cachedTools.firstOrNull { it.qualifiedName == qualifiedName }
            ?: return AndroidTaskExecutor.ExecutionResult(
                JSONObject().put("error", "MCP tool not found: $qualifiedName"),
                "Missing MCP tool: $qualifiedName"
            )
        return try {
            val rpc = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", System.currentTimeMillis())
                put("method", "tools/call")
                put("params", JSONObject().apply {
                    put("name", tool.toolName)
                    put("arguments", args)
                })
            }
            val resp = postJsonRpc(tool.serverUrl, tool.authHeader, rpc)
            if (resp.has("error")) {
                val err = resp.getJSONObject("error").toString()
                AndroidTaskExecutor.ExecutionResult(JSONObject().put("error", err), "MCP error: ${tool.toolName}")
            } else {
                val result = resp.optJSONObject("result") ?: JSONObject().put("raw", resp.toString())
                AndroidTaskExecutor.ExecutionResult(result, "MCP [${tool.serverName}]: ${tool.toolName}")
            }
        } catch (e: Exception) {
            AndroidTaskExecutor.ExecutionResult(
                JSONObject().put("error", e.message ?: "MCP call failed"),
                "MCP call failed: ${tool.toolName}"
            )
        }
    }

    private fun postJsonRpc(url: String, authHeader: String, body: JSONObject): JSONObject {
        val rb = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (authHeader.isNotBlank()) rb.addHeader("Authorization", authHeader)

        httpClient.newCall(rb.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${raw.take(200)}")
            // Support either direct JSON-RPC response or SSE-wrapped JSON-RPC line
            val jsonStr = if (raw.trimStart().startsWith("{")) raw else {
                raw.lines().firstOrNull { it.startsWith("data:") }?.removePrefix("data:")?.trim() ?: "{}"
            }
            return JSONObject(jsonStr)
        }
    }
}
