package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.Events
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseDragEvent
import org.dreamfinity.dsgl.core.event.MouseUpEvent
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
    key: Any? = null
) : DOMNode(key) {
    var value: Long = value
        private set
    var trackColor: Int = 0xFF4A4A52.toInt()
    var knobColor: Int = DsglColors.TEXT
    private var dragging: Boolean = false
    private var trackRect: Rect = Rect(0, 0, 0, 0)
    private var knobSize: Int = 8
    private var trackHeight: Int = 4

    init {
        setValue(value)
        EventBus.run {
            this@RangeInputNode.addEventListener(Events.MOUSEDOWN) { event: MouseDownEvent ->
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                if (!bounds.contains(event.mouseX, event.mouseY)) return@addEventListener
                dragging = true
                updateFromMouse(event.mouseX)
            }
            this@RangeInputNode.addEventListener(Events.MOUSEUP) { event: MouseUpEvent ->
                if (event.mouseButton != MouseButton.LEFT) return@addEventListener
                dragging = false
            }
            this@RangeInputNode.addEventListener(Events.DRAG) { event: MouseDragEvent ->
                if (!dragging) return@addEventListener
                val currentX = event.lastMouseX + event.dx
                updateFromMouse(currentX)
            }
        }
    }

    override fun measure(ctx: UiMeasureContext): Size {
        val contentWidth = width ?: 120
        val contentHeight = height ?: 12
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        val contentX = contentX()
        val contentY = contentY()
        val contentWidth = contentWidth()
        val contentHeight = contentHeight()
        trackHeight = maxOf(2, (contentHeight / 3))
        knobSize = maxOf(trackHeight * 2, 8)
        val trackY = contentY + (contentHeight - trackHeight) / 2
        trackRect = Rect(contentX, trackY, contentWidth, trackHeight)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        addBorderCommands(out)
        out.add(RenderCommand.DrawRect(trackRect.x, trackRect.y, trackRect.width, trackRect.height, trackColor))
        val knobX = valueToX()
        val knobY = trackRect.y + (trackRect.height - knobSize) / 2
        out.add(RenderCommand.DrawRect(knobX, knobY, knobSize, knobSize, knobColor))
    }

    private fun updateFromMouse(mouseX: Int) {
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

    private fun valueToX(): Int {
        val trackWidth = trackRect.width.coerceAtLeast(1)
        if (max == min) return trackRect.x
        val ratio = (value - min).toDouble() / (max - min).toDouble()
        return (trackRect.x + ratio * trackWidth - knobSize / 2.0).toInt()
            .coerceIn(trackRect.x, trackRect.x + trackWidth - knobSize)
    }

    private fun setValue(next: Long) {
        var clamped = next
        if (clamped < min) clamped = min
        if (clamped > max) clamped = max
        value = clamped
    }
}
