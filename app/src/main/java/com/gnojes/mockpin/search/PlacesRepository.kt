package com.gnojes.mockpin.search

import android.content.Context
import com.gnojes.mockpin.BuildConfig
import com.gnojes.mockpin.data.SavedLocation
import com.gnojes.mockpin.location.HelpAction
import com.gnojes.mockpin.location.UserError
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.*
import kotlinx.coroutines.tasks.await

data class PlaceSuggestion(
    val id: String,
    val name: String,
    val address: String,
    internal val token: AutocompleteSessionToken,
)

class PlacesRepository(private val context: Context) {
    val keyConfigured = BuildConfig.MAPS_API_KEY.isNotBlank() && BuildConfig.MAPS_API_KEY != "DEFAULT_API_KEY"
    private val client by lazy {
        check(keyConfigured) { "API key is missing" }
        if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context.applicationContext, BuildConfig.MAPS_API_KEY)
        Places.createClient(context.applicationContext)
    }
    private var session: AutocompleteSessionToken? = null
    private var lastRequestAt = 0L

    fun endSession() { session = null }

    suspend fun autocomplete(query: String): List<PlaceSuggestion> {
        // A long idle interval starts a new session; no country restriction or location request.
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastRequestAt > 180_000) session = null
        lastRequestAt = now
        val token = session ?: AutocompleteSessionToken.newInstance().also { session = it }
        val request = FindAutocompletePredictionsRequest.builder().setQuery(query).setSessionToken(token).build()
        return client.findAutocompletePredictions(request).await().autocompletePredictions.take(5).map {
            PlaceSuggestion(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString(), token)
        }
    }

    suspend fun details(suggestion: PlaceSuggestion): SavedLocation {
        val fields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION)
        val request = FetchPlaceRequest.builder(suggestion.id, fields).setSessionToken(suggestion.token).build()
        // Selection consumes the autocomplete session even if the network call fails.
        endSession()
        val place = client.fetchPlace(request).await().place
        val location = place.location ?: error("장소의 좌표가 없습니다. 다른 장소를 선택해 주세요.")
        return SavedLocation(place.displayName ?: suggestion.name, location.latitude, location.longitude,
            place.formattedAddress, place.id).also { check(it.valid) { "유효한 장소 좌표가 없습니다." } }
    }

    fun error(error: Exception): UserError {
        if (!keyConfigured) return UserError("secrets.properties에 MAPS_API_KEY를 입력하고 앱을 다시 빌드해 주세요.", HelpAction.API_KEY)
        return when ((error as? ApiException)?.statusCode) {
            PlacesStatusCodes.OVER_QUERY_LIMIT -> UserError("Places 요청 한도를 초과했습니다. Demo Key quota와 결제 설정을 확인하고 잠시 후 재시도해 주세요.", HelpAction.API_KEY)
            PlacesStatusCodes.REQUEST_DENIED -> UserError("Places 요청이 거부되었습니다. Places API (New) 활성화, API 키 제한 및 결제 설정을 확인해 주세요.", HelpAction.API_KEY)
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> UserError("장소 검색에 연결하지 못했습니다. 인터넷 연결을 확인하고 재시도해 주세요.")
            PlacesStatusCodes.NOT_FOUND -> UserError("장소 정보를 찾지 못했습니다. 다시 검색해 주세요.")
            else -> UserError("장소 정보를 가져오지 못했습니다. 네트워크, API Key 및 Places API (New) quota를 확인한 뒤 재시도해 주세요.", HelpAction.API_KEY)
        }
    }
}
