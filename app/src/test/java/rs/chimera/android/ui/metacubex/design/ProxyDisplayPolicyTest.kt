package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.ProxyDelayHistory
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.backend.model.ProxySnapshot

class ProxyDisplayPolicyTest {
    @Test
    fun failedHealthcheckHidesOldLatencyWithoutChangingSelectionAndRecovers() {
        val failed = ProxySnapshot(
            name = "failed",
            type = "Vless",
            history = listOf(ProxyDelayHistory(12, 1L)),
            alive = false,
        )
        val snapshot = ProxyGroupSnapshot(
            name = "selector",
            proxies = listOf("failed", "working"),
            selected = "failed",
            mode = ProxyMode.RULE,
            proxyDetails = mapOf(
                "selector" to ProxySnapshot("selector", "Selector", emptyList(), isGroup = true),
                "failed" to failed,
                "working" to ProxySnapshot("working", "Vless", listOf(ProxyDelayHistory(80, 1L))),
            ),
        )

        val page = ProxyDisplayPolicy.groupPages(listOf(snapshot), "N/A").single()
        val failedEntry = page.proxies.first()
        assertEquals(true, page.isSelectable)
        assertEquals(true, failedEntry.isSelected)
        assertEquals("", failedEntry.delayText)
        assertEquals(null, failedEntry.delayMillis)
        assertEquals(UNKNOWN_PROXY_DELAY, failedEntry.delayValue)
        assertEquals(
            listOf("working", "failed"),
            ProxyDisplayPolicy.sort(page.proxies, ProxySortOrder.DELAY).map { it.name },
        )
        assertEquals(12, failed.history.single().delay)

        val recoveredNode = failed.copy(alive = true, history = failed.history + ProxyDelayHistory(30, 2L))
        val recovered = snapshot.copy(proxyDetails = snapshot.proxyDetails + ("failed" to recoveredNode))
        val recoveredPage = ProxyDisplayPolicy.groupPages(listOf(recovered), "N/A").single()
        assertEquals("30", recoveredPage.proxies.first().delayText)
        assertEquals(true, recoveredPage.proxies.first().isSelected)
        assertEquals(
            listOf("failed", "working"),
            ProxyDisplayPolicy.sort(recoveredPage.proxies, ProxySortOrder.DELAY).map { it.name },
        )
    }

    @Test
    fun gridSpanTracksReferenceColumnChoices() {
        assertEquals(6, ProxyDisplayPolicy.spanSize(1))
        assertEquals(3, ProxyDisplayPolicy.spanSize(2))
        assertEquals(2, ProxyDisplayPolicy.spanSize(3))
        assertEquals(6, ProxyDisplayPolicy.spanSize(0))
        assertEquals(2, ProxyDisplayPolicy.spanSize(4))
    }

    @Test
    fun missingAndZeroDelaySortLastLikeCmfa() {
        val entries = listOf(
            ProxyDisplayEntry("Zulu", "SS", "-", null, false),
            ProxyDisplayEntry(
                "Zero",
                "SS",
                "",
                null,
                false,
                delayValue = UNKNOWN_PROXY_DELAY,
            ),
            ProxyDisplayEntry("Beta", "SS", "40", 40, false),
            ProxyDisplayEntry("Alpha", "SS", "12", 12, true),
            ProxyDisplayEntry(
                "Long",
                "SS",
                "",
                null,
                false,
                delayValue = Short.MAX_VALUE + 1,
            ),
        )

        assertEquals(
            listOf("Zulu", "Zero", "Beta", "Alpha", "Long"),
            ProxyDisplayPolicy.sort(entries, ProxySortOrder.DEFAULT).map { it.name },
        )
        assertEquals(
            listOf("Alpha", "Beta", "Long", "Zero", "Zulu"),
            ProxyDisplayPolicy.sort(entries, ProxySortOrder.NAME).map { it.name },
        )
        assertEquals(
            listOf("Alpha", "Beta", "Long", "Zulu", "Zero"),
            ProxyDisplayPolicy.sort(entries, ProxySortOrder.DELAY).map { it.name },
        )
        assertEquals("", entries.first { it.name == "Zero" }.delayText)
    }

