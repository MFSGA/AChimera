package rs.chimera.android.viewmodel

internal object AppFilterModePreference {
    fun parse(value: String?): AppFilterMode =
        value
            ?.let { stored -> AppFilterMode.entries.firstOrNull { it.name == stored } }
            ?: AppFilterMode.ALL
}
