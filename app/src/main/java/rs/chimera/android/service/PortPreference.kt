package rs.chimera.android.service

internal object PortPreference {
    fun parse(value: Any?): UShort? {
        val port =
            when (value) {
                is Int -> value.toLong()
                is Long -> value
                is String -> value.trim().toLongOrNull()
                else -> null
            } ?: return null

        return port
            .takeIf { it in MIN_PORT..MAX_PORT }
            ?.toUShort()
    }

    private const val MIN_PORT = 1L
    private const val MAX_PORT = 65_535L
}
