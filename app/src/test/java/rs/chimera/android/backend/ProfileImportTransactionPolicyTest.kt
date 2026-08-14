package rs.chimera.android.backend

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileImportTransactionPolicyTest {
    @Test
    fun successfulImportMovesFileAndPersistsMetadata() {
        val directory = Files.createTempDirectory("profile-import").toFile()
        val staged = directory.resolve("staged.yaml").apply { writeText("profile") }
        val destination = directory.resolve("profile.yaml")
        var pendingName: String? = null
        var persistedName: String? = null

        val result = ProfileImportTransactionPolicy.run(
            stagedFile = staged,
            destinationFile = destination,
            beginImportTransaction = { pendingName = it.name },
            persistMetadata = {
                persistedName = it.name
                "saved"
            },
            clearImportTransaction = { error("clear must not run after a successful commit") },
        )

        assertEquals("saved", result)
        assertEquals(destination.name, pendingName)
        assertEquals(destination.name, persistedName)
        assertFalse(staged.exists())
        assertEquals("profile", destination.readText())
    }

    @Test
    fun metadataFailureDeletesUncommittedDestinationAndClearsJournal() {
        val directory = Files.createTempDirectory("profile-import").toFile()
        val staged = directory.resolve("staged.yaml").apply { writeText("profile") }
        val destination = directory.resolve("profile.yaml")
        val expected = IllegalStateException("catalog failed")
        var clearedName: String? = null

        val actual = runCatching {
            ProfileImportTransactionPolicy.run(
                stagedFile = staged,
                destinationFile = destination,
                beginImportTransaction = {},
                persistMetadata = { throw expected },
                clearImportTransaction = { clearedName = it.name },
            )
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertFalse(staged.exists())
        assertFalse(destination.exists())
        assertEquals(destination.name, clearedName)
    }

    @Test
    fun journalFailureDeletesStagedFile() {
        val directory = Files.createTempDirectory("profile-import").toFile()
        val staged = directory.resolve("staged.yaml").apply { writeText("profile") }
        val destination = directory.resolve("profile.yaml")
        val expected = IllegalStateException("journal failed")

        val actual = runCatching {
            ProfileImportTransactionPolicy.run(
                stagedFile = staged,
                destinationFile = destination,
                beginImportTransaction = { throw expected },
                persistMetadata = { error("persist must not run") },
                clearImportTransaction = { error("clear must not run") },
            )
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertFalse(staged.exists())
        assertFalse(destination.exists())
    }

    @Test
    fun existingDestinationIsNeverOverwritten() {
        val directory = Files.createTempDirectory("profile-import").toFile()
        val staged = directory.resolve("staged.yaml").apply { writeText("new") }
        val destination = directory.resolve("profile.yaml").apply { writeText("old") }

        val actual = runCatching {
            ProfileImportTransactionPolicy.run(
                stagedFile = staged,
                destinationFile = destination,
                beginImportTransaction = { error("journal must not start") },
                persistMetadata = { error("persist must not run") },
                clearImportTransaction = { error("clear must not run") },
            )
        }.exceptionOrNull()

        assertTrue(actual is IllegalStateException)
        assertEquals("old", destination.readText())
        assertTrue(staged.exists())
    }
}
