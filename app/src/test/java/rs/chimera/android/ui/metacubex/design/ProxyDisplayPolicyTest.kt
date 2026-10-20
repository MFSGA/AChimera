package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.ProxyDelayHistory
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.backend.model.ProxySnapshot

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

    @Test
    fun groupPagesOnlyShowGroupsAndOnlySelectorsAcceptSelection() {
        val selectorPage = ProxyGroupSnapshot(
            name = "手动选择",
            proxies = listOf("node-a", "fallback"),
            selected = "node-a",
            mode = ProxyMode.RULE,
            proxyDetails = mapOf(
                "手动选择" to ProxySnapshot("手动选择", "Selector", emptyList(), isGroup = true, now = "node-a"),
                "node-a" to ProxySnapshot(
                    "node-a",
                    "Shadowsocks",
                    listOf(ProxyDelayHistory(delay = 42, time = 1L)),
                ),
                "fallback" to ProxySnapshot(
                    "fallback",
                    "Fallback",
                    emptyList(),
                    isGroup = true,
                    now = "node-b",
                ),
            ),
        )
        val urlTestPage = ProxyGroupSnapshot(
            name = "自动测速",
            proxies = listOf("node-a"),
            selected = "node-a",
            mode = ProxyMode.RULE,
            proxyDetails = mapOf(
                "自动测速" to ProxySnapshot("自动测速", "URLTest", emptyList(), isGroup = true),
                "node-a" to ProxySnapshot("node-a", "Shadowsocks", emptyList()),
            ),
        )
        val leafProxyPage = ProxyGroupSnapshot(
            name = "node-a",
            proxies = emptyList(),
            selected = null,
            mode = ProxyMode.RULE,
            proxyDetails = mapOf("node-a" to ProxySnapshot("node-a", "Shadowsocks", emptyList())),
        )

        val pages = ProxyDisplayPolicy.groupPages(
            listOf(selectorPage, urlTestPage, leafProxyPage),
            unavailableType = "Unavailable",
        )

        assertEquals(listOf("手动选择", "自动测速"), pages.map { it.name })
        assertEquals(true, pages[0].isSelectable)
        assertEquals(false, pages[1].isSelectable)
        assertEquals(true, pages[0].proxies.first().isSelected)
        assertEquals("Fallback(node-b)", pages[0].proxies[1].type)
        assertEquals("42", pages[0].proxies.first().delayText)
    }

    @Test
    fun selectableFilterHidesNonSelectorGroupsOnlyWhenEnabled() {
        val snapshots = listOf(
            ProxyGroupSnapshot(
                name = "手动选择",
                proxies = listOf("node-a"),
                selected = "node-a",
                mode = ProxyMode.RULE,
                proxyDetails = mapOf(
                    "手动选择" to ProxySnapshot("手动选择", "Selector", emptyList(), isGroup = true),
                    "node-a" to ProxySnapshot("node-a", "Vless", emptyList()),
                ),
            ),
            ProxyGroupSnapshot(
                name = "自动测速",
                proxies = listOf("node-a"),
                selected = "node-a",
                mode = ProxyMode.RULE,
                proxyDetails = mapOf(
                    "自动测速" to ProxySnapshot("自动测速", "URLTest", emptyList(), isGroup = true),
                    "node-a" to ProxySnapshot("node-a", "Vless", emptyList()),
                ),
            ),
        )

        assertEquals(
            listOf("手动选择", "自动测速"),
            ProxyDisplayPolicy.groupPages(snapshots, "Unavailable").map { it.name },
        )
        assertEquals(
            listOf("手动选择"),
            ProxyDisplayPolicy.groupPages(
                snapshots,
                "Unavailable",
                excludeNotSelectable = true,
            ).map { it.name },
        )
    }
}