    @Test
    fun nameSortUsesCmfasCaseSensitiveTitleOrder() {
        val entries = listOf(
            ProxyDisplayEntry("alpha", "SS", "", null, false),
            ProxyDisplayEntry("Zulu", "SS", "", null, false),
            ProxyDisplayEntry("Alpha", "SS", "", null, false),
        )

        assertEquals(
            listOf("Alpha", "Zulu", "alpha"),
            ProxyDisplayPolicy.sort(entries, ProxySortOrder.NAME).map { it.name },
        )
    }

    @Test
    fun nameSortUsesDisplayedTitleWhileKeepingOriginalProxyName() {
        val entries = listOf(
            ProxyDisplayEntry(
                name = "🇺🇸 New York",
                subtitle = "🇺🇸",
                delayText = "",
                delayMillis = null,
                isSelected = false,
                title = "New York",
            ),
            ProxyDisplayEntry(
                name = "🇭🇰 Hong Kong",
                subtitle = "🇭🇰",
                delayText = "",
                delayMillis = null,
                isSelected = false,
                title = "Hong Kong",
            ),
        )

        assertEquals(
            listOf("🇭🇰 Hong Kong", "🇺🇸 New York"),
            ProxyDisplayPolicy.sort(entries, ProxySortOrder.NAME).map { it.name },
        )
    }

    @Test
    fun nameSortOrdersSupplementaryCharactersLikeUtf8Reference() {
        val privateUse = "\uE000"
        val supplementary = "\uD83D\uDE00"
        val entries = listOf(
            ProxyDisplayEntry(supplementary, "SS", "", null, false),
            ProxyDisplayEntry(privateUse, "SS", "", null, false),
        )

        assertEquals(
            listOf(privateUse, supplementary),
            ProxyDisplayPolicy.sort(entries, ProxySortOrder.NAME).map { it.name },
        )
    }

