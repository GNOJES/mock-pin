package com.gnojes.mockpin.location

import org.junit.Assert.*
import org.junit.Test

class RestorationStatusTest {
    @Test fun retryConfirmsPreviouslyUnconfirmedInactiveStatus() {
        val status = MockStatus(restoration = LocationRestoration.UNCONFIRMED)
        assertEquals(LocationRestoration.CONFIRMED, status.withConfirmedRealLocation().restoration)
    }
    @Test fun delayedRealFixDoesNotOverwriteNewSessionOrCleanupFailure() {
        for (phase in listOf(MockPhase.ACTIVE, MockPhase.STARTING, MockPhase.STOPPING, MockPhase.RECOVERY_REQUIRED)) {
            val status = MockStatus(phase, restoration = LocationRestoration.UNCONFIRMED)
            assertEquals(status, status.withConfirmedRealLocation())
        }
    }
}
