package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class RuntimeProfilePathPolicyTest {
    @Test
    fun `existing profile path is trimmed and returned`() {
        val profile = Files.createTempFile("chimera-runtime-profile", ".yaml").toFile()

        assertEquals(
            profile.absolutePath,
            RuntimeProfilePathPolicy.requireAvailable(
                rawPath = "  ${profile.absolutePath}  ",
                unavailableMessage = "profile unavailable",
            ),
        )
    }

    @Test
    fun `missing profile error does not expose path`() {
        val sensitivePath = "/data/user/0/rs.chimera.android/files/private/customer-profile.yaml"

        val error = assertThrows(IllegalArgumentException::class.java) {
            RuntimeProfilePathPolicy.requireAvailable(
                rawPath = sensitivePath,
                unavailableMessage = "profile unavailable",
            )
        }

        assertEquals("profile unavailable", error.message)
        assertFalse(error.message.orEmpty().contains(sensitivePath))
    }

    @Test
    fun `blank profile path uses fixed unavailable message`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            RuntimeProfilePathPolicy.requireAvailable(
                rawPath = "   ",
                unavailableMessage = "profile unavailable",
            )
        }

        assertEquals("profile unavailable", error.message)
    }
}
