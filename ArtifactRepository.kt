package com.example.geminidevagent.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ArtifactType {
    PLAN, WALKTHROUGH, SCREENSHOT, REPORT
}

data class ArtifactItem(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val type: ArtifactType = ArtifactType.PLAN,
    val content: String,
    val imageBase64: String? = null,
    val isApproved: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

class ArtifactRepository(context: Context) {

    private val prefs = context.getSharedPreferences("gemini_artifacts", Context.MODE_PRIVATE)
    private val _artifacts = MutableStateFlow<List<ArtifactItem>>(loadArtifacts())
    val artifacts: StateFlow<List<ArtifactItem>> = _artifacts.asStateFlow()

    fun addArtifact(
        title: String,
        type: ArtifactType,
        content: String,
        imageBase64: String? = null
    ): ArtifactItem {
        val item = ArtifactItem(
            title = title.trim().ifEmpty { "Artifact" },
            type = type,
            content = content,
            imageBase64 = imageBase64,
            isApproved = type != ArtifactType.PLAN
        )
        saveAndEmit(listOf(item) + _artifacts.value)
        return item
    }

    fun approveArtifact(id: String) {
        val updated = _artifacts.value.map { if (it.id == id) it.copy(isApproved = true) else it }
        saveAndEmit(updated)
    }

    fun deleteArtifact(id: String) {
        saveAndEmit(_artifacts.value.filterNot { it.id == id })
    }

    fun clearAll() = saveAndEmit(emptyList())

    private fun saveAndEmit(list: List<ArtifactItem>) {
        _artifacts.value = list
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("title", a.title)
                put("type", a.type.name)
                put("content", a.content)
                if (a.imageBase64 != null) put("imageBase64", a.imageBase64)
                put("isApproved", a.isApproved)
                put("createdAtMillis", a.createdAtMillis)
            })
        }
        prefs.edit().putString(KEY_ARTIFACTS, arr.toString()).apply()
    }

    private fun loadArtifacts(): List<ArtifactItem> {
        val raw = prefs.getString(KEY_ARTIFACTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val type = runCatching { ArtifactType.valueOf(o.optString("type", "PLAN")) }.getOrDefault(ArtifactType.PLAN)
                    add(ArtifactItem(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        type = type,
                        content = o.optString("content", ""),
                        imageBase64 = o.optString("imageBase64", "").ifBlank { null },
                        isApproved = o.optBoolean("isApproved", false),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis())
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_ARTIFACTS = "artifacts_json"
    }
}
