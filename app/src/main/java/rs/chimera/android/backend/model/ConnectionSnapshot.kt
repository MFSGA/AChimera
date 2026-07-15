package rs.chimera.android.backend.model

data class ConnectionSnapshot(
    val id: String,
    val host: String,
    val process: String?,
    val upload: Long,
    val download: Long,
    val startTime: Long,
    val chains: List<String>,
    val rule: String?,
    val metadata: Map<String, String>,
)

data class ConnectionsSnapshot(
    val connections: List<ConnectionSnapshot>,
    val downloadTotal: Long,
    val uploadTotal: Long,
)
