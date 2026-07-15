package rs.chimera.android.backend.model

enum class BackendRuntimeErrorSource {
    TRAFFIC,
    MEMORY,
    PROXY_GROUPS,
}

data class BackendRuntimeError(
    val source: BackendRuntimeErrorSource,
    val message: String,
)
