package com.example.geminidevagent.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MemoryItem(
    val id: String = UUID.randomUUID().toString().take(8),
    val fact: String,
    val category: String = "General",
    val source: String = "AI",
    val createdAtMillis: Long = System.currentTimeMillis()
)

class MemoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences("gemini_memory", Context.MODE_PRIVATE)
    private val _memories = MutableStateFlow<List<MemoryItem>>(loadMemories())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    fun addMemory(fact: String, category: String = "General", source: String = "AI"): MemoryItem {
        val cleaned = fact.trim()
        val existing = _memories.value.firstOrNull { it.fact.equals(cleaned, ignoreCase = true) }
        if (existing != null) return existing

        val item = MemoryItem(
            fact = cleaned,
            category = category.trim().ifEmpty { "General" },
            source = source
        )
        saveAndEmit(listOf(item) + _memories.value)
        return item
    }

    fun deleteMemory(queryOrId: String): MemoryItem? {
        val norm = queryOrId.trim().lowercase()
        val target = _memories.value.firstOrNull {
            it.id.lowercase() == norm || it.fact.lowercase().contains(norm)
        } ?: return null
        saveAndEmit(_memories.value.filterNot { it.id == target.id })
        return target
    }

    fun clearAll() {
        saveAndEmit(emptyList())
    }

    fun buildContextPromptBlock(): String {
        val current = _memories.value
        if (current.isEmpty()) return ""
        return buildString {
            append("\n\n[User Memory & Context]\n")
            current.take(25).forEach { m ->
                append("- (${m.category}) ${m.fact}\n")
            }
        }
    }

    private fun saveAndEmit(list: List<MemoryItem>) {
        _memories.value = list
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("fact", m.fact)
                    put("category", m.category)
                    put("source", m.source)
                    put("createdAtMillis", m.createdAtMillis)
                }
            )
        }
        prefs.edit().putString(KEY_MEMORIES_JSON, arr.toString()).apply()
    }

    private fun loadMemories(): List<MemoryItem> {
        val raw = prefs.getString(KEY_MEMORIES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        MemoryItem(
                            id = obj.getString("id"),
                            fact = obj.getString("fact"),
                            category = obj.optString("category", "General"),
                            source = obj.optString("source", "AI"),
                            createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_MEMORIES_JSON = "memories_json"
    }
}
