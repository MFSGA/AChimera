package rs.chimera.android.backend

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.backend.model.ProfileSummary

class ProfileConfigurationReaderTest {
    @Test
    fun readsTheSelectedManagedProfileConfiguration() {
        val directory = Files.createTempDirectory("profile-files").toFile()
        val config = directory.resolve("profile.yaml").apply { writeText("mixed-port: 7890\n") }
        val reader = ProfileConfigurationReader(directory) { id ->
            if (id == PROFILE_ID) profile(config.path) else null
        }

        assertEquals("mixed-port: 7890\n", reader.read(PROFILE_ID))
    }

    @Test
    fun returnsMetadataFromTheManagedFileInsteadOfProfileUpdateTime() {
        val directory = Files.createTempDirectory("profile-files").toFile()
        val expectedModifiedAt = 1_700_000_000_000L
        val config = directory.resolve("profile.YAML").apply {
            writeText("mixed-port: 7890\n")
            setLastModified(expectedModifiedAt)
        }
        val reader = ProfileConfigurationReader(directory) { id ->
            if (id == PROFILE_ID) profile(config.path) else null
        }

        val metadata = reader.metadata(PROFILE_ID)

        assertEquals("yaml", metadata.extension)
        assertEquals(config.length(), metadata.size)
        assertEquals(expectedModifiedAt, metadata.lastModified)
    }

    @Test
    fun rejectsUnknownProfilesAndFilesOutsideManagedStorage() {
        val directory = Files.createTempDirectory("profile-files").toFile()
        val outside = Files.createTempDirectory("outside-profile-files")
            .resolve("profile.yaml")
            .toFile()
            .apply { writeText("secret: value") }
        val reader = ProfileConfigurationReader(directory) { id ->
            if (id == PROFILE_ID) profile(outside.path) else null
        }

        assertTrue(runCatching { reader.read("missing") }.isFailure)
        assertTrue(runCatching { reader.metadata("missing") }.isFailure)
        assertTrue(runCatching { reader.read(PROFILE_ID) }.isFailure)
        assertTrue(runCatching { reader.metadata(PROFILE_ID) }.isFailure)
    }

    @Test
    fun rejectsSymlinksThatEscapeManagedStorage() {
        val directory = Files.createTempDirectory("profile-files")
        val outside = Files.createTempDirectory("outside-profile-files")
            .resolve("profile.yaml")
            .toFile()
            .apply { writeText("secret: value") }
        val link = directory.resolve("profile.yaml")
        Files.createSymbolicLink(link, outside.toPath())
        val reader = ProfileConfigurationReader(directory.toFile()) { profile(link.toString()) }

        assertTrue(runCatching { reader.read(PROFILE_ID) }.isFailure)
        assertTrue(runCatching { reader.metadata(PROFILE_ID) }.isFailure)
    }

    @Test
    fun rejectsOversizedConfigurationFiles() {
        val directory = Files.createTempDirectory("profile-files").toFile()
        val oversized = directory.resolve("profile.yaml").apply {
            writeBytes(ByteArray((ProfileImportPolicy.MAX_PROFILE_BYTES + 1).toInt()))
        }
        val reader = ProfileConfigurationReader(directory) { profile(oversized.path) }

        assertTrue(runCatching { reader.read(PROFILE_ID) }.isFailure)
        assertTrue(runCatching { reader.metadata(PROFILE_ID) }.isFailure)
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
