package com.kalpesh.women_safety
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.mappls.sdk.geojson.Feature
import com.mappls.sdk.geojson.FeatureCollection
import com.mappls.sdk.geojson.Point
import com.mappls.sdk.maps.annotations.IconFactory
import com.mappls.sdk.maps.annotations.Marker
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.Mappls
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback as MapplsOnMapReadyCallback
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.geometry.LatLng
import com.mappls.sdk.maps.geometry.LatLngBounds
import com.mappls.sdk.maps.style.expressions.Expression.exponential
import com.mappls.sdk.maps.style.expressions.Expression.interpolate
import com.mappls.sdk.maps.style.expressions.Expression.stop
import com.mappls.sdk.maps.style.expressions.Expression.zoom
import com.mappls.sdk.maps.style.layers.PropertyFactory
import com.mappls.sdk.maps.style.layers.SymbolLayer
import com.mappls.sdk.maps.style.sources.GeoJsonSource
import com.mappls.sdk.maps.utils.BitmapUtils
import com.mappls.sdk.services.account.MapplsAccountManager
import com.mappls.sdk.services.api.OnResponseCallback
import com.mappls.sdk.services.api.nearby.MapplsNearby
import com.mappls.sdk.services.api.nearby.MapplsNearbyManager
import com.mappls.sdk.services.api.nearby.model.NearbyAtlasResponse
import com.mappls.sdk.services.api.nearby.model.NearbyAtlasResult
import java.io.IOException
import java.util.Locale

