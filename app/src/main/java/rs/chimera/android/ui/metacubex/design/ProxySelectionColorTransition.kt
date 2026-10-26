package rs.chimera.android.ui.metacubex.design

import kotlin.math.absoluteValue

internal data class ProxySelectionColorFrame(
    val color: Int,
    val animating: Boolean,
)

internal object ProxySelectionTransitionPolicy {
    fun shouldAnimate(previous: ProxyDisplayEntry?, current: ProxyDisplayEntry?): Boolean =
        previous != null &&
            current != null &&
            previous.name == current.name &&
            previous.selectionGroupName == current.selectionGroupName &&
            previous.isSelected != current.isSelected
}

/** Matches CMFA's frame-time-based interpolation between selected and unselected card fills. */
internal class ProxySelectionColorTransition {
    private var color: Int? = null
    private var lastFrameTime = 0L

    fun snap(color: Int, frameTime: Long) {
        this.color = color
        lastFrameTime = frameTime
    }

    fun reset() {
        color = null
        lastFrameTime = 0L
    }

    fun bind(
        previous: ProxyDisplayEntry?,
        current: ProxyDisplayEntry?,
        target: Int,
        frameTime: Long,
        forceSnap: Boolean = false,
        preserveExistingTransition: Boolean = false,
    ): Boolean {
        if (forceSnap) {
            snap(target, frameTime)
            return false
        }

        val selectionChanged = ProxySelectionTransitionPolicy.shouldAnimate(previous, current)
        val continuesTransition =
            preserveExistingTransition ||
                (
                    previous != null &&
                        current != null &&
                        previous.name == current.name &&
                        previous.selectionGroupName == current.selectionGroupName &&
                        previous.isSelected == current.isSelected &&
                        color != null &&
                        color != target
                    )
        val shouldAnimate = selectionChanged || continuesTransition
        if (!shouldAnimate || color == null) {
            snap(target, frameTime)
            return false
        }
        return color != target
    }

    fun advance(target: Int, frameTime: Long): ProxySelectionColorFrame {
        val current = color ?: run {
            snap(target, frameTime)
            return ProxySelectionColorFrame(target, animating = false)
        }
        if (current == target) {
            lastFrameTime = frameTime
            return ProxySelectionColorFrame(target, animating = false)
        }

        val maxDelta = maxOf(
            (component(target, 24) - component(current, 24)).absoluteValue,
            (component(target, 16) - component(current, 16)).absoluteValue,
            (component(target, 8) - component(current, 8)).absoluteValue,
            (component(target, 0) - component(current, 0)).absoluteValue,
        )
        val frameOffset = (frameTime - lastFrameTime).coerceAtLeast(0L)
        val colorOffset = (frameOffset / maxDelta.toFloat().coerceAtLeast(0.001f))
            .coerceIn(0f, 1f)
        val next = if (colorOffset > 0.999f) {
            target
        } else {
            argb(
                interpolate(component(current, 24), component(target, 24), colorOffset),
                interpolate(component(current, 16), component(target, 16), colorOffset),
                interpolate(component(current, 8), component(target, 8), colorOffset),
                interpolate(component(current, 0), component(target, 0), colorOffset),
            )
        }

        color = next
        lastFrameTime = frameTime
        return ProxySelectionColorFrame(next, animating = next != target)
    }

    private fun component(color: Int, shift: Int): Int = (color ushr shift) and 0xff

    private fun interpolate(from: Int, to: Int, offset: Float): Int =
        (from + (to - from) * offset).toInt()

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}
