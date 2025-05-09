package com.kalpesh.women_safety

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class profileActivity : AppCompatActivity() {

    private lateinit var etName: TextInputEditText
    private lateinit var etNumber: TextInputEditText
    private lateinit var etAddress: TextInputEditText
    private lateinit var etTaluka: TextInputEditText
    private lateinit var etDistrict: TextInputEditText
    private lateinit var etState: TextInputEditText
    private lateinit var etEmergency1: TextInputEditText
    private lateinit var etEmergency2: TextInputEditText
    private lateinit var btnSaveUpdate: Button
    private lateinit var database: DatabaseReference
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_information)

        // Initialize views
        etName = findViewById(R.id.et_name)
        etNumber = findViewById(R.id.et_number)
        etAddress = findViewById(R.id.et_address)
        etTaluka = findViewById(R.id.et_taluka)
        etDistrict = findViewById(R.id.et_district)
        etState = findViewById(R.id.et_state)
        etEmergency1 = findViewById(R.id.et_emergency1)
        etEmergency2 = findViewById(R.id.et_emergency2)
        btnSaveUpdate = findViewById(R.id.btn_save_update)

        // Initialize Firebase database reference
        database = FirebaseDatabase.getInstance().getReference("Users")

        // Check if user exists
        checkUserExists()

        // Save/Update information
        btnSaveUpdate.setOnClickListener {
            if (validateInput()) {
                saveOrUpdateInfo()
            }
        }
    }

    private fun checkUserExists() {
        database.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Populate fields with existing data
                    val userInfo = snapshot.getValue(UserInfo::class.java)
                    userInfo?.let {
                        etName.setText(it.name)
                        etNumber.setText(it.number)
                        etAddress.setText(it.address)
                        etTaluka.setText(it.taluka)
                        etDistrict.setText(it.district)
                        etState.setText(it.state)
                        etEmergency1.setText(it.emergencyContact1)
                        etEmergency2.setText(it.emergencyContact2)
                    }
                    btnSaveUpdate.text = "Update Information"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@profileActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun validateInput(): Boolean {
        var isValid = true

        val name = etName.text.toString().trim()
        val number = etNumber.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val taluka = etTaluka.text.toString().trim()
        val district = etDistrict.text.toString().trim()
        val state = etState.text.toString().trim()
        val emergency1 = etEmergency1.text.toString().trim()
        val emergency2 = etEmergency2.text.toString().trim()

        if (name.isEmpty()) {
            etName.error = "Name is required"
            isValid = false
        } else {
            etName.error = null
        }

        if (number.isEmpty()) {
            etNumber.error = "Phone number is required"
            isValid = false
        } else if (!number.matches(Regex("^[0-9]{10}$"))) {
            etNumber.error = "Enter a valid 10-digit number"
            isValid = false
        } else {
            etNumber.error = null
        }

        if (address.isEmpty()) {
            etAddress.error = "Address is required"
            isValid = false
        } else {
            etAddress.error = null
        }

        if (taluka.isEmpty()) {
            etTaluka.error = "Taluka is required"
            isValid = false
        } else {
            etTaluka.error = null
        }

        if (district.isEmpty()) {
            etDistrict.error = "District is required"
            isValid = false
        } else {
            etDistrict.error = null
        }

        if (state.isEmpty()) {
            etState.error = "State is required"
            isValid = false
        } else {
            etState.error = null
        }

        if (emergency1.isEmpty()) {
            etEmergency1.error = "Emergency contact 1 is required"
            isValid = false
        } else if (!emergency1.matches(Regex("^[0-9]{10}$"))) {
            etEmergency1.error = "Enter a valid 10-digit number"
            isValid = false
        } else {
            etEmergency1.error = null
        }

        // Emergency 2 is optional, but if filled, it must be valid
        if (emergency2.isNotEmpty() && !emergency2.matches(Regex("^[0-9]{10}$"))) {
            etEmergency2.error = "Enter a valid 10-digit number"
            isValid = false
        } else {
            etEmergency2.error = null
        }

        // Prevent both emergency contacts from being the same
        if (emergency1.isNotEmpty() && emergency2.isNotEmpty() && emergency1 == emergency2) {
            etEmergency2.error = "Emergency contacts should be different"
            isValid = false
        }

        if (!isValid) {
            Toast.makeText(this, "Please correct the errors", Toast.LENGTH_SHORT).show()
        }

        return isValid
    }

    private fun saveOrUpdateInfo() {
        val userInfo = UserInfo(
            name = etName.text.toString(),
            number = etNumber.text.toString(),
            address = etAddress.text.toString(),
            taluka = etTaluka.text.toString(),
            district = etDistrict.text.toString(),
            state = etState.text.toString(),
            emergencyContact1 = etEmergency1.text.toString(),
            emergencyContact2 = etEmergency2.text.toString()
        )

        database.child(userId).setValue(userInfo).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Information saved/updated successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save information.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// Data class for user information
data class UserInfo(
    val name: String = "",
    val number: String = "",
    val address: String = "",
    val taluka: String = "",
    val district: String = "",
    val state: String = "",
    val emergencyContact1: String = "",
    val emergencyContact2: String = ""
)