class SafetyMapActivity : AppCompatActivity(), OnMapReadyCallback, MapplsOnMapReadyCallback,
    MapplsMap.OnMarkerClickListener {

    private lateinit var mapView: MapView
    private var mapplsMap: MapplsMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnMyLocation: ImageButton
    private var currentLocation: LatLng? = null
    private var currentLocationMarker: Marker? = null

    // Store hospital markers with their data
    private val hospitalMarkers = HashMap<Marker, NearbyAtlasResult>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "SafetyMapActivity"
        private const val DEFAULT_ZOOM_LEVEL = 15.0
        private const val MAX_HOSPITAL_DISTANCE_METERS = 5000 // 5km max distance for hospitals
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Mappls SDK with your REST and Map SDK keys
        MapplsAccountManager.getInstance().apply {
            restAPIKey = "772b6950d7ff705845ac5275c0b49e64"
            mapSDKKey = "772b6950d7ff705845ac5275c0b49e64"
            atlasClientId = "96dHZVzsAuuVAzX39l7GAmFzFMtAoTz79J11oNG-nIKoWjzyTGrwzoOQ7u-7uirY7tgB9djB8foqqElPrKB1Nw=="
            atlasClientSecret = "lrFxI-iSEg8XW9mJDsde8HfcP7dz9_oftnG40umZQPnhtPzdBQRQwSvqNH_bx0CrQfCW7J_Uw8BYENlOBNjzT7KKz98tcBL2"
        }

        Mappls.getInstance(applicationContext)

        setContentView(R.layout.activity_safety_map)

        // Initialize MapView and Location Client
        mapView = findViewById(R.id.map_view)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // Initialize My Location button
        btnMyLocation = findViewById(R.id.btn_my_location)
        btnMyLocation.setOnClickListener {
            focusOnCurrentLocation()
        }
    }

    private fun focusOnCurrentLocation() {
        if (checkLocationPermission()) {
            // If we already have the current location, use it
            currentLocation?.let {
                mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, DEFAULT_ZOOM_LEVEL))
                return
            }

            // Otherwise, get current location
            getCurrentLocationAndMoveCamera(zoomToLocation = true)
        } else {
            requestLocationPermission()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(mapplsMap: MapplsMap) {
        this.mapplsMap = mapplsMap

        // Enable map gestures
        mapplsMap.uiSettings?.apply {
            isZoomGesturesEnabled = true
            isTiltGesturesEnabled = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
            logoGravity = Gravity.TOP
            isDoubleTapGesturesEnabled = true
        }

        // Set marker click listener
        mapplsMap.setOnMarkerClickListener(this)

        if (checkLocationPermission()) {
            // Initial map setup with current location
            getCurrentLocationAndMoveCamera(zoomToLocation = true)
        } else {
            requestLocationPermission()
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        // Check if this is a hospital marker
        val hospital = hospitalMarkers[marker]
        if (hospital != null) {
            // Show dialog to navigate to this hospital
            hospital.latitude?.let { lat ->
                hospital.longitude?.let { lng ->
                    showNavigationConfirmDialog(
                        hospital.placeName ?: "Hospital",
                        lat.toDouble(),
                        lng.toDouble()
                    )
                }
            }
            return true // Consume the event
        }
        return false // Let default behavior handle other markers
    }

    override fun onMapError(p0: Int, p1: String?) {
        Log.e(TAG, "Map Error: $p0 - $p1")
        showToast("Map Error: $p1")
    }

    private fun checkLocationPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocationAndMoveCamera(zoomToLocation = true)
        } else {
            showToast("Location permission denied")
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocationAndMoveCamera(zoomToLocation: Boolean = false) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                // Check if latitude and longitude are valid
                if (it.latitude != 0.0 && it.longitude != 0.0) {
                    val newLatLng = LatLng(it.latitude, it.longitude)
                    currentLocation = newLatLng // Store current location

                    // Add or update marker at the current location
                    addOrUpdateCurrentLocationMarker(newLatLng)

                    if (zoomToLocation) {
                        // Zoom to the current location with appropriate zoom level
                        // Use a higher zoom level for better focus on local area
                        mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, DEFAULT_ZOOM_LEVEL))
                    }

                    // After ensuring camera is positioned properly, then find hospitals
                    findNearbyHospitals(it.latitude, it.longitude)
                } else {
                    showToast("Invalid location data")

                    // Try requesting a fresh location instead of using last known
                    requestFreshLocation()
                }
            } ?: run {
                showToast("Location unavailable, trying to get fresh location")
                Log.e(TAG, "Unable to get last known location")

                // Try requesting a fresh location
                requestFreshLocation()
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error getting location: ${e.message}", e)
            showToast("Error getting location: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation() {
        if (!checkLocationPermission()) {
            requestLocationPermission()
            return
        }

        // Create location request for fresh location
        val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
            priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000 // 10 seconds
            fastestInterval = 5000 // 5 seconds
        }

        // Request a single update
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val newLatLng = LatLng(location.latitude, location.longitude)
                        currentLocation = newLatLng

                        // Add or update marker
                        addOrUpdateCurrentLocationMarker(newLatLng)

                        // Zoom to location
                        mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, DEFAULT_ZOOM_LEVEL))

                        // Find nearby hospitals
                        findNearbyHospitals(location.latitude, location.longitude)

                        // Remove updates after we get the location
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            },
            null
        )
    }

    private fun addOrUpdateCurrentLocationMarker(location: LatLng) {
        // Remove existing marker if any
        currentLocationMarker?.remove()

        mapplsMap?.let { map ->
            val iconFactory = IconFactory.getInstance(this)

            // Create a blue colored default marker
            val icon = iconFactory.defaultMarker()

            // Create the marker
            currentLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(location)
                    .title("My Location")
                    .icon(icon)
            )
        }
    }

    private fun findNearbyHospitals(latitude: Double, longitude: Double) {
        try {
            Log.d(TAG, "Searching for hospitals near: $latitude, $longitude")

            // Create the NearbyAtlas request using the correct builder pattern
            val nearbyBuilder = MapplsNearby.builder()
                .setLocation("$latitude,$longitude")
                .keyword("hospital")
                .radius(5000)
                .sortBy("distance")
                .page(1)  // Get the first page of results

            // Create manager with the builder
            val nearbyManager = MapplsNearbyManager.newInstance(nearbyBuilder.build())

            // Add callback for the response
            nearbyManager.call(object : OnResponseCallback<NearbyAtlasResponse> {
                override fun onSuccess(response: NearbyAtlasResponse?) {
                    Log.d(TAG, "Nearby API Success with ${response?.suggestedLocations?.size ?: 0} locations")

                    val results = response?.suggestedLocations
                    if (results.isNullOrEmpty()) {
                        runOnUiThread {
                            showToast("No hospitals found in this area")
                        }
                        return
                    }

                    // Create a safe copy of valid hospitals
                    val validHospitals = ArrayList<NearbyAtlasResult>()

                    for (hospital in results) {
                        if (hospital.latitude != null && hospital.longitude != null) {
                            validHospitals.add(hospital)
                        } else {
                            // Attempt to geocode hospital name to get coordinates
                            val hospitalCoordinates = geocodeHospitalName(hospital.placeName)
                            if (hospitalCoordinates != null) {
                                // Update the hospital object with the fetched coordinates
                                hospital.latitude = hospitalCoordinates.latitude
                                hospital.longitude = hospitalCoordinates.longitude
                                validHospitals.add(hospital)
                            } else {
                                Log.w(TAG, "Skipping hospital with missing coordinates: ${hospital.placeName}")
                            }
                        }
                    }

                    if (validHospitals.isEmpty()) {
                        runOnUiThread {
                            showToast("No valid hospital locations found")
                        }
                        return
                    }

                    runOnUiThread {
                        try {
                            val userLocation = LatLng(latitude, longitude)
                            addHospitalMarkers(validHospitals, userLocation)
                            showToast("Found ${validHospitals.size} hospitals nearby")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error displaying markers: ${e.message}", e)
                            showToast("Error displaying hospitals: ${e.localizedMessage}")
                        }
                    }
                }

                override fun onError(code: Int, message: String?) {
                    Log.e(TAG, "Nearby API Error $code: $message")
                    runOnUiThread {
                        showToast("Error finding hospitals: $message")
                    }
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Exception while finding hospitals: ${e.message}", e)
            showToast("Error: ${e.localizedMessage}")
        }
    }

    // Geocode hospital name to get coordinates using Geocoder
    private fun geocodeHospitalName(hospitalName: String?): LatLng? {
        if (hospitalName.isNullOrEmpty()) {
            return null
        }

        val geocoder = Geocoder(this, Locale.getDefault())
        return try {
            val addresses = geocoder.getFromLocationName(hospitalName, 1)
            if (addresses?.isNotEmpty() == true) {
                addresses[0]?.let { LatLng(it.latitude, it.longitude) }
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Geocoding failed for $hospitalName: ${e.message}")
            null
        }
    }

    private fun addHospitalMarkers(hospitals: List<NearbyAtlasResult>, userLocation: LatLng) {
        // Clear existing hospital markers
        hospitalMarkers.keys.forEach { it.remove() }
        hospitalMarkers.clear()

        mapplsMap?.let { map ->
            val iconFactory = IconFactory.getInstance(this)

            // Try to get hospital icon
            val hospitalIcon = try {
                BitmapUtils.getBitmapFromDrawable(
                    ContextCompat.getDrawable(this, R.drawable.ic_hospital)
                )?.let { iconFactory.fromBitmap(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating hospital icon, using default", e)
                iconFactory.defaultMarker()
            }

            // Filter hospitals to keep only nearby ones (within ~30km of user location)
            // This prevents markers in other countries/far away from affecting the view
            val nearbyHospitals = hospitals.filter { hospital ->
                try {
                    val lat = hospital.latitude?.toDouble() ?: return@filter false
                    val lng = hospital.longitude?.toDouble() ?: return@filter false
                    val hospitalLocation = LatLng(lat, lng)

                    // Calculate distance between user and hospital
                    val distance = calculateDistance(
                        userLocation.latitude, userLocation.longitude,
                        hospitalLocation.latitude, hospitalLocation.longitude
                    )

                    // Keep only hospitals within 30km
                    distance <= 30000 // 30km in meters
                } catch (e: Exception) {
                    Log.e(TAG, "Error calculating distance for hospital", e)
                    false
                }
            }

            Log.d(TAG, "Filtered to ${nearbyHospitals.size} nearby hospitals out of ${hospitals.size} total")

            // Add markers for each nearby hospital
            nearbyHospitals.forEach { hospital ->
                hospital.latitude?.let { lat ->
                    hospital.longitude?.let { lng ->
                        val position = LatLng(lat.toDouble(), lng.toDouble())

                        // Create marker
                        val marker = map.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title(hospital.placeName ?: "Hospital")
                                .snippet(hospital.placeAddress ?: "")
                                .icon(hospitalIcon)
                        )

                        // Store marker with its associated hospital data
                        hospitalMarkers[marker] = hospital
                    }
                }
            }

            // Instead of fitting all markers, just keep the current zoom level
            // focused on the user location - this ensures we don't zoom out too far
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    userLocation,
                    DEFAULT_ZOOM_LEVEL // Use the default zoom level constant
                )
            )
        }
    }

    // Calculate distance between two geographical points in meters using Haversine formula
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Earth radius in meters

        val latDistance = Math.toRadians(lat2 - lat1)
        val lngDistance = Math.toRadians(lon2 - lon1)

        val a = (Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2))

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    // Show dialog to confirm navigation to hospital
    private fun showNavigationConfirmDialog(hospitalName: String, latitude: Double, longitude: Double) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Navigate to Hospital")
        builder.setMessage("Would you like to navigate to $hospitalName?")

        builder.setPositiveButton("Navigate") { dialog, _ ->
            dialog.dismiss()
            openMapplsForNavigation(latitude, longitude)
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    private fun openMapplsForNavigation(latitude: Double, longitude: Double) {
        try {
            // Try to open using Mappls app first
            val mapplsUri = Uri.parse("mappls://navigate?destination=$latitude,$longitude")
            val mapplsIntent = Intent(Intent.ACTION_VIEW, mapplsUri)

            // Check if Mappls app is installed
            if (mapplsIntent.resolveActivity(packageManager) != null) {
                startActivity(mapplsIntent)
                return
            }

            // As a fallback, try Google Maps
            val googleUri = Uri.parse("google.navigation:q=$latitude,$longitude")
            val googleIntent = Intent(Intent.ACTION_VIEW, googleUri)

            if (googleIntent.resolveActivity(packageManager) != null) {
                startActivity(googleIntent)
                return
            }

            // General fallback to any maps app
            val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
            val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)

            if (geoIntent.resolveActivity(packageManager) != null) {
                startActivity(geoIntent)
            } else {
                showToast("No navigation app found on your device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening navigation: ${e.message}", e)
            showToast("Error starting navigation: ${e.localizedMessage}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // MapView lifecycle methods
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onMapReady(p0: GoogleMap) {
        // This method is for compatibility with the GoogleMap interface
        // We're using MapplsMap instead, so we don't need to implement this
    }
}