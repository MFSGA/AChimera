package rs.chimera.android.backend

import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileImportRecoveryPolicyTest {
    @Test
    fun createStageUsesDestinationDirectoryAndExtension() {
        val directory = Files.createTempDirectory("profile-import-recovery").toFile()
        val destination = directory.resolve("profile.yaml")

        val staged = ProfileImportRecoveryPolicy.createStage(destination)

        assertEquals(directory.canonicalFile, staged.parentFile?.canonicalFile)
        assertTrue(staged.name.startsWith(".profile-import-"))
        assertTrue(staged.name.endsWith(".yaml"))
    }

    @Test
    fun removesManagedStagedImports() {
        val directory = Files.createTempDirectory("profile-import-recovery").toFile()
        val staged = directory.resolve(".profile-import-${UUID.randomUUID()}.yaml").apply {
            writeText("partial")
        }

        ProfileImportRecoveryPolicy.recover(directory, emptySet(), emptySet())

        assertFalse(staged.exists())
    }

    @Test
    fun removesPendingDestinationMissingFromCatalog() {
        val directory = Files.createTempDirectory("profile-import-recovery").toFile()
        val destination = directory.resolve("profile.yaml").apply { writeText("orphan") }

        ProfileImportRecoveryPolicy.recover(
            directory = directory,
            referencedPaths = emptySet(),
            pendingDestinationNames = setOf(destination.name),
        )

        assertFalse(destination.exists())
    }

    @Test
    fun keepsPendingDestinationReferencedByCatalog() {
        val directory = Files.createTempDirectory("profile-import-recovery").toFile()
        val destination = directory.resolve("profile.yaml").apply { writeText("committed") }

        ProfileImportRecoveryPolicy.recover(
            directory = directory,
            referencedPaths = setOf(destination.absolutePath),
            pendingDestinationNames = setOf(destination.name),
        )

        assertTrue(destination.exists())
    }

    @Test
    fun leavesUnmanagedFilesUntouched() {
        val directory = Files.createTempDirectory("profile-import-recovery").toFile()
        val visible = directory.resolve("profile.yaml").apply { writeText("visible") }
        val invalidStage = directory.resolve(".profile-import-not-a-uuid.yaml").apply { writeText("hidden") }

        ProfileImportRecoveryPolicy.recover(directory, emptySet(), emptySet())

        assertTrue(visible.exists())
        assertTrue(invalidStage.exists())
    }
}
