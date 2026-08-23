package rs.chimera.android.viewmodel

import kotlinx.coroutines.CancellationException
import rs.chimera.android.backend.model.ProfileSummary

internal sealed interface ProfileCatalogRefreshResult {
    data class Applied(val snapshot: ProfileCatalogSnapshot) : ProfileCatalogRefreshResult

    data class Failed(val error: Exception) : ProfileCatalogRefreshResult

    data object Stale : ProfileCatalogRefreshResult
}

internal class ProfileCatalogRefresher {
    private val refreshes = LatestOperationGate()

    suspend fun refresh(loadProfiles: suspend () -> List<ProfileSummary>): ProfileCatalogRefreshResult {
        val generation = refreshes.next()
        return try {
            val snapshot = ProfileCatalogSnapshot.from(loadProfiles())
            if (refreshes.isCurrent(generation)) {
                ProfileCatalogRefreshResult.Applied(snapshot)
            } else {
                ProfileCatalogRefreshResult.Stale
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (refreshes.isCurrent(generation)) {
                ProfileCatalogRefreshResult.Failed(error)
            } else {
                ProfileCatalogRefreshResult.Stale
            }
        }
    }
}
