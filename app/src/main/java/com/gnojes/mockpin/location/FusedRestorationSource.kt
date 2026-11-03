package com.gnojes.mockpin.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

internal class FusedRestorationSource(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // Executed within the user's location foreground service.
    suspend fun request(): RestorationFix? {
        val cancellation = CancellationTokenSource()
        try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0)
                .setDurationMillis(15_000)
                .build()
            return client.getCurrentLocation(request, cancellation.token).await()?.let {
                RestorationFix(it.isMock, it.elapsedRealtimeNanos)
            }
        } finally {
            cancellation.cancel()
        }
    }
}
