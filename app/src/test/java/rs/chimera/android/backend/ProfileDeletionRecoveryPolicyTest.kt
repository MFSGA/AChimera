package rs.chimera.android.backend

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDeletionRecoveryPolicyTest {
    @Test
    fun referencedProfileRestoresMissingOriginalFile() {
        val directory = Files.createTempDirectory("profile-recovery").toFile()
        val original = directory.resolve("profile.yaml")
        val staged = directory.resolve(".profile.yaml.deleting").apply { writeText("content") }

        ProfileDeletionRecoveryPolicy.recover(directory, setOf(original.absolutePath))

        assertTrue(original.exists())
        assertEquals("content", original.readText())
        assertFalse(staged.exists())
    }

    @Test
    fun referencedProfileClearsStaleStageWhenOriginalExists() {
        val directory = Files.createTempDirectory("profile-recovery").toFile()
        val original = directory.resolve("profile.yaml").apply { writeText("current") }
        val staged = directory.resolve(".profile.yaml.deleting").apply { writeText("stale") }

        ProfileDeletionRecoveryPolicy.recover(directory, setOf(original.absolutePath))

        assertEquals("current", original.readText())
        assertFalse(staged.exists())
    }

    @Test
    fun unreferencedProfileCompletesStagedDeletion() {
        val directory = Files.createTempDirectory("profile-recovery").toFile()
        val staged = directory.resolve(".profile.yaml.deleting").apply { writeText("content") }

        ProfileDeletionRecoveryPolicy.recover(directory, emptySet())

        assertFalse(staged.exists())
        assertFalse(directory.resolve("profile.yaml").exists())
    }

    @Test
    fun unrelatedFilesAreLeftUntouched() {
        val directory = Files.createTempDirectory("profile-recovery").toFile()
        val profile = directory.resolve("profile.yaml").apply { writeText("content") }
        val partial = directory.resolve("profile.yaml.download").apply { writeText("partial") }

        ProfileDeletionRecoveryPolicy.recover(directory, emptySet())

        assertTrue(profile.exists())
        assertTrue(partial.exists())
    }

    @Test
    fun failedCleanupReportsStagedFile() {
        val directory = Files.createTempDirectory("profile-recovery").toFile()
        val staged = directory.resolve(".profile.yaml.deleting").apply {
            mkdir()
            resolve("child").writeText("content")
        }

        val failure = runCatching {
            ProfileDeletionRecoveryPolicy.recover(directory, emptySet())
        }.exceptionOrNull()

        assertEquals(
            "Failed to clear staged profile file: .profile.yaml.deleting",
            failure?.message,
        )
        assertTrue(staged.exists())
    }
}
