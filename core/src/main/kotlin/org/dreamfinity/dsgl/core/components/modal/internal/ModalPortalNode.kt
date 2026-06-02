package org.dreamfinity.dsgl.core.components.modal.internal

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine

/**
 * Root node for modal portal composition:
 * child[0] is regular content and children[1..] are full-viewport modal layers.
 */
internal class ModalPortalAnchorNode(
    key: Any?,
) : DOMNode(key) {
    override val styleType: String = "modal-portal"

    override fun measure(ctx: UiMeasureContext): Size {
        val content = children.firstOrNull()
        val contentSize = content?.measure(ctx) ?: Size(0, 0)
        val totalWidth = (width ?: contentSize.width) + padding.horizontal + border.horizontal
        val totalHeight = (height ?: contentSize.height) + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun render(
        ctx: UiMeasureContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val parentNode = parent
        val resolvedBounds =
            if (parentNode != null && parentNode.parent == null) {
                Rect(
                    x = 0,
                    y = 0,
                    width = StyleEngine.viewportWidthPx().coerceAtLeast(0),
                    height = StyleEngine.viewportHeightPx().coerceAtLeast(0),
                )
            } else if (parentNode != null) {
                Rect(
                    x = parentNode.bounds.x + parentNode.border.left + parentNode.padding.left,
                    y = parentNode.bounds.y + parentNode.border.top + parentNode.padding.top,
                    width =
                        (parentNode.bounds.width - parentNode.border.horizontal - parentNode.padding.horizontal)
                            .coerceAtLeast(
                                0,
                            ),
                    height =
                        (parentNode.bounds.height - parentNode.border.vertical - parentNode.padding.vertical)
                            .coerceAtLeast(
                                0,
                            ),
                )
            } else {
                Rect(x, y, width, height)
            }
        bounds = resolvedBounds
        children
            .firstOrNull()
            ?.render(ctx, resolvedBounds.x, resolvedBounds.y, resolvedBounds.width, resolvedBounds.height)
        if (children.size <= 1) return
        for (i in 1 until children.size) {
            children[i].render(ctx, resolvedBounds.x, resolvedBounds.y, resolvedBounds.width, resolvedBounds.height)
        }
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) = Unit
}

/**
 * Application-portal root for modal layers. The regular modal portal content stays
 * in the application root; modal layers render through this full-viewport root.
 */
internal class ModalPortalRootNode(
    key: Any?,
) : DOMNode(key) {
    override val styleType: String = "modal-portal-root"

    override fun measure(ctx: UiMeasureContext): Size =
        Size(
            width = StyleEngine.viewportWidthPx().coerceAtLeast(0),
            height = StyleEngine.viewportHeightPx().coerceAtLeast(0),
        )

    override fun render(
        ctx: UiMeasureContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        bounds =
            Rect(
                x = 0,
                y = 0,
                width =
                    StyleEngine
                        .viewportWidthPx()
                        .coerceAtLeast(width)
                        .coerceAtLeast(0),
                height =
                    StyleEngine
                        .viewportHeightPx()
                        .coerceAtLeast(height)
                        .coerceAtLeast(0),
            )
        children.forEach { child ->
            child.resolveLayoutStyleValues(ctx, bounds.width, bounds.height)
            child.render(ctx, bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) = Unit
}
