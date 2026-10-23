package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProxySubtitlePatternProviderTest {
    @Test
    fun readsAndCompilesPatternOncePerConfigRevision() {
        var metadata = ProxySubtitlePatternMetadata(lastModifiedMillis = 1L, length = 20L)
        var readCount = 0
        val provider = ProxySubtitlePatternProvider(
            readPattern = {
                readCount++
                "香港"
            },
            readMetadata = { metadata },
        )

        val first = provider.patternFor("profile.yaml")
        val cached = provider.patternFor("profile.yaml")
        assertNotNull(first)
        assertEquals(first, cached)
        assertEquals(1, readCount)

        metadata = metadata.copy(lastModifiedMillis = 2L)
        assertNotNull(provider.patternFor("profile.yaml"))
        assertEquals(2, readCount)
    }

    @Test
    fun unavailableOrInvalidOptionalPatternDoesNotFailProxyMapping() {
        val unavailable = ProxySubtitlePatternProvider(
            readPattern = { error("must not read a missing config") },
            readMetadata = { null },
        )
        assertNull(unavailable.patternFor("missing.yaml"))

        val invalid = ProxySubtitlePatternProvider(
            readPattern = { "[" },
            readMetadata = { ProxySubtitlePatternMetadata(lastModifiedMillis = 1L, length = 1L) },
        )
        assertNull(invalid.patternFor("invalid.yaml"))
    }

    @Test
    fun matchingPatternSplitsFirstCaseInsensitiveOccurrenceAndTrimsTitle() {
        val pattern = ProxySubtitlePatternProvider(
            readPattern = { "hk" },
            readMetadata = { ProxySubtitlePatternMetadata(lastModifiedMillis = 1L, length = 1L) },
        ).patternFor("profile.yaml")

        assertEquals(
            ProxyDisplayText(title = "Node", subtitle = "HK"),
            formatProxyDisplayText(" HK Node ", "VLESS", pattern),
        )
        assertEquals(
            ProxyDisplayText(title = "Node US", subtitle = "VLESS"),
            formatProxyDisplayText("Node US", "VLESS", pattern),
        )
    }
}
