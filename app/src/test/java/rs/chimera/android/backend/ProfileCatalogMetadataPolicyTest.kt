package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileCatalogMetadataPolicyTest {
    @Test
    fun missingLegacyMetadataUsesSafeFallbacks() {
        val metadata = ProfileCatalogMetadataPolicy.resolve(
            storedCreatedAt = null,
            storedLastUpdated = null,
            storedFileSize = null,
            actualFileSize = 123L,
        )

        assertEquals(0L, metadata.createdAt)
        assertNull(metadata.lastUpdated)
        assertEquals(123L, metadata.fileSize)
    }

    @Test
    fun storedMetadataWinsOverCurrentFileSize() {
        val metadata = ProfileCatalogMetadataPolicy.resolve(
            storedCreatedAt = 10L,
            storedLastUpdated = 20L,
            storedFileSize = 30L,
            actualFileSize = 40L,
        )

        assertEquals(10L, metadata.createdAt)
        assertEquals(20L, metadata.lastUpdated)
        assertEquals(30L, metadata.fileSize)
    }

    @Test
    fun invalidNegativeMetadataIsNormalized() {
        val metadata = ProfileCatalogMetadataPolicy.resolve(
            storedCreatedAt = -1L,
            storedLastUpdated = -1L,
            storedFileSize = -1L,
            actualFileSize = -1L,
        )

        assertEquals(0L, metadata.createdAt)
        assertNull(metadata.lastUpdated)
        assertEquals(0L, metadata.fileSize)
    }
}
