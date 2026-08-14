package rs.chimera.android.backend

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileImportPolicyTest {
    @Test
    fun copyWithLimitCopiesAcceptedProfile() {
        val content = "mixed-port: 7890\n".toByteArray()
        val output = ByteArrayOutputStream()

        val copied = ProfileImportPolicy.copyWithLimit(
            input = ByteArrayInputStream(content),
            output = output,
            maxBytes = content.size.toLong(),
        )

        assertEquals(content.size.toLong(), copied)
        assertArrayEquals(content, output.toByteArray())
    }

    @Test
    fun copyWithLimitRejectsOversizedProfile() {
        val error = assertThrows(IllegalStateException::class.java) {
            ProfileImportPolicy.copyWithLimit(
                input = ByteArrayInputStream(ByteArray(9)),
                output = ByteArrayOutputStream(),
                maxBytes = 8,
            )
        }

        assertEquals("Profile exceeds maximum size of 8 bytes", error.message)
    }

    @Test
    fun requireWithinLimitAcceptsBoundarySize() {
        val file = File.createTempFile("profile-import-policy", ".yaml").apply {
            writeBytes(ByteArray(8))
        }
        try {
            ProfileImportPolicy.requireWithinLimit(file, maxBytes = 8)
        } finally {
            file.delete()
        }
    }

    @Test
    fun requireWithinLimitRejectsOversizedDownloadedFile() {
        val file = File.createTempFile("profile-import-policy", ".yaml").apply {
            writeBytes(ByteArray(9))
        }
        try {
            val error = assertThrows(IllegalStateException::class.java) {
                ProfileImportPolicy.requireWithinLimit(file, maxBytes = 8)
            }
            assertEquals("Profile exceeds maximum size of 8 bytes", error.message)
        } finally {
            file.delete()
        }
    }
}
