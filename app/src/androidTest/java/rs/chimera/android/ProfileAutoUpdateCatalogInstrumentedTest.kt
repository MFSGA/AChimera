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
import rs.chimera.android.backend.model.RemoteProfileSettings
import rs.chimera.android.backend.model.ProfileUpdateInterval
import rs.chimera.android.backend.model.ProfileSubscriptionUsage
import rs.chimera.android.backend.model.toProfileSubscriptionUsage
import uniffi.chimera_ffi.DownloadResult
import uniffi.chimera_ffi.SubscriptionInfo

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
    fun legacyIntervalDefaultsToDailyAndCustomIntervalRoundTripsWithoutChangingSource() {
        val id = trackedId("interval")
        seedCatalog(id, requireBoundState = false)
        val before = readProfile()
        assertEquals(ProfileUpdateInterval.DEFAULT_MILLIS, before.autoUpdateIntervalMillis)
        val store = ProfileCatalogStore(catalogPrefs, ProfileCatalogCoordinator())
        val interval = 30L * 60_000
        store.updateRemoteProfileSettings(
            id,
            RemoteProfileSettings(before.name, before.url!!, true, autoUpdateIntervalMillis = interval),
        )
        val after = readProfile()
        assertEquals(interval, after.autoUpdateIntervalMillis)
        assertEquals(interval, store.readRemoteProfile(id).autoUpdateIntervalMillis)
        assertEquals(before.url, after.url)
        assertEquals(before.lastUpdated, after.lastUpdated)
        store.updateRemoteProfileSettings(
            id,
            RemoteProfileSettings(before.name, before.url!!, false, autoUpdateIntervalMillis = interval),
        )
        assertEquals(false, readProfile().autoUpdate)
        assertEquals(interval, readProfile().autoUpdateIntervalMillis)
    }

    @Test
    fun invalidStoredIntervalFailsExplicitlyWithoutResettingTheCatalog() {
        val id = trackedId("bad-interval")
        seedCatalog(id, requireBoundState = false)
        val catalog = JSONArray(catalogPrefs.getString("profiles_list", null))
        catalog.getJSONObject(0).put("autoUpdateIntervalMillis", 1L)
        val serialized = catalog.toString()
        catalogPrefs.edit().putString("profiles_list", serialized).commit()
        assertEquals(true, runCatching { readProfile() }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(serialized, catalogPrefs.getString("profiles_list", null))
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

    @Test
    fun remoteUpdatePersistsSubscriptionUsageForProfileSummary() {
        val id = trackedId("subscription-usage")
        seedCatalog(id = id, requireBoundState = false)
        val file = context.cacheDir.resolve("$id.yaml")
        val result = DownloadResult(
            success = true,
            fileSize = 42u,
            errorMessage = null,
            subscriptionInfo = SubscriptionInfo(
                upload = 120u,
                download = 340u,
                total = 1_000u,
                expire = 1_900_000_000u,
            ),
        )
        val usage = checkNotNull(result.toProfileSubscriptionUsage())
        val store = ProfileCatalogStore(catalogPrefs, ProfileCatalogCoordinator())

        assertEquals(true, store.updateRemoteProfileMetadata(id, file, null, NOW, usage))

        val profile = readProfile()
        assertEquals(120L, profile.subscriptionUploadBytes)
        assertEquals(340L, profile.subscriptionDownloadBytes)
        assertEquals(1_000L, profile.subscriptionTotalBytes)
        assertEquals(1_900_000_000_000L, profile.subscriptionExpiresAtMillis)

        store.updateRemoteProfileMetadata(
            id,
            file,
            null,
            NOW + 1L,
            ProfileSubscriptionUsage(
                uploadBytes = null,
                downloadBytes = 560L,
                totalBytes = null,
                expiresAtMillis = null,
            ),
        )

        val profileAfterPartialHeader = readProfile()
        assertEquals(120L, profileAfterPartialHeader.subscriptionUploadBytes)
        assertEquals(560L, profileAfterPartialHeader.subscriptionDownloadBytes)
        assertEquals(1_000L, profileAfterPartialHeader.subscriptionTotalBytes)
        assertEquals(1_900_000_000_000L, profileAfterPartialHeader.subscriptionExpiresAtMillis)

        store.updateRemoteProfileMetadata(id, file, null, NOW + 2L, subscriptionUsage = null)

        val profileAfterHeaderlessUpdate = readProfile()
        assertEquals(120L, profileAfterHeaderlessUpdate.subscriptionUploadBytes)
        assertEquals(560L, profileAfterHeaderlessUpdate.subscriptionDownloadBytes)
        assertEquals(1_000L, profileAfterHeaderlessUpdate.subscriptionTotalBytes)
        assertEquals(1_900_000_000_000L, profileAfterHeaderlessUpdate.subscriptionExpiresAtMillis)
    }

    @Test
    fun changingEachRemoteSourceFieldClearsSavedSubscriptionUsage() {
        val baseSettings = RemoteProfileSettings(
            name = "unused",
            url = "https://new.example/profile.yaml",
            autoUpdate = true,
            userAgent = "old-agent",
            proxyUrl = "https://proxy.example",
        )
        val changedSettings = listOf(
            baseSettings.copy(url = "https://new.example/changed.yaml"),
            baseSettings.copy(userAgent = "new-agent"),
            baseSettings.copy(proxyUrl = "https://new-proxy.example"),
        )

        changedSettings.forEachIndexed { index, settings ->
            val id = trackedId("subscription-source-change-$index")
            seedCatalog(id = id, requireBoundState = false)
            val profile = JSONArray(checkNotNull(catalogPrefs.getString("profiles_list", null)))
                .getJSONObject(0)
                .put("userAgent", "old-agent")
                .put("proxyUrl", "https://proxy.example")
                .put("subscriptionUploadBytes", 120L)
                .put("subscriptionDownloadBytes", 340L)
                .put("subscriptionTotalBytes", 1_000L)
                .put("subscriptionExpiresAtMillis", 1_900_000_000_000L)
            catalogPrefs.edit()
                .putString("profiles_list", JSONArray().put(profile).toString())
                .commit()

            ProfileCatalogStore(catalogPrefs, ProfileCatalogCoordinator())
                .updateRemoteProfileSettings(id, settings.copy(name = id))

            val updatedProfile = readProfile()
            assertNull(updatedProfile.subscriptionUploadBytes)
            assertNull(updatedProfile.subscriptionDownloadBytes)
            assertNull(updatedProfile.subscriptionTotalBytes)
            assertNull(updatedProfile.subscriptionExpiresAtMillis)
        }
    }

    @Test
    fun changingNonSourceSettingsPreservesSavedSubscriptionUsage() {
        val id = trackedId("subscription-nonsource-change")
        seedCatalog(id = id, requireBoundState = false)
        val profile = JSONArray(checkNotNull(catalogPrefs.getString("profiles_list", null)))
            .getJSONObject(0)
            .put("userAgent", "same-agent")
            .put("proxyUrl", "https://proxy.example")
            .put("subscriptionUploadBytes", 120L)
            .put("subscriptionDownloadBytes", 340L)
            .put("subscriptionTotalBytes", 1_000L)
            .put("subscriptionExpiresAtMillis", 1_900_000_000_000L)
        catalogPrefs.edit()
            .putString("profiles_list", JSONArray().put(profile).toString())
            .commit()
        val store = ProfileCatalogStore(catalogPrefs, ProfileCatalogCoordinator())

        store.updateRemoteProfileSettings(
            id,
            RemoteProfileSettings(
                name = "Renamed",
                url = "https://new.example/profile.yaml",
                autoUpdate = false,
                userAgent = "same-agent",
                proxyUrl = "https://proxy.example",
            ),
        )

        val updatedProfile = readProfile()
        assertEquals(120L, updatedProfile.subscriptionUploadBytes)
        assertEquals(340L, updatedProfile.subscriptionDownloadBytes)
        assertEquals(1_000L, updatedProfile.subscriptionTotalBytes)
        assertEquals(1_900_000_000_000L, updatedProfile.subscriptionExpiresAtMillis)
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
