package rs.chimera.android.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class VpnDesiredStateReason {
    USER_START,
    USER_STOP,
    START_FAILED,
    PERMISSION_REVOKED,
    RECOVERY_MISSING_PROFILE,
    RECOVERY_PERMISSION_REQUIRED,
    RUNTIME_FAILED,
    CORE_EXITED,
}

internal data class VpnDesiredStateSnapshot(
    val shouldRun: Boolean,
    val updatedAt: Long,
    val reason: VpnDesiredStateReason,
)

internal class VpnDesiredStateStore(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        VpnDesiredStateRuntimeState.update(snapshot().shouldRun)
    }

    fun snapshot(): VpnDesiredStateSnapshot =
        VpnDesiredStateSnapshotPolicy.parse(preferences.all)

    fun markRunning(reason: VpnDesiredStateReason = VpnDesiredStateReason.USER_START) {
        persist(shouldRun = true, reason = reason)
    }

    fun markStopped(reason: VpnDesiredStateReason) {
        persist(shouldRun = false, reason = reason)
    }

    private fun persist(shouldRun: Boolean, reason: VpnDesiredStateReason) {
        check(
            preferences.edit()
                .putBoolean(KEY_SHOULD_RUN, shouldRun)
                .putLong(KEY_UPDATED_AT, now())
                .putString(KEY_REASON, reason.name)
                .commit(),
        ) { "Unable to persist desired VPN state" }
        VpnDesiredStateRuntimeState.update(shouldRun)
    }

    internal companion object {
        const val PREFS_NAME = "vpn_desired_state"
        const val KEY_SHOULD_RUN = "should_run"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_REASON = "reason"
    }
}

internal object VpnDesiredStateRuntimeState {
    private val mutableShouldRun = MutableStateFlow(false)

    val shouldRun = mutableShouldRun.asStateFlow()

    fun update(shouldRun: Boolean) {
        mutableShouldRun.value = shouldRun
    }
}