    @Test
    fun groupPagesOnlyShowGroupsAndOnlySelectorsAcceptSelection() {
        val selectorPage = ProxyGroupSnapshot(
            name = "手动选择",
            proxies = listOf("node-a", "fallback", "zero-delay"),
            selected = "node-a",
            mode = ProxyMode.RULE,
            proxyDetails = mapOf(
                "手动选择" to ProxySnapshot("手动选择", "Selector", emptyList(), isGroup = true, now = "node-a"),
                "node-a" to ProxySnapshot(
                    "node-a",
                    "Shadowsocks",
                    listOf(ProxyDelayHistory(delay = 42, time = 1L)),
                    title = "Hong Kong 01",
                    subtitle = "🇭🇰",
                ),
                "fallback" to ProxySnapshot(
                    "fallback",
                    "Fallback",
                    emptyList(),
                    isGroup = true,
                    now = "node-b",
                    title = "formatted group title",
                ),
                "zero-delay" to ProxySnapshot(
                    "zero-delay",
                    "Shadowsocks",
                    listOf(ProxyDelayHistory(delay = 0, time = 2L)),
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
        val fallbackPage = ProxyGroupSnapshot(
            name = "fallback",
            proxies = listOf("node-b"),
            selected = "node-b",
            mode = ProxyMode.RULE,
            proxyDetails = mapOf(
                "fallback" to ProxySnapshot("fallback", "Fallback", emptyList(), isGroup = true, now = "node-b"),
                "node-b" to ProxySnapshot("node-b", "Shadowsocks", emptyList()),
            ),
        )

        val snapshots = listOf(selectorPage, urlTestPage, leafProxyPage, fallbackPage)
        val pages = ProxyDisplayPolicy.groupPages(
            snapshots,
            unavailableType = "Unavailable",
        )

        assertEquals(listOf("手动选择", "自动测速", "fallback"), pages.map { it.name })
        assertEquals(true, pages[0].isSelectable)
        assertEquals(false, pages[1].isSelectable)
        assertEquals(true, pages[0].proxies.first().isSelected)
        assertEquals("node-a", pages[0].proxies.first().name)
        assertEquals("Hong Kong 01", pages[0].proxies.first().title)
        assertEquals("🇭🇰", pages[0].proxies.first().subtitle)
        assertEquals("fallback", pages[0].proxies[1].title)
        assertEquals("Fallback(node-b)", pages[0].proxies[1].subtitle)
        assertEquals("42", pages[0].proxies.first().delayText)
        assertEquals(null, pages[0].proxies[2].delayMillis)
        assertEquals(UNKNOWN_PROXY_DELAY, pages[0].proxies[2].delayValue)
        assertEquals("", pages[0].proxies[2].delayText)
        assertEquals(null, pages[2].proxies.first().delayMillis)
        assertEquals(UNKNOWN_PROXY_DELAY, pages[2].proxies.first().delayValue)
        assertEquals("", pages[2].proxies.first().delayText)

        val selectablePages = ProxyDisplayPolicy.groupPages(
            snapshots,
            unavailableType = "Unavailable",
            excludeNotSelectable = true,
        )
        assertEquals(listOf("手动选择"), selectablePages.map { it.name })
        assertEquals("Fallback", selectablePages.single().proxies[1].subtitle)
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

    @Test
    fun filteringFallsBackToFirstVisibleGroupAndKeepsItAfterFilterIsRemoved() {
        val visibleGroups = listOf(
            ProxyDisplayGroup("selector", isSelectable = true, proxies = emptyList()),
            ProxyDisplayGroup("fallback", isSelectable = true, proxies = emptyList()),
        )
        val allGroups = visibleGroups + ProxyDisplayGroup(
            "url-test",
            isSelectable = false,
            proxies = emptyList(),
        )

        assertEquals(
            "fallback",
            ProxyDisplayPolicy.groupNameAfterFilterChange("fallback", "url-test", visibleGroups),
        )
        val selectedAfterFiltering = ProxyDisplayPolicy.groupNameAfterFilterChange(
            "url-test",
            "url-test",
            visibleGroups,
        )
        assertEquals("selector", selectedAfterFiltering)
        assertEquals(
            selectedAfterFiltering,
            ProxyDisplayPolicy.groupNameAfterFilterChange(
                selectedAfterFiltering,
                "url-test",
                allGroups,
            ),
        )
        assertEquals(
            "url-test",
            ProxyDisplayPolicy.groupNameAfterFilterChange(null, "url-test", emptyList()),
        )
        assertEquals(
            "selector",
            ProxyDisplayPolicy.groupNameAfterFilterChange(
                "selector",
                "url-test",
                visibleGroups,
            ),
        )
    }

    @Test
    fun filterChangeTracksDisplayedPagerPageWhenCachedIndexIsStale() {
        val visibleGroups = listOf(
            ProxyDisplayGroup("selector", isSelectable = true, proxies = emptyList()),
            ProxyDisplayGroup("fallback", isSelectable = true, proxies = emptyList()),
        )
        val allGroups = visibleGroups + ProxyDisplayGroup(
            "url-test",
            isSelectable = false,
            proxies = emptyList(),
        )

        val selectedAfterFiltering = ProxyDisplayPolicy.groupNameAfterFilterChange(
            currentlyDisplayedPageIndex = 2,
            displayedGroups = allGroups,
            lastGroupName = "url-test",
            visibleGroups = visibleGroups,
        )
        assertEquals("selector", selectedAfterFiltering)

        assertEquals(
            "fallback",
            ProxyDisplayPolicy.groupNameAfterFilterChange(
                currentlyDisplayedPageIndex = 1,
                displayedGroups = visibleGroups,
                lastGroupName = "url-test",
                visibleGroups = allGroups,
            ),
        )
        assertEquals(
            "selector",
            ProxyDisplayPolicy.groupNameAfterFilterChange(
                currentlyDisplayedPageIndex = 0,
                displayedGroups = visibleGroups,
                lastGroupName = selectedAfterFiltering,
                visibleGroups = allGroups,
            ),
        )
    }
}
