package com.kalpesh.women_safety

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class MainActivity : AppCompatActivity() {

    private var emergencyContacts: List<String> = emptyList()
    private lateinit var btnManualSOS: ImageButton
    private lateinit var btnToggleVoiceSOS: Button
    private lateinit var logoutButton: ImageView
    private lateinit var btnpolicecall: ImageButton
    private lateinit var currentLocationOnMap: ImageView
    private lateinit var emergencyButton: ImageButton
    private lateinit var btnprofile: ImageButton
    private lateinit var personalInformationText: TextView
    private lateinit var  btnnearbyhospital : ImageButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var speechRecognizer: SpeechRecognizer
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isVoiceSOSActive = false
    private val predefinedWords = listOf("help", "help me", "danger","बचाओ", "बचाओ मुझे", "सहायता", "खतरा",
        "वाचवा", "मदत करा", "धोक्याचा इशारा", "मदत" )

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 101
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fetchEmergencyContacts()

        setContentView(R.layout.activity_main)

        btnprofile = findViewById(R.id.personalinfobtn)
        btnpolicecall = findViewById(R.id.callpolicebtn)
        emergencyButton = findViewById(R.id.emergencybtn)
        personalInformationText = findViewById(R.id.personalInformationText)
        btnManualSOS = findViewById(R.id.btnManualSOS)
        logoutButton = findViewById(R.id.logoutBtn)
        currentLocationOnMap = findViewById(R.id.location_icon)
        btnToggleVoiceSOS = findViewById(R.id.toggleVoiceSOS)
        btnnearbyhospital = findViewById(R.id.nearbyhospitalbtn)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        if (!hasPermissions()) {
            requestPermissions()
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        btnManualSOS.setOnClickListener {
            if (hasPermissions()) {
                triggerManualSOS()
            } else {
                Toast.makeText(this, "Please grant all permissions to use this feature.", Toast.LENGTH_SHORT).show()
            }
        }

        btnpolicecall.setOnClickListener {
            val intent = Intent()
            intent.setClassName("com.android.phone", "com.android.phone.MiuiKrEmergencyListActivity")
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "Emergency List activity not found.", Toast.LENGTH_LONG).show()
            }
        }

        currentLocationOnMap.setOnClickListener{
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

        btnToggleVoiceSOS.setOnClickListener {
            if (hasPermissions()) {
                toggleVoiceSOS()
            } else {
                Toast.makeText(this, "Please grant all permissions to use this feature.", Toast.LENGTH_SHORT).show()
            }
        }

        personalInformationText.setOnClickListener{
            val intent = Intent(this, profileActivity::class.java)
            startActivity(intent)
        }

        emergencyButton.setOnClickListener{
            try {
                // Create an explicit intent to open the EmergencyDialerActivity
                val intent = Intent()
                //intent.setClassName("com.google.android.apps.safetyhub", "com.google.android.apps.safetyhub.emergencydialer.ui.EmergencyDialerActivity");
                intent.setClassName(
                    "com.google.android.apps.safetyhub",
                    "com.google.android.apps.safetyhub.LauncherActivity"
                )

                startActivity(intent)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                // Handle the case where the activity is not found or the app is not installed
                val playStoreIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.safetyhub")
                )
                startActivity(playStoreIntent)
            }
        }

        btnprofile.setOnClickListener {
            val intent = Intent(this, profileActivity::class.java)
            startActivity(intent)
        }
        btnnearbyhospital.setOnClickListener{
            val intent = Intent(this, SafetyMapActivity::class.java)
            startActivity(intent)
        }


        // Bottom Navigation
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    true
                }
                R.id.nav_chat -> {
                    val intent = Intent(this, BlogActivity::class.java) // Replace with your SOSActivity
                    startActivity(intent)
                    true
                }
                R.id.nav_chatbot -> {
                    try {
                        val intent = Intent(this, ChatbotActivity::class.java)
                        startActivity(intent)
                        true
//                        val url = "https://wa.me/+14155238886?text=Hello" // Your WhatsApp number
//                        val intent = Intent(Intent.ACTION_VIEW)
//                        intent.data = Uri.parse(url)
//                        startActivity(intent)

                    } catch (e: Exception) {
                        Toast.makeText(this, "WhatsApp is not installed!", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.nav_profilee -> {
                    val intent = Intent(this, ErisAndTapDetectionTestingActivity::class.java) // Replace with your ProfileActivity
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }

    }
    private fun fetchEmergencyContacts() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val database = FirebaseDatabase.getInstance().getReference("Users")
        database.child(userId).addListenerForSingleValueEvent(/* listener = */ object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val contactList = mutableListOf<String>()
                val contact1 = snapshot.child("emergencyContact1").getValue(String::class.java)
                val contact2 = snapshot.child("emergencyContact2").getValue(String::class.java)
                if (!contact1.isNullOrBlank()) contactList.add(contact1)
                if (!contact2.isNullOrBlank()) contactList.add(contact2)
                emergencyContacts = contactList
                Log.d(TAG, "Emergency contacts cached: $emergencyContacts")
            }

            override fun onCancelled(error: DatabaseError) {
                //Toast.makeText(@ErisAndTapDetectionTestingActivity, "Error fetching contacts: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.SEND_SMS
            ),
            REQUEST_CODE_PERMISSIONS
        )
    }

    @SuppressLint("MissingPermission")
    private fun triggerManualSOS() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                sendSOSAlert(location)
            } else {
                Toast.makeText(this, "Unable to fetch location. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleVoiceSOS() {
        isVoiceSOSActive = !isVoiceSOSActive
        if (isVoiceSOSActive) {
            btnToggleVoiceSOS.text = "Disable Voice SOS"
            startVoiceRecognition()
        } else {
            btnToggleVoiceSOS.text = "Enable Voice SOS"
            speechRecognizer.stopListening()
            Toast.makeText(this, "Voice SOS Disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private val languages = listOf("en-US", "hi-IN", "mr-IN")
    private var currentLanguageIndex = 0


    private fun startVoiceRecognition() {
        val selectedLanguage = languages[currentLanguageIndex]

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }


        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@MainActivity, "Listening for SOS commands...", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Toast.makeText(this@MainActivity, "Error occurred: $error", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (match in matches) {
                        if (predefinedWords.contains(match.lowercase())) {
                            triggerVoiceSOS(match)
                            return
                        }
                    }
                }
                Toast.makeText(this@MainActivity, "No SOS word detected", Toast.LENGTH_SHORT).show()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    private fun triggerVoiceSOS(triggeredWord: String) {
        Toast.makeText(this, "Voice SOS Triggered by word: $triggeredWord", Toast.LENGTH_LONG).show()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                sendSOSAlert(location)
            } else {
                Toast.makeText(this, "Unable to fetch location for Voice SOS.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Function to Send SOS Alert
    fun sendSOSAlert(location: Location) {
        val userId = auth.currentUser?.uid ?: return
        val sosMessage = "SOS! I am in danger. My current location: " +
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"


        // List of emergency contacts (replace with real numbers)
        val Contacts = emergencyContacts

        for (contact in Contacts) {
            sendSMS(contact, sosMessage)
        }

        // Storing SOS alert data in Firebase (Optional)
        val sosData = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to System.currentTimeMillis(),
            "userId" to userId
        )

        database.reference.child("sos_alerts").push().setValue(sosData)
            .addOnSuccessListener {
                Toast.makeText(this, "SOS Alert Sent!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send SOS alert.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun sendSMS(phoneNumber: String, message: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 1)
        } else {
            try {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                Toast.makeText(this, "SOS SMS Sent to $phoneNumber", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to send SMS!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to Request SMS Permission
    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.SEND_SMS), 100)
    }

    // Backup Function: Open Default SMS App
    private fun openSMSApp(phoneNumber: String, message: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phoneNumber"))
        intent.putExtra("sms_body", message)
        this@MainActivity.startActivity(intent)
    }



    private fun showSuccessDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("SOS Alert Sent")
        builder.setMessage("Your SOS alert has been sent successfully.")
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }
}