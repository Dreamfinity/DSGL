package org.dreamfinity.dsgl.core.colorpicker.internal

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.system.SystemOverlayCommandDslRenderer

internal class SystemColorPickerOverlayNode(
    key: Any? = "dsgl-system-color-picker"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker"

    private var cursorX: Int = 0
    private var cursorY: Int = 0
    private val commandBuffer: MutableList<RenderCommand> = ArrayList(512)
    private var renderedCommandsHash: Int = 0

    fun updateCursor(mouseX: Int, mouseY: Int) {
        cursorX = mouseX
        cursorY = mouseY
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        ColorPickerRuntime.engine.onFrame(width, height)
        ColorPickerRuntime.engine.onCursorPosition(cursorX, cursorY)
        commandBuffer.clear()
        ColorPickerRuntime.engine.appendOverlayCommands(commandBuffer)
        val nextHash = commandBuffer.hashCode()
        if (nextHash != renderedCommandsHash || children.size != commandBuffer.size) {
            SystemOverlayCommandDslRenderer.rebuildInto(this, commandBuffer, "system-color-picker")
            renderedCommandsHash = nextHash
            markRenderCommandsDirty()
        }
        children.forEach { child ->
            child.render(ctx, bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }

    override fun volatileRenderCommandsSignature(nowMs: Long): Long {
        return renderedCommandsHash.toLong()
    }
}
