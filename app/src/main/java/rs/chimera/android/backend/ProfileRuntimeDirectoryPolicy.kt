package rs.chimera.android.backend

import java.io.File
import java.security.MessageDigest

internal object ProfileRuntimeDirectoryPolicy {
    fun directory(cacheDirectory: File, profilePath: String): File {
        val profile = File(profilePath).canonicalFile
        require(profile.isFile) { "Profile file is missing" }

        val cacheRoot = cacheDirectory.canonicalFile
        val expectedProfilesRoot = File(cacheRoot, PROFILE_RUNTIME_DIRECTORY)
        val profilesRoot = expectedProfilesRoot.canonicalFile
        check(profilesRoot == expectedProfilesRoot.absoluteFile && isWithin(cacheRoot, profilesRoot)) {
            "Profile runtime directory is outside the cache directory"
        }
        val maximumKeyLength = UNIX_SOCKET_PATH_MAX_BYTES - 1 -
            profilesRoot.path.toByteArray(Charsets.UTF_8).size - 1 - CORE_SOCKET_SUFFIX.length
        val profileKeyLength = minOf(PROFILE_KEY_HEX_LENGTH, maximumKeyLength)
        check(profileKeyLength >= MIN_PROFILE_KEY_HEX_LENGTH) {
            "Cache directory is too long for a safe profile runtime socket path"
        }
        val profileKey = MessageDigest.getInstance("SHA-256")
            .digest(profile.path.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            .take(profileKeyLength)
        val expectedDirectory = File(profilesRoot, profileKey)
        val directory = expectedDirectory.canonicalFile
        check(directory == expectedDirectory.absoluteFile && isWithin(profilesRoot, directory)) {
            "Profile runtime directory is outside the cache directory"
        }
        check(File(directory, CORE_SOCKET_FILE).absolutePath.toByteArray(Charsets.UTF_8).size < UNIX_SOCKET_PATH_MAX_BYTES) {
            "Profile runtime directory is too long for the core socket path"
        }
        return directory
    }

    private fun isWithin(root: File, child: File): Boolean =
        child.path == root.path || child.path.startsWith(root.path + File.separator)

    private const val PROFILE_RUNTIME_DIRECTORY = "p"
    private const val PROFILE_KEY_HEX_LENGTH = 48
    private const val CORE_SOCKET_SUFFIX = "/clash.sock"
    private const val CORE_SOCKET_FILE = "clash.sock"
    private const val MIN_PROFILE_KEY_HEX_LENGTH = 32
    private const val UNIX_SOCKET_PATH_MAX_BYTES = 108
}
