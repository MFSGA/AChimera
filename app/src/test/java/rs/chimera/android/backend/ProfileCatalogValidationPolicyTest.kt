package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileCatalogValidationPolicyTest {
    @Test
    fun acceptsValidEntriesWithoutChangingOrder() {
        val entries = listOf(
            ProfileCatalogEntry("first", "First", "/profiles/first.yaml", true),
            ProfileCatalogEntry("second", "Second", "/profiles/second.yaml", false),
        )

        assertEquals(entries, ProfileCatalogValidationPolicy.validate(entries))
    }

    @Test
    fun rejectsBlankRequiredFields() {
        listOf(
            ProfileCatalogEntry("", "Name", "/profiles/a.yaml", false) to "id",
            ProfileCatalogEntry("id", "", "/profiles/a.yaml", false) to "name",
            ProfileCatalogEntry("id", "Name", "", false) to "filePath",
        ).forEach { (entry, field) ->
            val error = assertThrows(IllegalStateException::class.java) {
                ProfileCatalogValidationPolicy.validate(listOf(entry))
            }
            assertEquals("Profile catalog entry 0 has blank $field", error.message)
        }
    }

    @Test
    fun rejectsDuplicateIds() {
        val error = assertThrows(IllegalStateException::class.java) {
            ProfileCatalogValidationPolicy.validate(
                listOf(
                    ProfileCatalogEntry("same", "First", "/profiles/first.yaml", true),
                    ProfileCatalogEntry("same", "Second", "/profiles/second.yaml", false),
                ),
            )
        }

        assertEquals("Profile catalog contains duplicate id: same", error.message)
    }
}
