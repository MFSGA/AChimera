package rs.chimera.android.backend

import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
            pendingBackupNames = setOf(backup.name),
        )

        assertEquals("old-profile", target.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun pendingBackupRestoresMissingTarget() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val target = directory.resolve("remote.yaml")
        val backup = managedBackup(directory, target.name).apply { writeText("old-profile") }

        ProfileBackupRecoveryPolicy.recover(
            directory = directory,
            pendingBackupNames = setOf(backup.name),
        )

        assertEquals("old-profile", target.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun committedBackupIsDiscardedEvenWhenTargetChangedAfterCommit() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val target = directory.resolve("remote.yaml").apply { writeText("manually-edited-profile") }
        val backup = managedBackup(directory, target.name).apply { writeText("old-profile") }

        ProfileBackupRecoveryPolicy.recover(directory)

        assertEquals("manually-edited-profile", target.readText())
        assertFalse(backup.exists())
    }

    @Test
    fun committedBackupIsDiscardedWhenTargetIsMissing() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val backup = managedBackup(directory, "remote.yaml").apply { writeText("old-profile") }

        ProfileBackupRecoveryPolicy.recover(directory)

        assertFalse(directory.resolve("remote.yaml").exists())
        assertFalse(backup.exists())
    }

    @Test
    fun unmanagedBackupIsLeftUntouched() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val unmanaged = directory.resolve("remote.yaml.backup").apply { writeText("keep") }

        ProfileBackupRecoveryPolicy.recover(directory)

        assertTrue(unmanaged.exists())
    }

    @Test
    fun inspectionFailureDoesNotLookLikeEmptyRecoveryDirectory() {
        val unreadableDirectory = object : java.io.File("unreadable-profile-backups") {
            override fun listFiles(): Array<java.io.File>? = null
        }

        assertThrows(IllegalStateException::class.java) {
            ProfileBackupRecoveryPolicy.recover(unreadableDirectory)
        }
    }

    private fun managedBackup(directory: java.io.File, targetName: String) =
        directory.resolve(".$targetName.${UUID.randomUUID()}.backup")
}
