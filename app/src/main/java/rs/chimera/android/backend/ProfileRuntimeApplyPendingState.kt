package rs.chimera.android.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rs.chimera.android.backend.model.ProfileSummary

internal fun pendingRuntimeApplyRevision(profile: ProfileSummary?): Pair<String, Long>? =
    profile
        ?.takeIf { it.runtimeApplyPending }
        ?.let { it.id to ProfileAutoUpdatePolicy.profileRevision(it) }

internal class ProfileRuntimeApplyPendingState(
    private val readActiveProfile: () -> ProfileSummary?,
    private val stateStore: ProfileAutoUpdateStateStore,
    private val refreshActiveProfile: () -> Unit,
) {
    fun readRevision(): Pair<String, Long>? = pendingRuntimeApplyRevision(readActiveProfile())

    suspend fun clear(
        profileId: String,
        expectedProfileRevision: Long,
    ) = withContext(Dispatchers.IO) {
        if (stateStore.clearRuntimeApplyPending(profileId, expectedProfileRevision)) {
            refreshActiveProfile()
        }
    }
}
