package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.colorpicker.internal.SystemColorPickerOverlayNode
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.internal.SystemInspectorOverlayNode
import org.dreamfinity.dsgl.core.overlay.OverlayLayerHost
import org.dreamfinity.dsgl.core.overlay.UiLayerId
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleApplicationScope

class SystemOverlayHost(
    private val inspectorController: InspectorController
) : OverlayLayerHost {
    override val layerId: UiLayerId = UiLayerId.SystemOverlay

    private val rootNode: SystemOverlayRootNode = SystemOverlayRootNode()
    private val inspectorNode: SystemInspectorOverlayNode = SystemInspectorOverlayNode(inspectorController).applyParent(rootNode)
    private val colorPickerNode: SystemColorPickerOverlayNode = SystemColorPickerOverlayNode().applyParent(rootNode)
    private val tree: DomTree = DomTree(
        root = rootNode,
        styleScope = StyleApplicationScope.SystemOverlay
    )

    fun syncFrame(
        inspectedRoot: DOMNode?,
        inspectedLayoutRevision: Long,
        cursorX: Int,
        cursorY: Int,
        inspectorPointerCaptured: Boolean
    ) {
        inspectorNode.bindInspectedTree(inspectedRoot, inspectedLayoutRevision)
        inspectorNode.updateCursor(cursorX, cursorY, inspectorPointerCaptured)
        colorPickerNode.updateCursor(cursorX, cursorY)
    }

    override fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        tree.render(ctx, width, height)
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> {
        return tree.paint(ctx, applyStyles = true)
    }

    override fun clearRefs() {
        tree.clearRefs()
    }
}

