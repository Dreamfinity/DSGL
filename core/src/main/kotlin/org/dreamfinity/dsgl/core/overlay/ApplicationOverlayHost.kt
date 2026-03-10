package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.style.StyleApplicationScope

class ApplicationOverlayHost : OverlayLayerHost {
    override val layerId: UiLayerId = UiLayerId.ApplicationOverlay

    private val rootNode: ApplicationOverlayRootNode = ApplicationOverlayRootNode()
    private val tree: DomTree = DomTree(
        root = rootNode,
        styleScope = StyleApplicationScope.Application
    )

    override fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        tree.render(ctx, width, height)
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> {
        return tree.paint(ctx, applyStyles = true)
    }

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = false

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean = false

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = false

    override fun clearRefs() {
        tree.clearRefs()
    }
}
