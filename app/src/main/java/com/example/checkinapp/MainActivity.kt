package com.example.checkinapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult


class MainActivity : AppCompatActivity() {

    private lateinit var nameInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var checkInBtn: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val LOCATION_REQUEST_CODE = 100
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
22
        // Initialize views - make sure IDs match exactly with XML
        nameInput = findViewById(R.id.editTextName)
        statusText = findViewById(R.id.textStatus)
        checkInBtn = findViewById(R.id.btnCheckIn)
        progressBar = findViewById(R.id.progressBar)

        // Initialize location provider
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkInBtn.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            checkLocationPermissionAndProceed()
        }

    }

    private fun checkLocationPermissionAndProceed() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getLocationAndSend()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                showPermissionRationale()
            }
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_REQUEST_CODE
                )
            }
        }
    }

    private fun showPermissionRationale() {
        Toast.makeText(
            this,
            "Location permission is required for check-in functionality",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun getLocationAndSend() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Create a location request
        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            numUpdates = 1  // Stop after getting one accurate location
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    sendCheckInData(location)
                } else {
                    statusText.text = "Unable to get current location"
                }
                // Stop location updates after one
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        // Start location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun sendCheckInData(location: Location) {
        val url = "http://172.30.152.121/receive_checkin.php" // Correct endpoint!

        val name = nameInput.text.toString().takeIf { it.isNotBlank() } ?: run {
            statusText.text = "Please enter your name"
            return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val request = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusText.text = response // shows "Check-in recorded successfully!"
                }
            },
            { error ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusText.text = "Check-in failed: ${error.message ?: "Unknown error"}"
                }
                Log.e("Volley", "Check-in error", error)
            }
        ) {
            override fun getParams(): Map<String, String> {
                return hashMapOf(
                    "username" to name,
                    "latitude" to location.latitude.toString(),
                    "longitude" to location.longitude.toString()
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocationAndSend()
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}