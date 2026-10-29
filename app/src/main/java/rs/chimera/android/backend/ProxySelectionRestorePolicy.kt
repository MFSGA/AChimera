package rs.chimera.android.backend

import kotlinx.coroutines.withTimeoutOrNull
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProxyGroupSnapshot

internal data class CoreProfileRevision(
    val profileId: String,
    val revision: Long?,
)

internal fun coreProfileRevisionMatches(
    loadedRevision: CoreProfileRevision?,
    activeProfile: ProfileSummary,
): Boolean = loadedRevision == CoreProfileRevision(activeProfile.id, activeProfile.lastUpdated)

internal data class SavedProxySelection(
    val groupName: String,
    val proxyName: String,
)

internal data class ProxySelectionRestorePlan(
    val selections: List<SavedProxySelection>,
    val obsoleteGroups: List<String>,
)

internal suspend fun runProxySelectionRestoreWithinTimeout(
    timeoutMillis: Long,
    onTimeout: () -> Unit,
    restore: suspend () -> Unit,
) {
    require(timeoutMillis > 0L)
    val completed = withTimeoutOrNull(timeoutMillis) {
        restore()
        true
    } ?: false
    if (!completed) onTimeout()
}

/** Matches CMFA's restore behavior while discarding choices no longer present in the profile. */
internal fun planProxySelectionRestore(
    groups: List<ProxyGroupSnapshot>,
    savedSelections: Map<String, String>,
): ProxySelectionRestorePlan {
    val groupsByName = groups.associateBy(ProxyGroupSnapshot::name)
    val selections = mutableListOf<SavedProxySelection>()
    val obsoleteGroups = mutableListOf<String>()

    savedSelections.toSortedMap().forEach { (groupName, proxyName) ->
        val group = groupsByName[groupName]
        val selector = group?.proxyDetails?.get(groupName)
        if (
            group != null &&
            selector?.isGroup == true &&
            selector.type == SELECTOR_TYPE &&
            proxyName in group.proxies
        ) {
            selections += SavedProxySelection(groupName, proxyName)
        } else {
            obsoleteGroups += groupName
        }
    }

    return ProxySelectionRestorePlan(selections, obsoleteGroups)
}

private const val SELECTOR_TYPE = "Selector"
