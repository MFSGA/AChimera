package rs.chimera.android.backend

import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDownloadRecoveryPolicyTest {
    @Test
    fun createStageUsesDestinationDirectoryAndManagedName() {
        val directory = Files.createTempDirectory("profile-download-stage").toFile()
        val destination = directory.resolve("remote.yaml")

        val staged = ProfileDownloadRecoveryPolicy.createStage(destination)

        assertEquals(directory.canonicalFile, staged.parentFile?.canonicalFile)
        assertTrue(staged.name.startsWith(".remote.yaml."))
        assertTrue(staged.name.endsWith(".download"))
    }

    @Test
    fun removesManagedStagedDownload() {
        val directory = Files.createTempDirectory("profile-download-recovery").toFile()
        val staged = directory.resolve(".profile.yaml.${UUID.randomUUID()}.download").apply {
            writeText("partial")
        }

        ProfileDownloadRecoveryPolicy.cleanup(directory)

        assertFalse(staged.exists())
    }

    @Test
    fun leavesUnmanagedDownloadFilesUntouched() {
        val directory = Files.createTempDirectory("profile-download-recovery").toFile()
        val visible = directory.resolve("profile.yaml.download").apply { writeText("visible") }
        val invalidUuid = directory.resolve(".profile.yaml.not-a-uuid.download").apply { writeText("hidden") }

        ProfileDownloadRecoveryPolicy.cleanup(directory)

        assertTrue(visible.exists())
        assertTrue(invalidUuid.exists())
    }

    @Test
    fun leavesDirectoriesUntouched() {
        val directory = Files.createTempDirectory("profile-download-recovery").toFile()
        val stagedDirectory = directory.resolve(".profile.yaml.${UUID.randomUUID()}.download").apply {
            mkdir()
        }

        ProfileDownloadRecoveryPolicy.cleanup(directory)

        assertTrue(stagedDirectory.exists())
    }
}
