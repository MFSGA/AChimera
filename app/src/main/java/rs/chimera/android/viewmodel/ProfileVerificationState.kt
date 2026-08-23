package rs.chimera.android.viewmodel

internal data class ProfileVerificationState(
    val isVerifying: Boolean = false,
    val result: String? = null,
    val succeeded: Boolean? = null,
) {
    fun clearForOperation(kind: ProfileOperationKind): ProfileVerificationState =
        if (kind == ProfileOperationKind.VERIFYING) this else ProfileVerificationState()

    companion object {
        fun verifying(): ProfileVerificationState = ProfileVerificationState(isVerifying = true)

        fun completed(
            result: String,
            succeeded: Boolean,
        ): ProfileVerificationState =
            ProfileVerificationState(
                result = result,
                succeeded = succeeded,
            )
    }
}
