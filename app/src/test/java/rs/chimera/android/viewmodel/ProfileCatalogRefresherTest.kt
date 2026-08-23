package rs.chimera.android.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary

class ProfileCatalogRefresherTest {
    @Test
    fun olderRefreshIsIgnoredAfterNewerRefreshCompletes() = runBlocking {
        val refresher = ProfileCatalogRefresher()
        val firstResult = CompletableDeferred<List<ProfileSummary>>()
        val first = async { refresher.refresh { firstResult.await() } }
        yield()

        val second = refresher.refresh { emptyList() }
        firstResult.complete(emptyList())

        assertEquals(ProfileCatalogRefreshResult.Stale, first.await())
        assertEquals(ProfileCatalogSnapshot(emptyList(), null), (second as ProfileCatalogRefreshResult.Applied).snapshot)
    }

    @Test
    fun staleFailureDoesNotReplaceNewerRefresh() = runBlocking {
        val refresher = ProfileCatalogRefresher()
        val firstResult = CompletableDeferred<List<ProfileSummary>>()
        val first = async { refresher.refresh { firstResult.await() } }
        yield()

        val second = refresher.refresh { emptyList() }
        firstResult.completeExceptionally(IllegalStateException("stale"))

        assertEquals(ProfileCatalogRefreshResult.Stale, first.await())
        assertEquals(ProfileCatalogSnapshot(emptyList(), null), (second as ProfileCatalogRefreshResult.Applied).snapshot)
    }

    @Test
    fun latestFailureIsReported() = runBlocking {
        val refresher = ProfileCatalogRefresher()
        val failure = IllegalStateException("latest")

        val result = refresher.refresh { throw failure }

        assertSame(failure, (result as ProfileCatalogRefreshResult.Failed).error)
    }
}
