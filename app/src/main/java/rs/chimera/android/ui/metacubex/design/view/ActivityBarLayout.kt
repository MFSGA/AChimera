package rs.chimera.android.ui.metacubex.design.view

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes

/** Keeps toolbar touches from reaching the proxy list drawn behind it, as in CMFA. */
class ActivityBarLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0,
) : FrameLayout(context, attributeSet, defStyleAttr, defStyleRes) {
    init {
        alpha = 0.96f
        setBackgroundColor(
            TypedValue().apply {
                context.theme.resolveAttribute(android.R.attr.windowBackground, this, true)
            }.data,
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        super.dispatchTouchEvent(ev)

        return true
    }
}
