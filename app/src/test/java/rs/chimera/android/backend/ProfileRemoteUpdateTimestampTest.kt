package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileRemoteUpdateTimestampTest {
    @Test
    fun commitTimestampAdvancesWhenClockDoesNot() {
        assertEquals(1_001L, nextRemoteProfileCommitTimestamp(previous = 1_000L, now = 1_000L))
        assertEquals(1_001L, nextRemoteProfileCommitTimestamp(previous = 1_000L, now = 999L))
        assertEquals(2_000L, nextRemoteProfileCommitTimestamp(previous = 1_000L, now = 2_000L))
        assertEquals(2_000L, nextRemoteProfileCommitTimestamp(previous = null, now = 2_000L))
        assertEquals(
            Long.MAX_VALUE,
            nextRemoteProfileCommitTimestamp(previous = Long.MAX_VALUE, now = Long.MAX_VALUE),
        )
    }
}
