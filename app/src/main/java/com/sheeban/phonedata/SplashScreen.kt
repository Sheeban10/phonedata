package com.sheeban.phonedata

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sheeban.phonedata.databinding.ActivitySplashScreenBinding

class SplashScreen : AppCompatActivity() {

    private lateinit var binding: ActivitySplashScreenBinding
    private val permissionRequestCode = 123
    private val permissions = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.BATTERY_STATS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        checkAndRequestPermission()

    }

    private fun startMain() {
        Handler().postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, 3000
        )
    }

    private fun checkAndRequestPermission(){
        val permissionsToRequest = mutableListOf<String>()

        for (permission in permissions){
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.isNotEmpty()){
            ActivityCompat.requestPermissions(this,permissionsToRequest.toTypedArray(),permissionRequestCode)
        }
        else {
            startMain()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == permissionRequestCode){
            var allPermissionsGranted = true

            for(i in grantResults.indices){
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED){
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted){
                startMain()
            }
            else {
                if (permissions.any{ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED}) {
                }
                startMain()
            }
        }
    }

}