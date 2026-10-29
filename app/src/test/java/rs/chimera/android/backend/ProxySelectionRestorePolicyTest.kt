package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.backend.model.ProxySnapshot
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class ProxySelectionRestorePolicyTest {
    @Test
    fun `only persists choices for the exact profile revision loaded by the core`() {
        val current = profile(id = "profile-a", revision = 20L)

        assertTrue(coreProfileRevisionMatches(CoreProfileRevision("profile-a", 20L), current))
        assertFalse(coreProfileRevisionMatches(CoreProfileRevision("profile-a", 10L), current))
        assertFalse(coreProfileRevisionMatches(CoreProfileRevision("profile-b", 20L), current))
        assertFalse(coreProfileRevisionMatches(null, current))
    }

    @Test
    fun `restore deadline skips a controller request that does not finish`() = runBlocking {
        var timedOut = false

        runProxySelectionRestoreWithinTimeout(
            timeoutMillis = 1L,
            onTimeout = { timedOut = true },
            restore = { awaitCancellation() },
        )

        assertTrue(timedOut)
    }

    @Test
    fun `restore deadline preserves cancellation from service shutdown`() = runBlocking {
        var timedOut = false
        val job = launch {
            runProxySelectionRestoreWithinTimeout(
                timeoutMillis = 100L,
                onTimeout = { timedOut = true },
                restore = { awaitCancellation() },
            )
        }
        yield()
        job.cancel(CancellationException("service stopped"))
        job.join()

        assertFalse(timedOut)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `restores existing selector members and prunes removed groups or members`() {
        val groups = listOf(
            group("selector", "Selector", listOf("node-a", "node-b")),
            group("automatic", "URLTest", listOf("node-a", "node-b")),
        )

        val plan = planProxySelectionRestore(
            groups = groups,
            savedSelections = mapOf(
                "selector" to "node-b",
                "removed-member" to "node-c",
                "automatic" to "node-a",
                "removed-group" to "node-a",
            ),
        )

        assertEquals(listOf(SavedProxySelection("selector", "node-b")), plan.selections)
        assertEquals(listOf("automatic", "removed-group", "removed-member"), plan.obsoleteGroups)
    }

    private fun group(
        name: String,
        type: String,
        proxies: List<String>,
    ): ProxyGroupSnapshot {
        val details = buildMap {
            put(name, ProxySnapshot(name, type, emptyList(), isGroup = true))
            proxies.forEach { proxyName ->
                put(proxyName, ProxySnapshot(proxyName, "Vless", emptyList()))
            }
        }
        return ProxyGroupSnapshot(
            name = name,
            proxies = proxies,
            selected = proxies.firstOrNull(),
            mode = ProxyMode.RULE,
            proxyDetails = details,
        )
    }

    private fun profile(id: String, revision: Long?) = ProfileSummary(
        id = id,
        name = id,
        filePath = "/profiles/$id.yaml",
        type = ProfileType.REMOTE,
        isActive = true,
        isRemote = true,
        lastUpdated = revision,
        fileSize = 0L,
    )
}
