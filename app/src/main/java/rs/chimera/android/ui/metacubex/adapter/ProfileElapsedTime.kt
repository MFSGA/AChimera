package rs.chimera.android.ui.metacubex.adapter

import java.util.concurrent.TimeUnit

internal data class ProfileElapsedTime(
    val unit: Unit,
    val amount: Long,
) {
    enum class Unit {
        RECENTLY,
        MINUTES,
        HOURS,
        DAYS,
    }
}

internal fun profileElapsedTime(
    lastUpdatedAt: Long?,
    now: Long = System.currentTimeMillis(),
): ProfileElapsedTime? {
    if (lastUpdatedAt == null || lastUpdatedAt <= 0L) return null

    val elapsed = (now - lastUpdatedAt).coerceAtLeast(0L)
    val days = TimeUnit.MILLISECONDS.toDays(elapsed)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    return when {
        days > 0 -> ProfileElapsedTime(ProfileElapsedTime.Unit.DAYS, days)
        hours > 0 -> ProfileElapsedTime(ProfileElapsedTime.Unit.HOURS, hours)
        minutes > 0 -> ProfileElapsedTime(ProfileElapsedTime.Unit.MINUTES, minutes)
        else -> ProfileElapsedTime(ProfileElapsedTime.Unit.RECENTLY, 0L)
    }
}
