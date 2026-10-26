package rs.chimera.android.backend

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.service.LegacyProviderDirectoryMigrator

class ProfileProviderFileReaderTest {
    @Test
    fun listsManagedProviderFilesAndDirectoriesWithMetadata() {
        val filesDirectory = Files.createTempDirectory("profile-files").toFile()
        val cacheDirectory = Files.createTempDirectory("provider-cache").toFile()
        val profile = filesDirectory.resolve("profile.yaml").apply { writeText("config") }
        val workDirectory = ProfileRuntimeDirectoryPolicy.directory(cacheDirectory, profile.path)
        val providers = workDirectory.resolve("providers").apply { mkdirs() }
        providers.resolve("z.yaml").writeText("node")
        providers.resolve("nested").mkdirs()
        providers.resolve("nested/data.db").writeText("data")
        val reader = reader(filesDirectory, cacheDirectory, profile)

        val root = reader.list(PROFILE_ID, emptyList())
        val nested = reader.list(PROFILE_ID, listOf("nested"))

        assertEquals(listOf("nested", "z.yaml"), root.map { it.name })
        assertTrue(root.first().isDirectory)
        assertEquals(4L, root.last().size)
        assertEquals(listOf("nested", "data.db"), nested.single().relativePath)
    }

    @Test
    fun readsLegacySharedDirectoryUntilThatProfileCompletesMigration() {
        val filesDirectory = Files.createTempDirectory("profile-files").toFile()
        val cacheDirectory = Files.createTempDirectory("provider-cache").toFile()
        val profile = filesDirectory.resolve("profile.yaml").apply { writeText("config") }
        cacheDirectory.resolve("providers").apply { mkdirs() }.resolve("old.yaml").writeText("old")
        val reader = reader(filesDirectory, cacheDirectory, profile)

        assertEquals(listOf("old.yaml"), reader.list(PROFILE_ID, emptyList()).map { it.name })

        LegacyProviderDirectoryMigrator.prepare(cacheDirectory, ProfileRuntimeDirectoryPolicy.directory(cacheDirectory, profile.path))
        cacheDirectory.resolve("providers/new.yaml").writeText("new")
        assertEquals(listOf("old.yaml"), reader.list(PROFILE_ID, emptyList()).map { it.name })
    }

    @Test
    fun rejectsTraversalAndSymlinksEscapingProviderDirectory() {
        val filesDirectory = Files.createTempDirectory("profile-files").toFile()
        val cacheDirectory = Files.createTempDirectory("provider-cache").toFile()
        val profile = filesDirectory.resolve("profile.yaml").apply { writeText("config") }
        val providers = ProfileRuntimeDirectoryPolicy.directory(cacheDirectory, profile.path)
            .resolve("providers").apply { mkdirs() }
        val outside = Files.createTempDirectory("outside-providers")
            .resolve("secret.yaml").toFile().apply { writeText("secret") }
        Files.createSymbolicLink(providers.toPath().resolve("escape.yaml"), outside.toPath())
        val reader = reader(filesDirectory, cacheDirectory, profile)

        assertTrue(runCatching { reader.list(PROFILE_ID, listOf("..")) }.isFailure)
        assertTrue(runCatching { reader.list(PROFILE_ID, emptyList()) }.isFailure)
        assertEquals("secret", outside.readText())
    }

    @Test
    fun rejectsProviderRootSymlinkThatAliasesAnotherDirectory() {
        val filesDirectory = Files.createTempDirectory("profile-files").toFile()
        val cacheDirectory = Files.createTempDirectory("provider-cache").toFile()
        val profile = filesDirectory.resolve("profile.yaml").apply { writeText("config") }
        val workDirectory = ProfileRuntimeDirectoryPolicy.directory(cacheDirectory, profile.path)
        workDirectory.mkdirs()
        val outside = Files.createTempDirectory("other-provider-root")
        Files.createSymbolicLink(workDirectory.resolve("providers").toPath(), outside)
        val reader = reader(filesDirectory, cacheDirectory, profile)

        assertTrue(runCatching { reader.list(PROFILE_ID, emptyList()) }.isFailure)
    }

    private fun reader(filesDirectory: java.io.File, cacheDirectory: java.io.File, profileFile: java.io.File) =
        ProfileProviderFileReader(filesDirectory, cacheDirectory) { id ->
            if (id == PROFILE_ID) profile(profileFile.path) else null
        }

    private fun profile(path: String) = ProfileSummary(
        id = PROFILE_ID,
        name = "Test",
        filePath = path,
        type = ProfileType.LOCAL,
        isActive = false,
        isRemote = false,
        lastUpdated = null,
        fileSize = 0L,
    )

    private companion object {
        const val PROFILE_ID = "test-profile"
    }
}
