package rs.chimera.android.backend

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.util.toUserVisibleMessage

internal class BackendRuntimeErrorTracker(
    private val formatError: (Throwable) -> String,
) {
    constructor(context: Context) : this(
        formatError = { error ->
            error.toUserVisibleMessage(
                context,
                rs.chimera.android.R.string.profile_unknown_error,
            )
        },
    )

    private val mutableError = MutableStateFlow<BackendRuntimeError?>(null)
    val error: StateFlow<BackendRuntimeError?> = mutableError.asStateFlow()

    fun record(
        source: BackendRuntimeErrorSource,
        prefix: String,
        error: Throwable,
    ) {
        mutableError.value = BackendRuntimeError(source, "$prefix: ${formatError(error)}")
    }

    fun clear(source: BackendRuntimeErrorSource) {
        if (mutableError.value?.source == source) {
            mutableError.value = null
        }
    }
}
