package rs.chimera.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.StartVpnResult

@RunWith(AndroidJUnit4::class)
class VpnPreconditionInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearActiveProfile() {
        Global.updateProfilePath("")
    }

    @Test
    fun vpnCannotStartWithoutAnActiveProfile() = runBlocking {
        val result = BackendProvider.provide().prepareStartVpn()

        assertTrue(result is StartVpnResult.Error)
        assertEquals(
            context.getString(R.string.service_profile_required),
            (result as StartVpnResult.Error).message,
        )
    }
}
