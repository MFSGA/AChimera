package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyDisplayPolicyTest {
    @Test
    fun gridSpanTracksReferenceColumnChoices() {
        assertEquals(6, ProxyDisplayPolicy.spanSize(1))
        assertEquals(3, ProxyDisplayPolicy.spanSize(2))
        assertEquals(2, ProxyDisplayPolicy.spanSize(3))
        assertEquals(6, ProxyDisplayPolicy.spanSize(0))
        assertEquals(2, ProxyDisplayPolicy.spanSize(4))
    }

    @Test
    fun nameAndDelaySortKeepUnknownDelayAtTheEnd() {
        val entries = listOf(
            ProxyDisplayEntry("Zulu", "SS", "-", null, false),
            ProxyDisplayEntry("Beta", "SS", "40", 40, false),
            ProxyDisplayEntry("Alpha", "SS", "12", 12, true),
        )

        assertEquals(
            listOf("Zulu", "Beta", "Alpha"),
            ProxyDisplayPolicy.sort(entries, ProxySortOrder.DEFAULT).map { it.name },
        )
        assertEquals(
            listOf("Alpha", "Beta", "Zulu"),
            ProxyDisplayPolicy.sort(entries, ProxySortOrder.NAME).map { it.name },
        )
        assertEquals(
            listOf("Alpha", "Beta", "Zulu"),
            ProxyDisplayPolicy.sort(entries, ProxySortOrder.DELAY).map { it.name },
        )
    }
}
