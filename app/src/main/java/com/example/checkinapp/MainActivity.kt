package com.example.checkinapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.checkinapp.databinding.ActivityMainBinding
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val LOCATION_SETTINGS_REQUEST_CODE = 101
        private const val SERVER_URL = "http://172.30.152.121/receive_checkin.php"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeLocationClient()
        setupUI()
    }

    private fun initializeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkGooglePlayServices()
    }

    private fun checkGooglePlayServices() {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            googleApiAvailability.getErrorDialog(this, resultCode, 0)?.show()
        }
    }

    private fun setupUI() {
        binding.editTextName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleCheckIn()
                true
            } else false
        }

        binding.btnCheckIn.setOnClickListener {
            handleCheckIn()
        }
    }

    private fun handleCheckIn() {
        val username = binding.editTextName.text.toString().trim()
        when {
            username.isEmpty() -> showToast("Please enter your name")
            !hasLocationPermission() -> requestLocationPermission()
            else -> proceedWithLocationCheck(username)
        }
    }

    private fun proceedWithLocationCheck(username: String) {
        if (isLocationEnabled()) {
            getCurrentLocation(username)
        } else {
            showLocationEnableDialog(username)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestLocationPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        ActivityCompat.requestPermissions(
            this,
            permissions,
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showLocationEnableDialog(username: String) {
        AlertDialog.Builder(this)
            .setTitle("Location Required")
            .setMessage("Please enable location services to continue")
            .setPositiveButton("Settings") { _, _ ->
                startActivityForResult(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                    LOCATION_SETTINGS_REQUEST_CODE
                )
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener {
                // Handle case when user cancels without enabling location
                showToast("Location services must be enabled to check in")
            }
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(username: String) {
        binding.progressBar.visibility = View.VISIBLE

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000
        ).apply {
            setMinUpdateIntervalMillis(5000)
            setWaitForAccurateLocation(true)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                removeLocationUpdates()
                binding.progressBar.visibility = View.GONE

                locationResult.lastLocation?.let { location ->
                    sendLocationToServer(
                        username,
                        location.latitude,
                        location.longitude
                    )
                } ?: run {
                    showToast("Unable to get location")
                    getLastKnownLocation(username)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback as LocationCallback,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Location request failed: ${e.message}")
                showToast("Location error: ${e.localizedMessage}")
                getLastKnownLocation(username)
            }
        } catch (e: SecurityException) {
            binding.progressBar.visibility = View.GONE
            Log.e(TAG, "Security Exception: ${e.message}")
            showToast("Location permission was revoked")
            requestLocationPermission()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(username: String) {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                location?.let {
                    sendLocationToServer(
                        username,
                        it.latitude,
                        it.longitude
                    )
                } ?: showToast("No last known location available")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Last location failed: ${e.message}")
                showToast("Failed to get last known location")
            }
    }

    private fun removeLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    private fun sendLocationToServer(username: String, latitude: Double, longitude: Double) {
        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(
            Request.Method.POST, SERVER_URL,
            { response ->
                Log.d(TAG, "Server response: $response")
                showToast("Check-in successful!", Toast.LENGTH_LONG)
            },
            { error ->
                Log.e(TAG, "Server error: ${error.message}")
                showToast("Check-in failed: ${error.message}", Toast.LENGTH_LONG)
            }
        ) {
            override fun getParams() = mutableMapOf(
                "username" to username,
                "latitude" to latitude.toString(),
                "longitude" to longitude.toString(),
                "checkin_time" to System.currentTimeMillis().toString()
            )
        }
        queue.add(request)
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, check if location is enabled
                    binding.btnCheckIn.performClick()
                } else {
                    showToast("Location permission denied")
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOCATION_SETTINGS_REQUEST_CODE) {
            if (isLocationEnabled()) {
                binding.btnCheckIn.performClick()
            } else {
                showToast("Location services still disabled")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeLocationUpdates()
    }
}