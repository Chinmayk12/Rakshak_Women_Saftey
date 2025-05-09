package com.kalpesh.women_safety

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.auth.FirebaseAuth
import com.kalpesh.women_safety.databinding.ActivityChatbotBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatbotActivity : AppCompatActivity() {
    private lateinit var logoutButton: ImageView
    private lateinit var binding: ActivityChatbotBinding
    private lateinit var currentLocationOnMap: ImageView
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var generativeModel: GenerativeModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatbotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logoutButton = findViewById(R.id.logoutBtn)
        currentLocationOnMap = findViewById(R.id.location_icon)

        currentLocationOnMap.setOnClickListener {
            val intent = Intent(this, CurrentLocationMap::class.java)
            startActivity(intent)
        }

        logoutButton.setOnClickListener {

            AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Yes") { dialog, _ ->
                    FirebaseAuth.getInstance().signOut()
                    // Optional: Clear activity stack and go to login screen
                    val intent = Intent(this, Login::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
        // Initialize Gemini
        generativeModel = GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = "AIzaSyCVkZ5VnlFRmFE47UzXyEXBitZ10KBKWeY" // Replace with your actual API key
        )



        setupChatRecyclerView()
        setupClickListeners()

        // Add welcome message
        addBotMessage("Hello! I'm your Rakshak assistant. How can I help you today?")
    }

    private fun setupChatRecyclerView() {
        chatAdapter = ChatAdapter(chatMessages)
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatbotActivity)
            adapter = chatAdapter
        }
    }

    private fun setupClickListeners() {
        binding.sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun sendMessage() {
        val message = binding.messageInput.text.toString().trim()
        if (message.isNotEmpty()) {
            addUserMessage(message)
            binding.messageInput.text.clear()
            getGeminiResponse(message)
        }
    }

    private fun addUserMessage(message: String) {
        chatMessages.add(ChatMessage(message, true))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun addBotMessage(message: String) {
        chatMessages.add(ChatMessage(message, false))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun getGeminiResponse(userMessage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = generativeModel.generateContent(userMessage)
                withContext(Dispatchers.Main) {
                    addBotMessage(response.text ?: "I couldn't generate a response.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addBotMessage("Error: ${e.localizedMessage}")
                }
            }
        }
    }
}