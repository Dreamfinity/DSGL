package org.dreamfinity.dsgl.core.overlay

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
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.StyleEngine

class ApplicationOverlayRootNode(
    key: Any? = "dsgl-application-overlay-root"
) : DOMNode(key) {
    override val styleType: String = "dsgl-application-overlay-root"
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private val debugTintNode: ContainerNode = UiScope(this).div({
        this.key = "dsgl-application-overlay-debug-tint"
        style = {
            display = Display.None
        }
    })

    internal fun setViewportBounds(width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(0)
        viewportHeight = height.coerceAtLeast(0)
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
        val tintEnabled = OverlayDebugVisualization.enabled && isTintEnabled(UiLayerId.ApplicationOverlay)
        if (tintEnabled) {
            debugTintNode.display = Display.Block
            debugTintNode.backgroundColor = OverlayDebugVisualization.applicationOverlayFillColor
            debugTintNode.border = Border.all(1, OverlayDebugVisualization.applicationOverlayBorderColor)
            debugTintNode.render(ctx, bounds.x, bounds.y, bounds.width, bounds.height)
        } else {
            debugTintNode.display = Display.None
            debugTintNode.render(ctx, 0, 0, 0, 0)
        }
        children.forEach { child ->
            if (child === debugTintNode) return@forEach
            child.render(ctx, bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }

    override fun defaultForegroundColor(): Int = DsglColors.TEXT

    override fun defaultFontId(): String = FontRegistry.DEFAULT_FONT_ID

    override fun defaultFontSize(): Int = 16
}