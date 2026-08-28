package rs.chimera.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.backend.PROFILE_AUTO_UPDATE_BOUND_STATE_REQUIRED_KEY
import rs.chimera.android.backend.ProfileAutoUpdatePolicy
import rs.chimera.android.backend.ProfileAutoUpdateState
import rs.chimera.android.backend.ProfileAutoUpdateStateStore
import rs.chimera.android.backend.ProfileCatalogCoordinator
import rs.chimera.android.backend.ProfileCatalogReader
import rs.chimera.android.backend.ProfileCatalogStore

@RunWith(AndroidJUnit4::class)
class ProfileAutoUpdateCatalogInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val catalogPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val stateStore = ProfileAutoUpdateStateStore(context)
    private val ids = mutableSetOf<String>()

    @After
    fun tearDown() {
        catalogPrefs.edit().clear().commit()
        ids.forEach(stateStore::clear)
    }

    @Test
    fun invalidatedSourceRejectsLegacyFutureBackoff() {
        val id = trackedId("invalidated")
        seedCatalog(id = id, requireBoundState = true)
        stateStore.write(id, legacyFutureBackoff())

        val profile = readProfile()

        assertNull(profile.nextAutoUpdateAt)
        assertEquals(
            listOf(profile),
            ProfileAutoUpdatePolicy.eligibleProfiles(listOf(profile), now = NOW),
        )
    }

    @Test
    fun invalidatedSourceStillUsesCurrentBoundState() {
        val id = trackedId("invalidated-bound")
        seedCatalog(id = id, requireBoundState = true)
        val profile = readProfile()
        stateStore.write(
            id,
            ProfileAutoUpdatePolicy.bindStateToSource(
                profile,
                legacyFutureBackoff().copy(runtimeApplyPending = true),
            ),
        )

        val refreshedProfile = readProfile()

        assertEquals(FUTURE_BACKOFF, refreshedProfile.nextAutoUpdateAt)
        assertEquals(true, refreshedProfile.runtimeApplyPending)
        assertEquals(
            emptyList<Any>(),
            ProfileAutoUpdatePolicy.eligibleProfiles(listOf(refreshedProfile), now = NOW),
        )
    }

    @Test
    fun legacyCatalogStillUsesLegacyFutureBackoff() {
        val id = trackedId("legacy")
        seedCatalog(id = id, requireBoundState = false)
        stateStore.write(id, legacyFutureBackoff())

        val profile = readProfile()

        assertEquals(FUTURE_BACKOFF, profile.nextAutoUpdateAt)
        assertEquals(
            emptyList<Any>(),
            ProfileAutoUpdatePolicy.eligibleProfiles(listOf(profile), now = NOW),
        )
    }

    private fun seedCatalog(id: String, requireBoundState: Boolean) {
        val file = context.cacheDir.resolve("$id.yaml").apply { writeText("mixed-port: 7890") }
        val profile = JSONObject()
            .put("id", id)
            .put("name", id)
            .put("filePath", file.absolutePath)
            .put("type", "REMOTE")
            .put("isActive", true)
            .put("url", "https://new.example/profile.yaml")
            .put("autoUpdate", true)
        if (requireBoundState) {
            profile.put(PROFILE_AUTO_UPDATE_BOUND_STATE_REQUIRED_KEY, true)
        }
        catalogPrefs.edit()
            .putString("profiles_list", JSONArray().put(profile).toString())
            .putString("profile_path", file.absolutePath)
            .commit()
    }

    private fun readProfile() =
        ProfileCatalogReader(
            catalogStore = ProfileCatalogStore(catalogPrefs, ProfileCatalogCoordinator()),
            autoUpdateStateStore = stateStore,
        ).readProfiles().single()

    private fun legacyFutureBackoff() =
        ProfileAutoUpdateState(
            lastAttempt = NOW,
            failureCount = 2,
            nextAttemptAt = FUTURE_BACKOFF,
            lastError = "IOException",
        )

    private fun trackedId(label: String): String =
        "catalog-bound-state-$label-${System.nanoTime()}".also(ids::add)

    private companion object {
        const val PREFS_NAME = "profile_auto_update_catalog_test"
        const val NOW = 1_000L
        const val FUTURE_BACKOFF = 10_000L
    }
}
