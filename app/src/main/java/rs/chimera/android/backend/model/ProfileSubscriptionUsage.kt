package rs.chimera.android.backend.model

import uniffi.chimera_ffi.SubscriptionInfo
import uniffi.chimera_ffi.DownloadResult
import org.json.JSONObject

data class ProfileSubscriptionUsage(
    val uploadBytes: Long?,
    val downloadBytes: Long?,
    val totalBytes: Long?,
    val expiresAtMillis: Long?,
)

internal fun SubscriptionInfo.toProfileSubscriptionUsage(): ProfileSubscriptionUsage? {
    fun ULong?.toLongOrNull(): Long? = this
        ?.takeIf { it <= Long.MAX_VALUE.toULong() }
        ?.toLong()

    val upload = upload.toLongOrNull()
    val download = download.toLongOrNull()
    val total = total.toLongOrNull()
    val expireAtMillis = expire.toLongOrNull()?.let { seconds ->
        if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L
    }
    if (upload == null && download == null && total == null && expireAtMillis == null) return null

    return ProfileSubscriptionUsage(
        uploadBytes = upload,
        downloadBytes = download,
        totalBytes = total,
        expiresAtMillis = expireAtMillis,
    )
}

internal fun DownloadResult.toProfileSubscriptionUsage(): ProfileSubscriptionUsage? =
    subscriptionInfo?.toProfileSubscriptionUsage()

internal fun JSONObject.putProfileSubscriptionUsage(usage: ProfileSubscriptionUsage) {
    usage.uploadBytes?.let { put("subscriptionUploadBytes", it) }
    usage.downloadBytes?.let { put("subscriptionDownloadBytes", it) }
    usage.totalBytes?.let { put("subscriptionTotalBytes", it) }
    usage.expiresAtMillis?.let { put("subscriptionExpiresAtMillis", it) }
}
