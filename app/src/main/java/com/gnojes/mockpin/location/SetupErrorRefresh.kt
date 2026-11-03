package com.gnojes.mockpin.location

internal fun refreshSetupError(error: UserError?, currentSetupError: UserError?, recoveryRequired: Boolean = false): UserError? {
    if (error?.action !in setOf(HelpAction.DEVELOPER_SETTINGS, HelpAction.LOCATION_SETTINGS, HelpAction.APP_SETTINGS)) return error
    if (currentSetupError != null) return currentSetupError
    return if (recoveryRequired) UserError("이전 실행이 정상 종료되지 않아 가상 위치가 남아 있을 수 있습니다. ‘Mock Mode 정리’를 눌러 실제 위치로 복귀하세요.") else null
}
