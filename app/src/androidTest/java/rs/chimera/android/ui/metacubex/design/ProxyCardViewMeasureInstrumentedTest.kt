package rs.chimera.android.ui.metacubex.design

import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R

@RunWith(AndroidJUnit4::class)
class ProxyCardViewMeasureInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun measureSpecsMatchReferenceForEveryGridDensity() {
        for (columns in 1..3) {
            val view = ProxyCardView(context).apply { this.columns = columns }
            val desiredHeight = desiredHeight(columns)

            view.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            assertEquals(context.resources.displayMetrics.widthPixels, view.measuredWidth)
            assertEquals(desiredHeight, view.measuredHeight)

            val listItemWidth = 654
            view.measure(
                View.MeasureSpec.makeMeasureSpec(listItemWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            assertEquals(listItemWidth, view.measuredWidth)
            assertEquals(desiredHeight, view.measuredHeight)

            val atMostWidth = 321
            val atMostHeight = desiredHeight - 1
            view.measure(
                View.MeasureSpec.makeMeasureSpec(atMostWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(atMostHeight, View.MeasureSpec.AT_MOST),
            )
            assertEquals(atMostWidth, view.measuredWidth)
            assertEquals(atMostHeight, view.measuredHeight)

            val looseAtMostHeight = desiredHeight + 1
            view.measure(
                View.MeasureSpec.makeMeasureSpec(listItemWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(looseAtMostHeight, View.MeasureSpec.AT_MOST),
            )
            assertEquals(listItemWidth, view.measuredWidth)
            assertEquals(desiredHeight, view.measuredHeight)

            val exactHeight = desiredHeight - 1
            view.measure(
                View.MeasureSpec.makeMeasureSpec(listItemWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(exactHeight, View.MeasureSpec.EXACTLY),
            )
            assertEquals(listItemWidth, view.measuredWidth)
            assertEquals(exactHeight, view.measuredHeight)
        }
    }

    private fun desiredHeight(columns: Int): Int {
        val resources = context.resources
        val textSize = resources.getDimensionPixelSize(
            if (columns == 2) R.dimen.metacubex_proxy_text_size
            else R.dimen.metacubex_proxy_text_size_compact,
        ).toFloat()
        val contentPadding = resources.getDimensionPixelSize(
            if (columns == 2) R.dimen.metacubex_proxy_content_padding
            else R.dimen.metacubex_proxy_content_padding_compact,
        )
        val textMargin = resources.getDimensionPixelSize(
            if (columns == 2) R.dimen.metacubex_proxy_text_margin
            else R.dimen.metacubex_proxy_text_margin_compact,
        )
        val layoutPadding = resources.getDimensionPixelSize(R.dimen.metacubex_proxy_layout_padding)
        val paint = Paint().apply { this.textSize = textSize }
        val bounds = Rect()
        paint.getTextBounds("Stub!", 0, 1, bounds)

        return layoutPadding * 2 + contentPadding * 2 + bounds.height() * 2 + textMargin
    }
}
