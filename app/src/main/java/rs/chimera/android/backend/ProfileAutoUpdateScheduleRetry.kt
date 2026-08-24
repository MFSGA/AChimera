package rs.chimera.android.backend

internal object ProfileAutoUpdateScheduleRetry {
    fun run(attempt: () -> Boolean): Boolean {
        if (attempt()) return true
        return attempt()
    }

    fun <T : Any> replace(
        previous: T?,
        cancelPrevious: () -> Unit,
        scheduleReplacement: () -> Boolean,
        restorePrevious: (T) -> Boolean,
    ): Boolean {
        cancelPrevious()
        if (run(scheduleReplacement)) return true
        if (previous != null) run { restorePrevious(previous) }
        return false
    }
}
