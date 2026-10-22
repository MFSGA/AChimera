package rs.chimera.android.backend

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDuplicatePolicyTest {
    @Test
    fun managedProfileFileIsAccepted() {
        val directory = Files.createTempDirectory("profiles").toFile()
        val source = directory.resolve("source.yaml").apply { writeText("profile") }

        assertEquals(
            source.canonicalFile,
            ProfileDuplicatePolicy.requireManagedSource(directory, source.path),
        )
    }

    @Test
    fun profileOutsideAppDirectoryIsRejected() {
        val root = Files.createTempDirectory("profiles").toFile()
        val outsideDirectory = Files.createTempDirectory("outside-profiles").toFile()
        val source = outsideDirectory.resolve("source.yaml").apply { writeText("profile") }

        assertTrue(
            runCatching {
                ProfileDuplicatePolicy.requireManagedSource(root, source.path)
            }.isFailure,
        )
    }

    @Test
    fun missingAndOversizedProfileFilesAreRejected() {
        val directory = Files.createTempDirectory("profiles").toFile()
        val missing = directory.resolve("missing.yaml")
        val oversized = directory.resolve("large.yaml").apply {
            writeBytes(ByteArray((ProfileImportPolicy.MAX_PROFILE_BYTES + 1).toInt()))
        }

        assertTrue(runCatching {
            ProfileDuplicatePolicy.requireManagedSource(directory, missing.path)
        }.isFailure)
        assertTrue(runCatching {
            ProfileDuplicatePolicy.requireManagedSource(directory, oversized.path)
        }.isFailure)
    }
}
