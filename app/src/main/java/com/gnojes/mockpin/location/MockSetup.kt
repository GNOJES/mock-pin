package com.gnojes.mockpin.location

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Process
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException

object MockSetup {
    fun hasLocationPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun check(context: Context): UserError? {
        if (!hasLocationPermission(context)) return UserError("정확한 위치 권한이 필요합니다. 앱 권한에서 위치를 허용해 주세요.", HelpAction.APP_SETTINGS)
        if (!context.getSystemService(LocationManager::class.java).isLocationEnabled)
            return UserError("휴대폰의 위치 서비스가 꺼져 있습니다. 위치를 켜 주세요.", HelpAction.LOCATION_SETTINGS)
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS)
            return UserError("Google Play services를 설치하거나 업데이트해 주세요.", HelpAction.APP_SETTINGS)
        val mockAllowed = runCatching {
            context.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
        if (!mockAllowed) return UserError("MockPin을 개발자 옵션의 모의 위치 앱으로 지정해야 합니다.", HelpAction.DEVELOPER_SETTINGS)
        return null
    }

    fun error(error: Exception): UserError {
        if (error is SecurityException || (error is ApiException && error.statusCode == 13))
            return UserError("모의 위치 권한을 사용할 수 없습니다. 개발자 옵션에서 MockPin을 선택하고 위치 권한을 확인해 주세요.", HelpAction.DEVELOPER_SETTINGS)
        return UserError("모의 위치 실행에 실패했습니다. 위치 서비스와 Google Play services 상태를 확인한 뒤 다시 시도해 주세요.", HelpAction.LOCATION_SETTINGS)
    }
}
