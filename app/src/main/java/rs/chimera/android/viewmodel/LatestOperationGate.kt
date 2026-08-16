package rs.chimera.android.viewmodel

/** Issues monotonically increasing tokens so stale async callbacks can be ignored. */
internal class LatestOperationGate {
    private var generation = 0L

    fun next(): Long {
        generation += 1
        return generation
    }

    fun isCurrent(token: Long): Boolean = token == generation
}
