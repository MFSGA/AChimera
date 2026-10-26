package rs.chimera.android.service

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyProviderDirectoryMigratorTest {
    @Test
    fun copiesLegacyFilesWithoutDeletingSourceOrOverwritingProfileFiles() {
        val legacyWorkDirectory = Files.createTempDirectory("legacy-work").toFile()
        val legacyProviders = legacyWorkDirectory.resolve("providers").apply { mkdirs() }
        legacyProviders.resolve("shared.yaml").writeText("legacy")
        legacyProviders.resolve("nested").mkdirs()
        legacyProviders.resolve("nested/data.db").writeText("nested")
        val profileWorkDirectory = Files.createTempDirectory("profile-work").toFile()
        val profileProviders = profileWorkDirectory.resolve("providers").apply { mkdirs() }
        profileProviders.resolve("shared.yaml").writeText("profile")

        val result = LegacyProviderDirectoryMigrator.prepare(legacyWorkDirectory, profileWorkDirectory)

        assertEquals("profile", result.resolve("shared.yaml").readText())
        assertEquals("nested", result.resolve("nested/data.db").readText())
        assertEquals("legacy", legacyProviders.resolve("shared.yaml").readText())
        assertTrue(legacyProviders.resolve("nested/data.db").isFile)
    }

    @Test
    fun copiesOnlyOnceAfterSuccessfulMigration() {
        val legacyWorkDirectory = Files.createTempDirectory("legacy-work").toFile()
        val legacyProviders = legacyWorkDirectory.resolve("providers").apply { mkdirs() }
        legacyProviders.resolve("first.yaml").writeText("first")
        val profileWorkDirectory = Files.createTempDirectory("profile-work").toFile()

        LegacyProviderDirectoryMigrator.prepare(legacyWorkDirectory, profileWorkDirectory)
        legacyProviders.resolve("later.yaml").writeText("later")
        val result = LegacyProviderDirectoryMigrator.prepare(legacyWorkDirectory, profileWorkDirectory)

        assertTrue(result.resolve("first.yaml").isFile)
        assertFalse(result.resolve("later.yaml").exists())
        assertTrue(legacyProviders.resolve("later.yaml").isFile)
    }

    @Test
    fun rejectsLegacyProviderSymlinkThatEscapesTheOldRuntimeDirectory() {
        val legacyWorkDirectory = Files.createTempDirectory("legacy-work")
        val legacyProviders = Files.createDirectory(legacyWorkDirectory.resolve("providers"))
        val privateFile = legacyWorkDirectory.resolve("private.yaml").toFile().apply { writeText("secret") }
        Files.createSymbolicLink(legacyProviders.resolve("outside"), privateFile.toPath())
        val profileWorkDirectory = Files.createTempDirectory("profile-work").toFile()

        assertTrue(
            runCatching {
                LegacyProviderDirectoryMigrator.prepare(legacyWorkDirectory.toFile(), profileWorkDirectory)
            }.isFailure,
        )
        assertFalse(profileWorkDirectory.resolve("providers/.legacy-providers-copied").exists())
        assertEquals("secret", privateFile.readText())
    }
}
