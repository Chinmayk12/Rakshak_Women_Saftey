package com.kalpesh.women_safety


import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.*
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import android.os.VibrationEffect
import android.os.Build
import com.google.firebase.FirebaseApp

class ErisDetectionService : LifecycleService() {
    private lateinit var cameraExecutor: ExecutorService
    private var calibrationPattern: List<Long> = listOf()
    private var currentBlinkPattern = mutableListOf<Long>()
    private var lastBlinkTime: Long = 0
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var vibrator: Vibrator
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var processCameraProvider: ProcessCameraProvider? = null
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var locationManager: EmergencyLocationManager

    companion object {
        private const val CHANNEL_ID = "SOS_Service_Channel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "SOSDetectionService"
        var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        initializeServiceComponents()
    }

    private fun initializeServiceComponents() {
        createNotificationChannel()
        cameraExecutor = Executors.newSingleThreadExecutor()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        setupWakeLock()

        // Initialize our location manager
        locationManager = EmergencyLocationManager.getInstance(this)
    }

    private fun setupWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SOSDetection:WakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes wake lock
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            "START_DETECTION" -> {
                intent.getLongArrayExtra("calibrationPattern")?.let { pattern ->
                    calibrationPattern = pattern.toList()
                    startDetection()
                }
            }
            "STOP_DETECTION" -> stopDetection()
        }

        return START_STICKY
    }

    private fun startDetection() {
        startForeground(NOTIFICATION_ID, createMonitoringNotification())

        // Start location updates immediately to ensure we have fresh location data
        locationManager.startLocationUpdates()

        startCamera()
        isServiceRunning = true
    }

    private fun stopDetection() {
        releaseResources()
        stopSelf()
    }

    private fun releaseResources() {
        try {
            imageAnalysis?.clearAnalyzer()
            camera?.cameraControl?.enableTorch(false)
            processCameraProvider?.unbindAll()

            // Stop location updates to save battery
            locationManager.stopLocationUpdates()

            if (wakeLock.isHeld) wakeLock.release()
            cameraExecutor.shutdown()
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
                "SOS Detection Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Running SOS detection in background"
                enableLights(true)
                lightColor = Color.RED
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createMonitoringNotification(
        contentText: String = "Monitoring for emergency signals"
    ): Notification {
        val notificationIntent = Intent(this, ErisAndTapDetectionTestingActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOS Detection Active")
            .setContentText("Monitoring for emergency blinks")
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).addListener({
            try {
                processCameraProvider = ProcessCameraProvider.getInstance(this).get()

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, createFaceAnalyzer()) }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                processCameraProvider?.unbindAll()
                camera = processCameraProvider?.bindToLifecycle(
                    this, cameraSelector, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun createFaceAnalyzer(): ImageAnalysis.Analyzer {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        val detector = FaceDetection.getClient(options)

        return ImageAnalysis.Analyzer { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0f
                            val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0f

                            if (leftEyeOpenProb < 0.1 && rightEyeOpenProb < 0.1) {
                                handleBlink()
                            }
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun handleBlink() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastBlinkTime > 500) {
            currentBlinkPattern.add(currentTime - lastBlinkTime)

            if (currentBlinkPattern.size > calibrationPattern.size) {
                currentBlinkPattern.removeAt(0)
            }

            if (currentBlinkPattern.size == calibrationPattern.size && patternsMatch()) {
                triggerSOS()
                currentBlinkPattern.clear()
            }

            lastBlinkTime = currentTime
        }
    }

    private fun patternsMatch(): Boolean {
        if (currentBlinkPattern.size != calibrationPattern.size) return false

        val match = currentBlinkPattern.zip(calibrationPattern).all { (current, calibration) ->
            abs(current - calibration) <= calibration * 0.5
        }

        if (match) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "SOS Triggered!", Toast.LENGTH_SHORT).show()
            }
        }

        return match
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
        "locationProvider" to (location.provider ?: "unknown"),
        "locationAccuracy" to (if (location.hasAccuracy()) location.accuracy else -1f),
        "locationTime" to location.time
    )

    private fun sendEmergencySMS(message: String) {
        val emergencyContacts = listOf("8010944027","8265004346")

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
        notificationManager.notify(2, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseResources()
    }
}