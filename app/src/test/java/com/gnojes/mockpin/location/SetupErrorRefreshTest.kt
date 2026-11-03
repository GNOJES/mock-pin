package com.gnojes.mockpin.location

import org.junit.Assert.*
import org.junit.Test

class SetupErrorRefreshTest {
    private val developerError = UserError("모의 위치 앱을 지정하세요", HelpAction.DEVELOPER_SETTINGS)

    @Test fun resolvedDeveloperSettingRemovesStalePrompt() {
        assertNull(refreshSetupError(developerError, null))
    }
    @Test fun incompleteSetupShowsRemainingRequirement() {
        val remaining = UserError("위치 서비스를 켜세요", HelpAction.LOCATION_SETTINGS)
        assertEquals(remaining, refreshSetupError(developerError, remaining))
    }
    @Test fun unrelatedErrorsSurviveReturningFromSettings() {
        val error = UserError("API 키를 확인하세요", HelpAction.API_KEY)
        assertEquals(error, refreshSetupError(error, null))
        val storage = UserError("저장 실패")
        assertEquals(storage, refreshSetupError(storage, null))
    }
    @Test fun recoveryStillRequiresCleanupAfterSettingIsFixed() {
        val refreshed = refreshSetupError(developerError, null, recoveryRequired = true)
        assertNotNull(refreshed)
        assertNull(refreshed?.action)
        assertTrue(refreshed!!.message.contains("Mock Mode 정리"))
    }
}
