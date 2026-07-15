package rs.chimera.android.backend.model

data class TrafficSnapshot(
    val downloadTotal: Long,
    val uploadTotal: Long,
    val connectionCount: Int,
)
