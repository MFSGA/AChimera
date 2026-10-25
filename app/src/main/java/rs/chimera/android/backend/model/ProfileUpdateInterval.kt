package rs.chimera.android.backend.model

/** Positive, whole-minute interval; the existing autoUpdate flag owns enablement. */
object ProfileUpdateInterval {
    const val MINUTE_MILLIS = 60_000L
    const val MIN_MILLIS = 15L * MINUTE_MILLIS
    const val DEFAULT_MILLIS = 24L * 60L * MINUTE_MILLIS

    fun requireValid(value: Long): Long {
        require(value >= MIN_MILLIS && value % MINUTE_MILLIS == 0L) {
            "Profile update interval must be whole minutes and at least 15 minutes"
        }
        return value
    }

    /** Empty is disabled (zero); null means invalid, including conversion overflow. */
    fun parseMinutes(value: String): Long? {
        if (value.isEmpty()) return 0L
        val minutes = value.toLongOrNull() ?: return null
        if (minutes < 15 || minutes > Long.MAX_VALUE / MINUTE_MILLIS) return null
        return minutes * MINUTE_MILLIS
    }

    fun dueAt(updatedAt: Long, intervalMillis: Long): Long =
        if (updatedAt > Long.MAX_VALUE - intervalMillis) Long.MAX_VALUE else updatedAt + intervalMillis
}
