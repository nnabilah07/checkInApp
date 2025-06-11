package com.example.checkinapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
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

class MainActivity : AppCompatActivity() {

    private lateinit var nameInput: TextInputEditText
    private lateinit var idInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var checkInBtn: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views - make sure IDs match exactly with XML
        nameInput = findViewById(R.id.editTextName)
        idInput = findViewById(R.id.editTextId)
        statusText = findViewById(R.id.textStatus)
        checkInBtn = findViewById(R.id.btnCheckIn)

        // Initialize location provider
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkInBtn.setOnClickListener {
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

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    sendCheckInData(it)
                } ?: run {
                    statusText.text = "Unable to get current location"
                }
            }
            .addOnFailureListener { e ->
                statusText.text = "Location error: ${e.localizedMessage}"
                Log.e("Location", "Error getting location", e)
            }
    }

    private fun sendCheckInData(location: Location) {
        val url = "http://your-group-server-ip-or-domain/checkin.php" // Replace with your actual URL
        val name = nameInput.text.toString().takeIf { it.isNotBlank() } ?: run {
            statusText.text = "Please enter your name"
            return
        }

        val userId = idInput.text.toString().takeIf { it.isNotBlank() } ?: run {
            statusText.text = "Please enter your ID"
            return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val request = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                runOnUiThread {
                    statusText.text = "Check-in successful!"
                }
            },
            { error ->
                runOnUiThread {
                    statusText.text = "Check-in failed: ${error.message ?: "Unknown error"}"
                }
                Log.e("Volley", "Check-in error", error)
            }
        ) {
            override fun getParams(): Map<String, String> {
                return hashMapOf(
                    "name" to name,
                    "user_id" to userId,
                    "latitude" to location.latitude.toString(),
                    "longitude" to location.longitude.toString(),
                    "timestamp" to timestamp
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