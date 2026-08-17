package rs.chimera.android.backend

internal class SettingsApplyException(
    cause: Throwable,
) : IllegalStateException(
    "Runtime settings were persisted but could not be applied",
    cause,
)
