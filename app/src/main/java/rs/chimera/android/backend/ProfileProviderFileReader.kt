package rs.chimera.android.backend

import java.io.File
import java.util.Locale
import rs.chimera.android.backend.model.ProfileProviderFileMetadata
import rs.chimera.android.backend.model.ProfileSummary

internal class ProfileProviderFileReader(
    private val filesDirectory: File,
    private val cacheDirectory: File,
    private val loadProfile: (String) -> ProfileSummary?,
) {
    fun list(id: String, relativePath: List<String>): List<ProfileProviderFileMetadata> {
        val profile = loadProfile(id) ?: throw IllegalArgumentException("Profile not found")
        val managedProfile = ProfileDuplicatePolicy.requireManagedSource(filesDirectory, profile.filePath)
        val workDirectory = ProfileRuntimeDirectoryPolicy.directory(cacheDirectory, managedProfile.path)
        val (root, isLegacyRoot) = providerRoot(workDirectory)
        if (!root.exists()) return emptyList()

        val canonicalRoot = root.canonicalFile
        val workRoot = workDirectory.canonicalFile
        val cacheRoot = cacheDirectory.canonicalFile
        require(
            if (isLegacyRoot) {
                canonicalRoot == File(cacheRoot, LEGACY_PROVIDERS_DIRECTORY)
            } else {
                canonicalRoot == File(workRoot, PROVIDERS_DIRECTORY)
            },
        ) { "Provider directory is outside managed storage" }

        require(relativePath.none { segment ->
            segment.isEmpty() ||
                segment == "." ||
                segment == ".." ||
                segment.contains(File.separatorChar) ||
                segment.contains('\u0000')
        }) { "Invalid provider path" }

        val directory = relativePath.fold(canonicalRoot) { parent, segment ->
            File(parent, segment).canonicalFile.also { child ->
                require(isWithin(canonicalRoot, child)) { "Provider path is outside its directory" }
            }
        }
        require(directory.isDirectory) { "Provider directory is unavailable" }
        val children = directory.listFiles()
            ?: throw IllegalStateException("Failed to list provider directory")

        return children.mapNotNull { child ->
            if (!child.exists()) return@mapNotNull null
            val canonicalChild = child.canonicalFile
            require(isWithin(canonicalRoot, canonicalChild)) { "Provider entry is outside its directory" }
            val isDirectory = canonicalChild.isDirectory
            require(isDirectory || canonicalChild.isFile) { "Provider entry is unavailable" }
            ProfileProviderFileMetadata(
                name = child.name,
                relativePath = relativePath + child.name,
                isDirectory = isDirectory,
                size = if (isDirectory) 0L else canonicalChild.length(),
                lastModified = canonicalChild.lastModified().takeIf { it > 0L },
            )
        }.sortedWith(
            compareByDescending<ProfileProviderFileMetadata> { it.isDirectory }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.name },
        )
    }

    private fun providerRoot(workDirectory: File): Pair<File, Boolean> {
        val migrationMarker = File(workDirectory, MIGRATION_MARKER)
        if (migrationMarker.exists()) return File(workDirectory, PROVIDERS_DIRECTORY) to false

        val legacyProviders = File(cacheDirectory, LEGACY_PROVIDERS_DIRECTORY)
        return if (legacyProviders.exists()) {
            legacyProviders to true
        } else {
            File(workDirectory, PROVIDERS_DIRECTORY) to false
        }
    }

    private fun isWithin(root: File, child: File): Boolean =
        child.path == root.path || child.path.startsWith(root.path + File.separator)

    private companion object {
        const val LEGACY_PROVIDERS_DIRECTORY = "providers"
        const val MIGRATION_MARKER = ".legacy-providers-copied"
        const val PROVIDERS_DIRECTORY = "providers"
    }
}
