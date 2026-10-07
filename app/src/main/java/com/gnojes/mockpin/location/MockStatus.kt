package com.gnojes.mockpin.location

import com.gnojes.mockpin.data.SavedLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MockPhase { INACTIVE, STARTING, ACTIVE, STOPPING, RECOVERY_REQUIRED }
enum class HelpAction { DEVELOPER_SETTINGS, LOCATION_SETTINGS, APP_SETTINGS, API_KEY }
data class UserError(val message: String, val action: HelpAction? = null)
data class MockStatus(
    val phase: MockPhase = MockPhase.INACTIVE,
    val current: SavedLocation? = null,
    val error: UserError? = null,
) {
    val running: Boolean get() = phase == MockPhase.ACTIVE || phase == MockPhase.STARTING
    val busy: Boolean get() = phase == MockPhase.STARTING || phase == MockPhase.STOPPING
}

object MockServiceState {
    private val mutable = MutableStateFlow(MockStatus())
    val status = mutable.asStateFlow()
    internal fun publish(status: MockStatus) { mutable.value = status }
}
