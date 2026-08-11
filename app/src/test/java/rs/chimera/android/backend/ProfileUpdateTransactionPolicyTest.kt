package rs.chimera.android.backend

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUpdateTransactionPolicyTest {
    @Test
    fun successfulCommitKeepsUpdatedFileAndRemovesBackup() {
        val directory = Files.createTempDirectory("profile-update").toFile()
        val destination = directory.resolve("remote.yaml").apply { writeText("old") }

        runBlocking {
            ProfileUpdateTransactionPolicy.run(
                destinationFile = destination,
                update = { directory.resolve("updated.yaml").apply { writeText("new") } },
                persistMetadata = { _, _ -> "saved" },
            )
        }

        assertEquals("new", destination.readText())
        assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".backup") })
    }

    @Test
    fun metadataFailureRestoresOriginalFileAndClearsJournal() {
        val directory = Files.createTempDirectory("profile-update").toFile()
        val destination = directory.resolve("remote.yaml").apply { writeText("old") }
        val expected = IllegalStateException("catalog failed")
        var pendingBackupName: String? = null
        var clearedBackupName: String? = null

        val actual = runCatching {
            runBlocking {
                ProfileUpdateTransactionPolicy.run(
                    destinationFile = destination,
                    update = { directory.resolve("updated.yaml").apply { writeText("new") } },
                    persistMetadata = { _, _ -> throw expected },
                    beginBackupTransaction = { pendingBackupName = it.name },
                    clearBackupTransaction = { clearedBackupName = it.name },
                )
            }
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertEquals("old", destination.readText())
        assertEquals(pendingBackupName, clearedBackupName)
    }

    @Test
    fun metadataFailureRemovesNewFileWhenOriginalWasMissing() {
        val directory = Files.createTempDirectory("profile-update").toFile()
        val destination = directory.resolve("remote.yaml")

        runCatching {
            runBlocking {
                ProfileUpdateTransactionPolicy.run(
                    destinationFile = destination,
                    update = { directory.resolve("updated.yaml").apply { writeText("new") } },
                    persistMetadata = { _, _ -> error("catalog failed") },
                )
            }
        }

        assertFalse(destination.exists())
    }

    @Test
    fun successfulCommitReceivesJournaledBackup() {
        val directory = Files.createTempDirectory("profile-update").toFile()
        val destination = directory.resolve("remote.yaml").apply { writeText("old") }
        var pendingBackupName: String? = null
        var committedBackupName: String? = null

        runBlocking {
            ProfileUpdateTransactionPolicy.run(
                destinationFile = destination,
                update = {
                    assertTrue(pendingBackupName != null)
                    directory.resolve("updated.yaml").apply { writeText("new") }
                },
                persistMetadata = { _, backup ->
                    committedBackupName = backup?.name
                    "saved"
                },
                beginBackupTransaction = { pendingBackupName = it.name },
            )
        }

        assertEquals(pendingBackupName, committedBackupName)
        assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".backup") })
    }

    @Test
    fun updateFailureLeavesOriginalFileUntouched() {
        val directory = Files.createTempDirectory("profile-update").toFile()
        val destination = directory.resolve("remote.yaml").apply { writeText("old") }
        val expected = IllegalStateException("download failed")
        var pendingBackupName: String? = null
        var clearedBackupName: String? = null

        val actual = runCatching {
            runBlocking {
                ProfileUpdateTransactionPolicy.run(
                    destinationFile = destination,
                    update = { throw expected },
                    persistMetadata = { _, _ -> error("persist must not run") },
                    beginBackupTransaction = { pendingBackupName = it.name },
                    clearBackupTransaction = { clearedBackupName = it.name },
                )
            }
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertTrue(destination.exists())
        assertEquals("old", destination.readText())
        assertEquals(pendingBackupName, clearedBackupName)
        assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".backup") })
    }
}
