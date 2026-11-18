package com.example.geminidevagent.model

import java.util.UUID

enum class MessageSender {
    USER, ASSISTANT, TOOL_EVENT, ERROR
}

data class AttachmentData(
    val name: String,
    val mimeType: String,
    val base64: String
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val toolName: String? = null,
    val attachmentName: String? = null,
    val generatedImageBase64: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)
