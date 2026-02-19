package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine

/**
 * Retained DOM tree. Render phase builds this tree, paint phase draws it.
 *
 * Hosts should call [render] when size changes or when a rebuild is requested,
 * and [paint] every frame to obtain render commands.
 */
class DomTree(val root: DOMNode) {
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0
    private var laidOut: Boolean = false

    /** Measures and lays out the tree for the given viewport. */
    fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        lastWidth = width
        lastHeight = height
        StyleEngine.applyStylesRecursively(root)
        root.render(ctx, 0, 0, width, height)
        laidOut = true
    }

    /** Builds render commands for the current layout. */
    fun paint(ctx: UiMeasureContext): List<RenderCommand> {
        val layoutDirtyFromStyles = StyleEngine.applyStylesRecursively(root)
        if ((!laidOut || layoutDirtyFromStyles) && lastWidth > 0 && lastHeight > 0) {
            root.render(ctx, 0, 0, lastWidth, lastHeight)
            laidOut = true
        }
        val out = mutableListOf<RenderCommand>()
        root.buildRenderCommands(ctx, out)
        return out
    }

    fun dispatchClick(event: MouseClickEvent): Boolean {
        if (lastWidth <= 0 || lastHeight <= 0) {
            return false
        }

        return dispatchClick(root, event)
    }
}
