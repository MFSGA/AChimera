package rs.chimera.android.ui.metacubex.design.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.applySystemBarInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val isRtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
        view.setPaddingRelative(
            if (isRtl) bars.right else bars.left,
            bars.top,
            if (isRtl) bars.left else bars.right,
            bars.bottom,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
