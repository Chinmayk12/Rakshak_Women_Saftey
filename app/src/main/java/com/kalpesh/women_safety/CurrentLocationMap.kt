package com.kalpesh.women_safety

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.Mappls
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback as MapplsOnMapReadyCallback
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.geometry.LatLng
import com.mappls.sdk.maps.annotations.IconFactory
import com.mappls.sdk.maps.annotations.Marker
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.services.account.MapplsAccountManager

class CurrentLocationMap : AppCompatActivity(), OnMapReadyCallback, MapplsOnMapReadyCallback {

    private lateinit var mapView: MapView
    private var mapplsMap: MapplsMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnMyLocation: ImageButton
    private var currentLocation: LatLng? = null
    private var currentLocationMarker: Marker? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "SafetyMapActivity"
        private const val DEFAULT_ZOOM_LEVEL = 15.0
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
            currentLocation?.let {
                mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(it, DEFAULT_ZOOM_LEVEL))
                return
            }
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

        if (checkLocationPermission()) {
            getCurrentLocationAndMoveCamera(zoomToLocation = true)
        } else {
            requestLocationPermission()
        }
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
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocationAndMoveCamera(zoomToLocation = true)
        } else {
            showToast("Location permission denied")
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocationAndMoveCamera(zoomToLocation: Boolean = false) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                if (it.latitude != 0.0 && it.longitude != 0.0) {
                    val newLatLng = LatLng(it.latitude, it.longitude)
                    currentLocation = newLatLng

                    addOrUpdateCurrentLocationMarker(newLatLng)

                    if (zoomToLocation) {
                        mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, DEFAULT_ZOOM_LEVEL))
                    }
                } else {
                    showToast("Invalid location data")
                    requestFreshLocation()
                }
            } ?: run {
                showToast("Location unavailable, trying to get fresh location")
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

        val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
            priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000
            fastestInterval = 5000
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val newLatLng = LatLng(location.latitude, location.longitude)
                        currentLocation = newLatLng
                        addOrUpdateCurrentLocationMarker(newLatLng)
                        mapplsMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, DEFAULT_ZOOM_LEVEL))
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            },
            null
        )
    }

    private fun addOrUpdateCurrentLocationMarker(location: LatLng) {
        currentLocationMarker?.remove()

        mapplsMap?.let { map ->
            val iconFactory = IconFactory.getInstance(this)
            val icon = iconFactory.defaultMarker()

            currentLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(location)
                    .title("My Location")
                    .icon(icon)
            )
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