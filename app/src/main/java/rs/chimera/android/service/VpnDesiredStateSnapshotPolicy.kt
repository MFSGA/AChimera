package rs.chimera.android.service

internal object VpnDesiredStateSnapshotPolicy {
    fun parse(values: Map<String, *>): VpnDesiredStateSnapshot {
        val reason = (values[VpnDesiredStateStore.KEY_REASON] as? String)
            ?.let { value -> runCatching { VpnDesiredStateReason.valueOf(value) }.getOrNull() }
            ?: VpnDesiredStateReason.USER_STOP
        return VpnDesiredStateSnapshot(
            shouldRun = values[VpnDesiredStateStore.KEY_SHOULD_RUN] as? Boolean ?: false,
            updatedAt = (values[VpnDesiredStateStore.KEY_UPDATED_AT] as? Number)?.toLong() ?: 0L,
            reason = reason,
        )
    }
}
