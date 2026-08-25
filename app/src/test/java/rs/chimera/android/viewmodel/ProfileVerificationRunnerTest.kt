package rs.chimera.android.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class ProfileVerificationRunnerTest {
    @Test
    fun verifiesResolvedActiveProfilePath() = runBlocking {
        var verifiedPath: String? = null
        val runner = ProfileVerificationRunner(
            listProfiles = { listOf(profile(path = "/profiles/active.yaml", active = true)) },
            verifyProfile = { path ->
                verifiedPath = path
                Result.success("valid")
            },
        )

        val result = runner.run("/profiles/saved.yaml")

        assertEquals("/profiles/active.yaml", verifiedPath)
        assertEquals(ProfileVerificationExecutionResult.Verified("valid"), result)
    }

    @Test
    fun missingTargetDoesNotInvokeVerifier() = runBlocking {
        var verifyCalls = 0
        val runner = ProfileVerificationRunner(
            listProfiles = { emptyList() },
            verifyProfile = {
                verifyCalls += 1
                Result.success("unused")
            },
        )

        val result = runner.run(null)

        assertSame(ProfileVerificationExecutionResult.Missing, result)
        assertEquals(0, verifyCalls)
    }

    @Test
    fun verificationFailureIsReturned() = runBlocking {
        val expected = IllegalStateException("invalid")
        val runner = ProfileVerificationRunner(
            listProfiles = { listOf(profile(path = "/profiles/active.yaml", active = true)) },
            verifyProfile = { Result.failure(expected) },
        )

        val result = runner.run(null) as ProfileVerificationExecutionResult.Failed

        assertSame(expected, result.error)
    }

    @Test
    fun cancellationIsPropagated() = runBlocking {
        val expected = CancellationException("cancelled")
        val runner = ProfileVerificationRunner(
            listProfiles = { throw expected },
            verifyProfile = { Result.success("unused") },
        )

        try {
            runner.run(null)
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertSame(expected, error)
        }
    }

    private fun profile(
        path: String,
        active: Boolean,
    ) = ProfileSummary(
        id = "profile",
        name = "profile",
        filePath = path,
        type = ProfileType.LOCAL,
        isActive = active,
        isRemote = false,
        lastUpdated = null,
        fileSize = 0L,
    )
}
