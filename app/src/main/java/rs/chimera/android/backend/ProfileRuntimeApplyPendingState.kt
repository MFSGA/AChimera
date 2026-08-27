package rs.chimera.android.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rs.chimera.android.backend.model.ProfileSummary

internal data class ProfileRuntimeApplyPendingToken(
    val profileId: String,
    val profileRevision: Long,
    val sourceFingerprint: String,
)

internal fun pendingRuntimeApplyToken(profile: ProfileSummary?): ProfileRuntimeApplyPendingToken? =
    profile
        ?.takeIf { it.runtimeApplyPending }
        ?.let {
            ProfileRuntimeApplyPendingToken(
                profileId = it.id,
                profileRevision = ProfileAutoUpdatePolicy.profileRevision(it),
                sourceFingerprint = ProfileAutoUpdatePolicy.sourceFingerprint(it),
            )
        }

internal class ProfileRuntimeApplyPendingState(
    private val readActiveProfile: () -> ProfileSummary?,
    private val stateStore: ProfileAutoUpdateStateStore,
    private val refreshActiveProfile: () -> Unit,
) {
    fun readToken(): ProfileRuntimeApplyPendingToken? = pendingRuntimeApplyToken(readActiveProfile())

    suspend fun clear(token: ProfileRuntimeApplyPendingToken) = withContext(Dispatchers.IO) {
        if (
            stateStore.clearRuntimeApplyPending(
                token.profileId,
                token.profileRevision,
                token.sourceFingerprint,
            )
        ) {
            refreshActiveProfile()
        }
    }
}
