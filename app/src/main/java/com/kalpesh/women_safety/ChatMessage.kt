package com.kalpesh.women_safety

data class ChatMessage(
    val text: String,
    val isUser: Boolean, // true=user message, false=bot message
    val timestamp: Long = System.currentTimeMillis()
)