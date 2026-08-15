package rs.chimera.android.viewmodel

import rs.chimera.android.backend.model.BackendRuntimeError

internal object HomeErrorPolicy {
    fun resolve(
        serviceError: String?,
        runtimeError: BackendRuntimeError?,
    ): String? = serviceError?.takeIf { it.isNotBlank() } ?: runtimeError?.message
}
