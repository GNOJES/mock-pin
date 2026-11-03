package com.gnojes.mockpin.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RealLocationVerifierTest {
    @Test fun freshNonMockFixConfirmsRestoration() = runTest {
        assertEquals(LocationRestoration.CONFIRMED,
            RealLocationVerifier({ RestorationFix(false, 101) }, { 100 }).verify())
    }
    @Test fun cachedRealFixCannotConfirmRestoration() = runTest {
        assertEquals(LocationRestoration.UNCONFIRMED,
            RealLocationVerifier({ RestorationFix(false, 99) }, { 100 }).verify())
    }
    @Test fun mockFixCannotConfirmRestoration() = runTest {
        assertEquals(LocationRestoration.UNCONFIRMED,
            RealLocationVerifier({ RestorationFix(true, 101) }, { 100 }).verify())
    }
    @Test fun missingFixLeavesRestorationUnconfirmed() = runTest {
        assertEquals(LocationRestoration.UNCONFIRMED,
            RealLocationVerifier({ null }, { 100 }).verify())
    }
    @Test fun permissionFailureLeavesRestorationUnconfirmed() = runTest {
        assertEquals(LocationRestoration.UNCONFIRMED,
            RealLocationVerifier({ throw SecurityException() }, { 100 }).verify())
    }
    @Test fun providerCancelledRequestLeavesRestorationUnconfirmed() = runTest {
        assertEquals(LocationRestoration.UNCONFIRMED,
            RealLocationVerifier({ throw CancellationException("Provider cancelled task") }, { 100 }).verify())
    }
    @Test fun stalledRequestHasBoundedWait() = runTest {
        assertEquals(LocationRestoration.UNCONFIRMED,
            RealLocationVerifier({ awaitCancellation() }, { 100 }).verify())
    }
}
