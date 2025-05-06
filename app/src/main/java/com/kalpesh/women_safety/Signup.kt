package com.kalpesh.women_safety

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import com.kalpesh.women_safety.databinding.ActivitySignupBinding
import java.util.regex.Pattern

class Signup : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    // Password pattern: 8+ chars, 1 digit, 1 special char
    private val PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[0-9])(?=.*[!@#\$%^&*])(?=\\S+\$).{8,}\$"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding setup - MUST come first
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        // Initialize Firebase
        auth = Firebase.auth
        database = FirebaseDatabase.getInstance()

        setupClickListeners()
        setupTextWatchers()
    }

    private fun setupClickListeners() {
        binding.apply {
            tvlogin.setOnClickListener {
                navigateToLogin()
            }

            btnRegister.setOnClickListener {
                validateAndRegister()
            }
        }
    }

    private fun setupTextWatchers() {
        binding.apply {
            etName.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) etName.error = null
            }

            etContact.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) etName.error = null
            }
        }
    }

    private fun validateAndRegister() {
        binding.apply {
            val name = etName.text.toString().trim()
            val contact = etContact.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            when {
                name.isEmpty() -> {
                    etName.error = "Full name required"
                    etName.requestFocus()
                }
                contact.isEmpty() || !isValidPhone(contact) -> {
                    etContact.error = "Valid phone number required"
                    etContact.requestFocus()
                }
                email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    etEmail.error = "Valid email required"
                    etEmail.requestFocus()
                }
                password.isEmpty() || !isValidPassword(password) -> {
                    etPassword.error =
                        "Password must be 8+ chars with 1 number & 1 special character"
                    etPassword.requestFocus()
                }
                password != confirmPassword -> {
                    etConfirmPassword.error = "Passwords don't match"
                    etConfirmPassword.requestFocus()
                }
                else -> registerUser(name, contact, email, password)
            }
        }
    }

    private fun isValidPhone(phone: String): Boolean {
        return phone.length in 10..15 && phone.all { it.isDigit() }
    }

    private fun isValidPassword(password: String): Boolean {
        return PASSWORD_PATTERN.matcher(password).matches()
    }

    private fun registerUser(name: String, contact: String, email: String, password: String) {
        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = "Creating account..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    saveUserToDatabase(name, contact, email)
                } else {
                    binding.btnRegister.isEnabled = true
                    binding.btnRegister.text = "Register"
                    val error = task.exception?.message ?: "Registration failed"
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveUserToDatabase(name: String, contact: String, email: String) {
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        val user = User(
            name = name,
            contact = contact,
            email = email,
            emergencyContact = ""
        )

        database.getReference("Users").child(userId).setValue(user)
            .addOnCompleteListener { task ->
                binding.btnRegister.isEnabled = true
                binding.btnRegister.text = "Register"

                if (task.isSuccessful) {
                    navigateToMainActivity()
                } else {
                    Toast.makeText(
                        this,
                        "Database error: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, Login::class.java))
        finishAffinity()
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            finish()
        })

    }
}

data class User(
    val name: String,
    val contact: String,
    val email: String,
    val emergencyContact: String
)