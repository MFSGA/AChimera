package rs.chimera.android.ui.metacubex.design

import rs.chimera.android.backend.model.ProxyGroupSnapshot

/** Mirrors CMFA's update of the selected node and linked ProxyState.now values. */
internal data class ProxySelectionProjection(
    val groups: List<ProxyGroupSnapshot>,
)

internal class ProxySelectionState {
    private var confirmedGroups = emptyList<ProxyGroupSnapshot>()
    var displayedGroups = emptyList<ProxyGroupSnapshot>()
        private set

    fun acceptBackendSnapshot(groups: List<ProxyGroupSnapshot>) {
        confirmedGroups = groups
        displayedGroups = groups
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

    val projectedGroups = groups.map { snapshot ->
        val linkedSelector = snapshot.proxyDetails[groupName]
            ?.takeIf { it.isGroup }
            ?: return@map snapshot
        snapshot.copy(
            selected = if (snapshot.name == groupName) proxyName else snapshot.selected,
            proxyDetails = snapshot.proxyDetails +
                (groupName to linkedSelector.copy(now = proxyName)),
        )
    }
    return ProxySelectionProjection(projectedGroups)
}
