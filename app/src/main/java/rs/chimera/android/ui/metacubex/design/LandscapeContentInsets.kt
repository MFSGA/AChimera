package rs.chimera.android.ui.metacubex.design

/** Horizontal insets matching CMFA's centered landscape content viewport. */
internal data class LandscapeContentInsets(
    val start: Int,
    val end: Int,
)

internal fun landscapeContentInsets(
    width: Int,
    height: Int,
    minWidth: Int,
    start: Int,
    end: Int,
): LandscapeContentInsets {
    if (width <= height || width <= minWidth) return LandscapeContentInsets(start, end)

    val expectedWidth = width.coerceAtMost(height.coerceAtLeast(minWidth))
    val padding = (width - expectedWidth).coerceAtLeast(start + end) / 2
    return LandscapeContentInsets(
        start = padding.coerceAtLeast(start),
        end = padding.coerceAtLeast(end),
    )
}
