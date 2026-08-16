package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderlyingNetworkCoordinatorTest {
    @Test
    fun activeValidatedNetworkWinsOverBackupCandidate() {
        val ordered =
            UnderlyingNetworkPolicy.order(
                listOf(
                    candidate(
                        value = "wifi",
                        active = false,
                        validated = true,
                        metered = false,
                        transport = UnderlyingNetworkTransport.WIFI,
                    ),
                    candidate(
                        value = "cellular",
                        active = true,
                        validated = true,
                        metered = true,
                        transport = UnderlyingNetworkTransport.CELLULAR,
                    ),
                ),
            )

        assertEquals(listOf("cellular", "wifi"), ordered)
    }

    @Test
    fun validatedNetworkWinsOverActiveButUnvalidatedCandidate() {
        val ordered =
            UnderlyingNetworkPolicy.order(
                listOf(
                    candidate(
                        value = "cellular",
                        active = true,
                        validated = false,
                        metered = true,
                        transport = UnderlyingNetworkTransport.CELLULAR,
                    ),
                    candidate(
                        value = "wifi",
                        active = false,
                        validated = true,
                        metered = false,
                        transport = UnderlyingNetworkTransport.WIFI,
                    ),
                ),
            )

        assertEquals(listOf("wifi", "cellular"), ordered)
    }

    @Test
    fun latestNetworkResetGenerationSupersedesQueuedRequests() {
        val generation = NetworkResetGeneration()
        val first = generation.next()
        val second = generation.next()

        assertFalse(generation.isLatest(first))
        assertTrue(generation.isLatest(second))
    }

    @Test
    fun invalidatingNetworkResetGenerationRejectsOutstandingRequest() {
        val generation = NetworkResetGeneration()
        val request = generation.next()

        generation.invalidate()

        assertFalse(generation.isLatest(request))
    }

    @Test
    fun initialPrimaryDoesNotTriggerReset() {
        val tracker = UnderlyingNetworkChangeTracker()

        assertNull(tracker.update(100L))
        assertNull(tracker.update(100L))
    }

    @Test
    fun primaryHandoffTriggersReset() {
        val tracker = UnderlyingNetworkChangeTracker()
        tracker.update(100L)

        val transition = tracker.update(200L)

        requireNotNull(transition)
        assertEquals(100L, transition.previousPrimary)
        assertEquals(200L, transition.currentPrimary)
    }

    @Test
    fun lossAndRecoveryBothTriggerAfterBaseline() {
        val tracker = UnderlyingNetworkChangeTracker()
        tracker.update(100L)

        val lost = tracker.update(null)
        val recovered = tracker.update(200L)

        requireNotNull(lost)
        requireNotNull(recovered)
        assertEquals(100L, lost.previousPrimary)
        assertNull(lost.currentPrimary)
        assertNull(recovered.previousPrimary)
        assertEquals(200L, recovered.currentPrimary)
    }

    @Test
    fun resetClearsBaseline() {
        val tracker = UnderlyingNetworkChangeTracker()
        tracker.update(100L)
        assertTrue(tracker.update(200L) != null)

        tracker.reset()

        assertNull(tracker.update(300L))
    }

    private fun <T> candidate(
        value: T,
        active: Boolean,
        validated: Boolean,
        metered: Boolean,
        transport: UnderlyingNetworkTransport,
    ) = UnderlyingNetworkCandidate(
        value = value,
        isActive = active,
        isValidated = validated,
        isMetered = metered,
        transport = transport,
    )
}
