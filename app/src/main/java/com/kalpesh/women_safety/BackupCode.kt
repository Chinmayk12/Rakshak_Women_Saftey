package com.kalpesh.women_safety//package com.kalpesh.women_safety
//import android.Manifest
//import android.annotation.SuppressLint
//import android.content.pm.PackageManager
//import android.location.Geocoder
//import android.os.Bundle
//import android.util.Log
//import android.view.Gravity
//import android.widget.ImageButton
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import com.google.android.gms.location.FusedLocationProviderClient
//import com.google.android.gms.location.LocationServices
//import com.google.android.gms.maps.GoogleMap
//import com.google.android.gms.maps.OnMapReadyCallback
//import com.mappls.sdk.geojson.Feature
//import com.mappls.sdk.geojson.FeatureCollection
//import com.mappls.sdk.geojson.Point
//import com.mappls.sdk.maps.annotations.IconFactory
//import com.mappls.sdk.maps.annotations.MarkerOptions
//import com.mappls.sdk.maps.MapView
//import com.mappls.sdk.maps.Mappls
//import com.mappls.sdk.maps.MapplsMap
//import com.mappls.sdk.maps.camera.CameraUpdateFactory
//import com.mappls.sdk.maps.geometry.LatLng
//import com.mappls.sdk.maps.geometry.LatLngBounds
//import com.mappls.sdk.maps.style.layers.CircleLayer
//import com.mappls.sdk.maps.style.layers.PropertyFactory
//import com.mappls.sdk.maps.style.sources.GeoJsonSource
//import com.mappls.sdk.services.account.MapplsAccountManager
//import com.mappls.sdk.services.api.OnResponseCallback
//import com.mappls.sdk.services.api.nearby.MapplsNearby
//import com.mappls.sdk.services.api.nearby.MapplsNearbyManager
//import com.mappls.sdk.services.api.nearby.model.NearbyAtlasResponse
//import com.mappls.sdk.services.api.nearby.model.NearbyAtlasResult
//import java.io.IOException
//import java.util.Locale
//
//class SafetyMapActivity : AppCompatActivity(), OnMapReadyCallback,
//    com.mappls.sdk.maps.OnMapReadyCallback {
//
//    private lateinit var mapView: MapView
//    private var mapplsMap: MapplsMap? = null
//    private lateinit var fusedLocationClient: FusedLocationProviderClient
//    private lateinit var btnMyLocation: ImageButton
//    private var currentLocation: LatLng? = null
//    private var currentLocationMarker: com.mappls.sdk.maps.annotations.Marker? = null
//
//    companion object {
//        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
//        private const val TAG = "SafetyMapActivity"
//        private const val DEFAULT_ZOOM_LEVEL = 15.0 // Higher zoom level for better focus
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // Initialize Mappls SDK with your REST and Map SDK keys
//        MapplsAccountManager.getInstance().apply {
//            restAPIKey = "772b6950d7ff705845ac5275c0b49e64"
//            mapSDKKey = "772b6950d7ff705845ac5275c0b49e64"
//            atlasClientId = "96dHZVzsAuuVAzX39l7GAmFzFMtAoTz79J11oNG-nIKoWjzyTGrwzoOQ7u-7uirY7tgB9djB8foqqElPrKB1Nw=="
//            atlasClientSecret = "lrFxI-iSEg8XW9mJDsde8HfcP7dz9_oftnG40umZQPnhtPzdBQRQwSvqNH_bx0CrQfCW7J_Uw8BYENlOBNjzT7KKz98tcBL2"
//        }
//
//        Mappls.getInstance(applicationContext)
//
//        setContentView(R.layout.activity_safety_map)
//
//        // Initialize MapView and Location Client
//        mapView = findViewById(R.id.map_view)
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
//        mapView.onCreate(savedInstanceState)
//        mapView.getMapAsync(this)
//
//        // Initialize My Location button
//        btnMyLocation = findViewById(R.id.btn_my_location)
//        btnMyLocation.setOnClickListener {
//            focusOnCurrentLocation()
//        }
//    }
//
//    private fun focusOnCurrentLocation() {
//        if (checkLocationPermission()) {
//            // If we already have the current location, use it
//            currentLocation?.let {
//                mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, DEFAULT_ZOOM_LEVEL))
//                return
//            }
//
//            // Otherwise, get current location
//            getCurrentLocationAndMoveCamera(zoomToLocation = true)
//        } else {
//            requestLocationPermission()
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    override fun onMapReady(mapplsMap: MapplsMap) {
//        this.mapplsMap = mapplsMap
//
//        // Enable map gestures
//        mapplsMap.uiSettings?.apply {
//            isZoomGesturesEnabled = true
//            isTiltGesturesEnabled = true
//            isScrollGesturesEnabled = true
//            isRotateGesturesEnabled = true
//            logoGravity = Gravity.TOP
//            isDoubleTapGesturesEnabled = true
//        }
//
//
//        if (checkLocationPermission()) {
//            // Initial map setup with current location
//            getCurrentLocationAndMoveCamera(zoomToLocation = true)
//        } else {
//            requestLocationPermission()
//        }
//    }
//
//    override fun onMapError(p0: Int, p1: String?) {
//        Log.e(TAG, "Map Error: $p0 - $p1")
//        showToast("Map Error: $p1")
//    }
//
//    private fun checkLocationPermission() = ContextCompat.checkSelfPermission(
//        this, Manifest.permission.ACCESS_FINE_LOCATION
//    ) == PackageManager.PERMISSION_GRANTED
//
//    private fun requestLocationPermission() {
//        ActivityCompat.requestPermissions(
//            this,
//            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
//            LOCATION_PERMISSION_REQUEST_CODE
//        )
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<out String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
//            grantResults.isNotEmpty() &&
//            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            getCurrentLocationAndMoveCamera(zoomToLocation = true)
//        } else {
//            showToast("Location permission denied")
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun getCurrentLocationAndMoveCamera(zoomToLocation: Boolean = false) {
//        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
//            location?.let {
//                // Check if latitude and longitude are valid
//                if (it.latitude != 0.0 && it.longitude != 0.0) {
//                    val newLatLng = LatLng(it.latitude, it.longitude)
//                    currentLocation = newLatLng // Store current location
//
//                    // Add or update marker at the current location
//                    addOrUpdateCurrentLocationMarker(newLatLng)
//
//                    if (zoomToLocation) {
//                        // Zoom to the current location with appropriate zoom level
//                        mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, DEFAULT_ZOOM_LEVEL))
//                    }
//
//                    findNearbyHospitals(it.latitude, it.longitude)
//                } else {
//                    showToast("Invalid location data")
//                }
//            } ?: run {
//                showToast("Location unavailable")
//                Log.e(TAG, "Unable to get current location")
//            }
//        }.addOnFailureListener { e ->
//            Log.e(TAG, "Error getting location: ${e.message}", e)
//            showToast("Error getting location: ${e.localizedMessage}")
//        }
//    }
//
//    private fun addOrUpdateCurrentLocationMarker(location: LatLng) {
//        // Remove existing marker if any
//        currentLocationMarker?.remove()
//
//        mapplsMap?.let { map ->
//            val iconFactory = IconFactory.getInstance(this)
//
//            // Create a blue colored default marker
//            // Note: If your version of Mappls SDK supports colored markers
//            val icon = iconFactory.defaultMarker() // Some SDKs have .defaultMarker(COLOR_BLUE)
//
//            // Create the marker
//            currentLocationMarker = map.addMarker(
//                MarkerOptions()
//                    .position(location)
//                    .title("My Location")
//                    .icon(icon)
//            )
//        }
//    }
//
//    private fun findNearbyHospitals(latitude: Double, longitude: Double) {
//        try {
//            Log.d(TAG, "Searching for hospitals near: $latitude, $longitude")
//
//            // Create the NearbyAtlas request using the correct builder pattern
//            val nearbyBuilder = MapplsNearby.builder()
//                .setLocation("$latitude,$longitude")
//                .keyword("hospital")
//                .radius(5000)
//                .sortBy("distance")
//                .page(1)  // Get the first page of results
//
//            // Create manager with the builder
//            val nearbyManager = MapplsNearbyManager.newInstance(nearbyBuilder.build())
//
//            // Add callback for the response
//            nearbyManager.call(object : OnResponseCallback<NearbyAtlasResponse> {
//                override fun onSuccess(response: NearbyAtlasResponse?) {
//                    Log.d(TAG, "Nearby API Success with ${response?.suggestedLocations?.size ?: 0} locations")
//
//                    val results = response?.suggestedLocations
//                    if (results.isNullOrEmpty()) {
//                        runOnUiThread {
//                            showToast("No hospitals found in this area")
//                        }
//                        return
//                    }
//
//                    // Create a safe copy of valid hospitals
//                    val validHospitals = ArrayList<NearbyAtlasResult>()
//
//                    for (hospital in results) {
//                        if (hospital.latitude != null && hospital.longitude != null) {
//                            validHospitals.add(hospital)
//                        } else {
//                            // Attempt to geocode hospital name to get coordinates
//                            val hospitalCoordinates = geocodeHospitalName(hospital.placeName)
//                            if (hospitalCoordinates != null) {
//                                // Update the hospital object with the fetched coordinates
//                                hospital.latitude = hospitalCoordinates.latitude
//                                hospital.longitude = hospitalCoordinates.longitude
//                                validHospitals.add(hospital)
//                            } else {
//                                Log.w(TAG, "Skipping hospital with missing coordinates: ${hospital.placeName}")
//                            }
//                        }
//                    }
//
//                    if (validHospitals.isEmpty()) {
//                        runOnUiThread {
//                            showToast("No valid hospital locations found")
//                        }
//                        return
//                    }
//
//                    runOnUiThread {
//                        try {
//                            val userLocation = LatLng(latitude, longitude)
//                            addHospitalMarkers(validHospitals, userLocation)
//                            showToast("Found ${validHospitals.size} hospitals nearby")
//                        } catch (e: Exception) {
//                            Log.e(TAG, "Error displaying markers: ${e.message}", e)
//                            showToast("Error displaying hospitals: ${e.localizedMessage}")
//                        }
//                    }
//                }
//
//                override fun onError(code: Int, message: String?) {
//                    Log.e(TAG, "Nearby API Error $code: $message")
//                    runOnUiThread {
//                        showToast("Error finding hospitals: $message")
//                    }
//                }
//            })
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Exception while finding hospitals: ${e.message}", e)
//            showToast("Error: ${e.localizedMessage}")
//        }
//    }
//
//    // Geocode hospital name to get coordinates using Geocoder
//    private fun geocodeHospitalName(hospitalName: String?): LatLng? {
//        if (hospitalName.isNullOrEmpty()) {
//            return null
//        }
//
//        val geocoder = Geocoder(this, Locale.getDefault())
//        return try {
//            val addresses = geocoder.getFromLocationName(hospitalName, 1)
//            if (addresses?.isNotEmpty() == true) {
//                addresses[0]?.let { LatLng(it.latitude, it.longitude) }
//            } else {
//                null
//            }
//        } catch (e: IOException) {
//            Log.e(TAG, "Geocoding failed for $hospitalName: ${e.message}")
//            null
//        }
//    }
//
//    private fun addHospitalMarkers(hospitals: List<NearbyAtlasResult>, userLocation: LatLng) {
//        mapplsMap?.getStyle { style ->
//            try {
//                // Clear existing layers and sources
//                listOf("hospital-layer", "hospital-source").forEach {
//                    if (style.getLayer(it) != null) style.removeLayer(it)
//                    if (style.getSource(it) != null) style.removeSource(it)
//                }
//
//                // Start with user location in bounds
//                val bounds = LatLngBounds.Builder().include(userLocation)
//                val features = ArrayList<Feature>()
//
//                // Log all hospitals for debugging
//                Log.d(TAG, "Processing ${hospitals.size} hospitals")
//                hospitals.forEach { hospital ->
//                    Log.d(TAG, "Hospital: ${hospital.placeName}, lat: ${hospital.latitude}, lng: ${hospital.longitude}")
//                }
//
//                // Filter and process valid hospitals
//                for (hospital in hospitals) {
//                    // Skip hospitals with null coordinates
//                    if (hospital.latitude == null || hospital.longitude == null) {
//                        Log.w(TAG, "Skipping hospital with null coordinates: ${hospital.placeName}")
//                        continue
//                    }
//
//                    try {
//                        // Convert to double explicitly to avoid any boxing issues
//                        val latitude = hospital.latitude!!.toDouble()
//                        val longitude = hospital.longitude!!.toDouble()
//
//                        // Add location to bounds
//                        val hospitalLocation = LatLng(latitude, longitude)
//                        bounds.include(hospitalLocation)
//
//                        // Create feature for GeoJSON
//                        val point = Point.fromLngLat(longitude, latitude)
//                        val feature = Feature.fromGeometry(point)
//
//                        // Add properties
//                        feature.addNumberProperty("radius", 50.0)
//                        feature.addStringProperty("color", "#FF0000")
//                        feature.addStringProperty("name", hospital.placeName ?: "Unknown Hospital")
//                        feature.addStringProperty("address", hospital.placeAddress ?: "No address available")
//                        feature.addNumberProperty("distance", hospital.distance ?: 0.0)
//
//                        features.add(feature)
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Error processing hospital ${hospital.placeName}: ${e.message}")
//                    }
//                }
//
//                if (features.isEmpty()) {
//                    showToast("No valid hospital locations to display")
//                    return@getStyle
//                }
//
//                // Create GeoJSON source with features
//                val featureCollection = FeatureCollection.fromFeatures(features)
//                val source = GeoJsonSource("hospital-source", featureCollection)
//                style.addSource(source)
//
//                // Add outlined circle layer for hospitals (no fill, only outline)
//                val circleLayer = CircleLayer("hospital-layer", "hospital-source")
//                circleLayer.withProperties(
//                    PropertyFactory.circleRadius(50f),  // Larger radius for hospital area
//                    PropertyFactory.circleOpacity(0f),  // No fill color (transparent)
//                    PropertyFactory.circleStrokeWidth(3f), // Border width
//                    PropertyFactory.circleStrokeColor("#FF0000")  // Red color for border
//                )
//                style.addLayer(circleLayer)
//
//                // Don't adjust camera here - we only want to see the hospitals
//                // in relation to the user's current location which is already
//                // properly centered on the map
//            } catch (e: Exception) {
//                Log.e(TAG, "Error in addHospitalMarkers: ${e.message}", e)
//                showToast("Error displaying hospitals: ${e.localizedMessage}")
//            }
//        }
//    }
//
//    private fun showToast(message: String) {
//        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
//    }
//
//    // MapView lifecycle methods
//    override fun onStart() { super.onStart(); mapView.onStart() }
//    override fun onResume() { super.onResume(); mapView.onResume() }
//    override fun onPause() { super.onPause(); mapView.onPause() }
//    override fun onStop() { super.onStop(); mapView.onStop() }
//    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
//    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        mapView.onSaveInstanceState(outState)
//    }
//
//    override fun onMapReady(p0: GoogleMap) {
//        // This method is for compatibility with the GoogleMap interface
//        // We're using MapplsMap instead, so we don't need to implement this
//    }
//}