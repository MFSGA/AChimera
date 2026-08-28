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
    fun newestPendingBackupWinsWhenMultipleTransactionsRemain() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val target = directory.resolve("remote.yaml").apply { writeText("uncommitted-latest") }
        val olderBackup = managedBackup(directory, target.name).apply { writeText("older-committed") }
        val newerBackup = managedBackup(directory, target.name).apply { writeText("newer-committed") }
        assertTrue(olderBackup.setLastModified(1_000L))
        assertTrue(newerBackup.setLastModified(2_000L))

        ProfileBackupRecoveryPolicy.recover(
            directory = directory,
            pendingBackupNames = setOf(olderBackup.name, newerBackup.name),
        )

        assertEquals("newer-committed", target.readText())
        assertFalse(olderBackup.exists())
        assertFalse(newerBackup.exists())
    }

    @Test
    fun persistedBackupOrderWinsWhenTimestampsTie() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val target = directory.resolve("remote.yaml").apply { writeText("uncommitted-latest") }
        val olderBackup = orderedManagedBackup(directory, target.name, 10L).apply { writeText("older-committed") }
        val newerBackup = orderedManagedBackup(directory, target.name, 11L).apply { writeText("newer-committed") }
        assertTrue(olderBackup.setLastModified(1_000L))
        assertTrue(newerBackup.setLastModified(1_000L))

        ProfileBackupRecoveryPolicy.recover(
            directory = directory,
            pendingBackupNames = setOf(olderBackup.name, newerBackup.name),
        )

        assertEquals("newer-committed", target.readText())
        assertFalse(olderBackup.exists())
        assertFalse(newerBackup.exists())
    }

    @Test
    fun newBackupOrderAdvancesPastExistingTransaction() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val target = directory.resolve("remote.yaml").apply { writeText("current") }
        val existing = orderedManagedBackup(directory, target.name, Long.MAX_VALUE - 1).apply { writeText("older") }

        val created = ProfileBackupRecoveryPolicy.createBackupFile(target)

        assertEquals(target.name, ProfileBackupRecoveryPolicy.managedBackupTargetName(created.name))
        assertTrue(created.name.endsWith(".${Long.MAX_VALUE}.backup"))
        assertTrue(existing.exists())
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
    fun corruptPendingBackupNameCannotEscapeRecoveryDirectory() {
        val directory = Files.createTempDirectory("profile-backup-recovery").toFile()
        val outside = checkNotNull(directory.parentFile).resolve("outside-profile.backup").apply { writeText("keep") }
        val corruptName = "../${outside.name}"

        assertFalse(ProfileBackupRecoveryPolicy.isManagedBackupName(corruptName))

        ProfileBackupRecoveryPolicy.recover(
            directory = directory,
            pendingBackupNames = setOf(corruptName),
        )

        assertEquals("keep", outside.readText())
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

    private fun orderedManagedBackup(
        directory: java.io.File,
        targetName: String,
        order: Long,
    ) = directory.resolve(".$targetName.${UUID.randomUUID()}.$order.backup")
}
