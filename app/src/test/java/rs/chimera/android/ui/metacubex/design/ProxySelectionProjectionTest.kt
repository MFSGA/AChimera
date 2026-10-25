package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.backend.model.ProxySnapshot

class ProxySelectionProjectionTest {
    @Test
    fun selectionUpdatesItsCardAndEveryLinkedGroupLabel() {
        val groups = listOf(
            ProxyGroupSnapshot(
                name = SELECTOR,
                proxies = listOf(OLD_NODE, NEW_NODE),
                selected = OLD_NODE,
                mode = ProxyMode.RULE,
                proxyDetails = mapOf(
                    SELECTOR to ProxySnapshot(SELECTOR, PROXY_SELECTOR_TYPE, emptyList(), true, OLD_NODE),
                    OLD_NODE to ProxySnapshot(OLD_NODE, "Vless", emptyList()),
                    NEW_NODE to ProxySnapshot(NEW_NODE, "Vless", emptyList()),
                ),
            ),
            ProxyGroupSnapshot(
                name = PARENT_GROUP,
                proxies = listOf(SELECTOR, OLD_NODE, NEW_NODE),
                selected = OLD_NODE,
                mode = ProxyMode.RULE,
                proxyDetails = mapOf(
                    PARENT_GROUP to ProxySnapshot(PARENT_GROUP, "Fallback", emptyList(), true, OLD_NODE),
                    SELECTOR to ProxySnapshot(SELECTOR, PROXY_SELECTOR_TYPE, emptyList(), true, OLD_NODE),
                    OLD_NODE to ProxySnapshot(OLD_NODE, "Vless", emptyList()),
                    NEW_NODE to ProxySnapshot(NEW_NODE, "Vless", emptyList()),
                ),
            ),
            ProxyGroupSnapshot(
                name = SECOND_PARENT_GROUP,
                proxies = listOf(SELECTOR),
                selected = SELECTOR,
                mode = ProxyMode.RULE,
                proxyDetails = mapOf(
                    SECOND_PARENT_GROUP to
                        ProxySnapshot(SECOND_PARENT_GROUP, "LoadBalance", emptyList(), true, SELECTOR),
                    SELECTOR to ProxySnapshot(SELECTOR, PROXY_SELECTOR_TYPE, emptyList(), true, OLD_NODE),
                ),
            ),
        )

        val projection = projectProxySelection(groups, SELECTOR, NEW_NODE)

        val updatedSelector = projection?.groups?.first { it.name == SELECTOR }
        assertEquals(NEW_NODE, updatedSelector?.selected)
        assertEquals(NEW_NODE, updatedSelector?.proxyDetails?.get(SELECTOR)?.now)

        val updatedParent = projection?.groups?.first { it.name == PARENT_GROUP }
        assertEquals(OLD_NODE, updatedParent?.selected)
        assertEquals(NEW_NODE, updatedParent?.proxyDetails?.get(SELECTOR)?.now)

        val renderedParent = ProxyDisplayPolicy.groupPages(
            projection!!.groups,
            unavailableType = "Unavailable",
        ).first { it.name == PARENT_GROUP }
        val linkedCard = renderedParent.proxies.single { it.name == SELECTOR }
        assertEquals("$PROXY_SELECTOR_TYPE($NEW_NODE)", linkedCard.subtitle)
        assertFalse(linkedCard.isSelected)
        assertTrue(renderedParent.proxies.single { it.name == OLD_NODE }.isSelected)
        assertFalse(renderedParent.proxies.single { it.name == NEW_NODE }.isSelected)

        val renderedSecondParent = ProxyDisplayPolicy.groupPages(
            projection.groups,
            unavailableType = "Unavailable",
        ).first { it.name == SECOND_PARENT_GROUP }
        assertEquals("$PROXY_SELECTOR_TYPE($NEW_NODE)", renderedSecondParent.proxies.single().subtitle)
        assertTrue(renderedSecondParent.proxies.single().isSelected)
        assertNotSame(
            projection.groups[1].proxyDetails,
            projection.groups[2].proxyDetails,
        )

        assertEquals(OLD_NODE, groups.first { it.name == SELECTOR }.selected)
        assertEquals(OLD_NODE, groups[1].proxyDetails[SELECTOR]?.now)
        assertEquals(OLD_NODE, groups[2].proxyDetails[SELECTOR]?.now)
    }

    @Test
    fun projectionRejectsUnknownNodesAndNonSelectorGroups() {
        val selector = selectorGroup(type = PROXY_SELECTOR_TYPE)
        val urlTest = selectorGroup(name = "auto", type = "URLTest")

        assertNull(projectProxySelection(listOf(selector), SELECTOR, "missing-node"))
        assertNull(projectProxySelection(listOf(selector), "missing-group", NEW_NODE))
        assertNull(projectProxySelection(listOf(urlTest), "auto", NEW_NODE))
    }

