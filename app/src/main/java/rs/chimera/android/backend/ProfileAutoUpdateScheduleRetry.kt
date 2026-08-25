package rs.chimera.android.backend

internal object ProfileAutoUpdateScheduleRetry {
    fun run(attempt: () -> Boolean): Boolean {
        if (attempt()) return true
        return attempt()
    }

    @Synchronized
    fun <T : Any> replaceCurrent(
        loadPrevious: () -> T?,
        cancelPrevious: () -> Unit,
        scheduleReplacement: () -> Boolean,
        restorePrevious: (T) -> Boolean,
    ): Boolean {
        val previous = loadPrevious()
        cancelPrevious()
        if (run(scheduleReplacement)) return true
        if (previous != null) run { restorePrevious(previous) }
        return false
    }
}
