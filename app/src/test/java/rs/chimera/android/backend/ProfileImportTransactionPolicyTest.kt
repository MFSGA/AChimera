package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileImportTransactionPolicyTest {
    @Test
    fun cancelledCallerDoesNotBeginImportCommit() = runBlocking {
        var committed = false
        var cancellationObserved = false

        val job = launch {
            currentCoroutineContext()[kotlinx.coroutines.Job]?.cancel()
            try {
                ProfileImportTransactionPolicy.commit { committed = true }
            } catch (_: CancellationException) {
                cancellationObserved = true
            }
        }
        job.join()

        assertFalse(committed)
        assertTrue(cancellationObserved)
    }

    @Test
    fun committedImportFinishesAfterCallerCancellation() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var completed = false

        val import = launch {
            ProfileImportTransactionPolicy.commit {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                completed = true
            }
        }
        entered.await()

        import.cancel()
        release.complete(Unit)
        import.join()

        assertTrue(completed)
        assertTrue(import.isCancelled)
    }

    @Test
    fun postCommitFailureIsReportedWithoutFailingCommittedOperation() = runBlocking {
        val expected = IllegalStateException("schedule refresh failed")
        var reported: Exception? = null

        ProfileImportTransactionPolicy.completePostCommitBestEffort(
            afterCommit = { throw expected },
            onFailure = { reported = it },
        )

        assertSame(expected, reported)
    }

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
    fun fatalImportJournalCleanupErrorPropagates() {
        val directory = Files.createTempDirectory("profile-import").toFile()
        val staged = directory.resolve("staged.yaml").apply { writeText("profile") }
        val destination = directory.resolve("profile.yaml")
        val metadataError = IllegalStateException("catalog failed")
        val fatal = AssertionError("journal cleanup failed")

        val actual = runCatching {
            ProfileImportTransactionPolicy.run(
                stagedFile = staged,
                destinationFile = destination,
                beginImportTransaction = {},
                persistMetadata = { throw metadataError },
                clearImportTransaction = { throw fatal },
            )
        }.exceptionOrNull()

        assertSame(fatal, actual)
        assertFalse(staged.exists())
        assertFalse(destination.exists())
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
