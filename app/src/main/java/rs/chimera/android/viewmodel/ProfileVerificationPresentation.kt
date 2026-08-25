package rs.chimera.android.viewmodel

internal fun ProfileVerificationExecutionResult.toVerificationState(
    missingMessage: String,
    failureMessage: (Exception) -> String,
): ProfileVerificationState =
    when (this) {
        ProfileVerificationExecutionResult.Missing -> ProfileVerificationState.completed(
            result = missingMessage,
            succeeded = false,
        )

        is ProfileVerificationExecutionResult.Verified -> ProfileVerificationState.completed(
            result = content,
            succeeded = true,
        )

        is ProfileVerificationExecutionResult.Failed -> ProfileVerificationState.completed(
            result = failureMessage(error),
            succeeded = false,
        )
    }
