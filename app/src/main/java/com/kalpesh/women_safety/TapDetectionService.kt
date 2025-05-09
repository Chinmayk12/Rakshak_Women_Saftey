package com.kalpesh.women_safety

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlin.math.abs

class TapDetectionService : LifecycleService(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // Tap pattern detection variables
    private var tapPattern = mutableListOf<Long>()
    private var lastTapTime: Long = 0
    private var calibrationTapPattern = mutableListOf<Long>()

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var vibrator: Vibrator
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var locationManager: EmergencyLocationManager

    // For tap detection - REDUCED THRESHOLD to match ErisActivity
    private val tapThreshold = 7.5f // Matching ErisActivity's threshold
    private val cooldownPeriod = 500L // Same as in ErisActivity

    companion object {
        private const val CHANNEL_ID = "TapSOS_Service_Channel"
        private const val NOTIFICATION_ID = 2
        private const val TAG = "TapDetectionService"
        var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        initializeServiceComponents()
    }

    private fun initializeServiceComponents() {
        createNotificationChannel()
        setupSensors()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        setupWakeLock()

        // Initialize our location manager
        locationManager = EmergencyLocationManager.getInstance(this)
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            Log.e(TAG, "No accelerometer found on device")
        }
    }

    private fun setupWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TapDetection:WakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes wake lock
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            "START_DETECTION" -> {
                // Get the calibration pattern if provided
                intent.getLongArrayExtra("calibrationPattern")?.let {
                    calibrationTapPattern = it.toMutableList()
                    Log.d(TAG, "Received calibration pattern: $calibrationTapPattern")
                }
                startDetection()
            }
            "STOP_DETECTION" -> stopDetection()
        }

        return START_STICKY
    }

    private fun startDetection() {
        startForeground(NOTIFICATION_ID, createMonitoringNotification())

        // Start location updates immediately to ensure we have fresh location data
        locationManager.startLocationUpdates()

        // Register sensor listener
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            isServiceRunning = true
            Log.d(TAG, "Tap detection started with threshold: $tapThreshold")
        }
    }

    private fun stopDetection() {
        releaseResources()
        stopSelf()
    }

    private fun releaseResources() {
        try {
            sensorManager.unregisterListener(this)

            // Stop location updates to save battery
            locationManager.stopLocationUpdates()

            if (wakeLock.isHeld) wakeLock.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        } finally {
            isServiceRunning = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tap Detection Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Running tap detection in background"
                enableLights(true)
                lightColor = Color.RED
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createMonitoringNotification(
        contentText: String = "Monitoring for tap emergency pattern"
    ): Notification {
        val notificationIntent = Intent(this, ErisAndTapDetectionTestingActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tap Detection Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            detectTap(event)
        }
    }

    private fun detectTap(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate acceleration magnitude
        val acceleration = Math.sqrt(x * x + y * y + z * z.toDouble()) - SensorManager.GRAVITY_EARTH

        val currentTime = System.currentTimeMillis()

        // Match the logic from ErisActivity for consistency
        if (acceleration > tapThreshold && (currentTime - lastTapTime > cooldownPeriod)) {
            Log.d(TAG, "Tap detected with acceleration: $acceleration")
            handlePhoneTap()
        }
    }

    private fun handlePhoneTap() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastTap = currentTime - lastTapTime

        // Process tap following the same logic as in ErisActivity
        if (timeSinceLastTap > 500) { // Prevent multiple detections for same tap
            Log.d(TAG, "Processing tap with interval: $timeSinceLastTap")

            if (tapPattern.isEmpty()) {
                // First tap in sequence
                tapPattern.add(0L) // First tap has no interval
            } else {
                tapPattern.add(timeSinceLastTap)
            }

            // Keep pattern size consistent with calibration pattern
            if (tapPattern.size > 5) { // 5 taps = 4 intervals + initial 0
                tapPattern.removeAt(0)
            }

            // Debug log the current pattern
            Log.d(TAG, "Current tap pattern: $tapPattern")
            if (!calibrationTapPattern.isEmpty()) {
                Log.d(TAG, "Calibration tap pattern: $calibrationTapPattern")
            }

            // Check if we have enough taps and if the pattern matches
            if (tapPattern.size >= 5 && tapPatternsMatch()) {
                Log.d(TAG, "TAP PATTERN MATCHED! Triggering SOS!")

                // Show a toast notification
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "SOS Triggered by tap pattern!", Toast.LENGTH_SHORT).show()
                }

                // Trigger SOS
                triggerSOS()

                // Reset the pattern
                tapPattern.clear()
            }
        }
        lastTapTime = currentTime
    }

    private fun tapPatternsMatch(): Boolean {
        // Skip the first element (0) in both patterns
        val currentIntervals = tapPattern.drop(1)
        val calibrationIntervals = calibrationTapPattern.drop(1)

        if (currentIntervals.size < calibrationIntervals.size) return false

        return currentIntervals.zip(calibrationIntervals).all { (current, calibration) ->
            abs(current - calibration) <= calibration * 0.5 // Allow 50% tolerance
        }
    }

    private fun triggerSOS() {
        // Always vibrate first for immediate feedback
        vibrate()

        // Get current user (works both foreground/background)
        val user = auth.currentUser
        val userId = user?.uid ?: run {
            logSOSFailure("User not authenticated")
            return
        }

        // Use our location manager to get the best available location
        val location = locationManager.getLastKnownLocation()

        // Log location quality
        if (location.provider == "default_fallback") {
            Log.w(TAG, "Using fallback location coordinates - may not be accurate")
        } else {
            Log.d(TAG, "Using location from provider: ${location.provider}")
        }

        // Prepare and send alerts
        val sosMessage = createSOSMessage(location)
        val sosData = createSOSData(userId, location)

        sendEmergencySMS(sosMessage)
        sendFirebaseSosAlert(sosData)
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For Android Oreo and above
                val vibrationEffect = VibrationEffect.createOneShot(10000, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                // Deprecated method for older Android versions
                @Suppress("DEPRECATION")
                vibrator.vibrate(10000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration error", e)
        }
    }

    private fun createSOSMessage(location: Location): String =
        "SOS! I am in danger. My current location: " +
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"

    private fun createSOSData(userId: String, location: Location): Map<String, Any> = mapOf(
        "latitude" to location.latitude,
        "longitude" to location.longitude,
        "timestamp" to System.currentTimeMillis(),
        "userId" to userId,
        "triggerMethod" to "tap_pattern",
        "locationProvider" to (location.provider ?: "unknown"),
        "locationAccuracy" to (if (location.hasAccuracy()) location.accuracy else -1f),
        "locationTime" to location.time
    )

    private fun sendEmergencySMS(message: String) {
        val emergencyContacts = listOf("8010944027","")

        emergencyContacts.forEach { contact ->
            try {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(contact, null, message, null, null)
                Log.d(TAG, "SOS SMS sent to $contact")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to $contact", e)
            }
        }
    }

    private fun sendFirebaseSosAlert(sosData: Map<String, Any>) {
        database.reference.child("sos_alerts").push()
            .setValue(sosData)
            .addOnSuccessListener {
                Log.d(TAG, "SOS alert sent successfully")
                createSOSNotification("SOS Alert Sent Successfully")
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to send SOS alert", it)
                createSOSNotification("Failed to Send SOS Alert")
            }
    }

    private fun logSOSFailure(reason: String) {
        Log.e(TAG, "SOS Trigger Failed: $reason")
        createSOSNotification("SOS Trigger Failed: $reason")
    }

    private fun createSOSNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOS Alert")
            .setContentText(message)
            .setSmallIcon(R.drawable.sosimg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(3, notification)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseResources()
    }
}