package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ProfileFilePolicyTest {
    @Test
    fun successfulWriteKeepsImportedFile() {
        val directory = Files.createTempDirectory("chimera-profile").toFile()
        val file = directory.resolve("local.yaml")

        try {
            ProfileFilePolicy.writeOrRollback(file) { target -> target.writeText("mixed-port: 7890") }

            assertTrue(file.exists())
            assertEquals("mixed-port: 7890", file.readText())
        } finally {
            file.delete()
            directory.delete()
        }
    }

    @Test
    fun failedWriteDeletesPartialImportedFileAndRethrowsOriginalError() {
        val directory = Files.createTempDirectory("chimera-profile").toFile()
        val file = directory.resolve("local.yaml")
        val expected = IllegalStateException("copy failed")

        val actual = runCatching {
            ProfileFilePolicy.writeOrRollback(file) { target ->
                target.writeText("partial")
                throw expected
            }
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertFalse(file.exists())
        directory.delete()
    }

    @Test
    fun successfulCommitKeepsDownloadedFile() {
        val file = Files.createTempFile("chimera-profile", ".yaml").toFile()

        try {
            val result = ProfileFilePolicy.commitOrRollback(file) { "saved" }

            assertEquals("saved", result)
            assertTrue(file.exists())
        } finally {
            file.delete()
        }
    }

    @Test
    fun failedCommitDeletesDownloadedFileAndRethrowsOriginalError() {
        val file = Files.createTempFile("chimera-profile", ".yaml").toFile()
        val expected = IllegalStateException("catalog write failed")

        val actual = runCatching {
            ProfileFilePolicy.commitOrRollback(file) { throw expected }
        }.exceptionOrNull()

        assertSame(expected, actual)
        assertFalse(file.exists())
    }

    @Test
    fun deletingMissingPartialFileDoesNotMaskOriginalError() {
        val file = Files.createTempFile("chimera-profile", ".yaml").toFile()
        file.delete()
        val expected = IllegalStateException("download failed")

        ProfileFilePolicy.deleteAfterFailure(file, expected)

        assertTrue(expected.suppressed.isEmpty())
    }

    @Test
    fun cleanupFailureIsAttachedToOriginalError() {
        val directory = Files.createTempDirectory("chimera-profile").toFile()
        val child = directory.resolve("partial.yaml").apply { writeText("partial") }
        val expected = IllegalStateException("download failed")

        try {
            ProfileFilePolicy.deleteAfterFailure(directory, expected)

            assertEquals(1, expected.suppressed.size)
            assertTrue(expected.suppressed.single().message.orEmpty().contains(directory.name))
        } finally {
            child.delete()
            directory.delete()
        }
    }
}
