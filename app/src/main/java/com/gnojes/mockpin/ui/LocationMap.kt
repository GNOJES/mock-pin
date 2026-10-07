package com.gnojes.mockpin.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.gnojes.mockpin.location.UserError
import com.gnojes.mockpin.location.HelpAction
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gnojes.mockpin.data.SavedLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun LocationMap(selected: SavedLocation, active: SavedLocation?, onSelect: (SavedLocation) -> Unit, onLocate: () -> Unit,
    onError: (UserError) -> Unit, onKeyHelp: () -> Unit) {
    var loaded by remember { mutableStateOf(false) }
    var delayed by remember { mutableStateOf(false) }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(selected.latitude, selected.longitude), 15f) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { LocationServices.getFusedLocationProviderClient(context) }
    val currentActive by rememberUpdatedState(active)
    var locating by remember { mutableStateOf(false) }
    val locate: () -> Unit = {
        if (!locating && loaded) scope.launch {
            locating = true
            val cancellation = CancellationTokenSource()
            try {
                val mockPoint = currentActive
                val target = if (mockPoint != null) LatLng(mockPoint.latitude, mockPoint.longitude) else {
                    val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(0).setDurationMillis(15_000).build()
                    val location = client.getCurrentLocation(request, cancellation.token).await()
                    // A stopped mock session must not move the camera to an old mock fix.
                    val latestMock = currentActive
                    if (latestMock != null) LatLng(latestMock.latitude, latestMock.longitude)
                    else if (location != null && !location.isMock) LatLng(location.latitude, location.longitude)
                    else null
                }
                if (target != null) camera.animate(CameraUpdateFactory.newLatLngZoom(target, 15f))
                else onError(UserError("현재 위치를 찾지 못했습니다. 위치 서비스를 켜고 잠시 후 다시 눌러 주세요.", HelpAction.LOCATION_SETTINGS))
            } catch (error: CancellationException) {
                throw error
            } catch (_: SecurityException) {
                onError(UserError("내 위치로 이동하려면 위치 권한을 허용해 주세요.", HelpAction.APP_SETTINGS))
            } catch (_: Exception) {
                onError(UserError("현재 위치를 가져오지 못했습니다. 위치 서비스와 인터넷 연결을 확인해 주세요.", HelpAction.LOCATION_SETTINGS))
            } finally {
                cancellation.cancel()
                locating = false
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locate()
        else onError(UserError("내 위치로 이동하려면 위치 권한을 허용해 주세요.", HelpAction.APP_SETTINGS))
    }
    LaunchedEffect(loaded, selected) {
        if (loaded) {
            try { camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(selected.latitude, selected.longitude), 15f)) }
            catch (error: Exception) { if (error is CancellationException) throw error }
        }
    }
    LaunchedEffect(loaded) { if (!loaded) { delay(15_000); delayed = true } }
    Box(Modifier.fillMaxSize()) {
        GoogleMap(Modifier.fillMaxSize(), cameraPositionState = camera,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            contentPadding = PaddingValues(top = 76.dp, bottom = 36.dp), onMapLoaded = { loaded = true },
            onMapClick = { onSelect(SavedLocation("지도에서 선택", it.latitude, it.longitude)) }) {
            Marker(state = rememberUpdatedMarkerState(LatLng(selected.latitude, selected.longitude)), title = "선택: ${selected.name}",
                snippet = selected.coordinates, icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            active?.let {
                Marker(state = rememberUpdatedMarkerState(LatLng(it.latitude, it.longitude)), title = "Mock Location ACTIVE: ${it.name}",
                    snippet = it.coordinates, icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED), zIndex = 1f)
            }
        }
        Surface(Modifier.align(Alignment.TopEnd).padding(top = 88.dp, end = 12.dp),
            shape = MaterialTheme.shapes.large, shadowElevation = 4.dp) {
            TextButton(enabled = loaded && !locating, onClick = {
                onLocate()
                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (currentActive != null || hasPermission) locate()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text(if (locating) "위치 찾는 중…" else "◎ 내 위치로 이동") }
        }
        Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp), shape = MaterialTheme.shapes.small, tonalElevation = 2.dp) {
            TextButton(onClick = onKeyHelp) { Text(if (!loaded && delayed) "지도 로딩 지연 · 설정 확인" else "지도 / API 도움말", style = MaterialTheme.typography.labelSmall) }
        }
    }
}
