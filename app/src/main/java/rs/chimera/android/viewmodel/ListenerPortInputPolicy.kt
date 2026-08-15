package rs.chimera.android.viewmodel

import rs.chimera.android.service.PortPreference

internal object ListenerPortInputPolicy {
    fun parseRequired(value: String): UShort? = PortPreference.parse(value)

    fun parseOptional(value: String): UShort? =
        value.trim().takeIf { it.isNotEmpty() }?.let(PortPreference::parse)

    fun isOptionalValid(value: String): Boolean =
        value.isBlank() || parseOptional(value) != null
}
