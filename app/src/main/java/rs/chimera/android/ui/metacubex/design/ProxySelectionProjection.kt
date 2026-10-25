package rs.chimera.android.ui.metacubex.design

import java.util.IdentityHashMap
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxySnapshot

/** Mirrors CMFA's update of the selected node and linked ProxyState.now values. */
internal data class ProxySelectionProjection(
    val groups: List<ProxyGroupSnapshot>,
)

internal class ProxySelectionState {
    private var confirmedGroups = emptyList<ProxyGroupSnapshot>()
    var displayedGroups = emptyList<ProxyGroupSnapshot>()
        private set

    fun acceptBackendSnapshot(
        groups: List<ProxyGroupSnapshot>,
        preserveCachedSelections: Boolean = false,
    ) {
        val acceptedGroups = if (preserveCachedSelections) preserveSelections(groups) else groups
        confirmedGroups = acceptedGroups
        displayedGroups = acceptedGroups
    }

    fun projectSelection(groupName: String, proxyName: String): Boolean {
        val projection = projectProxySelection(displayedGroups, groupName, proxyName) ?: return false
        displayedGroups = projection.groups
        return true
    }

    fun confirmSelection(groupName: String, proxyName: String): Boolean {
        val projection = projectProxySelection(confirmedGroups, groupName, proxyName) ?: return false
        confirmedGroups = projection.groups
        return true
    }

    fun restoreConfirmedSnapshot() {
        displayedGroups = confirmedGroups
    }

    private fun preserveSelections(groups: List<ProxyGroupSnapshot>): List<ProxyGroupSnapshot> {
        if (confirmedGroups.isEmpty()) return groups

        val previousGroupsByName = confirmedGroups.associateBy(ProxyGroupSnapshot::name)
        val selectionsByGroup = groups.mapNotNull { incoming ->
            val previous = previousGroupsByName[incoming.name] ?: return@mapNotNull null
            val previousGroup = previous.proxyDetails[previous.name]
            val incomingGroup = incoming.proxyDetails[incoming.name]
            if (
                previousGroup?.isGroup != true ||
                incomingGroup?.isGroup != true ||
                previousGroup.type != incomingGroup.type
            ) {
                return@mapNotNull null
            }

            val selection = previous.selected
            if (selection == null || selection in incoming.proxies) incoming.name to selection else null
        }.toMap()
        if (selectionsByGroup.isEmpty()) return groups

        val projectedDetailsBySource =
            IdentityHashMap<Map<String, ProxySnapshot>, Map<String, ProxySnapshot>>()
        return groups.map { snapshot ->
            val selected = selectionsByGroup[snapshot.name]
            val projectedDetails = projectedDetailsBySource.getOrPut(snapshot.proxyDetails) {
                snapshot.proxyDetails.mapValues { (name, proxy) ->
                    if (proxy.isGroup && name in selectionsByGroup) {
                        proxy.copy(now = selectionsByGroup[name])
                    } else {
                        proxy
                    }
                }
            }
            snapshot.copy(
                selected = if (selectionsByGroup.containsKey(snapshot.name)) {
                    selected
                } else {
                    snapshot.selected
                },
                proxyDetails = projectedDetails,
            )
        }
    }
}

internal fun projectProxySelection(
    groups: List<ProxyGroupSnapshot>,
    groupName: String,
    proxyName: String,
): ProxySelectionProjection? {
    val selectorGroup = groups.firstOrNull { it.name == groupName } ?: return null
    val selector = selectorGroup.proxyDetails[groupName]
    if (selector?.isGroup != true || selector.type != PROXY_SELECTOR_TYPE) return null
    if (proxyName !in selectorGroup.proxies) return null

    val projectedDetailsBySource =
        IdentityHashMap<Map<String, ProxySnapshot>, Map<String, ProxySnapshot>>()
    val projectedGroups = groups.map { snapshot ->
        val linkedSelector = snapshot.proxyDetails[groupName]
            ?.takeIf { it.isGroup }
            ?: return@map snapshot
        val projectedDetails = projectedDetailsBySource.getOrPut(snapshot.proxyDetails) {
            snapshot.proxyDetails + (groupName to linkedSelector.copy(now = proxyName))
        }
        snapshot.copy(
            selected = if (snapshot.name == groupName) proxyName else snapshot.selected,
            proxyDetails = projectedDetails,
        )
    }
    return ProxySelectionProjection(projectedGroups)
}
