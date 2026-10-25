package rs.chimera.android.backend

import rs.chimera.android.backend.model.ProfileSummary
import java.io.File

internal class ProfileConfigurationReader(
    private val filesDirectory: File,
    private val loadProfile: (String) -> ProfileSummary?,
) {
    fun read(id: String): String {
        val profile = loadProfile(id) ?: throw IllegalArgumentException("Profile not found")
        val file = ProfileDuplicatePolicy.requireManagedSource(filesDirectory, profile.filePath)
        return file.readText(Charsets.UTF_8)
    }
}
