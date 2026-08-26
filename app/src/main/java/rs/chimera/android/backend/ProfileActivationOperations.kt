package rs.chimera.android.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import rs.chimera.android.backend.model.ServiceState

internal class ProfileActivationOperations(
    private val profileCatalogCoordinator: ProfileCatalogCoordinator,
    private val profileCatalogStore: ProfileCatalogStore,
    private val serviceState: StateFlow<ServiceState>,
    private val restartVpn: suspend () -> Unit,
    private val updateRuntimePath: (String?) -> Unit,
    private val refreshActiveProfile: () -> Unit,
) {
    suspend fun activate(id: String) = runProfileActivationTransaction {
        val previousState = withContext(Dispatchers.IO) {
            profileCatalogCoordinator.withLock {
                val document = profileCatalogStore.readDocument()
                val target = document.entries.firstOrNull { it.id == id }
                    ?: throw IllegalArgumentException("Profile not found")
                if (target.isActive) return@withLock null

                val updatedProfiles = requireNotNull(ProfileCatalogPolicy.activate(document.entries, id))
                val activePath = requireNotNull(ProfileCatalogPolicy.activePath(updatedProfiles))
                val previousState = ProfileActivationRollbackState(
                    catalog = document.serialized,
                    activePath = ProfileCatalogPolicy.activePath(document.entries),
                )
                commitProfileActivationSelection(
                    persistActivation = {
                        profileCatalogStore.commitCatalog(
                            catalog = profileCatalogStore.render(document, updatedProfiles),
                            activePath = activePath,
                        )
                    },
                    restoreSelection = { applySelection(activePath) },
                    rollbackActivation = {
                        profileCatalogStore.commitCatalog(previousState.catalog, previousState.activePath)
                        applySelection(previousState.activePath)
                    },
                )
                previousState
            }
        }
        if (previousState == null) return@runProfileActivationTransaction

        applyProfileActivationToRunningVpn(
            serviceState = serviceState,
            restartVpn = restartVpn,
            rollbackActivation = {
                withContext(Dispatchers.IO) {
                    profileCatalogCoordinator.withLock {
                        profileCatalogStore.commitCatalog(previousState.catalog, previousState.activePath)
                        applySelection(previousState.activePath)
                    }
                }
            },
        )
    }

    private fun applySelection(activePath: String?) {
        applyProfileActivationSelection(
            activePath = activePath,
            updateRuntimePath = updateRuntimePath,
            refreshActiveProfile = refreshActiveProfile,
        )
    }
}
