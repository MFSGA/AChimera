package rs.chimera.android.ui.metacubex.activity

internal data class ProxyDelayTestProgress(
    val current: Int,
    val total: Int,
)

/** Tracks URL tests independently so a different proxy group can be tested concurrently. */
internal class ProxyDelayTestTracker {
    private val progressByGroup = linkedMapOf<String, ProxyDelayTestProgress>()

    val isTesting: Boolean
        get() = progressByGroup.isNotEmpty()

    fun progress(groupName: String): ProxyDelayTestProgress? = progressByGroup[groupName]

    fun start(groupName: String, total: Int): Boolean {
        if (groupName.isBlank() || total <= 0 || groupName in progressByGroup) return false
        progressByGroup[groupName] = ProxyDelayTestProgress(current = 1, total = total)
        return true
    }

    fun update(groupName: String, current: Int, total: Int): Boolean {
        if (groupName !in progressByGroup || total <= 0) return false
        progressByGroup[groupName] = ProxyDelayTestProgress(
            current = current.coerceIn(1, total),
            total = total,
        )
        return true
    }

    fun finish(groupName: String): Boolean = progressByGroup.remove(groupName) != null
}
