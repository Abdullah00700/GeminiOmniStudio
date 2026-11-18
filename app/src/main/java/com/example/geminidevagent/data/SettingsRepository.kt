package com.example.geminidevagent.data

import android.content.Context
import com.example.geminidevagent.BuildConfig
import com.example.geminidevagent.tools.McpServerConfig
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("gemini_omni_settings", Context.MODE_PRIVATE)

    fun getApiKeys(): List<String> {
        val raw = prefs.getString(KEY_API_KEYS_JSON, null)
        val parsed = if (!raw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val k = arr.getString(i).trim()
                        if (k.isNotEmpty()) add(k)
                    }
                }
            }.getOrDefault(emptyList())
        } else emptyList()

        if (parsed.isNotEmpty()) return parsed
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        return if (buildKey.isNotEmpty()) listOf(buildKey) else emptyList()
    }

    fun saveApiKeys(keys: List<String>) {
        val cleaned = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val arr = JSONArray().apply { cleaned.forEach { put(it) } }
        prefs.edit().putString(KEY_API_KEYS_JSON, arr.toString()).putInt(KEY_ACTIVE_INDEX, 0).apply()
    }

    fun getActiveKeyIndex(): Int {
        val keys = getApiKeys()
        if (keys.isEmpty()) return 0
        return prefs.getInt(KEY_ACTIVE_INDEX, 0).coerceIn(0, keys.lastIndex)
    }

    fun setActiveKeyIndex(index: Int) = prefs.edit().putInt(KEY_ACTIVE_INDEX, index.coerceAtLeast(0)).apply()

    fun getModelName(): String =
        prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL)?.trim()?.ifEmpty { DEFAULT_MODEL } ?: DEFAULT_MODEL

    fun saveModelName(modelName: String) =
        prefs.edit().putString(KEY_MODEL_NAME, modelName.trim().ifEmpty { DEFAULT_MODEL }).apply()

    fun getCustomInstructions(): String = prefs.getString(KEY_CUSTOM_INSTR, "")?.trim().orEmpty()
    fun saveCustomInstructions(text: String) = prefs.edit().putString(KEY_CUSTOM_INSTR, text.trim()).apply()

    fun getLinkedAccount(): Pair<String, String>? {
        val email = prefs.getString(KEY_ACC_EMAIL, "")?.trim().orEmpty()
        if (email.isEmpty()) return null
        val name = prefs.getString(KEY_ACC_NAME, "")?.trim().orEmpty().ifEmpty { email.substringBefore("@") }
        return name to email
    }

    fun saveLinkedAccount(name: String, email: String) {
        prefs.edit().putString(KEY_ACC_NAME, name.trim()).putString(KEY_ACC_EMAIL, email.trim()).apply()
    }

    fun clearLinkedAccount() {
        prefs.edit().remove(KEY_ACC_NAME).remove(KEY_ACC_EMAIL).apply()
    }

    fun isScreenControlEnabled(): Boolean = prefs.getBoolean(KEY_SCREEN_CONTROL, false)
    fun setScreenControlEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SCREEN_CONTROL, enabled).apply()

    fun isVoiceModeEnabled(): Boolean = prefs.getBoolean(KEY_VOICE_MODE, false)
    fun setVoiceModeEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_VOICE_MODE, enabled).apply()

    /** Approval filter: skip all prompts, or auto-allow chosen tools. Keys: tool name, "mcp_*" for MCP tools. */
    fun getApprovalSkipAll(): Boolean = prefs.getBoolean(KEY_APPROVAL_SKIP_ALL, false)
    fun getAlwaysAllowedTools(): Set<String> = prefs.getStringSet(KEY_ALWAYS_ALLOWED, emptySet())?.toSet().orEmpty()
    fun saveApproval(skipAll: Boolean, allowed: Set<String>) =
        prefs.edit().putBoolean(KEY_APPROVAL_SKIP_ALL, skipAll).putStringSet(KEY_ALWAYS_ALLOWED, allowed).apply()
    fun addAlwaysAllowed(key: String) = prefs.edit().putStringSet(KEY_ALWAYS_ALLOWED, getAlwaysAllowedTools() + key).apply()

    fun getGithubToken(): String = prefs.getString(KEY_GITHUB_TOKEN, "")?.trim().orEmpty()
    fun saveGithubToken(token: String) = prefs.edit().putString(KEY_GITHUB_TOKEN, token.trim()).apply()

    fun getFeatureToggles(): Map<String, Boolean> {
        val raw = prefs.getString(KEY_TOGGLES, null)
        val defaults = DEFAULT_TOGGLES.toMutableMap()
        if (!raw.isNullOrBlank()) {
            runCatching {
                val obj = JSONObject(raw)
                obj.keys().forEach { k -> defaults[k] = obj.optBoolean(k, true) }
            }
        }
        return defaults
    }

    fun saveFeatureToggles(toggles: Map<String, Boolean>) {
        val obj = JSONObject()
        toggles.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(KEY_TOGGLES, obj.toString()).apply()
    }


    fun getWhatsAppContacts(): Map<String, String> {
        val raw = prefs.getString("whatsapp_contacts", null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap { obj.keys().forEach { k -> put(k, obj.getString(k)) } }
        }.getOrDefault(emptyMap())
    }

    fun saveWhatsAppContacts(contacts: Map<String, String>) {
        val obj = JSONObject()
        contacts.forEach { (name, phone) ->
            if (name.isNotBlank() && phone.isNotBlank()) obj.put(name.trim(), phone.trim())
        }
        prefs.edit().putString("whatsapp_contacts", obj.toString()).apply()
    }

    fun resolveWhatsAppPhone(nameOrPhone: String): String {
        val contacts = getWhatsAppContacts()
        val match = contacts.entries.firstOrNull { it.key.equals(nameOrPhone.trim(), ignoreCase = true) }
            ?: contacts.entries.firstOrNull { it.key.lowercase().contains(nameOrPhone.trim().lowercase()) }
        val raw = match?.value ?: nameOrPhone
        return raw.replace(Regex("[^0-9+]"), "").removePrefix("+")
    }


    fun getMcpServers(): List<McpServerConfig> {
        val raw = prefs.getString("mcp_servers", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(McpServerConfig(o.optString("name"), o.optString("url"), o.optString("authHeader")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveMcpServers(servers: List<McpServerConfig>) {
        val arr = JSONArray()
        servers.filter { it.url.isNotBlank() }.forEach { s ->
            arr.put(JSONObject().put("name", s.name.ifBlank { "MCP" }).put("url", s.url.trim()).put("authHeader", s.authHeader.trim()))
        }
        prefs.edit().putString("mcp_servers", arr.toString()).apply()
    }

    companion object {
        private const val KEY_API_KEYS_JSON = "api_keys_json"
        private const val KEY_ACTIVE_INDEX = "active_key_index"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_CUSTOM_INSTR = "custom_instructions"
        private const val KEY_APPROVAL_SKIP_ALL = "approval_skip_all"
        private const val KEY_ALWAYS_ALLOWED = "approval_always_allowed"
        private const val KEY_ACC_NAME = "acc_name"
        private const val KEY_ACC_EMAIL = "acc_email"
        private const val KEY_SCREEN_CONTROL = "screen_control_enabled"
        private const val KEY_VOICE_MODE = "voice_mode_enabled"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_TOGGLES = "feature_toggles"
        const val DEFAULT_MODEL = "gemini-4-hydrogen"

        val DEFAULT_TOGGLES = mapOf(
            "enableDeviceControl" to true,
            "enableDevTools" to true,
            "enableGitHub" to true,
            "enableTasks" to true,
            "enableWebSearch" to true,
            "enableMemory" to true,
            "enableSubagents" to true
        )
    }
}
