package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Retained DOM tree. Render phase builds this tree, paint phase draws it.
 */
class DomTree(val root: DOMNode) {
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0
    private var laidOut: Boolean = false

    fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        lastWidth = width
        lastHeight = height
        root.render(ctx, 0, 0, width, height)
        laidOut = true
    }

    fun paint(ctx: UiMeasureContext): List<RenderCommand> {
        if (!laidOut && lastWidth > 0 && lastHeight > 0) {
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
