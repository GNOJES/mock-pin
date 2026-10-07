package com.gnojes.mockpin.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.locationDataStore by preferencesDataStore("locations")

class LocationStore(context: Context) {
    private val store = context.applicationContext.locationDataStore
    private val favoriteKey = stringPreferencesKey("favorites")
    private val recentKey = stringPreferencesKey("recent")
    val favorites = store.data.map { decode(it[favoriteKey]) }
    val recent = store.data.map { decode(it[recentKey]) }

    suspend fun addFavorite(point: SavedLocation, name: String) {
        require(point.valid && name.isNotBlank())
        store.edit { prefs ->
            val locations = decode(prefs[favoriteKey]).filterNot { it.distanceTo(point) <= 10.0 }
            prefs[favoriteKey] = encode(locations + point.copy(name = name.trim().take(100)))
        }
    }
    suspend fun removeFavorite(point: SavedLocation) {
        store.edit { it[favoriteKey] = encode(decode(it[favoriteKey]).filterNot { saved -> saved == point }) }
    }
    suspend fun recordRecent(point: SavedLocation) {
        store.edit { it[recentKey] = encode(RecentLocations.add(decode(it[recentKey]), point)) }
    }

    private fun encode(points: List<SavedLocation>): String = JSONArray().apply {
        points.forEach { p -> put(JSONObject().apply {
            put("name", p.name); put("latitude", p.latitude); put("longitude", p.longitude)
            p.address?.let { put("address", it) }; p.placeId?.let { put("placeId", it) }
        }) }
    }.toString()

    private fun decode(raw: String?): List<SavedLocation> {
        if (raw == null) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                runCatching {
                    val obj = array.getJSONObject(index)
                    SavedLocation(obj.getString("name"), obj.getDouble("latitude"), obj.getDouble("longitude"),
                        obj.optString("address").takeIf { it.isNotBlank() }, obj.optString("placeId").takeIf { it.isNotBlank() })
                        .takeIf { it.valid }
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}
