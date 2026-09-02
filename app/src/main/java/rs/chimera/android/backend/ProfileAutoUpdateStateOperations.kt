package rs.chimera.android.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ProfileAutoUpdateStateOperations(
    private val profileUpdateCoordinator: ProfileUpdateCoordinator,
    private val profileCatalogStore: ProfileCatalogStore,
    private val profileAutoUpdateStateStore: ProfileAutoUpdateStateStore,
) {
    suspend fun recordIfCurrent(
        id: String,
        state: ProfileAutoUpdateState,
    ): ProfileAutoUpdateState? = withContext(Dispatchers.IO) {
        profileUpdateCoordinator.withLock(id) {
            commitAutoUpdateStateIfCurrent(
                state = state,
                readCurrent = { profileCatalogStore.readRemoteProfile(id) },
                commit = { profileAutoUpdateStateStore.writeBoundStateIfCurrent(id, state) },
            )
        }
    }

    suspend fun markRuntimeApplyPendingIfCurrent(
        id: String,
        state: ProfileAutoUpdateState,
    ): Boolean = withContext(Dispatchers.IO) {
        profileUpdateCoordinator.withLock(id) {
            markRuntimeApplyPendingAndReadCurrent(
                state = state,
                readCurrent = { profileCatalogStore.readRemoteProfile(id) },
                markPending = {
                    check(profileAutoUpdateStateStore.markRuntimeApplyPending(id, state)) {
                        "Failed to mark runtime apply pending state"
                    }
                },
                readPending = { profileAutoUpdateStateStore.read(id).runtimeApplyPending },
            )
        }
    }
}
