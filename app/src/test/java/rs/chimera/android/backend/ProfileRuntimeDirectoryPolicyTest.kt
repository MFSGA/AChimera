package rs.chimera.android.backend

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRuntimeDirectoryPolicyTest {
    @Test
    fun returnsStableIsolatedDirectoryForEachManagedProfilePath() {
        val cacheDirectory = Files.createTempDirectory("profile-runtime-cache").toFile()
        val profilesDirectory = Files.createTempDirectory("profile-runtime-profiles")
        val firstProfile = profilesDirectory.resolve("first.yaml").toFile().apply { writeText("first") }
        val secondProfile = profilesDirectory.resolve("second.yaml").toFile().apply { writeText("second") }

        val firstDirectory = ProfileRuntimeDirectoryPolicy.directory(cacheDirectory, firstProfile.path)

        assertEquals(firstDirectory, ProfileRuntimeDirectoryPolicy.directory(cacheDirectory, firstProfile.path))
        assertNotEquals(firstDirectory, ProfileRuntimeDirectoryPolicy.directory(cacheDirectory, secondProfile.path))
        assertTrue(firstDirectory.path.startsWith(cacheDirectory.canonicalPath + java.io.File.separator))
    }

    @Test
    fun keepsCoreSocketPathWithinUnixSocketLimitForAndroidPackagePath() {
        val cacheDirectory = File("/data/user/0/rs.chimera.android.dev/cache")
        val profile = Files.createTempFile("profile-runtime", ".yaml")

        val runtimeDirectory = ProfileRuntimeDirectoryPolicy.directory(cacheDirectory, profile.toString())
        val socketPath = File(runtimeDirectory, "clash.sock").absolutePath

        assertTrue(socketPath.toByteArray(Charsets.UTF_8).size < 108)
    }

    @Test
    fun rejectsRuntimeRootSymlinkThatEscapesCacheDirectory() {
        val cacheDirectory = Files.createTempDirectory("profile-runtime-cache")
        val outside = Files.createTempDirectory("profile-runtime-outside")
        val profile = Files.createTempFile("profile-runtime", ".yaml")
        Files.createSymbolicLink(cacheDirectory.resolve("p"), outside)

        assertTrue(
            runCatching {
                ProfileRuntimeDirectoryPolicy.directory(cacheDirectory.toFile(), profile.toString())
            }.isFailure,
        )
    }
}
