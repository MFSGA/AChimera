package rs.chimera.android.service

import java.io.File
import java.util.UUID

internal object LegacyProviderDirectoryMigrator {
    fun prepare(legacyWorkDirectory: File, profileWorkDirectory: File): File {
        check(profileWorkDirectory.isDirectory || profileWorkDirectory.mkdirs()) {
            "Failed to create profile runtime directory"
        }

        val workRoot = profileWorkDirectory.canonicalFile
        val providersDirectory = File(workRoot, PROVIDERS_DIRECTORY)
        check(providersDirectory.canonicalFile == providersDirectory.absoluteFile) {
            "Profile provider directory is invalid"
        }
        val marker = File(profileWorkDirectory, MIGRATION_MARKER)
        if (marker.exists()) {
            check(marker.isFile && marker.canonicalFile == File(workRoot, MIGRATION_MARKER)) {
                "Provider migration marker is invalid"
            }
        } else {
            check(providersDirectory.isDirectory || providersDirectory.mkdirs()) {
                "Failed to create profile providers directory"
            }

            val legacyProviders = File(legacyWorkDirectory, PROVIDERS_DIRECTORY)
            if (legacyProviders.exists()) {
                require(legacyProviders.isDirectory) { "Legacy providers path is invalid" }
                val legacyRoot = File(legacyWorkDirectory.canonicalFile, PROVIDERS_DIRECTORY)
                require(legacyProviders.canonicalFile == legacyRoot) { "Legacy providers path is invalid" }
                copyDirectory(
                    source = legacyProviders,
                    target = providersDirectory,
                    sourceRoot = legacyRoot,
                    targetRoot = workRoot,
                    visitedDirectories = mutableSetOf(),
                )
            }

            check(marker.createNewFile() || marker.isFile) {
                "Failed to record provider migration state"
            }
        }

        check(providersDirectory.isDirectory || providersDirectory.mkdirs()) {
            "Failed to create profile providers directory"
        }
        return providersDirectory
    }

    private fun copyDirectory(
        source: File,
        target: File,
        sourceRoot: File,
        targetRoot: File,
        visitedDirectories: MutableSet<String>,
    ) {
        val canonicalSource = source.canonicalFile
        val canonicalTarget = target.canonicalFile
        require(isWithin(sourceRoot, canonicalSource)) { "Legacy provider path is invalid" }
        require(isWithin(targetRoot, canonicalTarget)) { "Profile provider path is invalid" }
        require(visitedDirectories.add(canonicalSource.path)) { "Legacy provider directory contains a cycle" }
        check(canonicalTarget.isDirectory || canonicalTarget.mkdirs()) {
            "Failed to create profile providers directory"
        }

        val children = canonicalSource.listFiles()
            ?: throw IllegalStateException("Failed to read legacy provider directory")
        for (child in children) {
            val canonicalChild = child.canonicalFile
            require(isWithin(sourceRoot, canonicalChild)) { "Legacy provider path is invalid" }
            val destination = File(canonicalTarget, child.name)
            when {
                child.isDirectory -> {
                    check(destination.isDirectory || !destination.exists() && destination.mkdirs()) {
                        "Profile provider entry has an incompatible type"
                    }
                    copyDirectory(canonicalChild, destination, sourceRoot, targetRoot, visitedDirectories)
                }

                child.isFile -> copyFileIfMissing(child, destination, targetRoot)
            }
        }
    }

    private fun copyFileIfMissing(source: File, target: File, targetRoot: File) {
        if (target.exists()) {
            require(isWithin(targetRoot, target.canonicalFile)) { "Profile provider path is invalid" }
            check(target.isFile) { "Profile provider entry has an incompatible type" }
            return
        }
        val temporary = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            source.copyTo(temporary, overwrite = false)
            if (source.lastModified() > 0L) temporary.setLastModified(source.lastModified())
            check(temporary.renameTo(target)) { "Failed to copy legacy provider file" }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun isWithin(root: File, child: File): Boolean {
        val rootPath = root.canonicalPath
        val childPath = child.canonicalPath
        return childPath == rootPath || childPath.startsWith(rootPath + File.separator)
    }

    private const val PROVIDERS_DIRECTORY = "providers"
    private const val MIGRATION_MARKER = ".legacy-providers-copied"
}
