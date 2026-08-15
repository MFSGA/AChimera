package rs.chimera.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.ffi.ChimeraFfi
import uniffi.chimera_ffi.hello

@RunWith(AndroidJUnit4::class)
class NativeBridgeInstrumentedTest {
    @Test
    fun nativeLibraryAndUniffiBindingsInitialize() {
        ChimeraFfi.ensureInitialized()

        val status = hello()

        assertTrue("Unexpected native status: $status", status.startsWith("ffi: "))
    }
}
