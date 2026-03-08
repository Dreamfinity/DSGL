package org.dreamfinity.dsgl.core.inspector.internal

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.system.SystemOverlayCommandDslRenderer

internal class SystemInspectorOverlayNode(
    private val controller: InspectorController,
    key: Any? = "dsgl-system-inspector"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-inspector"

    private var inspectedRoot: DOMNode? = null
    private var inspectedLayoutRevision: Long = 0L
    private var cursorX: Int = 0
    private var cursorY: Int = 0
    private var pointerCaptured: Boolean = false
    private val commandBuffer: MutableList<RenderCommand> = ArrayList(512)
    private var renderedCommandsHash: Int = 0

    fun bindInspectedTree(root: DOMNode?, layoutRevision: Long) {
        inspectedRoot = root
        inspectedLayoutRevision = layoutRevision
    }

    fun updateCursor(mouseX: Int, mouseY: Int, pointerCaptured: Boolean) {
        cursorX = mouseX
        cursorY = mouseY
        this.pointerCaptured = pointerCaptured
    }

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        inspectedRoot?.let { root ->
            controller.onLayoutCommitted(root, inspectedLayoutRevision)
        }
        controller.onCursorMoved(cursorX, cursorY)
        if (pointerCaptured) {
            controller.onCapturedPointerMove(cursorX, cursorY, width, height)
        }
        commandBuffer.clear()
        controller.appendOverlayCommands(bounds.width, bounds.height, commandBuffer)
        val nextHash = commandBuffer.hashCode()
        if (nextHash != renderedCommandsHash || children.size != commandBuffer.size) {
            SystemOverlayCommandDslRenderer.rebuildInto(this, commandBuffer, "system-inspector")
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
