package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dom.reconcile.DomReconcileResult
import org.dreamfinity.dsgl.core.dom.reconcile.DomReconciler
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.ref.RefManager
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine

/**
 * Retained DOM tree. Render phase builds this tree, paint phase draws it.
 *
 * Hosts should call [render] when size changes or when a rebuild is requested,
 * and [paint] every frame to obtain render commands.
 */
class DomTree(var root: DOMNode) {
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0
    private var laidOut: Boolean = false
    private val paintBuffer: MutableList<RenderCommand> = ArrayList(256)
    private val refManager: RefManager = RefManager()

    /** Measures and lays out the tree for the given viewport. */
    fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        lastWidth = width
        lastHeight = height
        StyleEngine.applyStylesRecursively(root)
        root.render(ctx, 0, 0, width, height)
        refManager.commit(root)
        laidOut = true
    }

    /** Builds render commands for the current layout. */
    fun paint(ctx: UiMeasureContext, applyStyles: Boolean = true): List<RenderCommand> {
        val layoutDirtyFromStyles = if (applyStyles) {
            StyleEngine.applyStylesRecursively(root)
        } else {
            false
        }
        if ((!laidOut || layoutDirtyFromStyles) && lastWidth > 0 && lastHeight > 0) {
            root.render(ctx, 0, 0, lastWidth, lastHeight)
            refManager.commit(root)
            laidOut = true
        }
        paintBuffer.clear()
        root.appendRenderCommands(ctx, paintBuffer)
        return paintBuffer
    }

    /**
     * Reconciles this retained tree against a freshly built tree.
     * Reuses compatible nodes and returns detached subtrees for cleanup.
     */
    fun reconcileWith(next: DomTree): DomReconcileResult {
        val result = DomReconciler.reconcile(root, next.root)
        root = result.root
        laidOut = false
        return result
    }

    fun dispatchClick(event: MouseClickEvent): Boolean {
        if (lastWidth <= 0 || lastHeight <= 0) {
            return false
        }

        return dispatchClick(root, event)
    }

    fun clearRefs() {
        refManager.clear()
    }
}