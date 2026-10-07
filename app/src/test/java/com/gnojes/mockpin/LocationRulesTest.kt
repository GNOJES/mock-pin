package com.gnojes.mockpin

import com.gnojes.mockpin.data.SavedLocation
import com.gnojes.mockpin.data.RecentLocations
import org.junit.Assert.*
import org.junit.Test

class LocationRulesTest {
    @Test fun coordinateBoundariesAreAccepted() {
        assertNotNull(SavedLocation.parseCoordinates("-90", "180"))
        assertNotNull(SavedLocation.parseCoordinates("90", "-180"))
        assertEquals(37.5665, SavedLocation.parseCoordinates(" 37.5665 ", "126.9780")!!.latitude, 0.0)
    }
    @Test fun invalidAndNonFiniteCoordinatesAreRejected() {
        for ((lat, lon) in listOf("91" to "0", "0" to "-181", "NaN" to "0", "0" to "Infinity", "" to "0", "hello" to "0")) {
            assertNull("$lat, $lon", SavedLocation.parseCoordinates(lat, lon))
        }
    }
    @Test fun nearbyRecentLocationIsMovedToFrontWithoutDuplicates() {
        val old = SavedLocation("Old", 37.5665, 126.978)
        val other = SavedLocation("Other", 35.0, 128.0)
        val updated = SavedLocation("New", 37.56651, 126.97801)
        val result = RecentLocations.add(listOf(other, old), updated)
        assertEquals(listOf(updated, other), result)
    }
    @Test fun recentLocationsAreLimitedToTen() {
        val result = (0..12).fold(emptyList<SavedLocation>()) { list, n ->
            RecentLocations.add(list, SavedLocation("$n", n.toDouble(), 0.0))
        }
        assertEquals(10, result.size)
        assertEquals("12", result.first().name)
        assertEquals("3", result.last().name)
    }
    @Test fun nearbyLocationsAcrossDatelineAreDeduplicated() {
        val a = SavedLocation("A", 0.0, 179.99999)
        val b = SavedLocation("B", 0.0, -179.99999)
        assertEquals(listOf(b), RecentLocations.add(listOf(a), b))
    }
}
