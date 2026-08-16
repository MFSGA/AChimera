package rs.chimera.android.backend

internal object ProfileAutoUpdateScheduleRetry {
    fun run(attempt: () -> Boolean): Boolean {
        if (attempt()) return true
        return attempt()
    }
}
