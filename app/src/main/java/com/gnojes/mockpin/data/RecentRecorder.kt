package com.gnojes.mockpin.data

/** Records applied locations once, allowing a failed disk write to be retried. */
class RecentRecorder(private val write: suspend (SavedLocation) -> Unit) {
    private var lastRecorded: SavedLocation? = null
    suspend fun recordIfChanged(point: SavedLocation) {
        if (lastRecorded == point) return
        write(point)
        lastRecorded = point
    }
}
