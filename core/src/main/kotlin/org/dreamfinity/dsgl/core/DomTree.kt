package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.debug.LayoutDebug
import org.dreamfinity.dsgl.core.dom.debug.LayoutValidator
import org.dreamfinity.dsgl.core.dom.debug.LayoutViolation
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
    data class PaintStats(
        val frames: Long,
        val commandRebuilds: Long,
        val styledNodesLastFrame: Int,
        val styleCacheHitsLastFrame: Int,
        val styleRecomputedLastFrame: Int
    )

    private var lastWidth: Int = 0
    private var lastHeight: Int = 0
    private var laidOut: Boolean = false
    private val paintBuffer: MutableList<RenderCommand> = ArrayList(256)
    private val stagingPaintBuffer: MutableList<RenderCommand> = ArrayList(256)
    private val refManager: RefManager = RefManager()
    private var lastViolations: List<LayoutViolation> = emptyList()
    private var strictInvalidLayout: Boolean = false
    private var commandsDirty: Boolean = true
    private var lastStyleRevision: Long = Long.MIN_VALUE
    private var frames: Long = 0L
    private var commandRebuilds: Long = 0L
    private var lastPaintBuildErrorMs: Long = 0L
    private var lastStyleReport: StyleEngine.StyleApplyReport = StyleEngine.StyleApplyReport(
        layoutDirty = false,
        visualDirty = false,
        visitedNodes = 0,
        cacheHits = 0,
        recomputedNodes = 0
    )

    /** Measures and lays out the tree for the given viewport. */
    fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        lastWidth = width
        lastHeight = height
        lastStyleReport = StyleEngine.applyStylesRecursivelyDetailed(root)
        lastStyleRevision = StyleEngine.currentStyleRevision()
        root.render(ctx, 0, 0, width, height)
        validateLayout(ctx)
        refManager.commit(root)
        laidOut = true
        commandsDirty = true
    }

    /** Builds render commands for the current layout. */
    fun paint(ctx: UiMeasureContext, applyStyles: Boolean = true): List<RenderCommand> {
        frames += 1
        val styleRevision = if (applyStyles) StyleEngine.currentStyleRevision() else lastStyleRevision
        val styleReport = if (applyStyles && (styleRevision != lastStyleRevision || !laidOut)) {
            StyleEngine.applyStylesRecursivelyDetailed(root).also {
                lastStyleReport = it
                lastStyleRevision = styleRevision
            }
        } else {
            StyleEngine.StyleApplyReport(
                layoutDirty = false,
                visualDirty = false,
                visitedNodes = 0,
                cacheHits = 0,
                recomputedNodes = 0
            )
        }
        if ((!laidOut || styleReport.layoutDirty) && lastWidth > 0 && lastHeight > 0) {
            root.render(ctx, 0, 0, lastWidth, lastHeight)
            validateLayout(ctx)
            refManager.commit(root)
            laidOut = true
            commandsDirty = true
        } else if (styleReport.visualDirty) {
            commandsDirty = true
        }
        if (commandsDirty) {
            if (rebuildPaintCommands(ctx)) {
                commandRebuilds += 1
            }
        }
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
        commandsDirty = true
        return result
    }

    fun paintStats(): PaintStats {
        return PaintStats(
            frames = frames,
            commandRebuilds = commandRebuilds,
            styledNodesLastFrame = lastStyleReport.visitedNodes,
            styleCacheHitsLastFrame = lastStyleReport.cacheHits,
            styleRecomputedLastFrame = lastStyleReport.recomputedNodes
        )
    }

    fun markVisualDirty() {
        commandsDirty = true
    }

    private fun rebuildPaintCommands(ctx: UiMeasureContext): Boolean {
        return try {
            stagingPaintBuffer.clear()
            if (!strictInvalidLayout) {
                root.appendRenderCommands(ctx, stagingPaintBuffer)
            }
            LayoutValidator.appendDebugCommands(root, lastViolations, stagingPaintBuffer)
            paintBuffer.clear()
            paintBuffer.addAll(stagingPaintBuffer)
            commandsDirty = false
            true
        } catch (error: Throwable) {
            val now = System.currentTimeMillis()
            if (now - lastPaintBuildErrorMs >= 2_000L) {
                lastPaintBuildErrorMs = now
                println("[DSGL-DomTree] Paint command rebuild failed; keeping previous frame commands: ${error.message}")
            }
            false
        }
    }

    fun dispatchClick(event: MouseClickEvent): Boolean {
        if (lastWidth <= 0 || lastHeight <= 0) {
            return false
        }
        if (strictInvalidLayout) {
            return false
        }

        return dispatchClick(root, event)
    }

    fun clearRefs() {
        refManager.clear()
    }

    private fun validateLayout(ctx: UiMeasureContext) {
        val previousStrict = strictInvalidLayout
        if (!LayoutDebug.validateLayouts) {
            lastViolations = emptyList()
            LayoutDebug.lastViolationCount = 0
            strictInvalidLayout = false
            if (previousStrict != strictInvalidLayout) {
                commandsDirty = true
            }
            return
        }
        val violations = LayoutValidator.validate(root, ctx)
        lastViolations = violations
        strictInvalidLayout = violations.isNotEmpty() && LayoutDebug.strictBounds
        if (previousStrict != strictInvalidLayout) {
            commandsDirty = true
        }
        if (strictInvalidLayout) {
            val first = violations.first()
            println(
                "[DSGL-Layout] strict mode invalidated paint/hit-test due to ${first.code} " +
                    "key=${first.nodeKey} parent=${first.parentKey}: ${first.message}"
            )
        }
    }
}
