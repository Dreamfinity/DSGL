package org.dreamfinity.dsgl.core.colorpicker.internal

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupEngine
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanel
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayCommandDslRenderer
import org.dreamfinity.dsgl.core.render.RenderCommand

internal class SystemColorPickerOverlayNode(
    private val popupEngine: ColorPickerPopupEngine = ColorPickerRuntime.engine,
    private val overlayPanel: OverlayPanel? = null,
    key: Any? = "dsgl-system-color-picker"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-color-picker"

    private var cursorX: Int = 0
    private var cursorY: Int = 0
    private val commandBuffer: MutableList<RenderCommand> = ArrayList(512)
    private var renderCommandsRevision: Long = 0L

    private val panelNode: DOMNode? = overlayPanel?.node()?.applyParent(this)
    private val bodyBridgeNode: CommandBridgeNode? = overlayPanel?.let {
        CommandBridgeNode("system-color-picker-body").also(it::setBodyContent)
    }
    private val overlayBridgeNode: CommandBridgeNode? = overlayPanel?.let {
        CommandBridgeNode("system-color-picker-overlay").also(it::setOverlayContent)
    }

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
        val nativePanelNode = panelNode
        val bodyNode = bodyBridgeNode
        val overlayNode = overlayBridgeNode
        if (nativePanelNode != null && bodyNode != null && overlayNode != null) {
            if (overlayPanel?.panelRect() != null) {
                val bodyCommands = ArrayList<RenderCommand>(256)
                popupEngine.appendOverlayBodyCommands(bodyCommands)
                bodyNode.setCommands(bodyCommands)

                val overlayCommands = ArrayList<RenderCommand>(64)
                popupEngine.appendEyedropperOverlayCommands(
                    viewportWidth = width.coerceAtLeast(1),
                    viewportHeight = height.coerceAtLeast(1),
                    out = overlayCommands
                )
                overlayNode.setCommands(overlayCommands)
            } else {
                bodyNode.setCommands(emptyList())
                overlayNode.setCommands(emptyList())
            }
            nativePanelNode.render(ctx, x, y, width, height)
            return
        }

        commandBuffer.clear()
        popupEngine.appendOverlayCommands(commandBuffer)
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

    private class CommandBridgeNode(
        private val keyPrefix: String,
        key: Any? = "dsgl-system-color-picker-$keyPrefix"
    ) : DOMNode(key) {
        override val styleType: String = "dsgl-system-color-picker-command-bridge"
        private val commands: MutableList<RenderCommand> = ArrayList(256)
        private var signature: Long = 1L

        fun setCommands(next: List<RenderCommand>) {
            commands.clear()
            commands.addAll(next)
            signature += 1L
            markRenderCommandsDirty()
        }

        override fun measure(ctx: UiMeasureContext): Size {
            return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
        }

        override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
            bounds = Rect(x, y, width, height)
            if (SystemOverlayCommandDslRenderer.rebuildInto(this, commands, keyPrefix)) {
                signature += 1L
                markRenderCommandsDirty()
            }
            children.forEach { child ->
                child.render(ctx, bounds.x, bounds.y, bounds.width, bounds.height)
            }
        }

        override fun volatileRenderCommandsSignature(nowMs: Long): Long {
            return signature
        }
    }
}
