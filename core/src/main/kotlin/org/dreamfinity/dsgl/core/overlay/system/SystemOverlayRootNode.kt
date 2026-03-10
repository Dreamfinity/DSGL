package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.DsglColors
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.overlay.OverlayDebugVisualization
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine

internal class SystemOverlayRootNode(
    key: Any? = "dsgl-system-overlay-root"
) : DOMNode(key) {
    override val styleType: String = "dsgl-system-overlay-root"

    override fun measure(ctx: UiMeasureContext): Size {
        return Size(
            width = StyleEngine.viewportWidthPx().coerceAtLeast(0),
            height = StyleEngine.viewportHeightPx().coerceAtLeast(0)
        )
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        val viewportWidth = StyleEngine.viewportWidthPx().coerceAtLeast(0)
        val viewportHeight = StyleEngine.viewportHeightPx().coerceAtLeast(0)
        bounds = Rect(0, 0, viewportWidth, viewportHeight)
        children.forEach { child ->
            child.render(ctx, bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (!OverlayDebugVisualization.enabled()) return
        if (bounds.width <= 0 || bounds.height <= 0) return
        out += RenderCommand.DrawRect(
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            OverlayDebugVisualization.systemOverlayFillColor
        )
        val borderColor = OverlayDebugVisualization.systemOverlayBorderColor
        out += RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, 1, borderColor)
        out += RenderCommand.DrawRect(bounds.x, bounds.y + bounds.height - 1, bounds.width, 1, borderColor)
        out += RenderCommand.DrawRect(bounds.x, bounds.y, 1, bounds.height, borderColor)
        out += RenderCommand.DrawRect(bounds.x + bounds.width - 1, bounds.y, 1, bounds.height, borderColor)
    }

    override fun defaultForegroundColor(): Int = DsglColors.TEXT

    override fun defaultFontId(): String = FontRegistry.DEFAULT_FONT_ID

    override fun defaultFontSize(): Int = 16
}
