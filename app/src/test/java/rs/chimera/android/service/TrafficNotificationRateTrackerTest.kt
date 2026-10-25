package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.TrafficSnapshot

class TrafficNotificationRateTrackerTest {
    @Test
    fun firstSampleStartsWithZeroRates() {
        val tracker = TrafficNotificationRateTracker()

        val rates = tracker.record(TrafficSnapshot(downloadTotal = 500L, uploadTotal = 200L, connectionCount = 1), 1_000L)

        assertEquals(0L, rates.downloadBytesPerSecond)
        assertEquals(0L, rates.uploadBytesPerSecond)
    }

    @Test
    fun calculatesRatesFromCumulativeTrafficSamples() {
        val tracker = TrafficNotificationRateTracker()
        tracker.record(TrafficSnapshot(downloadTotal = 500L, uploadTotal = 200L, connectionCount = 1), 1_000L)

        val rates = tracker.record(TrafficSnapshot(downloadTotal = 4_500L, uploadTotal = 1_200L, connectionCount = 1), 3_000L)

        assertEquals(2_000L, rates.downloadBytesPerSecond)
        assertEquals(500L, rates.uploadBytesPerSecond)
    }

    @Test
    fun counterResetAndNonPositiveElapsedTimeNeverProduceNegativeRates() {
        val tracker = TrafficNotificationRateTracker()
        tracker.record(TrafficSnapshot(downloadTotal = 5_000L, uploadTotal = 3_000L, connectionCount = 1), 3_000L)

        val resetRates = tracker.record(TrafficSnapshot(downloadTotal = 10L, uploadTotal = 5L, connectionCount = 1), 4_000L)
        val invalidElapsedRates = tracker.record(
            TrafficSnapshot(downloadTotal = 20L, uploadTotal = 15L, connectionCount = 1),
            4_000L,
        )

        assertEquals(0L, resetRates.downloadBytesPerSecond)
        assertEquals(0L, resetRates.uploadBytesPerSecond)
        assertEquals(0L, invalidElapsedRates.downloadBytesPerSecond)
        assertEquals(0L, invalidElapsedRates.uploadBytesPerSecond)
    }
}
