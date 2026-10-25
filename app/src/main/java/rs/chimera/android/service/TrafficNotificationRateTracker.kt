package rs.chimera.android.service

import rs.chimera.android.backend.model.TrafficSnapshot

internal data class TrafficNotificationRates(
    val downloadBytesPerSecond: Long,
    val uploadBytesPerSecond: Long,
)

internal class TrafficNotificationRateTracker {
    private var previous: TrafficSnapshot? = null
    private var previousAtMillis: Long? = null

    fun record(snapshot: TrafficSnapshot, sampledAtMillis: Long): TrafficNotificationRates {
        val previousSnapshot = previous
        val previousTime = previousAtMillis
        previous = snapshot
        previousAtMillis = sampledAtMillis

        val elapsedMillis = previousTime?.let { sampledAtMillis - it }
            ?.takeIf { it > 0L }
            ?: return TrafficNotificationRates(0L, 0L)
        if (previousSnapshot == null) return TrafficNotificationRates(0L, 0L)

        return TrafficNotificationRates(
            downloadBytesPerSecond = ratePerSecond(
                previousSnapshot.downloadTotal,
                snapshot.downloadTotal,
                elapsedMillis,
            ),
            uploadBytesPerSecond = ratePerSecond(
                previousSnapshot.uploadTotal,
                snapshot.uploadTotal,
                elapsedMillis,
            ),
        )
    }

    private fun ratePerSecond(previous: Long, current: Long, elapsedMillis: Long): Long {
        val delta = (current.coerceAtLeast(0L) - previous.coerceAtLeast(0L)).coerceAtLeast(0L)
        return (delta.toDouble() * MILLIS_PER_SECOND / elapsedMillis)
            .toLong()
            .coerceAtLeast(0L)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
