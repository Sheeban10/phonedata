package com.sheeban.phonedata

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.sheeban.phonedata.data.DataEntry
import com.sheeban.phonedata.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    lateinit var timDate: TextView
    lateinit var captureCount: TextView
    lateinit var frequency: EditText
    lateinit var connectivity: TextView
    lateinit var charging: TextView
    lateinit var charge: TextView
    lateinit var locationTv: TextView
    lateinit var refresh: Button


    private var refreshCaptureCount = 0
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val firestore = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var delayMillis: Long = 900000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        createNotificationChannel()

        timDate = binding.tvTimeDate
        captureCount = binding.tvCaptureCount
        frequency = binding.tvFrequency
        connectivity = binding.tvConnectivity
        charging = binding.tvCharging
        charge = binding.tvCharge
        locationTv = binding.tvLocation
        refresh = binding.btnRefresh

        refreshCaptureCount = getRefreshCount()
        captureCount.text = "$refreshCaptureCount"


        frequency.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                val newValue = s.toString().toIntOrNull() ?: 15

                delayMillis = (newValue * 60000).toLong()
                scheduleFetchData()
            }
        })

        fetchData()

        scheduleFetchData()

        refresh.setOnClickListener {
            captureCount.text = "$refreshCaptureCount"
            saveRefreshCount(refreshCaptureCount)
                fetchData()
        }

    }

    private fun scheduleFetchData() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                // Call the fetchData() function
                fetchData()

                // Schedule the next fetchData() call
                handler.postDelayed(this, delayMillis)
            }
        }, delayMillis)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "battery_low_channel"
            val channelName = "Battery Low Channel"
            val channelDescription = "Channel for battery low notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }


    private fun fetchData() {
        time()
        checkConnectivity()
        batteryCharging()
        location()
        refreshCaptureCount++


        val currentTime = timDate.text.toString()
        val connectivityStatus = connectivity.text.toString()
        val batteryChargingStatus = charging.text.toString()
        val batteryChargePercentage = charge.text.toString()
        val locationCoordinates = locationTv.text.toString()

        val dataEntry = DataEntry(
            timestamp = currentTime,
            captureCount = refreshCaptureCount,
            frequency = delayMillis.toInt()/60000,
            connectivity = connectivityStatus,
            batteryCharging = batteryChargingStatus,
            batteryCharge = batteryChargePercentage,
            location = locationCoordinates
        )

        firestore.collection("data_entries")
            .add(dataEntry)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(this, "Data added to Firestore", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add data to Firestore", Toast.LENGTH_SHORT).show()
            }
    }


    private fun checkConnectivity(){
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo

        if(networkInfo != null && networkInfo.isConnected){
            connectivity.text = "ON"
        }else{
            connectivity.text = "OFF"
        }
    }

    private fun batteryCharging(){

        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryStatus = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val isCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
        val isDischarging = batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING
        val isFull = batteryStatus == BatteryManager.BATTERY_STATUS_FULL

        when{
            isCharging -> charging.text ="ON"
            isDischarging -> charging.text = "OFF"
            isFull -> charging.text = "FULL"
        }

        charge.text = "$batteryLevel%"

        if (batteryLevel < 20 && !isCharging) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channelId = "battery_low_channel"

            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_battery_low)
                .setContentTitle("Battery Low")
                .setContentText("Your battery is below 20%. Charge your device.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            // Use a unique notification ID to avoid overwriting previous notifications
            val notificationId = 1
            notificationManager.notify(notificationId, notification)
        }
    }

    private fun time(){
        val calender = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(calender.time)

        timDate.text = "$currentTime"

    }

    private  fun location(){

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val locationEnabledBefore = isLocationEnabled()

        if (ContextCompat.checkSelfPermission(
                this,Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED){
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null){
                    val geocoder =Geocoder(this, Locale.getDefault())
                    val address = geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1
                    )
                    if (address != null && address.isNotEmpty()){
                        locationTv.text = "${location.longitude}, ${location.latitude}"

                        if (!locationEnabledBefore) {
                            Toast.makeText(this, "Location is enabled", Toast.LENGTH_SHORT).show()
                        }
                        // Update the preference to indicate that location has been enabled
                        getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("location_enabled", true)
                            .apply()
                    } else{
                        Toast.makeText(this, "Error Getting Location", Toast.LENGTH_SHORT).show()
                    }
                } else{
                    Toast.makeText(this, "Turn on the location and restart app", Toast.LENGTH_SHORT).show()
                }
            }
        } else{
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 123)
        }
    }

    private fun isLocationEnabled():Boolean{
        val locationMode: Int
        try {
            locationMode = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.LOCATION_MODE
            )
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
            return false
        }
        return locationMode != Settings.Secure.LOCATION_MODE_OFF
    }


    private fun getRefreshCount(): Int{
        val sharedPreferences = getPreferences(Context.MODE_PRIVATE)
        return sharedPreferences.getInt("refreshCount", 0)
    }

    private fun saveRefreshCount(count: Int) {
        val sharedPreferences = getPreferences(Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("refreshCount", count)
        editor.apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshHandler.removeCallbacksAndMessages(null)
    }
}