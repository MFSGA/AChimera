package rs.chimera.android.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileMutationRunnerTest {
    @Test
    fun `successful mutation refreshes and completes once`() = runBlocking {
        var began = false
        var ended = false
        var cleared = false
        var refreshed = false
        val completions = mutableListOf<Boolean>()
        val runner = ProfileMutationRunner(
            scope = CoroutineScope(coroutineContext),
            tryBegin = { true.also { began = true } },
            end = { ended = true },
            clearStatus = { cleared = true },
            refresh = { refreshed = true },
        )

        runner.run(
            onChanged = { error("unexpected stale mutation") },
            onError = { error("unexpected mutation failure") },
            onCompleted = completions::add,
        ) {}!!.join()

        assertTrue(began)
        assertTrue(cleared)
        assertTrue(refreshed)
        assertTrue(ended)
        assertEquals(listOf(true), completions)
    }

    @Test
    fun `stale mutation reports failure without refreshing`() = runBlocking {
        var changed = false
        var refreshed = false
        var ended = false
        val completions = mutableListOf<Boolean>()
        val runner = ProfileMutationRunner(
            scope = CoroutineScope(coroutineContext),
            tryBegin = { true },
            end = { ended = true },
            clearStatus = {},
            refresh = { refreshed = true },
        )

        runner.run(
            onChanged = { changed = true },
            onError = { error("unexpected mutation failure") },
            onCompleted = completions::add,
        ) {
            throw ProfileChangedDuringMutationException()
        }!!.join()

        assertTrue(changed)
        assertFalse(refreshed)
        assertTrue(ended)
        assertEquals(listOf(false), completions)
    }

    @Test
    fun `busy mutation is rejected before clearing status`() = runBlocking {
        var cleared = false
        var ran = false
        var ended = false
        val completions = mutableListOf<Boolean>()
        val runner = ProfileMutationRunner(
            scope = CoroutineScope(coroutineContext),
            tryBegin = { false },
            end = { ended = true },
            clearStatus = { cleared = true },
            refresh = {},
        )

        val job = runner.run(
            onChanged = {},
            onError = {},
            onCompleted = completions::add,
        ) {
            ran = true
        }

        assertEquals(null, job)
        assertFalse(cleared)
        assertFalse(ran)
        assertFalse(ended)
        assertEquals(listOf(false), completions)
    }
}
