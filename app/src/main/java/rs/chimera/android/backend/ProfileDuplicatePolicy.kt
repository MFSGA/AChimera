package rs.chimera.android.backend

import java.io.File

internal object ProfileDuplicatePolicy {
    fun requireManagedSource(filesDirectory: File, sourcePath: String): File {
        val rootPath = filesDirectory.canonicalFile.path
        val source = File(sourcePath).canonicalFile
        require(source.path.startsWith(rootPath + File.separator)) {
            "Profile file is outside the app profile directory"
        }
        check(source.isFile) { "Profile file is missing" }
        ProfileImportPolicy.requireWithinLimit(source)
        return source
    }
}
