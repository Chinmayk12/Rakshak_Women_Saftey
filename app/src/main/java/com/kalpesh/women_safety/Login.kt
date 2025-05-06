package com.kalpesh.women_safety

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kalpesh.women_safety.databinding.ActivityLoginBinding

import kotlin.math.sign

class Login : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding setup
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        // Edge-to-edge handling
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Check if user is already logged in
        if (auth.currentUser != null) {
            navigateToMainActivity()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.apply {
            btnLogin.setOnClickListener { attemptLogin() }
            tvRegister.setOnClickListener {
                startActivity(Intent(this@Login, Signup::class.java))
            }
            tvForgotPassword.setOnClickListener {
                startActivity(Intent(this@Login, ForgotPassword::class.java))
            }
        }
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        when {
            email.isEmpty() -> {
                binding.etEmail.error = "Email required"
                binding.etEmail.requestFocus()
            }
            password.isEmpty() -> {
                binding.etPassword.error = "Password required"
                binding.etPassword.requestFocus()
            }
            else -> authenticateUser(email, password)
        }
    }

    private fun authenticateUser(email: String, password: String) {
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "Logging in..."

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Login"

                if (task.isSuccessful) {
                    navigateToMainActivity()
                } else {
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            finish()
        })
    }
}