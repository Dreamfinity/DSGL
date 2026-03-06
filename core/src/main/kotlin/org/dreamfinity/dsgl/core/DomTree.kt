package org.dreamfinity.dsgl.core

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.debug.LayoutDebug
import org.dreamfinity.dsgl.core.dom.debug.LayoutValidator
import org.dreamfinity.dsgl.core.dom.debug.LayoutViolation
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dom.reconcile.DomReconcileResult
import org.dreamfinity.dsgl.core.dom.reconcile.DomReconciler
import org.dreamfinity.dsgl.core.event.EventBus
import org.dreamfinity.dsgl.core.event.MouseClickEvent
import org.dreamfinity.dsgl.core.event.dispatchClick
import org.dreamfinity.dsgl.core.ref.RefManager
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.render.RenderCommandChunk
import org.dreamfinity.dsgl.core.style.StyleEngine
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

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
        val chunkNodesVisitedLastFrame: Int,
        val chunkNodesRebuiltLastFrame: Int,
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
    private var chunkNodesVisitedLastFrame: Int = 0
    private var chunkNodesRebuiltLastFrame: Int = 0
    private var chunkTreeChangedThisFrame: Boolean = false
    private var lastPaintBuildErrorMs: Long = 0L
    private val chunksByNode: MutableMap<DOMNode, RenderCommandChunk> = WeakHashMap()
    private val debugCommandStackChecks: Boolean = java.lang.Boolean.getBoolean("dsgl.render.debug.stack")
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
        StyleEngine.setViewportSize(width, height)
        lastStyleReport = StyleEngine.applyStylesRecursivelyDetailed(root)
        lastStyleRevision = StyleEngine.currentStyleRevision()
        root.resolveLayoutStyleValues(
            ctx = ctx,
            parentContentWidth = width,
            parentContentHeight = height
        )
        root.render(ctx, 0, 0, width, height)
        validateLayout(ctx)
        refManager.commit(root)
        laidOut = true
        commandsDirty = true
    }

    /** Builds render commands for the current layout. */
    fun paint(ctx: UiMeasureContext, applyStyles: Boolean = true): List<RenderCommand> {
        frames += 1
        StyleEngine.setViewportSize(lastWidth, lastHeight)
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
            root.resolveLayoutStyleValues(
                ctx = ctx,
                parentContentWidth = lastWidth,
                parentContentHeight = lastHeight
            )
            root.render(ctx, 0, 0, lastWidth, lastHeight)
            validateLayout(ctx)
            refManager.commit(root)
            laidOut = true
            commandsDirty = true
        } else if (styleReport.visualDirty) {
            commandsDirty = true
        }
        if (rebuildPaintCommands(ctx)) {
            commandRebuilds += 1
        }
        return paintBuffer
    }

    /**
     * Reconciles this retained tree against a freshly built tree.
     * Reuses compatible nodes and returns detached subtrees for cleanup.
     */
    fun reconcileWith(next: DomTree): DomReconcileResult {
        val result = DomReconciler.reconcile(root, next.root)
        cleanupDiscardedTemplateListeners(templateRoot = next.root, retainedRoot = result.root)
        root = result.root
        laidOut = false
        commandsDirty = true
        chunksByNode.clear()
        return result
    }

    fun paintStats(): PaintStats {
        return PaintStats(
            frames = frames,
            commandRebuilds = commandRebuilds,
            chunkNodesVisitedLastFrame = chunkNodesVisitedLastFrame,
            chunkNodesRebuiltLastFrame = chunkNodesRebuiltLastFrame,
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
            val nowMs = System.currentTimeMillis()
            chunkNodesVisitedLastFrame = 0
            chunkNodesRebuiltLastFrame = 0
            chunkTreeChangedThisFrame = false
            val chunkChanged = if (!strictInvalidLayout) {
                rebuildChunkRecursive(root, ctx, nowMs)
                chunkTreeChangedThisFrame
            } else {
                false
            }
            val changed = commandsDirty || chunkChanged
            if (!changed) {
                return false
            }
            stagingPaintBuffer.clear()
            if (!strictInvalidLayout) {
                val rootChunk = chunksByNode[root]
                if (rootChunk != null) {
                    appendChunkCommands(rootChunk, stagingPaintBuffer)
                }
            }
            LayoutValidator.appendDebugCommands(root, lastViolations, stagingPaintBuffer)
            if (debugCommandStackChecks) {
                validateCommandStacks(stagingPaintBuffer)
            }
            paintBuffer.clear()
            paintBuffer.addAll(stagingPaintBuffer)
            commandsDirty = false
            changed
        } catch (error: Throwable) {
            val now = System.currentTimeMillis()
            if (now - lastPaintBuildErrorMs >= 2_000L) {
                lastPaintBuildErrorMs = now
                println("[DSGL-DomTree] Paint command rebuild failed; keeping previous frame commands: ${error.message}")
            }
            false
        }
    }

    private fun cleanupDiscardedTemplateListeners(templateRoot: DOMNode, retainedRoot: DOMNode) {
        if (templateRoot === retainedRoot) return
        val retainedNodes: MutableSet<DOMNode> = Collections.newSetFromMap(IdentityHashMap())
        collectNodes(retainedRoot, retainedNodes)
        EventBus.run {
            traverseNodes(templateRoot) { node ->
                if (!retainedNodes.contains(node)) {
                    node.clearOwnListeners()
                }
            }
        }
    }

    private fun traverseNodes(root: DOMNode, visitor: (DOMNode) -> Unit) {
        visitor(root)
        root.children.forEach { child ->
            traverseNodes(child, visitor)
        }
    }

    private fun collectNodes(root: DOMNode, out: MutableSet<DOMNode>) {
        out.add(root)
        root.children.forEach { child ->
            collectNodes(child, out)
        }
    }

    private fun validateCommandStacks(commands: List<RenderCommand>) {
        var clipDepth = 0
        var transformDepth = 0
        var opacityDepth = 0
        commands.forEach { command ->
            when (command) {
                is RenderCommand.PushClip -> clipDepth += 1
                RenderCommand.PopClip -> {
                    clipDepth -= 1
                    require(clipDepth >= 0) { "clip stack underflow" }
                }
                is RenderCommand.PushTransform -> transformDepth += 1
                RenderCommand.PopTransform -> {
                    transformDepth -= 1
                    require(transformDepth >= 0) { "transform stack underflow" }
                }
                is RenderCommand.PushOpacity -> opacityDepth += 1
                RenderCommand.PopOpacity -> {
                    opacityDepth -= 1
                    require(opacityDepth >= 0) { "opacity stack underflow" }
                }
                else -> Unit
            }
        }
        require(clipDepth == 0) { "clip stack imbalance: $clipDepth" }
        require(transformDepth == 0) { "transform stack imbalance: $transformDepth" }
        require(opacityDepth == 0) { "opacity stack imbalance: $opacityDepth" }
    }

    private fun rebuildChunkRecursive(node: DOMNode, ctx: UiMeasureContext, nowMs: Long): RenderCommandChunk {
        chunkNodesVisitedLastFrame += 1
        val chunk = chunksByNode.getOrPut(node) { RenderCommandChunk(node) }
        val nodeHidden = node.dragRenderHidden || node.display == org.dreamfinity.dsgl.core.style.Display.None

        val childSignature = if (nodeHidden) {
            if (chunk.children.isNotEmpty()) {
                chunk.children.clear()
                chunkTreeChangedThisFrame = true
            }
            0L
        } else {
            var signature = 1L
            val expectedChildren = node.children
            var childrenChanged = chunk.children.size != expectedChildren.size
            if (childrenChanged) {
                chunk.children.clear()
            }
            expectedChildren.forEachIndexed { index, child ->
                val childChunk = rebuildChunkRecursive(child, ctx, nowMs)
                if (childrenChanged) {
                    chunk.children += childChunk
                } else if (chunk.children[index] !== childChunk) {
                    childrenChanged = true
                }
                signature = 31L * signature + childChunk.subtreeSignature
            }
            if (childrenChanged) {
                chunkTreeChangedThisFrame = true
                chunk.children.clear()
                expectedChildren.forEach { child ->
                    chunk.children += chunksByNode.getOrPut(child) { RenderCommandChunk(child) }
                }
            }
            signature
        }

        val nodeSignature = node.renderCommandsSignature(nowMs)
        val rebuildSelf = commandsDirty ||
            chunk.lastNodeSignature != nodeSignature ||
            chunk.lastChildrenSignature != childSignature ||
            chunk.lastNodeSignature == Long.MIN_VALUE

        if (rebuildSelf) {
            chunkTreeChangedThisFrame = true
            chunkNodesRebuiltLastFrame += 1
            rebuildChunkCommands(node, chunk, ctx, nodeHidden)
            chunk.lastNodeSignature = nodeSignature
            chunk.lastChildrenSignature = childSignature
        }

        chunk.subtreeSignature = 31L * nodeSignature + childSignature
        return chunk
    }

    private fun rebuildChunkCommands(
        node: DOMNode,
        chunk: RenderCommandChunk,
        ctx: UiMeasureContext,
        nodeHidden: Boolean
    ) {
        chunk.prefixCommands.clear()
        chunk.selfCommands.clear()
        chunk.suffixCommands.clear()
        if (nodeHidden) return

        val activeTransform = node.effectiveTransform()
        val activeOpacity = node.effectiveOpacity()
        val transformPushed = !activeTransform.isIdentity()
        val opacityPushed = activeOpacity < 0.999f

        if (transformPushed) {
            val ox = node.bounds.x + node.bounds.width * node.transformOrigin.originX
            val oy = node.bounds.y + node.bounds.height * node.transformOrigin.originY
            chunk.prefixCommands += RenderCommand.PushTransform(
                originX = ox,
                originY = oy,
                translateX = activeTransform.translateX,
                translateY = activeTransform.translateY,
                scaleX = activeTransform.scaleX,
                scaleY = activeTransform.scaleY,
                rotateDeg = activeTransform.rotateDeg
            )
        }
        if (opacityPushed) {
            chunk.prefixCommands += RenderCommand.PushOpacity(activeOpacity)
        }

        DOMNode.withChildrenRenderPass(enabled = false) {
            node.buildRenderCommands(ctx, chunk.selfCommands)
        }

        if (opacityPushed) {
            chunk.suffixCommands += RenderCommand.PopOpacity
        }
        if (transformPushed) {
            chunk.suffixCommands += RenderCommand.PopTransform
        }
    }

    private fun appendChunkCommands(chunk: RenderCommandChunk, out: MutableList<RenderCommand>) {
        out.addAll(chunk.prefixCommands)
        out.addAll(chunk.selfCommands)
        chunk.children.forEach { child ->
            appendChunkCommands(child, out)
        }
        out.addAll(chunk.suffixCommands)
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
