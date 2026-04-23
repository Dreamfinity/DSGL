package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.*
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.math.roundToLong

/**
 * Range slider input node.
 */
class RangeInputNode(
    value: Long = 0L,
    var min: Long = 0L,
    var max: Long = 100L,
    var step: Long? = null,
    key: Any? = null,
) : DOMNode(key) {
    private data class SliderGeometry(
        val trackRect: Rect,
        val knobSize: Int,
    )

    companion object {
        private var activeDragIdentity: Any? = null
        private var activeDragStartValue: Long? = null

        fun clearActiveDrag() {
            activeDragIdentity = null
            activeDragStartValue = null
        }
    }

    override val focusable: Boolean = true
    override val styleType: String = "input"
    private val initialValue: Long = value
    var value: Long = initialValue
        private set
    var trackColor: Int = 0xFF4A4A52.toInt()
    var knobColor: Int = DsglColors.TEXT

    init {
        setValue(initialValue)
        EventBus.run {
            this@RangeInputNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                if (this@RangeInputNode.styleDisabled) return@addEventListener
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                if (!containsGlobalPoint(event.mouseX, event.mouseY)) return@addEventListener
                activeDragIdentity = dragIdentity()
                activeDragStartValue = this@RangeInputNode.value
                val before = this@RangeInputNode.value
                updateFromMouse(event.mouseX)
                if (this@RangeInputNode.value != before) {
                    postInput(
                        this@RangeInputNode,
                        this@RangeInputNode.value.toString(),
                        this@RangeInputNode.value,
                    )
                }
            }
            this@RangeInputNode.addEventListener(Events.MOUSEUP) { event: MouseUpEvent ->
                if (this@RangeInputNode.styleDisabled) return@addEventListener
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                if (!isActiveDragTarget()) return@addEventListener
                val start = activeDragStartValue ?: this@RangeInputNode.value
                if (this@RangeInputNode.value != start) {
                    postChange(
                        this@RangeInputNode,
                        this@RangeInputNode.value.toString(),
                        this@RangeInputNode.value,
                    )
                }
                clearActiveDrag()
            }
            this@RangeInputNode.addEventListener(Events.DRAG) { event: MouseDragEvent ->
                if (this@RangeInputNode.styleDisabled) return@addEventListener
                if (!isActiveDragTarget()) return@addEventListener
                val currentX = event.lastMouseX + event.dx
                val before = this@RangeInputNode.value
                updateFromMouse(currentX)
                if (this@RangeInputNode.value != before) {
                    postInput(
                        this@RangeInputNode,
                        this@RangeInputNode.value.toString(),
                        this@RangeInputNode.value,
                    )
                }
            }
        }
    }

    internal override fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size =
        measureWithConstraint(availableOuterWidth)

    override fun measure(ctx: UiMeasureContext): Size = measureWithConstraint(null)

    private fun measureWithConstraint(availableOuterWidth: Int?): Size {
        val contentLimit = resolvedContentLimit(availableOuterWidth)
        val contentWidth = width ?: 120
        val resolvedWidth = contentLimit?.let { minOf(it, contentWidth) } ?: contentWidth
        val contentHeight = height ?: 12
        val totalWidth = resolvedWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    private fun resolvedContentLimit(availableOuterWidth: Int?): Int? {
        val explicit = width
        val extras = margin.horizontal + padding.horizontal + border.horizontal
        val constrainedByParent = availableOuterWidth?.let { (it - extras).coerceAtLeast(0) }
        return when {
            explicit != null && constrainedByParent != null -> minOf(explicit, constrainedByParent)
            explicit != null -> explicit
            else -> constrainedByParent
        }
    }

    override fun render(
        ctx: UiMeasureContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        bounds = Rect(x, y, width, height)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        val geometry = sliderGeometry()
        addBackgroundImageCommand(out)
        addBorderCommands(out)
        out.add(
            RenderCommand.DrawRect(
                geometry.trackRect.x,
                geometry.trackRect.y,
                geometry.trackRect.width,
                geometry.trackRect.height,
                trackColor,
            ),
        )
        val knobX = valueToX(geometry)
        val knobY = geometry.trackRect.y + (geometry.trackRect.height - geometry.knobSize) / 2
        out.add(RenderCommand.DrawRect(knobX, knobY, geometry.knobSize, geometry.knobSize, knobColor))
    }

    private fun updateFromMouse(mouseX: Int) {
        val geometry = sliderGeometry()
        val trackRect = geometry.trackRect
        val trackWidth = trackRect.width.coerceAtLeast(1)
        val clamped = mouseX.coerceIn(trackRect.x, trackRect.x + trackWidth)
        val ratio = (clamped - trackRect.x).toDouble() / trackWidth.toDouble()
        val raw = min + ((max - min) * ratio)
        var next = raw.roundToLong()
        step?.let { stepSize ->
            if (stepSize > 0) {
                val steps = ((next - min).toDouble() / stepSize.toDouble()).roundToLong()
                next = min + steps * stepSize
            }
        }
        setValue(next)
    }

    private fun valueToX(geometry: SliderGeometry): Int {
        val trackRect = geometry.trackRect
        val knobSize = geometry.knobSize
        val trackWidth = trackRect.width.coerceAtLeast(1)
        if (max == min) return trackRect.x
        val ratio = (value - min).toDouble() / (max - min).toDouble()
        return (trackRect.x + ratio * trackWidth - knobSize / 2.0)
            .toInt()
            .coerceIn(trackRect.x, trackRect.x + trackWidth - knobSize)
    }

    private fun sliderGeometry(): SliderGeometry {
        val contentHeight = contentHeight()
        val resolvedTrackHeight = maxOf(2, contentHeight / 3)
        val resolvedKnobSize = maxOf(resolvedTrackHeight * 2, 8)
        val trackRect =
            Rect(
                x = contentX(),
                y = contentY() + (contentHeight - resolvedTrackHeight) / 2,
                width = contentWidth(),
                height = resolvedTrackHeight,
            )
        return SliderGeometry(
            trackRect = trackRect,
            knobSize = resolvedKnobSize,
        )
    }

    private fun setValue(next: Long) {
        var clamped = next
        if (clamped < min) clamped = min
        if (clamped > max) clamped = max
        value = clamped
    }

    private fun dragIdentity(): Any = key ?: this

    private fun isActiveDragTarget(): Boolean {
        val active = activeDragIdentity ?: return false
        return active == dragIdentity()
    }

    override fun defaultBackgroundColor(): Int = trackColor

    override fun applyBackgroundColor(value: Int?) {
        if (value != null) {
            trackColor = value
        }
    }

    override fun defaultForegroundColor(): Int = knobColor

    override fun applyForegroundColor(value: Int) {
        knobColor = value
    }
}
