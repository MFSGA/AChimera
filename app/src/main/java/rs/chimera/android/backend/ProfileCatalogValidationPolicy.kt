package rs.chimera.android.backend

internal object ProfileCatalogValidationPolicy {
    fun validate(entries: List<ProfileCatalogEntry>): List<ProfileCatalogEntry> {
        entries.forEachIndexed { index, entry ->
            requireField(entry.id.isNotBlank(), index, "id")
            requireField(entry.name.isNotBlank(), index, "name")
            requireField(entry.filePath.isNotBlank(), index, "filePath")
        }

        val duplicateId = entries
            .groupingBy(ProfileCatalogEntry::id)
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        check(duplicateId == null) { "Profile catalog contains duplicate id: $duplicateId" }

        return entries
    }

    private fun requireField(valid: Boolean, index: Int, field: String) {
        check(valid) { "Profile catalog entry $index has blank $field" }
    }
}
