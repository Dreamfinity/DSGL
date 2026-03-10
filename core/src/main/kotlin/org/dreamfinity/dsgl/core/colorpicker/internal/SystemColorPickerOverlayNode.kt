package org.dreamfinity.dsgl.core.colorpicker.internal

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupEngine
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayCommandDslRenderer
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayPanel

internal class SystemColorPickerOverlayNode(
    private val popupEngine: ColorPickerPopupEngine = ColorPickerRuntime.engine,
    private val overlayPanel: SystemOverlayPanel? = null,
    key: Any? = "dsgl-system-color-picker"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker"

    private var cursorX: Int = 0
    private var cursorY: Int = 0
    private val commandBuffer: MutableList<RenderCommand> = ArrayList(512)
    private var renderCommandsRevision: Long = 0L

    fun updateCursor(mouseX: Int, mouseY: Int) {
        cursorX = mouseX
        cursorY = mouseY
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        popupEngine.onFrame(width, height)
        popupEngine.onCursorPosition(cursorX, cursorY)
        commandBuffer.clear()
        val panel = overlayPanel
        if (panel == null || panel.panelRect() == null) {
            popupEngine.appendOverlayCommands(commandBuffer)
        } else {
            panel.appendCommands(
                viewportWidth = width,
                viewportHeight = height,
                out = commandBuffer,
                appendBody = { _, out ->
                    popupEngine.appendOverlayBodyCommands(out)
                },
                appendOverlay = { out ->
                    popupEngine.appendEyedropperOverlayCommands(
                        viewportWidth = width.coerceAtLeast(1),
                        viewportHeight = height.coerceAtLeast(1),
                        out = out
                    )
                }
            )
        }
        if (SystemOverlayCommandDslRenderer.rebuildInto(this, commandBuffer, "system-color-picker")) {
            renderCommandsRevision += 1L
            markRenderCommandsDirty()
        }
        children.forEach { child ->
            child.render(ctx, bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }

    override fun volatileRenderCommandsSignature(nowMs: Long): Long {
        return renderCommandsRevision
    }
}
