package rs.chimera.android.backend.model

/** A state and its failure belong to one publication, never two independent updates. */
data class VpnRuntimeStatus(
    val state: ServiceState = ServiceState.STOPPED,
    val error: String? = null,
)
