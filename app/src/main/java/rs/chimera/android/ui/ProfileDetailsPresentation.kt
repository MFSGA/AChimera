package rs.chimera.android.ui

import android.content.Context
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.formatSize
import rs.chimera.android.model.Profile
import rs.chimera.android.model.ProfileType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun Profile.profileDetailsText(context: Context): String =
    profileDetailsText(
        context = context,
        name = name,
        filePath = filePath,
        createdAt = createdAt,
        remote = type == ProfileType.REMOTE,
        fileSize = fileSize,
        url = url,
        lastUpdated = lastUpdated,
        autoUpdate = autoUpdate,
        userAgent = userAgent,
        proxyUrl = proxyUrl,
    )

internal fun ProfileSummary.profileDetailsText(context: Context): String =
    profileDetailsText(
        context = context,
        name = name,
        filePath = filePath,
        createdAt = createdAt,
        remote = isRemote,
        fileSize = fileSize,
        url = url,
        lastUpdated = lastUpdated,
        autoUpdate = autoUpdate,
        userAgent = userAgent,
        proxyUrl = proxyUrl,
    )

private fun profileDetailsText(
    context: Context,
    name: String,
    filePath: String,
    createdAt: Long,
    remote: Boolean,
    fileSize: Long,
    url: String?,
    lastUpdated: Long?,
    autoUpdate: Boolean,
    userAgent: String?,
    proxyUrl: String?,
): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return buildList {
        add(context.getString(R.string.profile_details_name, name))
        add(
            context.getString(
                R.string.profile_details_type,
                context.getString(if (remote) R.string.profile_type_remote else R.string.profile_type_local),
            ),
        )
        add(context.getString(R.string.profile_details_path, filePath))
        add(context.getString(R.string.profile_details_size, formatSize(fileSize)))
        add(context.getString(R.string.profile_details_created, formatter.format(Date(createdAt))))
        url?.takeIf(String::isNotBlank)?.let {
            add(context.getString(R.string.profile_details_url, it))
        }
        lastUpdated?.let {
            add(context.getString(R.string.profile_last_updated, formatter.format(Date(it))))
        }
        add(
            context.getString(
                R.string.profile_details_auto_update,
                context.getString(if (autoUpdate) R.string.status_enabled else R.string.status_disabled),
            ),
        )
        userAgent?.takeIf(String::isNotBlank)?.let {
            add(context.getString(R.string.profile_details_user_agent, it))
        }
        proxyUrl?.takeIf(String::isNotBlank)?.let {
            add(context.getString(R.string.profile_details_proxy, it))
        }
    }.joinToString("\n")
}
