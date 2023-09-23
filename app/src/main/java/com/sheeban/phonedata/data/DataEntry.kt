package com.sheeban.phonedata.data

data class DataEntry(
    val timestamp: String,
    val captureCount: Int,
    val frequency: Int,
    val connectivity: String,
    val batteryCharging: String,
    val batteryCharge: String,
    val location: String
)
