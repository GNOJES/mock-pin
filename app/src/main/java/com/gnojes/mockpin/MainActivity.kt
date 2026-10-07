package com.gnojes.mockpin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.gnojes.mockpin.data.SavedLocation
import com.gnojes.mockpin.location.*
import com.gnojes.mockpin.ui.MockPinScreen
import com.gnojes.mockpin.ui.theme.MockPinTheme

class MainActivity : ComponentActivity() {
    private val model: MockPinViewModel by viewModels()
    private var pendingAction: String? = null
    private var pendingPoint: SavedLocation? = null
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (MockSetup.hasLocationPermission(this)) requestNotificationThenStart()
        else {
            pendingAction = null
            model.report(UserError("정확한 위치 권한이 거부되었습니다. 앱 설정에서 위치 권한과 ‘정확한 위치’를 허용한 뒤 START를 눌러 주세요.", HelpAction.APP_SETTINGS))
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Notification permission is optional for a location FGS; the UI explains STOP visibility.
        dispatchStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        pendingAction = savedInstanceState?.getString("pendingAction")
        if (savedInstanceState?.containsKey("pendingLat") == true) pendingPoint = SavedLocation(
            savedInstanceState.getString("pendingName") ?: "선택 위치", savedInstanceState.getDouble("pendingLat"),
            savedInstanceState.getDouble("pendingLon"), savedInstanceState.getString("pendingAddress"), savedInstanceState.getString("pendingId"))
        enableEdgeToEdge()
        setContent {
            MockPinTheme {
                MockPinScreen(model, onStart = { prepareStart(MockLocationService.START) },
                    onStop = ::stopMock, onApply = ::applyLocation,
                    onCleanup = { prepareStart(MockLocationService.CLEANUP) }, onSettings = ::openSettings)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingAction", pendingAction)
        pendingPoint?.let {
            outState.putString("pendingName", it.name); outState.putDouble("pendingLat", it.latitude)
            outState.putDouble("pendingLon", it.longitude); outState.putString("pendingAddress", it.address); outState.putString("pendingId", it.placeId)
        }
        super.onSaveInstanceState(outState)
    }

    private fun prepareStart(action: String) {
        if (model.status.value.busy) return
        pendingAction = action; pendingPoint = model.selected.value
        model.dismissError()
        if (!MockSetup.hasLocationPermission(this)) locationPermission.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        else requestNotificationThenStart()
    }

    private fun requestNotificationThenStart() {
        if (pendingAction == null) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else dispatchStart()
    }

    private fun dispatchStart() {
        val action = pendingAction ?: return
        val point = pendingPoint ?: model.selected.value
        pendingAction = null; pendingPoint = null
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            model.report(UserError("앱 화면으로 돌아온 뒤 다시 START를 눌러 주세요.")); return
        }
        MockSetup.check(this)?.let { model.report(it); return }
        if (!point.valid) { model.report(UserError("선택한 좌표가 잘못되었습니다.")); return }
        try { ContextCompat.startForegroundService(this, MockLocationService.command(this, action, point)) }
        catch (_: Exception) { model.report(UserError("Foreground Service 시작에 실패했습니다. 위치 권한과 위치 서비스를 확인한 뒤 앱 화면에서 다시 시도해 주세요.", HelpAction.APP_SETTINGS)) }
    }

    private fun stopMock() {
        try { startService(MockLocationService.command(this, MockLocationService.STOP)) }
        catch (_: Exception) { model.report(UserError("STOP 요청을 전달하지 못했습니다. 알림의 STOP 액션을 사용해 주세요.")) }
    }
    private fun applyLocation() {
        if (model.status.value.phase != MockPhase.ACTIVE) return
        try { startService(MockLocationService.command(this, MockLocationService.UPDATE, model.selected.value)) }
        catch (_: Exception) { model.report(UserError("선택 위치를 적용하지 못했습니다. STOP 후 다시 START해 주세요.")) }
    }
    private fun openSettings(action: HelpAction) {
        val intent = when (action) {
            HelpAction.DEVELOPER_SETTINGS -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            HelpAction.LOCATION_SETTINGS -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            HelpAction.APP_SETTINGS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            HelpAction.API_KEY -> return
        }
        try { startActivity(intent) }
        catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            catch (_: Exception) { model.report(UserError("설정 화면을 열 수 없습니다. 휴대폰 설정에서 직접 변경해 주세요.")) }
        }
    }
}
