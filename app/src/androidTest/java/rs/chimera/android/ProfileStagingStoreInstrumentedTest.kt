package rs.chimera.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.backend.ProfileBackupRecoveryPolicy
import rs.chimera.android.backend.ProfileCatalogCoordinator
import rs.chimera.android.backend.ProfileCatalogStore
import rs.chimera.android.backend.ProfileStagingStore
import rs.chimera.android.backend.profileImportPendingKey
import rs.chimera.android.backend.profileUpdatePendingKey

@RunWith(AndroidJUnit4::class)
class ProfileStagingStoreInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private lateinit var directory: File
    private lateinit var catalogStore: ProfileCatalogStore
    private lateinit var stagingStore: ProfileStagingStore

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        directory = context.cacheDir.resolve("profile-staging-${System.nanoTime()}").apply { mkdirs() }
        val coordinator = ProfileCatalogCoordinator()
        catalogStore = ProfileCatalogStore(prefs, coordinator)
        stagingStore = ProfileStagingStore(
            profilePrefs = prefs,
            filesDir = directory,
            catalogCoordinator = coordinator,
            catalogStore = catalogStore,
        )
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        directory.deleteRecursively()
    }

    @Test
    fun falseImportMarkerDoesNotDeleteDestination() {
        val destination = directory.resolve("profile.yaml").apply { writeText("current") }
        val markerKey = profileImportPendingKey(destination.name)
        prefs.edit().putBoolean(markerKey, false).commit()

        stagingStore.recoverImports()

        assertTrue(destination.isFile)
        assertEquals("current", destination.readText())
        assertFalse(prefs.contains(markerKey))
    }

    @Test
    fun committedUpdateClearsOlderMarkersForSameTarget() {
        val targetName = "profile.yaml"
        val oldBackup = ".$targetName.${UUID.randomUUID()}.backup"
        val currentBackup = ".$targetName.${UUID.randomUUID()}.backup"
        val otherBackup = ".other.yaml.${UUID.randomUUID()}.backup"
        val oldKey = profileUpdatePendingKey(oldBackup)
        val currentKey = profileUpdatePendingKey(currentBackup)
        val otherKey = profileUpdatePendingKey(otherBackup)
        prefs.edit()
            .putBoolean(oldKey, true)
            .putBoolean(currentKey, true)
            .putBoolean(otherKey, true)
            .commit()

        catalogStore.commitUpdate(
            backup = directory.resolve(currentBackup),
            targetName = targetName,
        ) {}

        assertFalse(prefs.contains(oldKey))
        assertFalse(prefs.contains(currentKey))
        assertTrue(prefs.contains(otherKey))
    }

    @Test
    fun committedUpdateLeavesOldBackupForCleanupWithoutRestoringIt() {
        val target = directory.resolve("profile.yaml").apply { writeText("latest") }
        val oldBackup = directory.resolve(".${target.name}.${UUID.randomUUID()}.backup").apply {
            writeText("old")
        }
        val currentBackup = directory.resolve(".${target.name}.${UUID.randomUUID()}.backup").apply {
            writeText("previous")
        }
        val oldKey = profileUpdatePendingKey(oldBackup.name)
        val currentKey = profileUpdatePendingKey(currentBackup.name)
        prefs.edit()
            .putBoolean(oldKey, true)
            .putBoolean(currentKey, true)
            .commit()

        catalogStore.commitUpdate(
            backup = currentBackup,
            targetName = target.name,
        ) {}
        stagingStore.recoverBackups()

        assertEquals("latest", target.readText())
        assertFalse(oldBackup.exists())
        assertFalse(currentBackup.exists())
        assertFalse(prefs.contains(oldKey))
        assertFalse(prefs.contains(currentKey))
    }

    @Test
    fun multiplePendingBackupsRestoreNewestAndClearAllMarkers() {
        val target = directory.resolve("profile.yaml").apply { writeText("uncommitted-latest") }
        val olderBackup = directory.resolve(".${target.name}.${UUID.randomUUID()}.backup").apply {
            writeText("older-committed")
            assertTrue(setLastModified(1_000L))
        }
        val newerBackup = directory.resolve(".${target.name}.${UUID.randomUUID()}.backup").apply {
            writeText("newer-committed")
            assertTrue(setLastModified(2_000L))
        }
        val olderKey = profileUpdatePendingKey(olderBackup.name)
        val newerKey = profileUpdatePendingKey(newerBackup.name)
        prefs.edit()
            .putBoolean(olderKey, true)
            .putBoolean(newerKey, true)
            .commit()

        stagingStore.recoverBackups()

        assertEquals("newer-committed", target.readText())
        assertFalse(olderBackup.exists())
        assertFalse(newerBackup.exists())
        assertFalse(prefs.contains(olderKey))
        assertFalse(prefs.contains(newerKey))
    }

    @Test
    fun persistedBackupOrderWinsWhenPendingMtimesMatch() {
        val target = directory.resolve("profile.yaml").apply { writeText("uncommitted-latest") }
        val olderBackup = directory.resolve(".${target.name}.${UUID.randomUUID()}.100.backup").apply {
            writeText("older-committed")
            assertTrue(setLastModified(5_000L))
        }
        val newerBackup = directory.resolve(".${target.name}.${UUID.randomUUID()}.101.backup").apply {
            writeText("newer-committed")
            assertTrue(setLastModified(5_000L))
        }
        val olderKey = profileUpdatePendingKey(olderBackup.name)
        val newerKey = profileUpdatePendingKey(newerBackup.name)
        prefs.edit()
            .putBoolean(olderKey, true)
            .putBoolean(newerKey, true)
            .commit()

        stagingStore.recoverBackups()

        assertEquals("newer-committed", target.readText())
        assertFalse(olderBackup.exists())
        assertFalse(newerBackup.exists())
        assertFalse(prefs.contains(olderKey))
        assertFalse(prefs.contains(newerKey))
    }

    @Test
    fun orderedBackupCreatedAfterLegacyBackupWinsMixedRecovery() {
        val target = directory.resolve("profile.yaml").apply { writeText("current") }
        val legacyBackup = directory.resolve(".${target.name}.${UUID.randomUUID()}.backup").apply {
            writeText("legacy-committed")
            assertTrue(setLastModified(System.currentTimeMillis() + 5_000L))
        }
        val orderedBackup = ProfileBackupRecoveryPolicy.createBackupFile(target).apply {
            writeText("newer-committed")
            assertTrue(setLastModified(legacyBackup.lastModified()))
        }
        val legacyKey = profileUpdatePendingKey(legacyBackup.name)
        val orderedKey = profileUpdatePendingKey(orderedBackup.name)
        prefs.edit()
            .putBoolean(legacyKey, true)
            .putBoolean(orderedKey, true)
            .commit()

        stagingStore.recoverBackups()

        assertEquals("newer-committed", target.readText())
        assertFalse(legacyBackup.exists())
        assertFalse(orderedBackup.exists())
        assertFalse(prefs.contains(legacyKey))
        assertFalse(prefs.contains(orderedKey))
    }

    @Test
    fun legacyBackupStillRestoresByModificationTime() {
        val target = directory.resolve("profile.yaml").apply { writeText("uncommitted-latest") }
        val olderBackup = directory.resolve(".${target.name}.${UUID.randomUUID()}.backup").apply {
            writeText("older-committed")
            assertTrue(setLastModified(1_000L))
        }
        val newerBackup = directory.resolve(".${target.name}.${UUID.randomUUID()}.backup").apply {
            writeText("newer-committed")
            assertTrue(setLastModified(2_000L))
        }
        val olderKey = profileUpdatePendingKey(olderBackup.name)
        val newerKey = profileUpdatePendingKey(newerBackup.name)
        prefs.edit()
            .putBoolean(olderKey, true)
            .putBoolean(newerKey, true)
            .commit()

        stagingStore.recoverBackups()

        assertEquals("newer-committed", target.readText())
        assertFalse(olderBackup.exists())
        assertFalse(newerBackup.exists())
        assertFalse(prefs.contains(olderKey))
        assertFalse(prefs.contains(newerKey))
    }

    @Test
    fun missingPendingBackupClearsMarker() {
        val missingBackup = ".profile.yaml.${UUID.randomUUID()}.backup"
        val markerKey = profileUpdatePendingKey(missingBackup)
        prefs.edit().putBoolean(markerKey, true).commit()

        stagingStore.recoverBackups()

        assertFalse(directory.resolve(missingBackup).exists())
        assertFalse(prefs.contains(markerKey))
    }

    @Test
    fun falseUpdateMarkerDoesNotRestoreBackup() {
        val target = directory.resolve("profile.yaml").apply { writeText("current") }
        val backup = directory.resolve(".${target.name}.${UUID.randomUUID()}.backup").apply {
            writeText("old")
        }
        val markerKey = profileUpdatePendingKey(backup.name)
        prefs.edit().putBoolean(markerKey, false).commit()

        stagingStore.recoverBackups()

        assertEquals("current", target.readText())
        assertFalse(backup.exists())
        assertFalse(prefs.contains(markerKey))
    }

    private companion object {
        const val PREFS_NAME = "profile_staging_store_test"
    }
}
