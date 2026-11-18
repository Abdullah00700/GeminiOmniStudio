package com.example.geminidevagent.model

import java.util.UUID

enum class TaskPriority {
    LOW, MEDIUM, HIGH
}

data class TaskItem(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val dueLabel: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)
