package rs.chimera.android.ui.metacubex.design

import android.view.View
import androidx.core.graphics.Insets
import rs.chimera.android.R

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

/** Combines system-bar insets with CMFA's wide-landscape content viewport. */
internal fun View.landscapeSystemBarInsets(systemBars: Insets): LandscapeContentInsets {
    val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
    return landscapeContentInsets(
        width = resources.displayMetrics.widthPixels,
        height = resources.displayMetrics.heightPixels,
        minWidth = resources.getDimensionPixelSize(R.dimen.metacubex_surface_landscape_min_width),
        start = if (isRtl) systemBars.right else systemBars.left,
        end = if (isRtl) systemBars.left else systemBars.right,
    )
}
