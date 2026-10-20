package rs.chimera.android.backend.model

data class ProviderSnapshot(
    val name: String,
    val type: String,
    val vehicleType: String,
    val updatedAtMillis: Long?,
)
