package rs.chimera.android.backend

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDeletionPolicyTest {
    @Test
    fun successfulDeletionCommitsCatalogAndRemovesFile() {
        val directory = Files.createTempDirectory("profile-delete").toFile()
        val file = directory.resolve("profile.yaml").apply { writeText("mixed-port: 7890") }
        var commits = 0

        ProfileDeletionPolicy.delete(
            file = file,
            shouldDeleteFile = true,
            persistDeletion = { commits += 1 },
            rollbackCatalog = { error("rollback must not run") },
        )

        assertEquals(1, commits)
        assertFalse(file.exists())
    }

    @Test
    fun persistenceFailureRestoresStagedFile() {
        val directory = Files.createTempDirectory("profile-delete").toFile()
        val file = directory.resolve("profile.yaml").apply { writeText("content") }
        val failure = IllegalStateException("catalog failed")

        val thrown = runCatching {
            ProfileDeletionPolicy.delete(
                file = file,
                shouldDeleteFile = true,
                persistDeletion = { throw failure },
                rollbackCatalog = { error("rollback must not run") },
            )
        }.exceptionOrNull()

        assertTrue(thrown === failure)
        assertTrue(file.exists())
        assertEquals("content", file.readText())
    }

    @Test
    fun finalDeleteFailureRollsBackCatalogAndRestoresPath() {
        val directory = Files.createTempDirectory("profile-delete").toFile()
        val file = directory.resolve("profile.yaml").apply {
            mkdir()
            resolve("child").writeText("content")
        }
        var rollbacks = 0

        val thrown = runCatching {
            ProfileDeletionPolicy.delete(
                file = file,
                shouldDeleteFile = true,
                persistDeletion = {},
                rollbackCatalog = { rollbacks += 1 },
            )
        }.exceptionOrNull()

        assertEquals("Failed to delete profile file: profile.yaml", thrown?.message)
        assertEquals(1, rollbacks)
        assertTrue(file.exists())
        assertTrue(file.resolve("child").exists())
    }

    @Test
    fun sharedFileOnlyCommitsCatalog() {
        val directory = Files.createTempDirectory("profile-delete").toFile()
        val file = directory.resolve("shared.yaml").apply { writeText("content") }

        ProfileDeletionPolicy.delete(
            file = file,
            shouldDeleteFile = false,
            persistDeletion = {},
            rollbackCatalog = { error("rollback must not run") },
        )

        assertTrue(file.exists())
    }
}
