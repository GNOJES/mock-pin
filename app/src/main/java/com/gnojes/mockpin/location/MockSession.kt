package com.gnojes.mockpin.location

import com.gnojes.mockpin.data.SavedLocation
import kotlinx.coroutines.*

data class MockSample(
    val point: SavedLocation,
    val timeMillis: Long,
    val elapsedNanos: Long,
    val accuracy: Float = 5f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
)

class SampleClock(private val wallClock: () -> Long, private val elapsedClock: () -> Long) {
    private var lastWall = 0L
    private var lastElapsed = 0L
    fun sample(point: SavedLocation): MockSample {
        require(point.valid) { "잘못된 좌표입니다." }
        lastWall = maxOf(wallClock(), lastWall + 1)
        lastElapsed = maxOf(elapsedClock(), lastElapsed + 1)
        return MockSample(point, lastWall, lastElapsed)
    }
}

interface MockDriver {
    suspend fun enable()
    suspend fun send(sample: MockSample)
    suspend fun disable()
}

class MockCleanupException(cause: Throwable) : Exception("Mock Mode 정리에 실패했습니다.", cause)

class MockSession(private val driver: MockDriver, private val clock: SampleClock) {
    suspend fun run(point: () -> SavedLocation, onApplied: suspend (SavedLocation) -> Unit) {
        try {
            // Google Tasks are not cancelled by await cancellation. Finish the in-flight
            // enable/send before disabling, so late task completion cannot re-enable mock.
            withContext(NonCancellable) { driver.enable() }
            currentCoroutineContext().ensureActive()
            while (currentCoroutineContext().isActive) {
                val target = point()
                withContext(NonCancellable) { driver.send(clock.sample(target)) }
                currentCoroutineContext().ensureActive()
                onApplied(target)
                delay(1000)
            }
        } finally {
            withContext(NonCancellable) {
                try { withTimeout(10_000) { driver.disable() } }
                catch (error: Exception) { throw MockCleanupException(error) }
            }
        }
    }
}
