package rs.chimera.android.ui.metacubex.design

/** Keeps changed selector nodes eligible for CMFA's short frame-time color transition. */
internal class ProxySelectionAnimationTracker {
    private val nodesByGroup = linkedMapOf<String, MutableMap<String, Long>>()

    fun record(
        groupName: String,
        previousSelection: String?,
        currentSelection: String,
        frameTime: Long,
    ): Map<String, Map<String, Long>> {
        pruneExpired(frameTime)
        val changedAt = nodesByGroup.getOrPut(groupName) { linkedMapOf() }
        val expiresAt = frameTime + PROXY_SELECTION_ANIMATION_LIFETIME_MS
        previousSelection?.let { changedAt[it] = expiresAt }
        changedAt[currentSelection] = expiresAt
        return snapshot(frameTime)
    }

    fun updateSelection(
        groupName: String,
        previousSelection: String?,
        currentSelection: String?,
        frameTime: Long,
    ) {
        when {
            currentSelection == null -> clearGroup(groupName)
            previousSelection != currentSelection -> record(
                groupName,
                previousSelection,
                currentSelection,
                frameTime,
            )
            else -> clearGroup(groupName)
        }
    }

    fun snapshot(frameTime: Long): Map<String, Map<String, Long>> {
        pruneExpired(frameTime)
        return nodesByGroup.mapValues { (_, changedNodes) -> changedNodes.toMap() }
    }

    fun clearGroup(groupName: String) {
        nodesByGroup.remove(groupName)
    }

    fun retainGroups(groupNames: Set<String>) {
        nodesByGroup.keys.retainAll(groupNames)
    }

    fun clear() {
        nodesByGroup.clear()
    }

    private fun pruneExpired(frameTime: Long) {
        nodesByGroup.values.forEach { changedAt ->
            changedAt.entries.removeAll { (_, expiresAt) -> frameTime >= expiresAt }
        }
        nodesByGroup.entries.removeAll { (_, changedAt) -> changedAt.isEmpty() }
    }
}

// CMFA advances each ARGB channel by at most one value per millisecond (255 total).
internal const val PROXY_SELECTION_ANIMATION_LIFETIME_MS = 320L
