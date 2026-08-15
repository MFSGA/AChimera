package rs.chimera.android.ui

import android.content.Context
import rs.chimera.android.R
import java.text.DateFormat
import java.util.Date

enum class ProfileAutoUpdateStatus {
    WAITING,
    SUCCESS,
    RETRY,
}

data class ProfileAutoUpdatePresentation(
    val status: ProfileAutoUpdateStatus,
    val lastAttempt: Long?,
    val failureCount: Int,
    val nextAttemptAt: Long?,
    val error: String?,
)

fun resolveProfileAutoUpdatePresentation(
    autoUpdate: Boolean,
    lastAttempt: Long?,
    failureCount: Int,
    nextAttemptAt: Long?,
    error: String?,
): ProfileAutoUpdatePresentation? {
    if (!autoUpdate) return null
    val failures = failureCount.coerceAtLeast(0)
    return ProfileAutoUpdatePresentation(
        status = when {
            failures > 0 -> ProfileAutoUpdateStatus.RETRY
            lastAttempt != null -> ProfileAutoUpdateStatus.SUCCESS
            else -> ProfileAutoUpdateStatus.WAITING
        },
        lastAttempt = lastAttempt,
        failureCount = failures,
        nextAttemptAt = nextAttemptAt,
        error = error?.trim()?.takeIf(String::isNotEmpty),
    )
}

fun ProfileAutoUpdatePresentation.format(context: Context): String {
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    return when (status) {
        ProfileAutoUpdateStatus.WAITING ->
            context.getString(R.string.profile_auto_update_waiting)

        ProfileAutoUpdateStatus.SUCCESS ->
            context.getString(
                R.string.profile_auto_update_last_success,
                dateFormat.format(Date(requireNotNull(lastAttempt))),
            )

        ProfileAutoUpdateStatus.RETRY -> {
            val retryAt = nextAttemptAt?.let { dateFormat.format(Date(it)) }
                ?: context.getString(R.string.not_available)
            val reason = error ?: context.getString(R.string.profile_unknown_error)
            context.getString(
                R.string.profile_auto_update_retry,
                failureCount,
                retryAt,
                reason,
            )
        }
    }
}
