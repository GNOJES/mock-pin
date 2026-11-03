package com.gnojes.mockpin.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class LocationRestoration { NOT_CHECKED, CHECKING, CONFIRMED, UNCONFIRMED }
data class RestorationFix(val isMock: Boolean, val elapsedNanos: Long)
internal class RealLocationVerifier(private val request: suspend () -> RestorationFix?, private val elapsedNanos: () -> Long) {
    suspend fun verify(): LocationRestoration {
        val requestedAt = elapsedNanos()
        val fix = try {
            withTimeoutOrNull(16_000) { request() }
        } catch (_: CancellationException) {
            // A Google Task can be cancelled without cancellation of this caller.
            currentCoroutineContext().ensureActive()
            null
        } catch (_: Exception) {
            null
        }
        return if (fix != null && !fix.isMock && fix.elapsedNanos >= requestedAt)
            LocationRestoration.CONFIRMED else LocationRestoration.UNCONFIRMED
    }
}
