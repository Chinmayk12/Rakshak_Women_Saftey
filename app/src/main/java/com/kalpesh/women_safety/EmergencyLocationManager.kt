package com.kalpesh.women_safety

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import java.util.concurrent.TimeUnit

/**
 * Manages location updates and provides the last known location
 * for emergency SOS purposes.
 */
class EmergencyLocationManager(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyLocationManager"
        private const val LOCATION_UPDATE_INTERVAL = 10000L // 10 seconds
        private const val LOCATION_FASTEST_INTERVAL = 5000L // 5 seconds

        // Singleton instance
        @Volatile private var instance: EmergencyLocationManager? = null

        fun getInstance(context: Context): EmergencyLocationManager {
            return instance ?: synchronized(this) {
                instance ?: EmergencyLocationManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    // FusedLocationClient (preferred method)
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Traditional LocationManager (fallback)
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    // Cache for the last known good location
    private var cachedLocation: Location? = null

    /**
     * Starts requesting location updates to keep location data fresh,
     * even when app is in background
     */
    fun startLocationUpdates() {
        try {
            if (checkLocationPermission()) {
                // First try to use FusedLocationProviderClient (more reliable)
                val locationRequest = LocationRequest.create().apply {
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                    interval = LOCATION_UPDATE_INTERVAL
                    fastestInterval = LOCATION_FASTEST_INTERVAL
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )

                // Also register traditional location listener as backup
                try {
                    // Try to get GPS updates
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            LOCATION_UPDATE_INTERVAL,
                            10f, // 10 meters
                            locationListener
                        )
                    }

                    // Try to get Network location updates
                    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            LOCATION_UPDATE_INTERVAL,
                            10f, // 10 meters
                            locationListener
                        )
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error setting up traditional location updates", ex)
                }

                // Immediately try to get last known location
                updateCachedLocation()
            } else {
                Log.e(TAG, "Location permission not granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }

    /**
     * Callback for FusedLocationProviderClient
     */
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                // Update our cached location with new data
                cachedLocation = location
                Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")

                // Persist location to shared preferences
                saveLocationToStorage(location)
            }
        }
    }

    /**
     * Listener for traditional LocationManager
     */
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // Update cached location with new data
            cachedLocation = location
            Log.d(TAG, "Classic location updated: ${location.latitude}, ${location.longitude}")

            // Persist location to storage
            saveLocationToStorage(location)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    /**
     * Stops location updates to save battery
     */
    fun stopLocationUpdates() {
        try {
            // Stop fused location updates
            fusedLocationClient.removeLocationUpdates(locationCallback)

            // Stop traditional location updates
            if (checkLocationPermission()) {
                locationManager.removeUpdates(locationListener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }

    /**
     * Gets the best available location using multiple strategies
     */
    fun getLastKnownLocation(): Location {
        updateCachedLocation()

        // Return cached location or create a default one with a warning flag
        return cachedLocation ?: createDefaultLocation()
    }

    /**
     * Try to update the cached location using all available methods
     */
    private fun updateCachedLocation() {
        if (!checkLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            return
        }

        try {
            // Try FusedLocationProviderClient (most reliable)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && isBetterLocation(location, cachedLocation)) {
                    cachedLocation = location
                    saveLocationToStorage(location)
                    Log.d(TAG, "Updated cached location from fused client")
                }
            }

            // Also try traditional LocationManager as backup
            try {
                // Try GPS provider
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { location ->
                    if (isBetterLocation(location, cachedLocation)) {
                        cachedLocation = location
                        saveLocationToStorage(location)
                        Log.d(TAG, "Updated cached location from GPS")
                    }
                }

                // Try network provider
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { location ->
                    if (isBetterLocation(location, cachedLocation)) {
                        cachedLocation = location
                        saveLocationToStorage(location)
                        Log.d(TAG, "Updated cached location from Network")
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error getting location from traditional providers", ex)
            }

            // If still no location, try loading from persistent storage
            if (cachedLocation == null) {
                loadLocationFromStorage()?.let { savedLocation ->
                    cachedLocation = savedLocation
                    Log.d(TAG, "Loaded cached location from storage")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating cached location", e)
        }
    }

    /**
     * Save location to SharedPreferences for persistence
     */
    private fun saveLocationToStorage(location: Location) {
        try {
            val prefs = context.getSharedPreferences("emergency_location", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putFloat("latitude", location.latitude.toFloat())
                putFloat("longitude", location.longitude.toFloat())
                putLong("timestamp", location.time)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location to storage", e)
        }
    }

    /**
     * Load location from SharedPreferences
     */
    private fun loadLocationFromStorage(): Location? {
        try {
            val prefs = context.getSharedPreferences("emergency_location", Context.MODE_PRIVATE)
            val latitude = prefs.getFloat("latitude", 0f).toDouble()
            val longitude = prefs.getFloat("longitude", 0f).toDouble()
            val timestamp = prefs.getLong("timestamp", 0)

            // Only use stored location if it's not default values and reasonably recent (24 hrs)
            if ((latitude != 0.0 || longitude != 0.0) &&
                System.currentTimeMillis() - timestamp < TimeUnit.HOURS.toMillis(24)) {

                return Location("storage").apply {
                    this.latitude = latitude
                    this.longitude = longitude
                    this.time = timestamp
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading location from storage", e)
        }
        return null
    }

    /**
     * Creates a default location with a special provider name to indicate it's a fallback
     */
    private fun createDefaultLocation(): Location {
        return Location("default_fallback").apply {
            // Use a clear placeholder that indicates this is not a real location
            latitude = 0.0
            longitude = 0.0
            time = System.currentTimeMillis()
        }
    }

    /**
     * Check if location permission is granted
     */
    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Determines whether one location is better than the current best location
     */
    private fun isBetterLocation(location: Location, currentBestLocation: Location?): Boolean {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true
        }

        // Check whether the new location fix is newer or older
        val timeDelta = location.time - currentBestLocation.time
        val isSignificantlyNewer = timeDelta > TimeUnit.MINUTES.toMillis(2)
        val isSignificantlyOlder = timeDelta < -TimeUnit.MINUTES.toMillis(2)
        val isNewer = timeDelta > 0

        // If it's been more than two minutes since the current location, use the new location
        if (isSignificantlyNewer) {
            return true
        } else if (isSignificantlyOlder) {
            // If the new location is significantly older, it must be worse
            return false
        }

        // Check whether the new location fix is more or less accurate
        val accuracyDelta = (location.accuracy - currentBestLocation.accuracy).toInt()
        val isLessAccurate = accuracyDelta > 0
        val isMoreAccurate = accuracyDelta < 0
        val isSignificantlyLessAccurate = accuracyDelta > 200

        // Check if the old and new location are from the same provider
        val isFromSameProvider = location.provider == currentBestLocation.provider

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true
        } else if (isNewer && !isLessAccurate) {
            return true
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true
        }

        return false
    }
}