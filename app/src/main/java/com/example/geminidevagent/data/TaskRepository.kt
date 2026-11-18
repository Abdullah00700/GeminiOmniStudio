package com.example.geminidevagent.data

import android.content.Context
import com.example.geminidevagent.model.TaskItem
import com.example.geminidevagent.model.TaskPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class TaskRepository(context: Context) {

    private val prefs = context.getSharedPreferences("gemini_tasks", Context.MODE_PRIVATE)
    private val _tasks = MutableStateFlow<List<TaskItem>>(loadTasks())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    fun addTask(title: String, dueLabel: String = "", priority: TaskPriority = TaskPriority.MEDIUM): TaskItem {
        val item = TaskItem(
            title = title.trim(),
            dueLabel = dueLabel.trim(),
            priority = priority
        )
        val updated = listOf(item) + _tasks.value
        saveAndEmit(updated)
        return item
    }

    fun toggleTask(id: String): TaskItem? {
        var toggled: TaskItem? = null
        val updated = _tasks.value.map { task ->
            if (task.id == id) {
                task.copy(isCompleted = !task.isCompleted).also { toggled = it }
            } else {
                task
            }
        }
        if (toggled != null) saveAndEmit(updated)
        return toggled
    }

    fun completeTaskByIdOrTitle(query: String): TaskItem? {
        val normalized = query.trim().lowercase()
        var matched: TaskItem? = null
        val updated = _tasks.value.map { task ->
            if (matched == null && !task.isCompleted &&
                (task.id.lowercase() == normalized || task.title.lowercase().contains(normalized))
            ) {
                task.copy(isCompleted = true).also { matched = it }
            } else {
                task
            }
        }
        if (matched != null) saveAndEmit(updated)
        return matched
    }

    fun deleteTaskByIdOrTitle(query: String): TaskItem? {
        val normalized = query.trim().lowercase()
        val target = _tasks.value.firstOrNull {
            it.id.lowercase() == normalized || it.title.lowercase().contains(normalized)
        } ?: return null
        saveAndEmit(_tasks.value.filterNot { it.id == target.id })
        return target
    }

    fun clearCompleted() {
        saveAndEmit(_tasks.value.filterNot { it.isCompleted })
    }

    private fun saveAndEmit(list: List<TaskItem>) {
        _tasks.value = list
        val arr = JSONArray()
        list.forEach { task ->
            arr.put(
                JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("dueLabel", task.dueLabel)
                    put("priority", task.priority.name)
                    put("isCompleted", task.isCompleted)
                    put("createdAtMillis", task.createdAtMillis)
                }
            )
        }
        prefs.edit().putString(KEY_TASKS_JSON, arr.toString()).apply()
    }

    private fun loadTasks(): List<TaskItem> {
        val raw = prefs.getString(KEY_TASKS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val priority = runCatching {
                        TaskPriority.valueOf(obj.optString("priority", "MEDIUM"))
                    }.getOrDefault(TaskPriority.MEDIUM)
                    add(
                        TaskItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            dueLabel = obj.optString("dueLabel", ""),
                            priority = priority,
                            isCompleted = obj.optBoolean("isCompleted", false),
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
        private const val KEY_TASKS_JSON = "tasks_json"
    }
}
