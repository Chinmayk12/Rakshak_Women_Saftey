package com.kalpesh.women_safety

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class ErisAndTapDetectionTestingActivity : AppCompatActivity() , SensorEventListener {
    private lateinit var viewFinder: PreviewView
    private lateinit var gestureStatus: TextView
    private lateinit var calibrateButton: Button
    private lateinit var tapPatternButton: Button
    private lateinit var toggleDetectionButton: Button
    private lateinit var instructionsText: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tapStatus: TextView

    private var isTestingTaps = false
    private var tapCount = 0
    private var lastTapTime: Long = 0
    private var tapPattern = mutableListOf<Long>()
    private var calibrationTapPattern = mutableListOf<Long>()
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private var isTestingMode = true // To prevent SMS during testing
    private var isCalibrating = false
    private var isDetecting = false
    private var calibrationBlinkPattern = mutableListOf<Long>()
    private var currentBlinkPattern = mutableListOf<Long>()
    private var lastBlinkTime: Long = 0
    private var blinkCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eris)

        initializeViews()
        setupButtons()
        checkPermissions()
        setupSensors()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun initializeViews() {
        viewFinder = findViewById(R.id.viewFinder)
        gestureStatus = findViewById(R.id.gestureStatus)
        calibrateButton = findViewById(R.id.calibrateButton)
        toggleDetectionButton = findViewById(R.id.toggleDetectionButton)
        instructionsText = findViewById(R.id.instructionsText)
        tapPatternButton = findViewById(R.id.tapPatternButton)
        tapStatus = findViewById(R.id.tapStatus)
    }

    private fun setupButtons() {
        calibrateButton.setOnClickListener {
            startCalibration()
        }

        tapPatternButton.setOnClickListener {
            if (isDetecting) {
                Toast.makeText(this, "Stop detection first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startTapCalibration()
        }

        toggleDetectionButton.setOnClickListener {
            if (calibrationBlinkPattern.isEmpty() || calibrationTapPattern.isEmpty()) {
                Toast.makeText(this, "Please calibrate both patterns first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            isTestingMode = false
            toggleDetection()
        }
    }

    private fun startTapCalibration() {
        isTestingTaps = true
        tapCount = 0
        tapPattern.clear()
        gestureStatus.text = "Calibrating Taps: Tap phone back 5 times"
        tapStatus.text = "Tap count: 0"
        instructionsText.text = "Tap the back of your phone 5 times to set pattern"
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isTestingTaps && !isDetecting) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate acceleration magnitude
        val acceleration = Math.sqrt(x * x + y * y + z * z.toDouble()) - SensorManager.GRAVITY_EARTH

        if (acceleration > 7.5f) { // Threshold for tap detection
            handlePhoneTap()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handlePhoneTap() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastTap = currentTime - lastTapTime

        if (timeSinceLastTap > 500) { // Prevent multiple detections for same tap
            if (isTestingTaps) {
                tapCount++
                tapStatus.text = "Tap count: $tapCount"

                if (tapCount > 1) {
                    tapPattern.add(timeSinceLastTap)
                }

                if (tapCount >= 5) {
                    calibrationTapPattern = tapPattern.toMutableList()
                    isTestingTaps = false
                    gestureStatus.text = "Tap Calibration Complete!"
                    tapStatus.text = "Tap pattern stored"
                    instructionsText.text = "Both patterns set. Start detection when ready."
                    Toast.makeText(this, "Tap calibration complete!", Toast.LENGTH_SHORT).show()
                }
            } else if (isDetecting) {
                // During detection mode
                tapPattern.add(timeSinceLastTap)

                if (tapPattern.size > calibrationTapPattern.size) {
                    tapPattern.removeAt(0)
                }

                if (tapPattern.size == calibrationTapPattern.size && tapPatternsMatch()) {
                    if (!isTestingMode) {
                        triggerSOS("TAP")
                    }
                    tapPattern.clear()
                }
            }
        }
        lastTapTime = currentTime
    }

    private fun tapPatternsMatch(): Boolean {
        if (tapPattern.size != calibrationTapPattern.size) return false

        return tapPattern.zip(calibrationTapPattern).all { (current, calibration) ->
            abs(current - calibration) <= calibration * 0.5
        }
    }
    private fun triggerSOS(triggerType: String) {
        // This will only be called when isTestingMode is false
        val serviceIntent = Intent(this, ErisDetectionService::class.java).apply {
            action = "TRIGGER_SOS"
            putExtra("triggerType", triggerType)
        }
        startService(serviceIntent)
    }

    private fun checkPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.SEND_SMS,
            Manifest.permission.VIBRATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        } else {
            startCamera()
        }
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
    }

    private fun startCalibration() {
        isCalibrating = true
        isDetecting = false
        calibrationBlinkPattern.clear()
        blinkCount = 0
        gestureStatus.text = "Calibrating: Blink 5 times..."  // Updated to 5 blinks
        instructionsText.text = "Blink naturally 5 times"  // Update instructions
        toggleDetectionButton.text = "Start Detection"
    }

    private fun startBackgroundDetection() {
        // Start the blink detection service
        val blinkServiceIntent = Intent(this, ErisDetectionService::class.java).apply {
            action = "START_DETECTION"
            putExtra("calibrationPattern", calibrationBlinkPattern.toLongArray())
        }

        // Start the tap detection service
        val tapServiceIntent = Intent(this, TapDetectionService::class.java).apply {
            action = "START_DETECTION"
        }

        requestBatteryOptimizationExemption()

        // Start both services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(blinkServiceIntent)
            startForegroundService(tapServiceIntent)
        } else {
            startService(blinkServiceIntent)
            startService(tapServiceIntent)
        }

        Toast.makeText(this, "Background detection started", Toast.LENGTH_SHORT).show()
    }

    private fun stopBackgroundDetection() {
        // Stop blink detection service
        val blinkServiceIntent = Intent(this, ErisDetectionService::class.java).apply {
            action = "STOP_DETECTION"
        }
        stopService(blinkServiceIntent)

        // Stop tap detection service
        val tapServiceIntent = Intent(this, TapDetectionService::class.java).apply {
            action = "STOP_DETECTION"
        }
        stopService(tapServiceIntent)

        Toast.makeText(this, "Detection stopped", Toast.LENGTH_SHORT).show()
    }

    private fun toggleDetection() {
        isDetecting = !isDetecting
        if (isDetecting) {
            if (calibrationBlinkPattern.isEmpty()) {
                Toast.makeText(this, "Please calibrate first", Toast.LENGTH_SHORT).show()
                isDetecting = false
                return
            }
            startBackgroundDetection()
        } else {
            stopBackgroundDetection()
        }
        toggleDetectionButton.text = if (isDetecting) "Stop Detection" else "Start Detection"
        gestureStatus.text = if (isDetecting) "Detection Active" else "Detection Stopped"
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Preview
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }

                // Image Analysis
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, setupFaceAnalyzer())
                    }

                // Select front camera
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                    Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_SHORT).show()
                }

            } catch (exc: Exception) {
                Log.e(TAG, "Camera initialization failed", exc)
                Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun setupFaceAnalyzer(): ImageAnalysis.Analyzer {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        val detector = FaceDetection.getClient(options)

        return ImageAnalysis.Analyzer { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0f
                            val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0f

                            // Detect blink when both eyes are mostly closed
                            if (leftEyeOpenProb < 0.1 && rightEyeOpenProb < 0.1) {
                                runOnUiThread {
                                    handleBlink()
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Face detection failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun handleBlink() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastBlinkTime > 500) { // Prevent multiple detections for same blink
            if (isCalibrating) {
                handleCalibrationBlink(currentTime)
            }
            lastBlinkTime = currentTime
        }
    }

    private fun handleCalibrationBlink(currentTime: Long) {
        if (blinkCount > 0) {
            calibrationBlinkPattern.add(currentTime - lastBlinkTime)
        }
        blinkCount++

        if (blinkCount >= 5) {  // 5 Blinks
            isCalibrating = false
            Toast.makeText(this, "Calibration Complete!", Toast.LENGTH_SHORT).show()
            gestureStatus.text = "Calibration Complete!"
            instructionsText.text = "Pattern stored. Tap 'Start Detection' to begin monitoring"
        } else {
            gestureStatus.text = "Calibrating: ${5 - blinkCount} blinks remaining..."  // Update instruction
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions required for app functionality", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        sensorManager.unregisterListener(this)
    }

    companion object {
        private const val TAG = "ErisActivity"
        private const val PERMISSION_REQUEST_CODE = 123
    }
}