package com.kalpesh.women_safety//package com.kalpesh.women_safety
//
//import android.Manifest
//import android.content.Context
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.hardware.Sensor
//import android.hardware.SensorEvent
//import android.hardware.SensorEventListener
//import android.hardware.SensorManager
//import android.os.Build
//import android.os.Bundle
//import android.os.PowerManager
//import android.util.Log
//import android.widget.Button
//import android.widget.TextView
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.camera.core.*
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import com.google.mlkit.vision.common.InputImage
//import com.google.mlkit.vision.face.FaceDetection
//import com.google.mlkit.vision.face.FaceDetectorOptions
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//import kotlin.math.abs
//
//class ErisActivity : AppCompatActivity() , SensorEventListener {
//    private lateinit var viewFinder: PreviewView
//    private lateinit var gestureStatus: TextView
//    private lateinit var calibrateButton: Button
//    private lateinit var tapPatternButton: Button
//    private lateinit var toggleDetectionButton: Button
//    private lateinit var instructionsText: TextView
//    private lateinit var cameraExecutor: ExecutorService
//    private lateinit var tapStatus: TextView
//
//    private var isTestingTaps = false
//    private var tapCount = 0
//    private var lastTapTime: Long = 0
//    private var tapPattern = mutableListOf<Long>()
//    private var calibrationTapPattern = mutableListOf<Long>()
//    private lateinit var sensorManager: SensorManager
//    private lateinit var accelerometer: Sensor
//    private var isTestingMode = true // To prevent SMS during testing
//    private var isCalibrating = false
//    private var isDetecting = false
//    private var calibrationBlinkPattern = mutableListOf<Long>()
//    private var currentBlinkPattern = mutableListOf<Long>()
//    private var lastBlinkTime: Long = 0
//    private var blinkCount = 0
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_eris)
//
//        initializeViews()
//        setupButtons()
//        checkPermissions()
//        setupSensors()
//
//        cameraExecutor = Executors.newSingleThreadExecutor()
//    }
//
//    private fun initializeViews() {
//        viewFinder = findViewById(R.id.viewFinder)
//        gestureStatus = findViewById(R.id.gestureStatus)
//        calibrateButton = findViewById(R.id.calibrateButton)
//        toggleDetectionButton = findViewById(R.id.toggleDetectionButton)
//        instructionsText = findViewById(R.id.instructionsText)
//        tapPatternButton = findViewById(R.id.tapPatternButton)
//        tapStatus = findViewById(R.id.tapStatus)
//    }
//
//    private fun setupButtons() {
//        calibrateButton.setOnClickListener {
//            startCalibration()
//        }
//
//        tapPatternButton.setOnClickListener {
//            if (isDetecting) {
//                Toast.makeText(this, "Stop detection first", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//            startTapCalibration()
//        }
//
//        toggleDetectionButton.setOnClickListener {
//            if (calibrationBlinkPattern.isEmpty() || calibrationTapPattern.isEmpty()) {
//                Toast.makeText(this, "Please calibrate both patterns first", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//            isTestingMode = false
//            toggleDetection()
//        }
//    }
//
//    private fun startTapCalibration() {
//        isTestingTaps = true
//        tapCount = 0
//        tapPattern.clear()
//        gestureStatus.text = "Calibrating Taps: Tap phone back 5 times"
//        tapStatus.text = "Tap count: 0"
//        instructionsText.text = "Tap the back of your phone 5 times to set pattern"
//    }
//
//    override fun onSensorChanged(event: SensorEvent) {
//        if (!isTestingTaps && !isDetecting) return
//
//        val x = event.values[0]
//        val y = event.values[1]
//        val z = event.values[2]
//
//        // Calculate acceleration magnitude
//        val acceleration = Math.sqrt(x * x + y * y + z * z.toDouble()) - SensorManager.GRAVITY_EARTH
//
//        if (acceleration > 2.5f) { // Threshold for tap detection
//            handlePhoneTap()
//        }
//    }
//
//    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
//
//    private fun handlePhoneTap() {
//        val currentTime = System.currentTimeMillis()
//        val timeSinceLastTap = currentTime - lastTapTime
//
//        if (timeSinceLastTap > 500) { // Prevent multiple detections for same tap
//            if (isTestingTaps) {
//                tapCount++
//                tapStatus.text = "Tap count: $tapCount"
//
//                if (tapCount > 1) {
//                    tapPattern.add(timeSinceLastTap)
//                }
//
//                if (tapCount >= 5) {
//                    calibrationTapPattern = tapPattern.toMutableList()
//                    isTestingTaps = false
//                    gestureStatus.text = "Tap Calibration Complete!"
//                    tapStatus.text = "Tap pattern stored"
//                    instructionsText.text = "Both patterns set. Start detection when ready."
//                    Toast.makeText(this, "Tap calibration complete!", Toast.LENGTH_SHORT).show()
//                }
//            } else if (isDetecting) {
//                // During detection mode
//                tapPattern.add(timeSinceLastTap)
//
//                if (tapPattern.size > calibrationTapPattern.size) {
//                    tapPattern.removeAt(0)
//                }
//
//                if (tapPattern.size == calibrationTapPattern.size && tapPatternsMatch()) {
//                    if (!isTestingMode) {
//                        triggerSOS("TAP")
//                    }
//                    tapPattern.clear()
//                }
//            }
//        }
//        lastTapTime = currentTime
//    }
//
//    private fun tapPatternsMatch(): Boolean {
//        if (tapPattern.size != calibrationTapPattern.size) return false
//
//        return tapPattern.zip(calibrationTapPattern).all { (current, calibration) ->
//            abs(current - calibration) <= calibration * 0.5
//        }
//    }
//    private fun triggerSOS(triggerType: String) {
//        // This will only be called when isTestingMode is false
//        val serviceIntent = Intent(this, ErisDetectionService::class.java).apply {
//            action = "TRIGGER_SOS"
//            putExtra("triggerType", triggerType)
//        }
//        startService(serviceIntent)
//    }
//
//    private fun checkPermissions() {
//        val requiredPermissions = mutableListOf(
//            Manifest.permission.CAMERA,
//            Manifest.permission.SEND_SMS,
//            Manifest.permission.VIBRATE,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        )
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
//        }
//
//        val permissionsToRequest = requiredPermissions.filter {
//            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
//        }.toTypedArray()
//
//        if (permissionsToRequest.isNotEmpty()) {
//            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
//        } else {
//            startCamera()
//        }
//    }
//
//    private fun setupSensors() {
//        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
//        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
//    }
//
//    private fun startCalibration() {
//        isCalibrating = true
//        isDetecting = false
//        calibrationBlinkPattern.clear()
//        blinkCount = 0
//        gestureStatus.text = "Calibrating: Blink 5 times..."  // Updated to 5 blinks
//        instructionsText.text = "Blink naturally 5 times"  // Update instructions
//        toggleDetectionButton.text = "Start Detection"
//    }
//
//
//    private fun startBackgroundDetection() {
//        val serviceIntent = Intent(this, ErisDetectionService::class.java).apply {
//            action = "START_DETECTION"
//            putExtra("calibrationPattern", calibrationBlinkPattern.toLongArray())
//            putExtra("tapPattern", calibrationTapPattern.toLongArray())
//        }
//
//        requestBatteryOptimizationExemption()
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            startForegroundService(serviceIntent)
//        } else {
//            startService(serviceIntent)
//        }
//
//        Toast.makeText(this, "Background detection started", Toast.LENGTH_SHORT).show()
//    }
//
//
//    private fun stopBackgroundDetection() {
//        val serviceIntent = Intent(this, ErisDetectionService::class.java).apply {
//            action = "STOP_DETECTION"
//        }
//        stopService(serviceIntent)
//        Toast.makeText(this, "Detection stopped", Toast.LENGTH_SHORT).show()
//    }
//
//    private fun toggleDetection() {
//        isDetecting = !isDetecting
//        if (isDetecting) {
//            if (calibrationBlinkPattern.isEmpty()) {
//                Toast.makeText(this, "Please calibrate first", Toast.LENGTH_SHORT).show()
//                isDetecting = false
//                return
//            }
//            startBackgroundDetection()
//        } else {
//            stopBackgroundDetection()
//        }
//        toggleDetectionButton.text = if (isDetecting) "Stop Detection" else "Start Detection"
//        gestureStatus.text = if (isDetecting) "Detection Active" else "Detection Stopped"
//    }
//
//    private fun requestBatteryOptimizationExemption() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            val packageName = packageName
//            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
//            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
//                val intent = Intent().apply {
//                    action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
//                    data = android.net.Uri.parse("package:$packageName")
//                }
//                startActivity(intent)
//            }
//        }
//    }
//
//    private fun startCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//
//        cameraProviderFuture.addListener({
//            try {
//                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//
//                // Preview
//                val preview = Preview.Builder()
//                    .build()
//                    .also {
//                        it.setSurfaceProvider(viewFinder.surfaceProvider)
//                    }
//
//                // Image Analysis
//                val imageAnalyzer = ImageAnalysis.Builder()
//                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                    .build()
//                    .also {
//                        it.setAnalyzer(cameraExecutor, setupFaceAnalyzer())
//                    }
//
//                // Select front camera
//                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
//
//                try {
//                    cameraProvider.unbindAll()
//                    cameraProvider.bindToLifecycle(
//                        this,
//                        cameraSelector,
//                        preview,
//                        imageAnalyzer
//                    )
//                } catch (exc: Exception) {
//                    Log.e(TAG, "Use case binding failed", exc)
//                    Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_SHORT).show()
//                }
//
//            } catch (exc: Exception) {
//                Log.e(TAG, "Camera initialization failed", exc)
//                Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_SHORT).show()
//            }
//        }, ContextCompat.getMainExecutor(this))
//    }
//
//    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
//    private fun setupFaceAnalyzer(): ImageAnalysis.Analyzer {
//        val options = FaceDetectorOptions.Builder()
//            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
//            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
//            .build()
//
//        val detector = FaceDetection.getClient(options)
//
//        return ImageAnalysis.Analyzer { imageProxy ->
//            val mediaImage = imageProxy.image
//            if (mediaImage != null) {
//                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
//
//                detector.process(image)
//                    .addOnSuccessListener { faces ->
//                        if (faces.isNotEmpty()) {
//                            val face = faces[0]
//                            val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0f
//                            val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0f
//
//                            // Detect blink when both eyes are mostly closed
//                            if (leftEyeOpenProb < 0.1 && rightEyeOpenProb < 0.1) {
//                                runOnUiThread {
//                                    handleBlink()
//                                }
//                            }
//                        }
//                    }
//                    .addOnFailureListener { e ->
//                        Log.e(TAG, "Face detection failed", e)
//                    }
//                    .addOnCompleteListener {
//                        imageProxy.close()
//                    }
//            } else {
//                imageProxy.close()
//            }
//        }
//    }
//
//    private fun handleBlink() {
//        val currentTime = System.currentTimeMillis()
//
//        if (currentTime - lastBlinkTime > 500) { // Prevent multiple detections for same blink
//            if (isCalibrating) {
//                handleCalibrationBlink(currentTime)
//            }
//            lastBlinkTime = currentTime
//        }
//    }
//
//    private fun handleCalibrationBlink(currentTime: Long) {
//        if (blinkCount > 0) {
//            calibrationBlinkPattern.add(currentTime - lastBlinkTime)
//        }
//        blinkCount++
//
//        if (blinkCount >= 5) {  // 5 Blinks
//            isCalibrating = false
//            Toast.makeText(this, "Calibration Complete!", Toast.LENGTH_SHORT).show()
//            gestureStatus.text = "Calibration Complete!"
//            instructionsText.text = "Pattern stored. Tap 'Start Detection' to begin monitoring"
//        } else {
//            gestureStatus.text = "Calibrating: ${5 - blinkCount} blinks remaining..."  // Update instruction
//        }
//    }
//
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == PERMISSION_REQUEST_CODE) {
//            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
//                startCamera()
//            } else {
//                Toast.makeText(this, "Permissions required for app functionality", Toast.LENGTH_LONG).show()
//                finish()
//            }
//        }
//    }
//    override fun onResume() {
//        super.onResume()
//        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
//    }
//    override fun onPause() {
//        super.onPause()
//        sensorManager.unregisterListener(this)
//    }
//
//
//    override fun onDestroy() {
//        super.onDestroy()
//        cameraExecutor.shutdown()
//        sensorManager.unregisterListener(this)
//    }
//
//    companion object {
//        private const val TAG = "ErisActivity"
//        private const val PERMISSION_REQUEST_CODE = 123
//    }
//}

///-----------------------------------------------------------------------------------

// Tap Service Code:
/*
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
import kotlin.math.sqrt

class TapDetectionService : LifecycleService(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // Count for specific 5-tap detection
    private var tapCount = 0
    private var tapStartTime: Long = 0
    private var lastTapTime: Long = 0

    // Thresholds for 5-tap detection
    private val maxTapInterval = 1000L // Maximum time between taps (ms)
    private val maxTapSequenceTime = 5000L // Maximum total time for 5 taps (ms)

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var vibrator: Vibrator
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var locationManager: EmergencyLocationManager

    // For tap detection
    private val tapThreshold = 15f // Adjust this value based on testing
    private var lastShakeTime = 0L
    private val cooldownPeriod = 300L // Minimum time between tap detections (ms)

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
        contentText: String = "Monitoring for 5-tap emergency signal"
    ): Notification {
        val notificationIntent = Intent(this, ErisActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("5-Tap Detection Active")
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
        val accelerationMagnitude = sqrt(x * x + y * y + z * z)
        val gravityMagnitude = 9.81f // Approximate gravity value

        // Calculate the difference from gravity (to detect sudden movements)
        val delta = abs(accelerationMagnitude - gravityMagnitude)

        val currentTime = System.currentTimeMillis()

        // Check if the acceleration exceeds our threshold and if we're not in cooldown
        if (delta > tapThreshold && (currentTime - lastShakeTime > cooldownPeriod)) {
            lastShakeTime = currentTime
            handleTap()
        }
    }

    private fun handleTap() {
        val currentTime = System.currentTimeMillis()

        // If this is the first tap in a potential sequence
        if (tapCount == 0) {
            tapCount = 1
            tapStartTime = currentTime
            lastTapTime = currentTime
            return
        }

        // Calculate time since last tap
        val timeSinceLastTap = currentTime - lastTapTime

        // If it's been too long since the last tap, restart the sequence
        if (timeSinceLastTap > maxTapInterval) {
            Log.d(TAG, "Tap interval too long (${timeSinceLastTap}ms), resetting sequence")
            tapCount = 1
            tapStartTime = currentTime
            lastTapTime = currentTime
            return
        }

        // If the total sequence is taking too long, restart
        if (currentTime - tapStartTime > maxTapSequenceTime) {
            Log.d(TAG, "Tap sequence taking too long (${currentTime - tapStartTime}ms), resetting")
            tapCount = 1
            tapStartTime = currentTime
            lastTapTime = currentTime
            return
        }

        // Increment tap count and update last tap time
        tapCount++
        lastTapTime = currentTime

        Log.d(TAG, "Tap detected: $tapCount of 5")

        // Check if we've reached 5 taps
        if (tapCount == 5) {
            Log.d(TAG, "5-tap pattern detected in ${currentTime - tapStartTime}ms")

            // Show a toast notification
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "SOS Triggered!", Toast.LENGTH_SHORT).show()
            }

            // Trigger SOS
            triggerSOS()

            // Reset tap count
            tapCount = 0
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
        "triggerMethod" to "5_tap_pattern",
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
 */
