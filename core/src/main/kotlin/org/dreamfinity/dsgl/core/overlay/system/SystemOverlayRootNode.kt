package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.UiScope
import org.dreamfinity.dsgl.core.debug.OverlayLayerDebugState.isTintEnabled
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Border
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.overlay.OverlayDebugVisualization
import org.dreamfinity.dsgl.core.overlay.UiLayerId
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.StyleEngine

internal class SystemOverlayRootNode(
    key: Any? = "dsgl-system-overlay-root"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-overlay-root"
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private val debugTintNode: ContainerNode = UiScope(this).div({
        this.key = "dsgl-system-overlay-debug-tint"
        style = {
            display = Display.None
        }
    })
    private val panelLaneNode: SystemOverlayLaneNode = SystemOverlayLaneNode(
        key = "dsgl-system-overlay-panel-lane",
        laneStyleType = "dsgl-system-overlay-panel-lane"
    )
    private val transientLaneNode: SystemOverlayLaneNode = SystemOverlayLaneNode(
        key = "dsgl-system-overlay-transient-lane",
        laneStyleType = "dsgl-system-overlay-transient-lane"
    )

    init {
        panelLaneNode.parent = this
        transientLaneNode.parent = this
        children += panelLaneNode
        children += transientLaneNode
    }

    internal fun setViewportBounds(width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(0)
        viewportHeight = height.coerceAtLeast(0)
        val rect = Rect(0, 0, viewportWidth, viewportHeight)
        bounds = rect
        panelLaneNode.bounds = rect
        transientLaneNode.bounds = rect
    }

    internal fun setLaneChildren(
        panelNodes: List<DOMNode>,
        transientNodes: List<DOMNode>
    ) {
        reconcileLane(panelLaneNode, panelNodes)
        reconcileLane(transientLaneNode, transientNodes)
    }

    internal fun mountedLaneNodes(lane: SystemOverlayLane): List<DOMNode> {
        return when (lane) {
            SystemOverlayLane.PanelContent -> panelLaneNode.children.toList()
            SystemOverlayLane.Transient -> transientLaneNode.children.toList()
        }
    }

    override fun measure(ctx: UiMeasureContext): Size {
        val resolvedWidth = if (viewportWidth > 0) viewportWidth else StyleEngine.viewportWidthPx().coerceAtLeast(0)
        val resolvedHeight = if (viewportHeight > 0) viewportHeight else StyleEngine.viewportHeightPx().coerceAtLeast(0)
        return Size(
            width = resolvedWidth,
            height = resolvedHeight
        )
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        setViewportBounds(width, height)
        bounds = Rect(0, 0, viewportWidth, viewportHeight)
        val tintEnabled = OverlayDebugVisualization.enabled && isTintEnabled(UiLayerId.SystemOverlay)
        if (tintEnabled) {
            debugTintNode.display = Display.Block
            debugTintNode.backgroundColor = OverlayDebugVisualization.systemOverlayFillColor
            debugTintNode.border = Border.all(1, OverlayDebugVisualization.systemOverlayBorderColor)
            debugTintNode.render(ctx, bounds.x, bounds.y, bounds.width, bounds.height)
        } else {
            debugTintNode.display = Display.None
            debugTintNode.render(ctx, 0, 0, 0, 0)
        }
        panelLaneNode.render(ctx, bounds.x, bounds.y, bounds.width, bounds.height)
        transientLaneNode.render(ctx, bounds.x, bounds.y, bounds.width, bounds.height)
    }

    override fun defaultForegroundColor(): Int = DsglColors.TEXT

    override fun defaultFontId(): String = FontRegistry.DEFAULT_FONT_ID

    override fun defaultFontSize(): Int = 16

    private fun reconcileLane(laneNode: SystemOverlayLaneNode, desiredNodes: List<DOMNode>) {
        val currentNodes = laneNode.children
        val unchanged = currentNodes.size == desiredNodes.size &&
            currentNodes.indices.all { index -> currentNodes[index] === desiredNodes[index] }
        if (unchanged) return
        currentNodes.forEach { node ->
            node.parent = null
        }
        currentNodes.clear()
        desiredNodes.forEach { node ->
            node.parent = laneNode
            currentNodes += node
        }
    }
}

private class SystemOverlayLaneNode(
    key: Any?,
    private val laneStyleType: String
) : DOMNode(key) {
    override val styleType: String = laneStyleType

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(bounds.width.coerceAtLeast(0), bounds.height.coerceAtLeast(0))
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        children.forEach { child ->
            child.render(ctx, x, y, width, height)
        }
    }
}
