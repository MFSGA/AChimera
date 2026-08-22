package rs.chimera.android.viewmodel

internal data class ProfileStatusMessage(
    val text: String? = null,
    val isError: Boolean = false,
) {
    companion object {
        fun of(text: String?, isError: Boolean = false): ProfileStatusMessage =
            ProfileStatusMessage(
                text = text,
                isError = text != null && isError,
            )
    }
}
