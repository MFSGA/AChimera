package rs.chimera.android.ui.metacubex.design

/** Horizontal viewport insets, including CMFA's landscape content-width constraint. */
internal data class ProxyLandscapeInsets(
    val start: Int,
    val end: Int,
)

internal fun proxyLandscapeInsets(
    width: Int,
    height: Int,
    minWidth: Int,
    start: Int,
    end: Int,
): ProxyLandscapeInsets {
    if (width <= height || width <= minWidth) return ProxyLandscapeInsets(start, end)

    val expectedWidth = width.coerceAtMost(height.coerceAtLeast(minWidth))
    val padding = (width - expectedWidth).coerceAtLeast(start + end) / 2
    return ProxyLandscapeInsets(
        start = padding.coerceAtLeast(start),
        end = padding.coerceAtLeast(end),
    )
}
