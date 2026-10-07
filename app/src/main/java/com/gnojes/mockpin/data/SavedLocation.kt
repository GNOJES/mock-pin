package com.gnojes.mockpin.data

import java.util.Locale
import kotlin.math.*

data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val placeId: String? = null,
) {
    val coordinates: String get() = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    val valid: Boolean get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun distanceTo(other: SavedLocation): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val a = sin((lat2 - lat1) / 2).pow(2) + cos(lat1) * cos(lat2) *
            sin(Math.toRadians(other.longitude - longitude) / 2).pow(2)
        return 6_371_000.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    companion object {
        fun parseCoordinates(latitude: String, longitude: String): SavedLocation? {
            val lat = latitude.trim().toDoubleOrNull() ?: return null
            val lon = longitude.trim().toDoubleOrNull() ?: return null
            return SavedLocation("직접 입력", lat, lon).takeIf { it.valid }
        }
        val DEFAULT = SavedLocation("서울시청", 37.5665, 126.9780)
    }
}

object RecentLocations {
    fun add(existing: List<SavedLocation>, point: SavedLocation): List<SavedLocation> =
        (listOf(point) + existing.filter { it.distanceTo(point) > 10.0 }).take(10)
}
