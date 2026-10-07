package com.gnojes.mockpin

import com.gnojes.mockpin.data.RecentRecorder
import com.gnojes.mockpin.data.SavedLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RecentRecorderTest {
    @Test fun failedRecentSaveIsRetriedWithoutRepeatingSuccessfulWrites() = runTest {
        var attempts = 0
        val saved = mutableListOf<SavedLocation>()
        val recorder = RecentRecorder { point ->
            attempts++
            if (attempts == 1) throw java.io.IOException("disk unavailable")
            saved += point
        }
        val point = SavedLocation.DEFAULT
        assertTrue(runCatching { recorder.recordIfChanged(point) }.isFailure)
        recorder.recordIfChanged(point)
        recorder.recordIfChanged(point)
        assertEquals(listOf(point), saved)
        assertEquals(2, attempts)
    }
    @Test fun aNewAppliedLocationIsRecorded() = runTest {
        val saved = mutableListOf<SavedLocation>()
        val recorder = RecentRecorder { saved += it }
        recorder.recordIfChanged(SavedLocation.DEFAULT)
        val jeju = SavedLocation("Jeju", 33.5, 126.5)
        recorder.recordIfChanged(jeju)
        assertEquals(listOf(SavedLocation.DEFAULT, jeju), saved)
    }
}
