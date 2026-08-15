package rs.chimera.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.backend.ProfileAutoUpdateState
import rs.chimera.android.backend.ProfileAutoUpdateStateStore

@RunWith(AndroidJUnit4::class)
class ProfileAutoUpdateStateStoreInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ids = mutableSetOf<String>()

    @After
    fun tearDown() {
        val store = ProfileAutoUpdateStateStore(context)
        ids.forEach(store::clear)
    }

    @Test
    fun statePersistsAcrossStoreInstancesAndCanBeCleared() {
        val id = trackedId("persisted")
        val expected = ProfileAutoUpdateState(
            lastAttempt = 1_723_456_789_000L,
            failureCount = 3,
            nextAttemptAt = 1_723_460_389_000L,
            lastError = "IOException",
        )

        ProfileAutoUpdateStateStore(context).write(id, expected)

        assertEquals(expected, ProfileAutoUpdateStateStore(context).read(id))

        ProfileAutoUpdateStateStore(context).clear(id)
        assertEmpty(ProfileAutoUpdateStateStore(context).read(id))
    }

    @Test
    fun invalidNegativeFailureCountIsNormalizedWhenPersisted() {
        val id = trackedId("negative")

        ProfileAutoUpdateStateStore(context).write(
            id,
            ProfileAutoUpdateState(
                lastAttempt = null,
                failureCount = -7,
                nextAttemptAt = null,
                lastError = null,
            ),
        )

        val stored = ProfileAutoUpdateStateStore(context).read(id)
        assertEquals(0, stored.failureCount)
        assertNull(stored.lastAttempt)
        assertNull(stored.nextAttemptAt)
        assertNull(stored.lastError)
    }

    private fun assertEmpty(state: ProfileAutoUpdateState) {
        assertNull(state.lastAttempt)
        assertEquals(0, state.failureCount)
        assertNull(state.nextAttemptAt)
        assertNull(state.lastError)
    }

    private fun trackedId(suffix: String): String =
        "instrumented-$suffix-${System.nanoTime()}".also(ids::add)
}
