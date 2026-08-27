package rs.chimera.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun runtimeApplyPendingClearsOnlyForMatchingRevision() {
        val id = trackedId("runtime-apply-revision")
        val expected = ProfileAutoUpdateState(
            lastAttempt = 1_723_456_789_000L,
            failureCount = 0,
            nextAttemptAt = 1_723_543_189_000L,
            lastError = null,
            runtimeApplyPending = true,
            sourceFingerprint = "source-a",
            profileRevision = 2_000L,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, expected)

        assertTrue(
            store.clearRuntimeApplyPending(
                id,
                expectedProfileRevision = 1_000L,
                expectedSourceFingerprint = "source-a",
            ),
        )
        assertEquals(expected, store.read(id))

        assertTrue(
            store.clearRuntimeApplyPending(
                id,
                expectedProfileRevision = 2_000L,
                expectedSourceFingerprint = "source-a",
            ),
        )
        val cleared = store.read(id)
        assertFalse(cleared.runtimeApplyPending)
        assertEquals(expected.copy(runtimeApplyPending = false), cleared)
    }

    @Test
    fun runtimeApplyPendingClearRejectsDifferentSourceAtSameRevision() {
        val id = trackedId("runtime-apply-source")
        val current = ProfileAutoUpdateState(
            lastAttempt = 2_000L,
            failureCount = 0,
            nextAttemptAt = 3_000L,
            lastError = null,
            runtimeApplyPending = true,
            sourceFingerprint = "source-b",
            profileRevision = 0L,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, current)

        assertTrue(
            store.clearRuntimeApplyPending(
                id,
                expectedProfileRevision = 0L,
                expectedSourceFingerprint = "source-a",
            ),
        )
        assertEquals(current, store.read(id))
    }

    @Test
    fun runtimeApplyPendingMarkPreservesMatchingRevisionMetadata() {
        val id = trackedId("runtime-apply-mark")
        val current = ProfileAutoUpdateState(
            lastAttempt = 2_000L,
            failureCount = 2,
            nextAttemptAt = 3_000L,
            lastError = "IOException",
            runtimeApplyPending = false,
            sourceFingerprint = "source-a",
            profileRevision = 4_000L,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, current)

        val requested = current.copy(
            lastAttempt = 9_000L,
            failureCount = 0,
            nextAttemptAt = 10_000L,
            lastError = null,
            runtimeApplyPending = true,
        )

        assertTrue(store.markRuntimeApplyPending(id, requested))
        assertEquals(current.copy(runtimeApplyPending = true), store.read(id))
    }

    @Test
    fun runtimeApplyPendingMarkBindsMatchingLegacyRevisionToSource() {
        val id = trackedId("runtime-apply-bind-source")
        val current = ProfileAutoUpdateState(
            lastAttempt = 2_000L,
            failureCount = 2,
            nextAttemptAt = 3_000L,
            lastError = "IOException",
            runtimeApplyPending = false,
            profileRevision = 4_000L,
        )
        val requested = current.copy(
            runtimeApplyPending = true,
            sourceFingerprint = "source-a",
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, current)

        assertTrue(store.markRuntimeApplyPending(id, requested))

        assertEquals(
            current.copy(
                runtimeApplyPending = true,
                sourceFingerprint = "source-a",
            ),
            store.read(id),
        )
    }

    @Test
    fun unboundRuntimeApplyMarkDoesNotCreatePendingState() {
        val id = trackedId("runtime-apply-unbound-mark")
        val store = ProfileAutoUpdateStateStore(context)
        val unboundPending = ProfileAutoUpdateState(
            lastAttempt = 2_000L,
            failureCount = 0,
            nextAttemptAt = 3_000L,
            lastError = null,
            runtimeApplyPending = true,
            profileRevision = 4_000L,
        )

        assertTrue(store.markRuntimeApplyPending(id, unboundPending))

        assertEmpty(store.read(id))
    }

    @Test
    fun staleRuntimeApplyMarkDoesNotOverwriteRefreshNowState() {
        val id = trackedId("runtime-apply-stale-mark")
        val refreshNow = ProfileAutoUpdateState(
            lastAttempt = null,
            failureCount = 0,
            nextAttemptAt = 8_000L,
            lastError = null,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, refreshNow)

        val stalePending = ProfileAutoUpdateState(
            lastAttempt = 2_000L,
            failureCount = 0,
            nextAttemptAt = 2_000L,
            lastError = null,
            runtimeApplyPending = true,
            sourceFingerprint = "old-source",
            profileRevision = 4_000L,
        )

        assertTrue(store.markRuntimeApplyPending(id, stalePending))
        assertEquals(refreshNow, store.read(id))
    }

    @Test
    fun staleRuntimeApplyMarkDoesNotOverwriteDifferentSource() {
        val id = trackedId("runtime-apply-stale-source")
        val current = ProfileAutoUpdateState(
            lastAttempt = 5_000L,
            failureCount = 2,
            nextAttemptAt = 8_000L,
            lastError = "new-source-error",
            runtimeApplyPending = false,
            sourceFingerprint = "source-b",
            profileRevision = 6_000L,
        )
        val stalePending = ProfileAutoUpdateState(
            lastAttempt = 7_000L,
            failureCount = 0,
            nextAttemptAt = 9_000L,
            lastError = null,
            runtimeApplyPending = true,
            sourceFingerprint = "source-a",
            profileRevision = 7_000L,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, current)

        assertTrue(store.markRuntimeApplyPending(id, stalePending))

        assertEquals(current, store.read(id))
    }

    @Test
    fun staleRuntimeApplyMarkDoesNotOverwriteNewerRevision() {
        val id = trackedId("runtime-apply-stale-revision")
        val current = ProfileAutoUpdateState(
            lastAttempt = 5_000L,
            failureCount = 1,
            nextAttemptAt = 8_000L,
            lastError = "newer",
            runtimeApplyPending = false,
            sourceFingerprint = "source-a",
            profileRevision = 6_000L,
        )
        val stalePending = current.copy(
            lastAttempt = 3_000L,
            failureCount = 0,
            nextAttemptAt = 4_000L,
            lastError = null,
            runtimeApplyPending = true,
            profileRevision = 4_000L,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, current)

        assertTrue(store.markRuntimeApplyPending(id, stalePending))

        assertEquals(current, store.read(id))
    }

    @Test
    fun legacyBindingPersistsOnlyWhenExpectedStateIsCurrent() {
        val id = trackedId("legacy-bind-current")
        val legacy = ProfileAutoUpdateState(
            lastAttempt = 2_000L,
            failureCount = 1,
            nextAttemptAt = 3_000L,
            lastError = "old",
        )
        val bound = legacy.copy(
            sourceFingerprint = "source-a",
            profileRevision = 4_000L,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, legacy)

        assertEquals(
            bound,
            store.bindLegacyStateIfCurrent(
                id = id,
                expectedLegacyState = legacy,
                boundState = bound,
            ),
        )
        assertEquals(bound, store.read(id))
    }

    @Test
    fun staleLegacyBindingDoesNotOverwriteRefreshNowState() {
        val id = trackedId("legacy-bind-stale")
        val legacy = ProfileAutoUpdateState(
            lastAttempt = 2_000L,
            failureCount = 1,
            nextAttemptAt = 3_000L,
            lastError = "old",
        )
        val bound = legacy.copy(
            sourceFingerprint = "source-a",
            profileRevision = 4_000L,
        )
        val refreshNow = ProfileAutoUpdateState(
            lastAttempt = null,
            failureCount = 0,
            nextAttemptAt = 8_000L,
            lastError = null,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, refreshNow)

        assertNull(
            store.bindLegacyStateIfCurrent(
                id = id,
                expectedLegacyState = legacy,
                boundState = bound,
            ),
        )
        assertEquals(refreshNow, store.read(id))
    }

    @Test
    fun boundStateWriteReplacesMatchingOrOlderRevision() {
        val id = trackedId("bound-write-current")
        val current = ProfileAutoUpdateState(
            lastAttempt = 2_000L,
            failureCount = 1,
            nextAttemptAt = 3_000L,
            lastError = "old",
            runtimeApplyPending = true,
            sourceFingerprint = "source-a",
            profileRevision = 4_000L,
        )
        val incoming = ProfileAutoUpdateState(
            lastAttempt = 5_000L,
            failureCount = 0,
            nextAttemptAt = 6_000L,
            lastError = null,
            runtimeApplyPending = false,
            sourceFingerprint = "source-a",
            profileRevision = 5_000L,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, current)

        store.writeBoundStateIfCurrent(id, incoming)

        assertEquals(incoming, store.read(id))
    }

    @Test
    fun staleBoundStateWriteDoesNotOverwriteNewerRevision() {
        val id = trackedId("bound-write-stale-revision")
        val current = ProfileAutoUpdateState(
            lastAttempt = 5_000L,
            failureCount = 2,
            nextAttemptAt = 8_000L,
            lastError = "newer",
            runtimeApplyPending = true,
            sourceFingerprint = "source-a",
            profileRevision = 6_000L,
        )
        val stale = current.copy(
            lastAttempt = 3_000L,
            failureCount = 0,
            nextAttemptAt = 4_000L,
            lastError = null,
            runtimeApplyPending = false,
            profileRevision = 4_000L,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, current)

        store.writeBoundStateIfCurrent(id, stale)

        assertEquals(current, store.read(id))
    }

    @Test
    fun staleBoundStateWriteDoesNotOverwriteRefreshNowState() {
        val id = trackedId("bound-write-refresh-now")
        val refreshNow = ProfileAutoUpdateState(
            lastAttempt = null,
            failureCount = 0,
            nextAttemptAt = 8_000L,
            lastError = null,
        )
        val stale = ProfileAutoUpdateState(
            lastAttempt = 2_000L,
            failureCount = 0,
            nextAttemptAt = 3_000L,
            lastError = null,
            sourceFingerprint = "old-source",
            profileRevision = 4_000L,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, refreshNow)

        store.writeBoundStateIfCurrent(id, stale)

        assertEquals(refreshNow, store.read(id))
    }

    @Test
    fun staleBoundStateWriteDoesNotOverwriteDifferentSource() {
        val id = trackedId("bound-write-stale-source")
        val current = ProfileAutoUpdateState(
            lastAttempt = 5_000L,
            failureCount = 1,
            nextAttemptAt = 8_000L,
            lastError = "new-source-error",
            runtimeApplyPending = true,
            sourceFingerprint = "source-b",
            profileRevision = 6_000L,
        )
        val stale = ProfileAutoUpdateState(
            lastAttempt = 7_000L,
            failureCount = 0,
            nextAttemptAt = 9_000L,
            lastError = null,
            runtimeApplyPending = false,
            sourceFingerprint = "source-a",
            profileRevision = 7_000L,
        )
        val store = ProfileAutoUpdateStateStore(context)
        store.write(id, current)

        store.writeBoundStateIfCurrent(id, stale)

        assertEquals(current, store.read(id))
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
