package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProviderSnapshot

class ProviderDisplayPolicyTest {
    @Test
    fun compatibleProvidersAreExcludedLikeCmfa() {
        assertFalse(provider(vehicleType = "Compatible").let(ProviderDisplayPolicy::isVisible))
        assertFalse(provider(vehicleType = "compatible").let(ProviderDisplayPolicy::isVisible))
    }

    @Test
    fun fileHttpAndInlineProvidersRemainVisible() {
        listOf("File", "HTTP", "Inline").forEach { vehicleType ->
            assertTrue(provider(vehicleType).let(ProviderDisplayPolicy::isVisible))
        }
    }

    @Test
    fun ruleProvidersUseTheSameCompatibleFilter() {
        assertTrue(provider(vehicleType = "HTTP", type = "Rule").let(ProviderDisplayPolicy::isVisible))
        assertFalse(provider(vehicleType = "Compatible", type = "Rule").let(ProviderDisplayPolicy::isVisible))
    }

    private fun provider(vehicleType: String, type: String = "Proxy") = ProviderSnapshot(
        name = "test",
        type = type,
        vehicleType = vehicleType,
        updatedAtMillis = null,
    )
}
