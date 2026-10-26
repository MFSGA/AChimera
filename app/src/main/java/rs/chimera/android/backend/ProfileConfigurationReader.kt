package rs.chimera.android.backend

import rs.chimera.android.backend.model.ProfileFileMetadata
import rs.chimera.android.backend.model.ProfileSummary
import java.io.File
import java.util.Locale

internal class ProfileConfigurationReader(
    private val filesDirectory: File,
    private val loadProfile: (String) -> ProfileSummary?,
) {
    fun read(id: String): String {
        return managedFile(id).readText(Charsets.UTF_8)
    }

    fun metadata(id: String): ProfileFileMetadata {
        val file = managedFile(id)
        val extension = file.extension.lowercase(Locale.ROOT)
            .takeIf { it.matches(FILE_EXTENSION_PATTERN) }
        return ProfileFileMetadata(
            extension = extension,
            size = file.length(),
            lastModified = file.lastModified().takeIf { it > 0L },
        )
    }

    private fun managedFile(id: String): File {
        val profile = loadProfile(id) ?: throw IllegalArgumentException("Profile not found")
        return ProfileDuplicatePolicy.requireManagedSource(filesDirectory, profile.filePath)
    }

    private companion object {
        val FILE_EXTENSION_PATTERN = Regex("[a-z0-9]{1,16}")
    }
}
