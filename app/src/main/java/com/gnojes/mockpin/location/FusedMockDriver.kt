package com.gnojes.mockpin.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

@SuppressLint("MissingPermission") // Activity and service both validate runtime permissions.
class FusedMockDriver(context: Context) : MockDriver {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    override suspend fun enable() { client.setMockMode(true).await() }
    override suspend fun disable() { client.setMockMode(false).await() }
    override suspend fun send(sample: MockSample) {
        client.setMockLocation(Location("gps").apply {
            latitude = sample.point.latitude
            longitude = sample.point.longitude
            accuracy = sample.accuracy
            speed = sample.speed
            bearing = sample.bearing
            time = sample.timeMillis
            elapsedRealtimeNanos = sample.elapsedNanos
        }).await()
    }
}
