package rs.chimera.android.viewmodel

import kotlinx.coroutines.CancellationException
import rs.chimera.android.backend.model.ProfileSummary

internal sealed interface ProfileVerificationExecutionResult {
    data object Missing : ProfileVerificationExecutionResult

    data class Verified(
        val content: String,
    ) : ProfileVerificationExecutionResult

    data class Failed(
        val error: Exception,
    ) : ProfileVerificationExecutionResult
}

internal class ProfileVerificationRunner(
    private val listProfiles: suspend () -> List<ProfileSummary>,
    private val verifyProfile: suspend (String) -> Result<String>,
) {
    suspend fun run(savedFilePath: String?): ProfileVerificationExecutionResult =
        try {
            val targetPath = resolveProfileVerificationTarget(
                profiles = listProfiles(),
                savedFilePath = savedFilePath,
            ) ?: return ProfileVerificationExecutionResult.Missing

            ProfileVerificationExecutionResult.Verified(
                verifyProfile(targetPath).getOrThrow(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ProfileVerificationExecutionResult.Failed(error)
        }
}
