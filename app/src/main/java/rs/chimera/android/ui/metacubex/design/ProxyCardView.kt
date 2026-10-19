package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.content.ContextCompat
import rs.chimera.android.R
import kotlin.math.max

/** Draws a proxy item using the compact card geometry used by CMFA's ProxyView. */
class ProxyCardView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = Rect()
    private val path = Path()
    private val layoutPadding = dimension(R.dimen.metacubex_proxy_layout_padding)
    private val cardRadius = dimension(R.dimen.metacubex_proxy_card_radius)
    private val shadowColor = Color.argb(0x15, 0x44, 0x44, 0x44)
    private val colorPrimary = resolveThemeColor(com.google.android.material.R.attr.colorPrimary)
    private val colorOnPrimary = resolveThemeColor(com.google.android.material.R.attr.colorOnPrimary)
    private val colorOnSurface = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
    private val colorSurface = resolveThemeColor(com.google.android.material.R.attr.colorSurface)
    private val selectableBackground = resolveThemeResource(android.R.attr.selectableItemBackground)

    var entry: ProxyDisplayEntry? = null
        set(value) {
            field = value
            contentDescription = value?.let { item ->
                listOfNotNull(
                    item.name,
                    item.type.takeIf(String::isNotBlank),
                    item.delayText.takeIf(String::isNotBlank),
                ).joinToString(", ")
            }
            isSelected = value?.isSelected == true
            ViewCompat.setStateDescription(this, if (isSelected) context.getString(R.string.selected) else null)
            requestLayout()
            invalidate()
        }

    var columns: Int = ProxyDisplayOptions.DEFAULT_COLUMNS
        set(value) {
            field = ProxyDisplayPolicy.normalizeColumns(value)
            requestLayout()
            invalidate()
        }

    private val textSize: Float
        get() = dimension(
            if (columns == 2) R.dimen.metacubex_proxy_text_size
            else R.dimen.metacubex_proxy_text_size_compact,
        )

    private val contentPadding: Float
        get() = dimension(
            if (columns == 2) R.dimen.metacubex_proxy_content_padding
            else R.dimen.metacubex_proxy_content_padding_compact,
        )

    private val textMargin: Float
        get() = dimension(
            if (columns == 2) R.dimen.metacubex_proxy_text_margin
            else R.dimen.metacubex_proxy_text_margin_compact,
        )

    init {
        // Keep the selector above the custom card fill so the pressed ripple remains visible.
        foreground = selectableBackground
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        paint.textSize = textSize
        paint.getTextBounds("Stub!", 0, 5, bounds)
        val desiredHeight = (
            layoutPadding * 2 +
                contentPadding * 2 +
                bounds.height() * 2 +
                textMargin
            ).toInt()
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        val measuredWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val item = entry ?: return
        val useCard = columns != 1
        val backgroundColor = when {
            item.isSelected -> colorPrimary
            useCard -> colorSurface
            else -> Color.TRANSPARENT
        }
        val foregroundColor = if (item.isSelected) colorOnPrimary else colorOnSurface

        paint.reset()
        paint.isAntiAlias = true
        paint.color = backgroundColor
        paint.style = Paint.Style.FILL
        if (useCard) {
            path.reset()
            path.addRoundRect(
                layoutPadding,
                layoutPadding,
                width - layoutPadding,
                height - layoutPadding,
                cardRadius,
                cardRadius,
                Path.Direction.CW,
            )
            paint.setShadowLayer(cardRadius, 0f, 0f, shadowColor)
            canvas.drawPath(path, paint)
            canvas.save()
            canvas.clipPath(path)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            canvas.save()
        }

        paint.clearShadowLayer()
        paint.color = foregroundColor
        paint.textSize = textSize
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)

        val delayCount = paint.breakText(
            item.delayText,
            false,
            max(0f, width - layoutPadding * 2 - contentPadding * 2),
            null,
        )
        paint.getTextBounds(item.delayText, 0, delayCount, bounds)
        val delayWidth = bounds.width().toFloat()
        val mainTextWidth = max(
            0f,
            width - layoutPadding * 2 - contentPadding * 2 - delayWidth - textMargin * 2,
        )
        val titleCount = paint.breakText(item.name, false, mainTextWidth, null)
        val typeCount = paint.breakText(item.type, false, mainTextWidth, null)
        val textOffset = (paint.descent() + paint.ascent()) / 2f

        if (delayCount > 0) {
            canvas.drawText(
                item.delayText,
                0,
                delayCount,
                width - layoutPadding - contentPadding - delayWidth,
                height / 2f - textOffset,
                paint,
            )
        }
        val textX = layoutPadding + contentPadding
        canvas.drawText(
            item.name,
            0,
            titleCount,
            textX,
            layoutPadding + (height - layoutPadding * 2) / 3f - textOffset,
            paint,
        )
        canvas.drawText(
            item.type,
            0,
            typeCount,
            textX,
            layoutPadding + (height - layoutPadding * 2) * 2f / 3f - textOffset,
            paint,
        )
        canvas.restore()
    }

    private fun dimension(resource: Int): Float = resources.getDimension(resource)

    private fun resolveThemeColor(attribute: Int): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(attribute, value, true)
        return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
    }

    private fun resolveThemeResource(attribute: Int) = TypedValue().let { value ->
        context.theme.resolveAttribute(attribute, value, true)
        ContextCompat.getDrawable(context, value.resourceId)
    }
}
