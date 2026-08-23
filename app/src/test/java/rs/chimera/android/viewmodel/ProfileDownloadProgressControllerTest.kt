package rs.chimera.android.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.chimera_ffi.DownloadProgress

class ProfileDownloadProgressControllerTest {
    @Test
    fun endedOperationIgnoresLateProgress() = runBlocking {
        var downloading = true
        var published: DownloadProgress? = null
        val scope = CoroutineScope(coroutineContext + Job())
        val controller = ProfileDownloadProgressController(
            scope = scope,
            shouldPublish = { downloading },
            publish = { published = it },
        )
        val generation = controller.begin()
        controller.report(generation, DownloadProgress(1uL, 10uL))
        yield()
        assertEquals(1uL, published?.downloaded)

        controller.end()
        downloading = false
        controller.report(generation, DownloadProgress(2uL, 10uL))
        yield()

        assertNull(published)
        scope.cancel()
    }

    @Test
    fun newerOperationRejectsOlderProgress() = runBlocking {
        var published: DownloadProgress? = null
        val scope = CoroutineScope(coroutineContext + Job())
        val controller = ProfileDownloadProgressController(
            scope = scope,
            shouldPublish = { true },
            publish = { published = it },
        )
        val older = controller.begin()
        val current = controller.begin()

        controller.report(older, DownloadProgress(1uL, 10uL))
        yield()
        assertNull(published)

        controller.report(current, DownloadProgress(4uL, 10uL))
        yield()
        assertEquals(4uL, published?.downloaded)
        scope.cancel()
    }
}
