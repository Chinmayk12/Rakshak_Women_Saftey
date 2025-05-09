package com.kalpesh.women_safety

import android.text.Html
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kalpesh.women_safety.databinding.ItemMessageBotBinding
import com.kalpesh.women_safety.databinding.ItemMessageUserBinding

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_BOT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> UserMessageViewHolder(
                ItemMessageUserBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            else -> BotMessageViewHolder(
                ItemMessageBotBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserMessageViewHolder -> holder.bind(messages[position])
            is BotMessageViewHolder -> holder.bind(messages[position])
        }
    }

    override fun getItemCount(): Int = messages.size

    // Helper function to convert markdown-like Gemini output to HTML
    private fun markdownToHtml(text: String): String {
        // Bold: **text** or __text__
        var html = text.replace(Regex("\\*\\*(.*?)\\*\\*|__(.*?)__")) {
            "<b>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</b>"
        }
        // Italic: *text* or _text_
        html = html.replace(Regex("\\*(.*?)\\*|_(.*?)_")) {
            "<i>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</i>"
        }
        // New lines to <br>
        html = html.replace("\n", "<br>")
        return html
    }

    inner class UserMessageViewHolder(private val binding: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            val htmlText = markdownToHtml(message.text)
            binding.messageText.text = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)
            binding.messageText.autoLinkMask = Linkify.WEB_URLS
            binding.messageText.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    inner class BotMessageViewHolder(private val binding: ItemMessageBotBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            val htmlText = markdownToHtml(message.text)
            binding.messageText.text = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)
            binding.messageText.autoLinkMask = Linkify.WEB_URLS
            binding.messageText.movementMethod = LinkMovementMethod.getInstance()
        }
    }
}