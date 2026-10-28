package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.TypedValue
import android.view.accessibility.AccessibilityNodeInfo
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
    private var selectionColorTransition = ProxySelectionColorTransition()
    private val delayTextState = ProxyDelayTextState()
    private var currentEntry: ProxyDisplayEntry? = null
    private val colorPrimary = resolveThemeColor(com.google.android.material.R.attr.colorPrimary)
    private val colorOnPrimary = resolveThemeColor(com.google.android.material.R.attr.colorOnPrimary)
    private val colorOnSurface = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
    private val colorSurface = resolveThemeColor(com.google.android.material.R.attr.colorSurface)
    private val selectableBackground = resolveThemeResource(android.R.attr.selectableItemBackground)

    var entry: ProxyDisplayEntry?
        get() = currentEntry
        set(value) = bindEntry(value)

    internal fun bindEntry(
        value: ProxyDisplayEntry?,
        forceSnapSelectionColor: Boolean = false,
        transition: ProxySelectionColorTransition? = null,
        preserveExistingTransition: Boolean = false,
    ) {
        transition?.let { selectionColorTransition = it }
        val previous = currentEntry
        currentEntry = value
        val frameTime = System.currentTimeMillis()
        val delayText = delayTextState.update(value)
        contentDescription = value?.let { item ->
            listOfNotNull(
                item.title,
                item.subtitle.takeIf(String::isNotBlank),
                delayText.takeIf(String::isNotBlank),
            ).joinToString(", ")
        }
        ViewCompat.setStateDescription(
            this,
            if (value?.isSelected == true) context.getString(R.string.selected) else null,
        )
        selectionColorTransition.bind(
            previous,
            value,
            backgroundColor(value),
            frameTime,
            forceSnap = forceSnapSelectionColor,
            preserveExistingTransition = preserveExistingTransition,
        )
        invalidate()
    }

    var columns: Int = ProxyDisplayOptions.DEFAULT_COLUMNS
        set(value) {
            val normalized = ProxyDisplayPolicy.normalizeColumns(value)
            if (field == normalized) return
            field = normalized
            entry?.let { selectionColorTransition.snap(backgroundColor(it), System.currentTimeMillis()) }
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
        background = selectableBackground
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isSelected = entry?.isSelected == true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> resources.displayMetrics.widthPixels
            MeasureSpec.AT_MOST, MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            else -> throw IllegalArgumentException("invalid measure spec")
        }

        paint.reset()
        paint.textSize = textSize
        paint.getTextBounds("Stub!", 0, 1, bounds)
        val desiredHeight = (
            layoutPadding * 2 +
                contentPadding * 2 +
                bounds.height() * 2 +
                textMargin
            ).toInt()

        val measuredHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> desiredHeight
            MeasureSpec.AT_MOST, MeasureSpec.EXACTLY ->
                desiredHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
            else -> throw IllegalArgumentException("invalid measure spec")
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun draw(canvas: Canvas) {
        val item = entry
        if (item == null) {
            super.draw(canvas)
            return
        }

        val useCard = columns != 1
        val frame = selectionColorTransition.advance(
            backgroundColor(item),
            System.currentTimeMillis(),
        )
        if (frame.animating) {
            postInvalidate()
        }

        paint.reset()
        paint.color = frame.color
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

        // CMFA draws its selectable background after the card fill and before the text.
        super.draw(canvas)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val item = entry ?: return
        val delayText = delayTextState.text

        paint.reset()
        paint.textSize = textSize

        val delayCount = paint.breakText(
            delayText,
            false,
            max(0f, width - layoutPadding * 2 - contentPadding * 2),
            null,
        )
        paint.getTextBounds(delayText, 0, delayCount, bounds)
        val delayWidth = bounds.width().toFloat()
        val mainTextWidth = max(
            0f,
            width - layoutPadding * 2 - contentPadding * 2 - delayWidth - textMargin * 2,
        )
        val titleCount = paint.breakText(item.title, false, mainTextWidth, null)
        val typeCount = paint.breakText(item.subtitle, false, mainTextWidth, null)
        val textOffset = (paint.descent() + paint.ascent()) / 2f

        // CMFA measures text using the default Paint flags, then enables antialiasing to draw.
        paint.reset()
        paint.isAntiAlias = true
        paint.color = if (item.isSelected) colorOnPrimary else colorOnSurface
        paint.textSize = textSize

        if (delayCount > 0) {
            canvas.drawText(
                delayText,
                0,
                delayCount,
                width - layoutPadding - contentPadding - delayWidth,
                height / 2f - textOffset,
                paint,
            )
        }
        val textX = layoutPadding + contentPadding
        canvas.drawText(
            item.title,
            0,
            titleCount,
            textX,
            layoutPadding + (height - layoutPadding * 2) / 3f - textOffset,
            paint,
        )
        canvas.drawText(
            item.subtitle,
            0,
            typeCount,
            textX,
            layoutPadding + (height - layoutPadding * 2) * 2f / 3f - textOffset,
            paint,
        )
    }

    private fun backgroundColor(item: ProxyDisplayEntry?): Int = when {
        item?.isSelected == true -> colorPrimary
        item != null && columns != 1 -> colorSurface
        else -> Color.TRANSPARENT
    }

    private fun dimension(resource: Int): Float =
        resources.getDimensionPixelSize(resource).toFloat()

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
