package rs.chimera.android.backend

import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileBackupRecoveryPolicyTest {
    @Test
    fun pendingBackupRestoresOriginalProfile() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val target = directory.resolve("remote.yaml").apply { writeText("new-profile") }
        val backup = managedBackup(directory, target.name).apply { writeText("old-profile") }

        ProfileBackupRecoveryPolicy.recover(
            directory = directory,
            metadataByPath = emptyMap(),
            pendingBackupNames = setOf(backup.name),
        )

        assertEquals("old-profile", target.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun missingTargetRestoresBackupThatMatchesCatalog() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val target = directory.resolve("remote.yaml")
        val backup = managedBackup(directory, target.name).apply { writeText("old-profile") }

        ProfileBackupRecoveryPolicy.recover(
            directory = directory,
            metadataByPath = mapOf(
                target.absolutePath to ProfileBackupRecoveryMetadata(
                    fileSize = backup.length(),
                    lastUpdated = null,
                ),
            ),
        )

        assertEquals("old-profile", target.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun committedTargetDiscardsStaleBackup() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val target = directory.resolve("remote.yaml").apply { writeText("new-profile") }
        val backup = managedBackup(directory, target.name).apply { writeText("old") }

        ProfileBackupRecoveryPolicy.recover(
            directory = directory,
            metadataByPath = mapOf(
                target.absolutePath to ProfileBackupRecoveryMetadata(
                    fileSize = target.length(),
                    lastUpdated = null,
                ),
            ),
        )

        assertEquals("new-profile", target.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun unmanagedBackupIsLeftUntouched() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val unmanaged = directory.resolve("remote.yaml.backup").apply { writeText("keep") }

        ProfileBackupRecoveryPolicy.recover(directory, emptyMap())

        assertTrue(unmanaged.exists())
    }

    private fun managedBackup(directory: java.io.File, targetName: String) =
        directory.resolve(".$targetName.${UUID.randomUUID()}.backup")
}
