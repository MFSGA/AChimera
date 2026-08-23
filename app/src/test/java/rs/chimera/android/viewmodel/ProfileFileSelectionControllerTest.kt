package rs.chimera.android.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileFileSelectionControllerTest {
    @Test
    fun newerSelectionRejectsOlderCompletion() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val controller = ProfileFileSelectionController<String>(scope)
        val older = CompletableDeferred<String>()
        val newer = CompletableDeferred<String>()

        controller.select(load = { older.await() })
        controller.select(load = { newer.await() })
        newer.complete("new")
        yield()
        assertEquals("new", controller.selection)

        older.complete("old")
        yield()
        assertEquals("new", controller.selection)
        scope.cancel()
    }

    @Test
    fun clearRejectsInflightCompletion() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val controller = ProfileFileSelectionController<String>(scope)
        val pending = CompletableDeferred<String>()

        controller.select(load = { pending.await() })
        controller.clear()
        pending.complete("late")
        yield()

        assertNull(controller.selection)
        scope.cancel()
    }
}