    @Test
    fun selectionProjectionPreservesUnlinkedSnapshots() {
        val selector = selectorGroup(type = PROXY_SELECTOR_TYPE)
        val unrelated = selectorGroup(name = "other", type = "Selector")

        val projection = projectProxySelection(listOf(selector, unrelated), SELECTOR, NEW_NODE)

        assertTrue(projection!!.groups.last() === unrelated)
        assertEquals(unrelated, projection.groups.last())
    }

    @Test
    fun projectionReusesUpdatedDetailsMapForGroupsThatShareTheSourceMap() {
        val sharedDetails = mapOf(
            SELECTOR to ProxySnapshot(SELECTOR, PROXY_SELECTOR_TYPE, emptyList(), true, OLD_NODE),
            PARENT_GROUP to ProxySnapshot(PARENT_GROUP, "Fallback", emptyList(), true, SELECTOR),
            OLD_NODE to ProxySnapshot(OLD_NODE, "Vless", emptyList()),
            NEW_NODE to ProxySnapshot(NEW_NODE, "Vless", emptyList()),
        )
        val groups = listOf(
            ProxyGroupSnapshot(
                name = SELECTOR,
                proxies = listOf(OLD_NODE, NEW_NODE),
                selected = OLD_NODE,
                mode = ProxyMode.RULE,
                proxyDetails = sharedDetails,
            ),
            ProxyGroupSnapshot(
                name = PARENT_GROUP,
                proxies = listOf(SELECTOR),
                selected = SELECTOR,
                mode = ProxyMode.RULE,
                proxyDetails = sharedDetails,
            ),
        )

        val projection = projectProxySelection(groups, SELECTOR, NEW_NODE)!!

        assertSame(projection.groups[0].proxyDetails, projection.groups[1].proxyDetails)
        assertEquals(NEW_NODE, projection.groups[1].proxyDetails[SELECTOR]?.now)
        assertEquals(OLD_NODE, sharedDetails[SELECTOR]?.now)
    }

    @Test
    fun failedSelectionAndRefreshRestoreTheLastConfirmedSnapshot() {
        val confirmed = listOf(selectorGroup(type = PROXY_SELECTOR_TYPE))
        val state = ProxySelectionState()
        state.acceptBackendSnapshot(confirmed)

        assertTrue(state.projectSelection(SELECTOR, NEW_NODE))
        assertEquals(NEW_NODE, state.displayedGroups.single().selected)

        state.restoreConfirmedSnapshot()

        assertEquals(confirmed, state.displayedGroups)
        assertEquals(OLD_NODE, state.displayedGroups.single().selected)
    }

    @Test
    fun successfulSelectionAdvancesRollbackSnapshotWithoutReplacingLaterProjection() {
        val initial = listOf(selectorGroup(type = PROXY_SELECTOR_TYPE))
        val state = ProxySelectionState()
        state.acceptBackendSnapshot(initial)

        assertTrue(state.projectSelection(SELECTOR, NEW_NODE))
        assertTrue(state.confirmSelection(SELECTOR, NEW_NODE))
        assertEquals(NEW_NODE, state.displayedGroups.single().selected)

        assertTrue(state.projectSelection(SELECTOR, OLD_NODE))
        assertEquals(OLD_NODE, state.displayedGroups.single().selected)

        state.restoreConfirmedSnapshot()

        assertEquals(NEW_NODE, state.displayedGroups.single().selected)
    }

    @Test
    fun confirmedSnapshotAdvancesAfterSuccessfulBackendRefresh() {
        val state = ProxySelectionState()
        val initial = listOf(selectorGroup(type = PROXY_SELECTOR_TYPE))
        val latestGroup = selectorGroup(type = PROXY_SELECTOR_TYPE)
        val latest = listOf(
            latestGroup.copy(
                selected = NEW_NODE,
                proxyDetails = latestGroup.proxyDetails +
                    (SELECTOR to latestGroup.proxyDetails.getValue(SELECTOR).copy(now = NEW_NODE)),
            ),
        )
        state.acceptBackendSnapshot(initial)
        assertFalse(state.projectSelection(SELECTOR, "missing-node"))

        state.acceptBackendSnapshot(latest)
        assertTrue(state.projectSelection(SELECTOR, OLD_NODE))
        state.restoreConfirmedSnapshot()

        assertEquals(latest, state.displayedGroups)
        assertEquals(NEW_NODE, state.displayedGroups.single().selected)
    }

    private fun selectorGroup(
        name: String = SELECTOR,
        type: String,
    ) = ProxyGroupSnapshot(
        name = name,
        proxies = listOf(OLD_NODE, NEW_NODE),
        selected = OLD_NODE,
        mode = ProxyMode.RULE,
        proxyDetails = mapOf(
            name to ProxySnapshot(name, type, emptyList(), isGroup = true, now = OLD_NODE),
            OLD_NODE to ProxySnapshot(OLD_NODE, "Vless", emptyList()),
            NEW_NODE to ProxySnapshot(NEW_NODE, "Vless", emptyList()),
        ),
    )

    private companion object {
        const val SELECTOR = "Manual"
        const val PARENT_GROUP = "Fallback"
        const val SECOND_PARENT_GROUP = "LoadBalance"
        const val OLD_NODE = "old-node"
        const val NEW_NODE = "new-node"
    }
}
