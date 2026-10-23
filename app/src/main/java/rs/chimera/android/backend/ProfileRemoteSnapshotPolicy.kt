package rs.chimera.android.backend

import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.model.ProfileType

internal object ProfileRemoteSnapshotPolicy {
    fun normalizeRequest(request: RemoteProfileRequest): RemoteProfileRequest =
        ProfileRemotePolicy.normalizeRequest(request).copy(autoUpdate = false)

    fun catalogFields(
        id: String,
        name: String,
        filePath: String,
        createdAt: Long,
        fileSize: Long,
    ): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "filePath" to filePath,
        "createdAt" to createdAt,
        "isActive" to false,
        "fileSize" to fileSize,
        "type" to ProfileType.LOCAL.name,
    )
}
