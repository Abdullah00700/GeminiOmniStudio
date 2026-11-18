package com.example.geminidevagent.tools

import org.json.JSONArray
import org.json.JSONObject

object TaskToolDeclarations {

    fun buildToolsArray(
        toggles: Map<String, Boolean>,
        includeScreenControl: Boolean = false,
        isSubagent: Boolean = false
    ): JSONArray {
        val declarations = JSONArray()

        if (toggles["enableDevTools"] != false) {
            declarations.put(functionDecl("create_pdf", "Generate a multi-page PDF document in the workspace.",
                JSONObject().apply {
                    put("filename", prop("STRING", "Output filename, e.g. 'report.pdf'."))
                    put("title", prop("STRING", "Document title."))
                    put("body", prop("STRING", "Full text content."))
                }, listOf("filename", "title", "body")))
            declarations.put(functionDecl("create_spreadsheet", "Generate an Excel-compatible spreadsheet (.xls) with headers and rows.",
                JSONObject().apply {
                    put("filename", prop("STRING", "Output filename, e.g. 'budget.xls'."))
                    put("sheet_name", prop("STRING", "Worksheet name."))
                    put("headers", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING")).put("description", "Column headers."))
                    put("rows", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING"))).put("description", "Table rows."))
                }, listOf("filename", "headers", "rows")))
            declarations.put(functionDecl("create_word_doc", "Generate a Word-compatible document (.doc) with formatted HTML body.",
                JSONObject().apply {
                    put("filename", prop("STRING", "Output filename, e.g. 'proposal.doc'."))
                    put("title", prop("STRING", "Document title."))
                    put("html_body", prop("STRING", "HTML paragraphs, lists, or tables."))
                }, listOf("filename", "title", "html_body")))
            declarations.put(functionDecl("create_presentation", "Generate an interactive slide deck playable in the in-app WebView.",
                JSONObject().apply {
                    put("filename", prop("STRING", "Base filename, e.g. 'pitch_deck'."))
                    put("deck_title", prop("STRING", "Presentation title."))
                    put("slides", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "OBJECT").put("properties", JSONObject().apply {
                        put("title", prop("STRING", "Slide title."))
                        put("body", prop("STRING", "Slide bullet points or text."))
                    })).put("description", "List of slides."))
                }, listOf("filename", "deck_title", "slides")))
            declarations.put(functionDecl("create_meeting_notes", "Save structured meeting notes and automatically add action items to the Tasks tab.",
                JSONObject().apply {
                    put("title", prop("STRING", "Meeting title."))
                    put("summary", prop("STRING", "Key discussion points."))
                    put("action_items", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING")).put("description", "Follow-up tasks."))
                }, listOf("title", "summary", "action_items")))

            declarations.put(functionDecl("workspace_edit_file", "Surgically replace an exact substring in an existing workspace file.",
                JSONObject().apply {
                    put("path", prop("STRING", "Relative file path."))
                    put("old_text", prop("STRING", "Exact existing text to replace."))
                    put("new_text", prop("STRING", "Replacement text."))
                }, listOf("path", "old_text", "new_text")))
            declarations.put(functionDecl("grep_search", "Search for a regex or text pattern across all files in the dev workspace.",
                JSONObject().apply { put("pattern", prop("STRING", "Text or regex pattern.")) }, listOf("pattern")))
            declarations.put(functionDecl("find_by_name", "Find workspace files whose path matches a substring.",
                JSONObject().apply { put("pattern", prop("STRING", "Filename substring.")) }, listOf("pattern")))
            declarations.put(functionDecl("capture_webview_screenshot", "Interactive Visual Browser: render a workspace HTML file or live https:// URL in an Android WebView, optionally run JS (click/type/assert), collect console errors & DOM text, and capture a PNG screenshot for visual inspection.",
                JSONObject().apply {
                    put("path", prop("STRING", "Relative workspace HTML path (e.g. 'index.html') or https:// URL."))
                    put("js_action", prop("STRING", "Optional JavaScript to execute after page load before taking screenshot (e.g. clicking buttons or testing state)."))
                }, listOf("path")))
            declarations.put(functionDecl("create_artifact", "Save a structured Markdown Plan or Walkthrough document into the Artifacts tab for user review.",
                JSONObject().apply {
                    put("title", prop("STRING", "Artifact title."))
                    put("artifact_type", prop("STRING", "'PLAN', 'WALKTHROUGH', or 'REPORT'."))
                    put("content", prop("STRING", "Full Markdown content."))
                }, listOf("title", "artifact_type", "content")))

            declarations.put(functionDecl("workspace_write_file", "Create or overwrite a code, HTML, TypeScript, or config file in the local dev workspace.",
                JSONObject().apply {
                    put("path", prop("STRING", "Relative path, e.g. 'index.html' or 'src/app.ts'."))
                    put("content", prop("STRING", "Full file content."))
                }, listOf("path", "content")))
            declarations.put(functionDecl("workspace_read_file", "Read a file from the local dev workspace.",
                JSONObject().apply { put("path", prop("STRING", "Relative file path.")) }, listOf("path")))
            declarations.put(functionDecl("workspace_list_files", "List all files in the local dev workspace.", JSONObject(), emptyList()))
            declarations.put(functionDecl("workspace_delete_file", "Delete a file from the local dev workspace.",
                JSONObject().apply { put("path", prop("STRING", "Relative file path.")) }, listOf("path")))
            declarations.put(functionDecl("run_shell_command", "Run a shell command inside the sandboxed dev workspace directory.",
                JSONObject().apply { put("command", prop("STRING", "Shell command.")) }, listOf("command")))
            declarations.put(functionDecl("http_request", "Execute a live HTTP REST API request (GET, POST, PUT, DELETE).",
                JSONObject().apply {
                    put("method", prop("STRING", "HTTP method."))
                    put("url", prop("STRING", "Full https:// URL."))
                    put("headers_json", prop("STRING", "Optional JSON headers."))
                    put("body", prop("STRING", "Optional request body."))
                }, listOf("method", "url")))
            declarations.put(functionDecl("encode_decode", "Base64, URL encode/decode, SHA-256, or JWT payload decode.",
                JSONObject().apply {
                    put("operation", prop("STRING", "base64_encode, base64_decode, url_encode, url_decode, sha256, jwt_decode."))
                    put("text", prop("STRING", "Input text."))
                }, listOf("operation", "text")))
            declarations.put(functionDecl("test_regex", "Test a regex pattern against sample text.",
                JSONObject().apply {
                    put("pattern", prop("STRING", "Regex pattern."))
                    put("text", prop("STRING", "Sample text."))
                }, listOf("pattern", "text")))
            declarations.put(functionDecl("format_json", "Validate and pretty-print JSON.",
                JSONObject().apply { put("json_text", prop("STRING", "Raw JSON.")) }, listOf("json_text")))
        }

        if (toggles["enableGitHub"] != false) {
            declarations.put(functionDecl("github_read_file", "Read a file from any GitHub repository.",
                JSONObject().apply {
                    put("owner", prop("STRING", "Owner/org."))
                    put("repo", prop("STRING", "Repo name."))
                    put("path", prop("STRING", "File path."))
                }, listOf("owner", "repo", "path")))
            declarations.put(functionDecl("github_list_issues", "List open issues in a GitHub repository.",
                JSONObject().apply {
                    put("owner", prop("STRING", "Owner/org."))
                    put("repo", prop("STRING", "Repo name."))
                }, listOf("owner", "repo")))
        }

        if (toggles["enableDeviceControl"] != false) {
            declarations.put(functionDecl("send_whatsapp_message", "Send a WhatsApp message to a linked contact name or phone number (with country code).",
                JSONObject().apply {
                    put("recipient", prop("STRING", "Contact name (from linked WhatsApp contacts) or phone number with country code."))
                    put("message", prop("STRING", "Message text to send."))
                }, listOf("recipient", "message")))
            declarations.put(functionDecl("send_whatsapp_file", "Share a generated file (PDF, Excel, Word, HTML) from the workspace directly to WhatsApp.",
                JSONObject().apply {
                    put("path", prop("STRING", "Relative workspace file path, e.g. 'report.pdf'."))
                    put("caption", prop("STRING", "Optional caption text."))
                }, listOf("path")))

            declarations.put(functionDecl("open_chrome_search", "Open Chrome for web, image, video, or news search, or open a URL.",
                JSONObject().apply {
                    put("query", prop("STRING", "Search query or URL."))
                    put("search_type", prop("STRING", "'web', 'images', 'videos', or 'news'."))
                }, listOf("query")))
            declarations.put(functionDecl("open_app", "Launch any installed Android app by name (Chrome, YouTube, WhatsApp, Maps, etc.).",
                JSONObject().apply { put("app_name", prop("STRING", "App display name.")) }, listOf("app_name")))
            declarations.put(functionDecl("toggle_flashlight", "Turn the phone's flashlight on or off.",
                JSONObject().apply { put("enabled", prop("BOOLEAN", "True for on, false for off.")) }, listOf("enabled")))
            declarations.put(functionDecl("open_system_settings", "Open Android Settings (wifi, bluetooth, display, sound, battery, location, general).",
                JSONObject().apply { put("section", prop("STRING", "Settings section.")) }, listOf("section")))
            declarations.put(functionDecl("open_camera", "Open the Camera app in photo or video mode.",
                JSONObject().apply { put("mode", prop("STRING", "'photo' or 'video'.")) }, emptyList()))
            declarations.put(functionDecl("play_music", "Play music or search in the installed music player.",
                JSONObject().apply { put("query", prop("STRING", "Song or artist.")) }, listOf("query")))
            declarations.put(functionDecl("get_battery_status", "Check device battery level and charging status.", JSONObject(), emptyList()))
            declarations.put(functionDecl("copy_to_clipboard", "Copy text to the Android clipboard.",
                JSONObject().apply { put("text", prop("STRING", "Text to copy.")) }, listOf("text")))
            declarations.put(functionDecl("share_text", "Open the Android Share sheet.",
                JSONObject().apply { put("text", prop("STRING", "Text to share.")) }, listOf("text")))
            declarations.put(functionDecl("open_youtube_search", "Search YouTube.",
                JSONObject().apply { put("query", prop("STRING", "Query.")) }, listOf("query")))
            declarations.put(functionDecl("open_maps", "Search Google Maps.",
                JSONObject().apply { put("query", prop("STRING", "Place or address.")) }, listOf("query")))
            declarations.put(functionDecl("dial_phone", "Open Phone dialer with number.",
                JSONObject().apply { put("phone_number", prop("STRING", "Phone number.")) }, listOf("phone_number")))
            declarations.put(functionDecl("send_sms", "Open Messages with pre-filled SMS.",
                JSONObject().apply {
                    put("phone_number", prop("STRING", "Phone number."))
                    put("message", prop("STRING", "Message text."))
                }, listOf("phone_number", "message")))
            declarations.put(functionDecl("set_alarm", "Set an alarm on Android Clock.",
                JSONObject().apply {
                    put("hour", prop("INTEGER", "0-23."))
                    put("minute", prop("INTEGER", "0-59."))
                    put("label", prop("STRING", "Label."))
                }, listOf("hour", "minute")))
            declarations.put(functionDecl("set_timer", "Start a countdown timer.",
                JSONObject().apply {
                    put("seconds", prop("INTEGER", "Duration in seconds."))
                    put("label", prop("STRING", "Label."))
                }, listOf("seconds")))
            declarations.put(functionDecl("create_calendar_event", "Open Calendar to add an event.",
                JSONObject().apply {
                    put("title", prop("STRING", "Title."))
                    put("description", prop("STRING", "Notes."))
                    put("location", prop("STRING", "Location."))
                }, listOf("title")))
            declarations.put(functionDecl("compose_email", "Open Email app with pre-filled draft.",
                JSONObject().apply {
                    put("to", prop("STRING", "Recipient."))
                    put("subject", prop("STRING", "Subject."))
                    put("body", prop("STRING", "Body."))
                }, listOf("to", "subject", "body")))
        }

        if (toggles["enableTasks"] != false) {
            declarations.put(functionDecl("add_task", "Add a task to the user's in-app task list.",
                JSONObject().apply {
                    put("title", prop("STRING", "Title."))
                    put("due_label", prop("STRING", "Optional due label."))
                    put("priority", prop("STRING", "LOW, MEDIUM, or HIGH."))
                }, listOf("title")))
            declarations.put(functionDecl("list_tasks", "List tasks from the in-app task list.",
                JSONObject().apply { put("include_completed", prop("BOOLEAN", "Include completed.")) }, emptyList()))
            declarations.put(functionDecl("complete_task", "Mark a task completed.",
                JSONObject().apply { put("query", prop("STRING", "ID or title keyword.")) }, listOf("query")))
            declarations.put(functionDecl("delete_task", "Delete a task.",
                JSONObject().apply { put("query", prop("STRING", "ID or title keyword.")) }, listOf("query")))
        }

        if (toggles["enableWebSearch"] != false) {
            declarations.put(functionDecl("web_search", "Search Google Search for live web facts, documentation, and verified source URLs.",
                JSONObject().apply { put("query", prop("STRING", "Search query.")) }, listOf("query")))
            declarations.put(functionDecl("fetch_webpage", "Fetch and read text from an https:// URL.",
                JSONObject().apply { put("url", prop("STRING", "URL.")) }, listOf("url")))
        }

        if (toggles["enableMemory"] != false) {
            declarations.put(functionDecl("save_memory", "Save a lasting fact or preference into persistent memory.",
                JSONObject().apply {
                    put("fact", prop("STRING", "Fact to remember."))
                    put("category", prop("STRING", "Category."))
                }, listOf("fact")))
            declarations.put(functionDecl("list_memories", "List saved memories.", JSONObject(), emptyList()))
        }

        if (!isSubagent && toggles["enableSubagents"] != false) {
            declarations.put(functionDecl("spawn_subagent", "Delegate a subtask to a specialized subagent (e.g. 'Frontend Engineer', 'QA Reviewer').",
                JSONObject().apply {
                    put("role", prop("STRING", "Role title."))
                    put("task", prop("STRING", "Instructions."))
                }, listOf("role", "task")))
        }

        if (includeScreenControl) {
            declarations.put(functionDecl("read_screen", "Read visible UI elements on the active screen.", JSONObject(), emptyList()))
            declarations.put(functionDecl("tap_on_screen", "Tap an on-screen element by label.",
                JSONObject().apply { put("label", prop("STRING", "Label.")) }, listOf("label")))
            declarations.put(functionDecl("type_on_screen", "Type text into an on-screen input field.",
                JSONObject().apply {
                    put("field_hint", prop("STRING", "Field hint."))
                    put("text", prop("STRING", "Text to type."))
                }, listOf("text")))
            declarations.put(functionDecl("scroll_screen", "Scroll the current screen up or down.",
                JSONObject().apply { put("direction", prop("STRING", "'up' or 'down'.")) }, listOf("direction")))
            declarations.put(functionDecl("press_navigation", "Press back, home, recents, or notifications.",
                JSONObject().apply { put("action", prop("STRING", "Navigation action.")) }, listOf("action")))
        }

        return JSONArray().apply {
            put(JSONObject().put("functionDeclarations", declarations))
        }
    }

    private fun functionDecl(name: String, description: String, properties: JSONObject, required: List<String>): JSONObject {
        val params = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", properties)
            if (required.isNotEmpty()) put("required", JSONArray(required))
        }
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", params)
        }
    }

    private fun prop(type: String, description: String): JSONObject =
        JSONObject().apply { put("type", type); put("description", description) }
}
