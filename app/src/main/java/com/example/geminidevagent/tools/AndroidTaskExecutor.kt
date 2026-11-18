package com.example.geminidevagent.tools

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.geminidevagent.accessibility.AssistantAccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.geminidevagent.data.ArtifactRepository
import com.example.geminidevagent.data.ArtifactType
import com.example.geminidevagent.data.MemoryRepository
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import com.example.geminidevagent.data.SettingsRepository
import com.example.geminidevagent.data.TaskRepository
import com.example.geminidevagent.data.WorkspaceRepository
import com.example.geminidevagent.model.TaskPriority
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class AndroidTaskExecutor(
    private val context: Context,
    private val workspaceRepository: WorkspaceRepository,
    private val taskRepository: TaskRepository,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
    private val artifactRepository: ArtifactRepository
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    data class ExecutionResult(
        val responseJson: JSONObject,
        val userSummary: String
    )

    fun execute(functionName: String, args: JSONObject, allowScreenControl: Boolean = false): ExecutionResult {
        return when (functionName) {
            // Dev Workspace & API tools
            "workspace_edit_file" -> handleEditFile(args)
            "grep_search" -> handleGrepSearch(args)
            "find_by_name" -> handleFindByName(args)
            "capture_webview_screenshot" -> handleCaptureWebView(args)
            "create_artifact" -> handleCreateArtifact(args)
            "create_pdf" -> handleCreatePdf(args)
            "create_spreadsheet" -> handleCreateSpreadsheet(args)
            "create_word_doc" -> handleCreateWordDoc(args)
            "create_presentation" -> handleCreatePresentation(args)
            "create_meeting_notes" -> handleCreateMeetingNotes(args)
            "workspace_write_file" -> handleWriteFile(args)
            "workspace_read_file" -> handleReadFile(args)
            "workspace_list_files" -> handleListFiles()
            "workspace_delete_file" -> handleDeleteFile(args)
            "run_shell_command" -> handleShellCommand(args)
            "http_request" -> handleHttpRequest(args)
            "encode_decode" -> handleEncodeDecode(args)
            "test_regex" -> handleTestRegex(args)
            "format_json" -> handleFormatJson(args)
            "github_read_file" -> handleGithubReadFile(args)
            "github_list_issues" -> handleGithubListIssues(args)
            // Web & Memory
            "web_search" -> handleWebSearch(args)
            "fetch_webpage" -> handleFetchWebpage(args)
            "save_memory" -> handleSaveMemory(args)
            "list_memories" -> handleListMemories()
            // Tasks
            "add_task" -> handleAddTask(args)
            "list_tasks" -> handleListTasks(args)
            "complete_task" -> handleCompleteTask(args)
            "delete_task" -> handleDeleteTask(args)
            // Device Control
            "send_whatsapp_message" -> handleWhatsAppMessage(args)
            "send_whatsapp_file" -> handleWhatsAppFile(args)
            "open_chrome_search" -> handleChromeSearch(args)
            "open_app" -> handleOpenApp(args)
            "toggle_flashlight" -> handleFlashlight(args)
            "open_system_settings" -> handleSystemSettings(args)
            "open_camera" -> handleOpenCamera(args)
            "play_music" -> handlePlayMusic(args)
            "get_battery_status" -> handleBatteryStatus()
            "copy_to_clipboard" -> handleCopyClipboard(args)
            "share_text" -> handleShareText(args)
            "open_youtube_search" -> handleYouTubeSearch(args)
            "open_maps" -> handleOpenMaps(args)
            "dial_phone" -> handleDialPhone(args)
            "send_sms" -> handleSendSms(args)
            "set_alarm" -> handleSetAlarm(args)
            "set_timer" -> handleSetTimer(args)
            "create_calendar_event" -> handleCalendarEvent(args)
            "compose_email" -> handleComposeEmail(args)
            // Accessibility Screen Control
            "read_screen", "tap_on_screen", "type_on_screen", "scroll_screen", "press_navigation" ->
                handleScreenTool(functionName, args, allowScreenControl)
            else -> ExecutionResult(JSONObject().put("error", "Unknown tool: $functionName"), "Unsupported: $functionName")
        }
    }


    private fun handleEditFile(args: JSONObject): ExecutionResult {
        val path = args.optString("path", "")
        val oldText = args.optString("old_text", "")
        val newText = args.optString("new_text", "")
        return try {
            val src = workspaceRepository.readFile(path)
            if (!src.contains(oldText)) {
                return ExecutionResult(JSONObject().put("error", "old_text not found in $path"), "Edit failed: $path")
            }
            workspaceRepository.writeFile(path, src.replaceFirst(oldText, newText))
            ExecutionResult(JSONObject().put("status", "edited").put("path", path), "Edited $path")
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "Edit failed")
        }
    }

    private fun handleGrepSearch(args: JSONObject): ExecutionResult {
        val pattern = args.optString("pattern", "")
        return try {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val hits = JSONArray()
            workspaceRepository.files.value.forEach { wf ->
                val lines = runCatching { workspaceRepository.readFile(wf.relativePath).lines() }.getOrDefault(emptyList())
                lines.forEachIndexed { idx, line ->
                    if (regex.containsMatchIn(line) && hits.length() < 40) {
                        hits.put(JSONObject().put("file", wf.relativePath).put("line", idx + 1).put("text", line.trim().take(200)))
                    }
                }
            }
            ExecutionResult(JSONObject().put("matches", hits), "Grep '$pattern': ${hits.length()} match(es)")
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "Grep error")
        }
    }

    private fun handleFindByName(args: JSONObject): ExecutionResult {
        val pat = args.optString("pattern", "").lowercase()
        val found = workspaceRepository.files.value.map { it.relativePath }.filter { it.lowercase().contains(pat) }
        return ExecutionResult(JSONObject().put("files", JSONArray(found)), "Found ${found.size} file(s)")
    }

    private fun handleCreateArtifact(args: JSONObject): ExecutionResult {
        val title = args.optString("title", "Plan")
        val type = runCatching { ArtifactType.valueOf(args.optString("artifact_type", "PLAN").uppercase()) }.getOrDefault(ArtifactType.PLAN)
        val content = args.optString("content", "")
        val item = artifactRepository.addArtifact(title, type, content)
        return ExecutionResult(JSONObject().put("status", "created").put("id", item.id).put("type", item.type.name), "Created ${item.type.name}: ${item.title}")
    }

    private fun handleCaptureWebView(args: JSONObject): ExecutionResult {
        val target = args.optString("path", "index.html").trim()
        val jsAction = args.optString("js_action", "").trim()
        val isUrl = target.startsWith("https://") || target.startsWith("http://")
        val html = if (!isUrl) {
            runCatching { workspaceRepository.readFile(target) }.getOrElse {
                return ExecutionResult(JSONObject().put("error", "File not found: $target"), "Missing $target")
            }
        } else ""

        if (isUrl) {
            val host = Uri.parse(target).host?.lowercase().orEmpty()
            if (host == "localhost" || host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) {
                return ExecutionResult(JSONObject().put("error", "Blocked private URL"), "Blocked private URL")
            }
        }

        val latch = CountDownLatch(1)
        val consoleErrors = mutableListOf<String>()
        var capturedB64: String? = null
        var domText = ""

        Handler(Looper.getMainLooper()).post {
            try {
                val wv = WebView(context)
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, 720, 1200)
                wv.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                        if (cm.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            consoleErrors.add("${cm.message()} (line ${cm.lineNumber()})")
                        }
                        return true
                    }
                }
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val finishCapture = {
                            wv.evaluateJavascript("document.body ? document.body.innerText.slice(0, 1500) : ''") { txt ->
                                domText = txt.orEmpty().trim('"')
                                try {
                                    val bmp = Bitmap.createBitmap(720, 1200, Bitmap.Config.ARGB_8888)
                                    wv.draw(Canvas(bmp))
                                    val out = ByteArrayOutputStream()
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
                                    capturedB64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                                } finally {
                                    latch.countDown()
                                }
                            }
                        }
                        if (jsAction.isNotBlank()) {
                            wv.evaluateJavascript(jsAction) {
                                Handler(Looper.getMainLooper()).postDelayed(finishCapture, 450)
                            }
                        } else {
                            Handler(Looper.getMainLooper()).postDelayed(finishCapture, 450)
                        }
                    }
                }
                if (isUrl) wv.loadUrl(target) else wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            } catch (_: Exception) {
                latch.countDown()
            }
        }
        latch.await(10, TimeUnit.SECONDS)
        if (capturedB64 != null) {
            artifactRepository.addArtifact("Visual Browser: $target", ArtifactType.SCREENSHOT, "Console errors: ${consoleErrors.size}", capturedB64)
        }
        val resp = JSONObject()
            .put("status", if (capturedB64 != null) "captured" else "failed")
            .put("console_errors", JSONArray(consoleErrors))
            .put("dom_text", domText)
        if (capturedB64 != null) resp.put("_inline_image_base64", capturedB64)
        return ExecutionResult(resp, if (capturedB64 != null) "Visual Browser QA ($target)" else "WebView capture timed out")
    }


    private fun handleCreatePdf(args: JSONObject): ExecutionResult {
        val f = DocumentGenerator.createPdf(workspaceRepository, args.optString("filename", "document.pdf"), args.optString("title", "Document"), args.optString("body", ""))
        return ExecutionResult(JSONObject().put("status", "created").put("path", f.relativePath).put("bytes", f.sizeBytes), "Created PDF: ${f.relativePath}")
    }

    private fun handleCreateSpreadsheet(args: JSONObject): ExecutionResult {
        val f = DocumentGenerator.createExcelXml(
            workspaceRepository,
            args.optString("filename", "sheet.xls"),
            args.optString("sheet_name", "Sheet1"),
            args.optJSONArray("headers") ?: JSONArray(),
            args.optJSONArray("rows") ?: JSONArray()
        )
        return ExecutionResult(JSONObject().put("status", "created").put("path", f.relativePath).put("bytes", f.sizeBytes), "Created Excel sheet: ${f.relativePath}")
    }

    private fun handleCreateWordDoc(args: JSONObject): ExecutionResult {
        val f = DocumentGenerator.createWordDoc(workspaceRepository, args.optString("filename", "doc.doc"), args.optString("title", "Document"), args.optString("html_body", ""))
        return ExecutionResult(JSONObject().put("status", "created").put("path", f.relativePath), "Created Word doc: ${f.relativePath}")
    }

    private fun handleCreatePresentation(args: JSONObject): ExecutionResult {
        val f = DocumentGenerator.createSlideDeck(workspaceRepository, args.optString("filename", "deck"), args.optString("deck_title", "Presentation"), args.optJSONArray("slides") ?: JSONArray())
        return ExecutionResult(JSONObject().put("status", "created").put("path", f.relativePath), "Created Slide Deck: ${f.relativePath}")
    }

    private fun handleCreateMeetingNotes(args: JSONObject): ExecutionResult {
        val title = args.optString("title", "Meeting Notes")
        val summary = args.optString("summary", "")
        val items = args.optJSONArray("action_items") ?: JSONArray()
        val md = buildString {
            append("# $title\n\n$summary\n\n## Action Items\n")
            for (i in 0 until items.length()) {
                val t = items.optString(i)
                if (t.isNotBlank()) {
                    append("- [ ] $t\n")
                    taskRepository.addTask(t, "From $title", TaskPriority.MEDIUM)
                }
            }
        }
        val f = workspaceRepository.writeFile("meeting_${title.lowercase().replace(Regex("[^a-z0-9]+"), "_")}.md", md)
        return ExecutionResult(JSONObject().put("status", "created").put("path", f.relativePath).put("tasks_added", items.length()), "Saved notes & added ${items.length()} task(s)")
    }

    private fun handleWriteFile(args: JSONObject): ExecutionResult {
        val path = args.optString("path", "index.html")
        val content = args.optString("content", "")
        return try {
            val saved = workspaceRepository.writeFile(path, content)
            ExecutionResult(
                JSONObject().put("status", "written").put("path", saved.relativePath).put("bytes", saved.sizeBytes),
                "Wrote ${saved.relativePath}"
            )
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "Write failed")
        }
    }

    private fun handleReadFile(args: JSONObject): ExecutionResult {
        val path = args.optString("path", "")
        return try {
            val text = workspaceRepository.readFile(path).take(8000)
            ExecutionResult(JSONObject().put("path", path).put("content", text), "Read $path")
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "Missing $path")
        }
    }

    private fun handleListFiles(): ExecutionResult {
        workspaceRepository.refresh()
        val arr = JSONArray()
        workspaceRepository.files.value.forEach { f -> arr.put(JSONObject().put("path", f.relativePath).put("bytes", f.sizeBytes)) }
        return ExecutionResult(JSONObject().put("count", arr.length()).put("files", arr), "Listed ${arr.length()} workspace file(s)")
    }

    private fun handleDeleteFile(args: JSONObject): ExecutionResult {
        val path = args.optString("path", "")
        val ok = runCatching { workspaceRepository.deleteFile(path) }.getOrDefault(false)
        return ExecutionResult(JSONObject().put("status", if (ok) "deleted" else "not_found"), if (ok) "Deleted $path" else "Not found: $path")
    }

    private fun handleShellCommand(args: JSONObject): ExecutionResult {
        val cmd = args.optString("command", "").trim()
        return try {
            val proc = ProcessBuilder("sh", "-c", cmd).directory(workspaceRepository.rootDir).redirectErrorStream(true).start()
            val finished = proc.waitFor(10, TimeUnit.SECONDS)
            if (!finished) proc.destroyForcibly()
            val out = proc.inputStream.bufferedReader().readText().take(6000)
            val code = if (finished) proc.exitValue() else -1
            workspaceRepository.refresh()
            ExecutionResult(JSONObject().put("exit_code", code).put("output", out), "Ran `$cmd` (exit $code)")
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "Shell error")
        }
    }

    private fun handleHttpRequest(args: JSONObject): ExecutionResult {
        val method = args.optString("method", "GET").uppercase()
        val url = args.optString("url", "").trim()
        val host = Uri.parse(url).host?.lowercase().orEmpty()
        if (host == "localhost" || host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) {
            return ExecutionResult(JSONObject().put("error", "Blocked private/local network URL."), "Blocked private URL")
        }
        val headersJson = args.optString("headers_json", "")
        val bodyStr = args.optString("body", "")
        return try {
            val b = Request.Builder().url(url)
            if (headersJson.isNotBlank()) {
                val h = JSONObject(headersJson)
                h.keys().forEach { k -> b.addHeader(k, h.getString(k)) }
            }
            val reqBody = if (method in setOf("POST", "PUT", "PATCH")) bodyStr.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()) else null
            b.method(method, reqBody)
            httpClient.newCall(b.build()).execute().use { resp ->
                ExecutionResult(
                    JSONObject().put("status_code", resp.code).put("body", resp.body?.string().orEmpty().take(5000)),
                    "$method $url -> HTTP ${resp.code}"
                )
            }
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "HTTP request failed")
        }
    }

    private fun handleEncodeDecode(args: JSONObject): ExecutionResult {
        val op = args.optString("operation", "").lowercase()
        val text = args.optString("text", "")
        return try {
            val out = when (op) {
                "base64_encode" -> Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
                "base64_decode" -> String(Base64.decode(text, Base64.DEFAULT))
                "url_encode" -> URLEncoder.encode(text, "UTF-8")
                "url_decode" -> URLDecoder.decode(text, "UTF-8")
                "sha256" -> MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
                "jwt_decode" -> {
                    val parts = text.split(".")
                    if (parts.size < 2) "Invalid JWT"
                    else "Header: ${String(Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_WRAP))}\nPayload: ${String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP))}"
                }
                else -> "Unsupported operation"
            }
            ExecutionResult(JSONObject().put("result", out), "Executed $op")
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "Codec error")
        }
    }

    private fun handleTestRegex(args: JSONObject): ExecutionResult {
        val pattern = args.optString("pattern", "")
        val text = args.optString("text", "")
        return try {
            val matches = Regex(pattern).findAll(text).map { it.value }.toList()
            ExecutionResult(JSONObject().put("match_count", matches.size).put("matches", JSONArray(matches)), "Regex matched ${matches.size} time(s)")
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "Invalid regex")
        }
    }

    private fun handleFormatJson(args: JSONObject): ExecutionResult {
        val raw = args.optString("json_text", "").trim()
        return try {
            val formatted = if (raw.startsWith("[")) JSONArray(raw).toString(2) else JSONObject(raw).toString(2)
            ExecutionResult(JSONObject().put("valid", true).put("formatted", formatted.take(6000)), "Formatted JSON")
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("valid", false).put("error", e.message), "Invalid JSON")
        }
    }

    private fun handleGithubReadFile(args: JSONObject): ExecutionResult {
        val owner = args.optString("owner", "")
        val repo = args.optString("repo", "")
        val path = args.optString("path", "").trimStart('/')
        return try {
            val rb = Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Accept", "application/vnd.github.raw+json")
            settingsRepository.getGithubToken().takeIf { it.isNotBlank() }?.let { rb.header("Authorization", "Bearer $it") }
            httpClient.newCall(rb.build()).execute().use { resp ->
                ExecutionResult(JSONObject().put("status", resp.code).put("content", resp.body?.string().orEmpty().take(8000)), "GitHub read $owner/$repo/$path")
            }
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "GitHub read failed")
        }
    }

    private fun handleGithubListIssues(args: JSONObject): ExecutionResult {
        val owner = args.optString("owner", "")
        val repo = args.optString("repo", "")
        return try {
            val rb = Request.Builder().url("https://api.github.com/repos/$owner/$repo/issues?per_page=10").header("Accept", "application/vnd.github+json")
            settingsRepository.getGithubToken().takeIf { it.isNotBlank() }?.let { rb.header("Authorization", "Bearer $it") }
            httpClient.newCall(rb.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return ExecutionResult(JSONObject().put("error", "GitHub HTTP ${resp.code}: ${raw.take(200)}"), "GitHub HTTP ${resp.code}")
                }
                val arr = JSONArray(raw)
                val slim = JSONArray()
                for (i in 0 until minOf(arr.length(), 10)) {
                    val o = arr.getJSONObject(i)
                    slim.put(JSONObject().put("number", o.optInt("number")).put("title", o.optString("title")).put("state", o.optString("state")))
                }
                ExecutionResult(JSONObject().put("issues", slim), "Listed ${slim.length()} GitHub issues")
            }
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "GitHub issues failed")
        }
    }

    private fun handleWebSearch(args: JSONObject): ExecutionResult {
        val query = args.optString("query", "").trim()
        val keys = settingsRepository.getApiKeys()
        if (keys.isEmpty()) {
            return ExecutionResult(JSONObject().put("error", "No Gemini API key configured for Google Search."), "Missing API key")
        }
        val model = settingsRepository.getModelName()
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray().put(
                    JSONObject().put("text", "Search Google and return accurate, concise facts with details for: $query")
                ))
            ))
            put("tools", JSONArray().put(JSONObject().put("googleSearch", JSONObject())))
        }.toString()

        val startIdx = settingsRepository.getActiveKeyIndex().coerceIn(0, keys.lastIndex)
        for (offset in keys.indices) {
            val idx = (startIdx + offset) % keys.size
            try {
                val req = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    .addHeader("x-goog-api-key", keys[idx])
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) continue
                    val json = JSONObject(raw)
                    val cand = json.optJSONArray("candidates")?.optJSONObject(0) ?: continue
                    val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                    val summary = (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text")?.takeIf { t -> t.isNotBlank() } }.joinToString("\n")
                    val sources = JSONArray()
                    val chunks = cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks") ?: JSONArray()
                    for (i in 0 until minOf(chunks.length(), 6)) {
                        val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                        sources.put(JSONObject().put("title", web.optString("title")).put("url", web.optString("uri")))
                    }
                    if (idx != startIdx) settingsRepository.setActiveKeyIndex(idx)
                    return ExecutionResult(
                        JSONObject().put("query", query).put("summary", summary).put("sources", sources),
                        "Google Search: '$query' (${sources.length()} sources)"
                    )
                }
            } catch (_: Exception) {
                continue
            }
        }
        return ExecutionResult(JSONObject().put("error", "Google Search failed across configured API keys."), "Google Search failed")
    }

    private fun handleFetchWebpage(args: JSONObject): ExecutionResult {
        val url = args.optString("url", "").trim()
        val host = Uri.parse(url).host?.lowercase().orEmpty()
        if (host == "localhost" || host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) {
            return ExecutionResult(JSONObject().put("error", "Blocked local/private network URL for security."), "Blocked private URL")
        }
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            httpClient.newCall(req).execute().use { resp ->
                val plain = resp.body?.string().orEmpty()
                    .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ").trim().take(4000)
                ExecutionResult(JSONObject().put("url", url).put("content", plain), "Read $url")
            }
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "Failed to read $url")
        }
    }

    private fun handleSaveMemory(args: JSONObject): ExecutionResult {
        val item = memoryRepository.addMemory(args.optString("fact", ""), args.optString("category", "General"), "AI")
        return ExecutionResult(JSONObject().put("status", "saved").put("fact", item.fact), "Saved memory: ${item.fact}")
    }

    private fun handleListMemories(): ExecutionResult {
        val arr = JSONArray()
        memoryRepository.memories.value.forEach { m -> arr.put(JSONObject().put("id", m.id).put("category", m.category).put("fact", m.fact)) }
        return ExecutionResult(JSONObject().put("count", arr.length()).put("memories", arr), "Read ${arr.length()} memory item(s)")
    }

    private fun handleAddTask(args: JSONObject): ExecutionResult {
        val title = args.optString("title").ifBlank { "Untitled Task" }
        val due = args.optString("due_label", "")
        val prio = runCatching { TaskPriority.valueOf(args.optString("priority", "MEDIUM").uppercase()) }.getOrDefault(TaskPriority.MEDIUM)
        val item = taskRepository.addTask(title, due, prio)
        return ExecutionResult(JSONObject().put("status", "created").put("id", item.id).put("title", item.title), "Added task: ${item.title}")
    }

    private fun handleListTasks(args: JSONObject): ExecutionResult {
        val inc = args.optBoolean("include_completed", false)
        val list = taskRepository.tasks.value.filter { inc || !it.isCompleted }
        val arr = JSONArray()
        list.forEach { t -> arr.put(JSONObject().put("id", t.id).put("title", t.title).put("priority", t.priority.name).put("done", t.isCompleted)) }
        return ExecutionResult(JSONObject().put("count", list.size).put("tasks", arr), "Checked ${list.size} task(s)")
    }

    private fun handleCompleteTask(args: JSONObject): ExecutionResult {
        val c = taskRepository.completeTaskByIdOrTitle(args.optString("query", ""))
        return if (c != null) ExecutionResult(JSONObject().put("status", "completed").put("title", c.title), "Completed: ${c.title}")
        else ExecutionResult(JSONObject().put("status", "not_found"), "Task not found")
    }

    private fun handleDeleteTask(args: JSONObject): ExecutionResult {
        val d = taskRepository.deleteTaskByIdOrTitle(args.optString("query", ""))
        return if (d != null) ExecutionResult(JSONObject().put("status", "deleted").put("title", d.title), "Deleted: ${d.title}")
        else ExecutionResult(JSONObject().put("status", "not_found"), "Task not found")
    }

    private fun handleFlashlight(args: JSONObject): ExecutionResult {
        val enabled = args.optBoolean("enabled", true)
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            if (id != null) {
                cm.setTorchMode(id, enabled)
                ExecutionResult(JSONObject().put("status", "ok").put("enabled", enabled), if (enabled) "Flashlight ON" else "Flashlight OFF")
            } else ExecutionResult(JSONObject().put("status", "no_flash"), "No flashlight")
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "Flashlight failed")
        }
    }

    private fun handleSystemSettings(args: JSONObject): ExecutionResult {
        val sec = args.optString("section", "general").lowercase()
        val action = when (sec) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val ok = launchIntentSafely(Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), "Opened $sec settings")
    }

    private fun handleOpenCamera(args: JSONObject): ExecutionResult {
        val mode = args.optString("mode", "photo").lowercase()
        val action = if (mode == "video") MediaStore.INTENT_ACTION_VIDEO_CAMERA else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        val ok = launchIntentSafely(Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), "Opened Camera ($mode)")
    }

    private fun handlePlayMusic(args: JSONObject): ExecutionResult {
        val q = args.optString("query", "").trim()
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, q)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = launchIntentSafely(intent)
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), if (ok) "Playing '$q'" else "No music app")
    }

    private fun handleBatteryStatus(): ExecutionResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return ExecutionResult(JSONObject().put("battery_percent", lvl).put("is_charging", bm.isCharging), "Battery: $lvl%")
    }

    private fun handleCopyClipboard(args: JSONObject): ExecutionResult {
        val text = args.optString("text", "")
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("Gemini", text))
        return ExecutionResult(JSONObject().put("status", "copied"), "Copied to clipboard")
    }

    private fun handleShareText(args: JSONObject): ExecutionResult {
        val text = args.optString("text", "")
        val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, text); type = "text/plain" }, "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = launchIntentSafely(chooser)
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), "Opened Share sheet")
    }


    private fun handleWhatsAppMessage(args: JSONObject): ExecutionResult {
        val recipient = args.optString("recipient", "").trim()
        val message = args.optString("message", "").trim()
        val digits = settingsRepository.resolveWhatsAppPhone(recipient)
        if (digits.length < 7) {
            return ExecutionResult(
                JSONObject().put("error", "No valid phone number for '$recipient'. Add them in Settings -> WhatsApp Contacts or provide a number with country code."),
                "Missing phone for '$recipient'"
            )
        }
        val uri = Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}")
        val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val ok = launchIntentSafely(waIntent) ||
            launchIntentSafely(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp.w4b"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) ||
            launchIntentSafely(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        return ExecutionResult(
            JSONObject().put("status", if (ok) "opened_whatsapp" else "whatsapp_not_found").put("phone", digits),
            if (ok) "Opened WhatsApp to $recipient" else "WhatsApp not installed"
        )
    }

    private fun handleWhatsAppFile(args: JSONObject): ExecutionResult {
        val path = args.optString("path", "").trim()
        val caption = args.optString("caption", "")
        return try {
            val file = workspaceRepository.resolveSafe(path)
            if (!file.exists()) return ExecutionResult(JSONObject().put("error", "File not found: $path"), "Missing $path")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val ok = launchIntentSafely(intent) ||
                launchIntentSafely(Intent(intent).apply { setPackage("com.whatsapp.w4b") })
            ExecutionResult(
                JSONObject().put("status", if (ok) "shared_to_whatsapp" else "whatsapp_not_found").put("path", path),
                if (ok) "Sent $path to WhatsApp" else "WhatsApp not installed"
            )
        } catch (e: Exception) {
            ExecutionResult(JSONObject().put("error", e.message), "WhatsApp file share failed")
        }
    }

    private fun handleChromeSearch(args: JSONObject): ExecutionResult {
        val q = args.optString("query", "").trim()
        val st = args.optString("search_type", "web").lowercase()
        val url = if (q.startsWith("http")) q else {
            val enc = Uri.encode(q)
            when (st) {
                "images" -> "https://www.google.com/search?q=$enc&udm=2"
                "videos" -> "https://www.google.com/search?q=$enc&tbm=vid"
                "news" -> "https://www.google.com/search?q=$enc&tbm=nws"
                else -> "https://www.google.com/search?q=$enc"
            }
        }
        val chrome = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage("com.android.chrome"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val ok = launchIntentSafely(chrome) || launchIntentSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed").put("url", url), "Opened Chrome for '$q'")
    }

    private fun handleOpenApp(args: JSONObject): ExecutionResult {
        val appName = args.optString("app_name", "").trim()
        val norm = appName.lowercase()
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == norm } ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(norm) }
        val ok = match?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }?.let { launchIntentSafely(it) } ?: false
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "not_found"), if (ok) "Opened ${match?.loadLabel(pm)}" else "App '$appName' not found")
    }

    private fun handleYouTubeSearch(args: JSONObject): ExecutionResult {
        val q = args.optString("query", "").trim()
        val ok = launchIntentSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), "Searched YouTube: '$q'")
    }

    private fun handleOpenMaps(args: JSONObject): ExecutionResult {
        val q = args.optString("query", "").trim()
        val ok = launchIntentSafely(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(q)}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), "Opened Maps: '$q'")
    }

    private fun handleDialPhone(args: JSONObject): ExecutionResult {
        val phone = args.optString("phone_number", "").trim()
        val ok = launchIntentSafely(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), "Opened Dialer: $phone")
    }

    private fun handleSendSms(args: JSONObject): ExecutionResult {
        val phone = args.optString("phone_number", "").trim()
        val msg = args.optString("message", "").trim()
        val ok = launchIntentSafely(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}")).apply { putExtra("sms_body", msg); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), "Opened Messages to $phone")
    }

    private fun handleSetAlarm(args: JSONObject): ExecutionResult {
        val h = args.optInt("hour", 8).coerceIn(0, 23)
        val m = args.optInt("minute", 0).coerceIn(0, 59)
        val lbl = args.optString("label", "Gemini Alarm")
        val ok = launchIntentSafely(Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, h); putExtra(AlarmClock.EXTRA_MINUTES, m); putExtra(AlarmClock.EXTRA_MESSAGE, lbl); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), String.format("Alarm %02d:%02d", h, m))
    }

    private fun handleSetTimer(args: JSONObject): ExecutionResult {
        val s = args.optInt("seconds", 60).coerceAtLeast(1)
        val lbl = args.optString("label", "Gemini Timer")
        val ok = launchIntentSafely(Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, s); putExtra(AlarmClock.EXTRA_MESSAGE, lbl); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), "Timer ${s}s")
    }

    private fun handleCalendarEvent(args: JSONObject): ExecutionResult {
        val t = args.optString("title", "Event")
        val ok = launchIntentSafely(Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, t)
            args.optString("description", "").takeIf { it.isNotBlank() }?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            args.optString("location", "").takeIf { it.isNotBlank() }?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), "Calendar: '$t'")
    }

    private fun handleComposeEmail(args: JSONObject): ExecutionResult {
        val to = args.optString("to", "")
        val ok = launchIntentSafely(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(to)}")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, args.optString("subject", ""))
            putExtra(Intent.EXTRA_TEXT, args.optString("body", ""))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return ExecutionResult(JSONObject().put("status", if (ok) "opened" else "failed"), "Email draft to $to")
    }

    private fun handleScreenTool(name: String, args: JSONObject, allowed: Boolean): ExecutionResult {
        if (!allowed) return ExecutionResult(JSONObject().put("status", "disabled"), "Screen Control is off")
        val svc = AssistantAccessibilityService.instance ?: return ExecutionResult(JSONObject().put("status", "not_connected"), "Accessibility not enabled")
        return when (name) {
            "read_screen" -> {
                val nodes = svc.readVisibleElements()
                ExecutionResult(JSONObject().put("elements", nodes), "Read ${nodes.length()} UI elements")
            }
            "tap_on_screen" -> {
                val lbl = args.optString("label", "")
                val ok = svc.tapByText(lbl)
                ExecutionResult(JSONObject().put("status", if (ok) "tapped" else "not_found"), if (ok) "Tapped '$lbl'" else "Not found: '$lbl'")
            }
            "type_on_screen" -> {
                val ok = svc.typeIntoField(args.optString("field_hint", ""), args.optString("text", ""))
                ExecutionResult(JSONObject().put("status", if (ok) "typed" else "failed"), if (ok) "Typed text" else "No input field")
            }
            "scroll_screen" -> {
                val dir = args.optString("direction", "down")
                ExecutionResult(JSONObject().put("status", if (svc.scroll(dir)) "scrolled" else "failed"), "Scrolled $dir")
            }
            "press_navigation" -> {
                val act = args.optString("action", "back")
                ExecutionResult(JSONObject().put("status", if (svc.performNavAction(act)) "ok" else "failed"), "Pressed $act")
            }
            else -> ExecutionResult(JSONObject().put("error", "Unknown"), "Unknown")
        }
    }

    private fun launchIntentSafely(intent: Intent): Boolean =
        try { context.startActivity(intent); true } catch (_: ActivityNotFoundException) { false } catch (_: Exception) { false }
}
