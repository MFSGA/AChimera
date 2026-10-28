package rs.chimera.android.ui.metacubex.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class ProfileElapsedTimeTest {
    @Test
    fun missingUpdateTimeIsNotDisplayedAsAnUpdate() {
        assertNull(profileElapsedTime(lastUpdatedAt = null, now = 10_000L))
        assertNull(profileElapsedTime(lastUpdatedAt = 0L, now = 10_000L))
    }

    @Test
    fun futureUpdateTimeIsPresentedAsRecent() {
        assertEquals(
            ProfileElapsedTime(ProfileElapsedTime.Unit.RECENTLY, 0L),
            profileElapsedTime(lastUpdatedAt = 11_000L, now = 10_000L),
        )
    }

    @Test
    fun elapsedTimeUsesMinuteHourAndDayBuckets() {
        val now = TimeUnit.DAYS.toMillis(10)

        assertEquals(
            ProfileElapsedTime(ProfileElapsedTime.Unit.MINUTES, 2L),
            profileElapsedTime(lastUpdatedAt = now - TimeUnit.MINUTES.toMillis(2), now = now),
        )
        assertEquals(
            ProfileElapsedTime(ProfileElapsedTime.Unit.HOURS, 1L),
            profileElapsedTime(lastUpdatedAt = now - TimeUnit.HOURS.toMillis(1), now = now),
        )
        assertEquals(
            ProfileElapsedTime(ProfileElapsedTime.Unit.DAYS, 1L),
            profileElapsedTime(lastUpdatedAt = now - TimeUnit.DAYS.toMillis(1), now = now),
        )
    }

    @Test
    fun elapsedTimeAdvancesFromMinutesToHoursAfterTheBoundary() {
        val updatedAt = TimeUnit.DAYS.toMillis(10)
        val oneHourLater = updatedAt + TimeUnit.HOURS.toMillis(1)

        assertEquals(
            ProfileElapsedTime(ProfileElapsedTime.Unit.MINUTES, 59L),
            profileElapsedTime(lastUpdatedAt = updatedAt, now = oneHourLater - 1L),
        )
        assertEquals(
            ProfileElapsedTime(ProfileElapsedTime.Unit.HOURS, 1L),
            profileElapsedTime(lastUpdatedAt = updatedAt, now = oneHourLater),
        )
    }
}
