package com.gnojes.mockpin.location

import com.gnojes.mockpin.data.SavedLocation
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockSessionTest {
    private val point = SavedLocation("Seoul", 37.5665, 126.978)
    private class Driver : MockDriver {
        val calls = mutableListOf<String>()
        val samples = mutableListOf<MockSample>()
        var enableGate: CompletableDeferred<Unit>? = null
        var enableError: Exception? = null
        var sendError: Exception? = null
        var disableError: Exception? = null
        var disableGate: CompletableDeferred<Unit>? = null
        override suspend fun enable() { calls += "enable"; enableGate?.await(); enableError?.let { throw it } }
        override suspend fun send(sample: MockSample) { calls += "send"; sendError?.let { throw it }; samples += sample }
        override suspend fun disable() { calls += "disable"; disableGate?.await(); disableError?.let { throw it } }
    }
    @Test fun sendsEverySecondAndCleansUpOnStop() = runTest {
        val driver = Driver()
        val session = MockSession(driver, SampleClock({ 1000L }, { 500L }))
        var applied: SavedLocation? = null
        val job = launch { session.run({ point }) { applied = it } }
        runCurrent()
        assertEquals(point, applied)
        advanceTimeBy(2001); runCurrent()
        job.cancelAndJoin()
        assertEquals(listOf("enable", "send", "send", "send", "disable"), driver.calls)
        assertEquals(listOf(1000L, 1001L, 1002L), driver.samples.map { it.timeMillis })
        assertEquals(listOf(500L, 501L, 502L), driver.samples.map { it.elapsedNanos })
        assertEquals(5f, driver.samples.first().accuracy)
        assertEquals(0f, driver.samples.first().speed)
        assertEquals(0f, driver.samples.first().bearing)
    }
    @Test fun sendFailureStillDisablesMockMode() = runTest {
        val driver = Driver().apply { sendError = IllegalStateException("send failed") }
        val error = runCatching { MockSession(driver, SampleClock({ 1L }, { 1L })).run({ point }) {} }.exceptionOrNull()
        assertEquals("send failed", error?.message)
        assertEquals(listOf("enable", "send", "disable"), driver.calls)
    }
    @Test fun failedEnableAlsoAttemptsCleanup() = runTest {
        val driver = Driver().apply { enableError = SecurityException("not selected") }
        val error = runCatching { MockSession(driver, SampleClock({ 1L }, { 1L })).run({ point }) {} }.exceptionOrNull()
        assertTrue(error is SecurityException)
        assertEquals(listOf("enable", "disable"), driver.calls)
    }
    @Test fun stopDuringEnableWaitsForEnableBeforeCleanup() = runTest {
        val gate = CompletableDeferred<Unit>()
        val driver = Driver().apply { enableGate = gate }
        val job = launch { MockSession(driver, SampleClock({ 1L }, { 1L })).run({ point }) {} }
        runCurrent(); job.cancel(); runCurrent()
        assertEquals(listOf("enable"), driver.calls)
        gate.complete(Unit); job.join()
        assertEquals(listOf("enable", "disable"), driver.calls)
    }
    @Test fun cleanupFailureIsReportedInsteadOfPretendingSuccess() = runTest {
        val driver = Driver().apply { sendError = IllegalStateException("send"); disableError = IllegalStateException("cleanup") }
        val error = runCatching { MockSession(driver, SampleClock({ 1L }, { 1L })).run({ point }) {} }.exceptionOrNull()
        assertTrue(error is MockCleanupException)
        // Coroutine stacktrace recovery can wrap an exception in a copy of itself.
        assertEquals("cleanup", generateSequence(error) { it.cause }.last().message)
    }
    @Test fun changingTargetIsAppliedOnNextUpdate() = runTest {
        val driver = Driver()
        var target = point
        val job = launch { MockSession(driver, SampleClock({ 1L }, { 1L })).run({ target }) {} }
        runCurrent()
        target = SavedLocation("Jeju", 33.5, 126.5)
        advanceTimeBy(1000); runCurrent(); job.cancelAndJoin()
        assertEquals(listOf("Seoul", "Jeju"), driver.samples.map { it.point.name })
    }
    @Test fun repeatedStopCannotReleaseDriverBeforeCleanupCompletes() = runTest {
        val gate = CompletableDeferred<Unit>()
        val driver = Driver().apply { disableGate = gate }
        val job = launch { MockSession(driver, SampleClock({ 1L }, { 1L })).run({ point }) {} }
        runCurrent(); job.cancel(); runCurrent()
        val activeDuringCleanup = job.isActive
        val ownedDuringCleanup = job.isInFlight
        // Repeated STOP cancels the SAME job, never opens a parallel cleanup session.
        job.cancel(); runCurrent()
        val callsDuringCleanup = driver.calls.toList()
        gate.complete(Unit); job.join()
        assertFalse(activeDuringCleanup)
        assertTrue("Cancelled session must still own the driver while disable is pending", ownedDuringCleanup)
        assertEquals(listOf("enable", "send", "disable"), callsDuringCleanup)
        assertFalse(job.isInFlight)
    }
}
