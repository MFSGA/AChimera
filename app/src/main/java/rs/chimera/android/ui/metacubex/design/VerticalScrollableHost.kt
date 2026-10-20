package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.absoluteValue
import kotlin.math.tan

/** Keeps vertical list gestures inside the page while allowing horizontal pager swipes. */
class VerticalScrollableHost @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : FrameLayout(context, attributeSet, defStyleAttr, defStyleRes) {
    private var initialX = 0f
    private var initialY = 0f

    private val degree = tan(Math.toRadians(15.0))

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val parentView = parent ?: return super.onInterceptTouchEvent(event)

        if (event.action == MotionEvent.ACTION_DOWN) {
            initialX = event.x
            initialY = event.y
            parentView.requestDisallowInterceptTouchEvent(true)
        } else if (event.action == MotionEvent.ACTION_MOVE) {
            val dx = event.x - initialX
            val dy = event.y - initialY
            val slope = dy.absoluteValue / dx.absoluteValue

            if (slope < degree) {
                parentView.requestDisallowInterceptTouchEvent(false)
            }
        }

        return super.onInterceptTouchEvent(event)
    }
}
