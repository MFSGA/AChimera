package rs.chimera.android.backend.model

data class ConnectionSnapshot(
    val id: String,
    val host: String,
    val upload: Long,
    val download: Long,
    val chains: List<String>,
    val rule: String?,
    val network: String,
    val sourceIp: String,
    val destinationIp: String,
    val sourcePort: String,
    val destinationPort: String,
)

data class ConnectionsSnapshot(
    val connections: List<ConnectionSnapshot>,
    val downloadTotal: Long,
    val uploadTotal: Long,
)
