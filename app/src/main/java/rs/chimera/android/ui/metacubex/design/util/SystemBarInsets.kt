package rs.chimera.android.ui.metacubex.design.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import rs.chimera.android.ui.metacubex.design.landscapeSystemBarInsets

fun View.applySystemBarInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val horizontalInsets = view.landscapeSystemBarInsets(bars)
        view.setPaddingRelative(
            horizontalInsets.start,
            bars.top,
            horizontalInsets.end,
            bars.bottom,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
