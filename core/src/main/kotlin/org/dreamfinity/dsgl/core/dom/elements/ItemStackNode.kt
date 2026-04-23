package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.ItemStackRef
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Node that draws a platform-specific item stack.
 */
class ItemStackNode(
    var stack: ItemStackRef,
    var size: Int = 18,
    var rotYDeg: Double = 160.0,
    var rotXDeg: Double = -11.0,
    key: Any? = null,
) : DOMNode(key) {
    override val styleType: String = "itemstack"

    internal override fun measureForLayout(ctx: UiMeasureContext, availableOuterWidth: Int?): Size =
        measureWithConstraint(ctx, availableOuterWidth)

    override fun measure(ctx: UiMeasureContext): Size = measureWithConstraint(ctx, null)

    private fun measureWithConstraint(ctx: UiMeasureContext, availableOuterWidth: Int?): Size {
        val contentLimit = resolvedContentLimit(availableOuterWidth)
        val contentWidth = width ?: size
        val resolvedWidth = contentLimit?.let { minOf(it, contentWidth) } ?: contentWidth
        val nameHeight = ctx.fontHeight + 2
        val contentHeight = height ?: (size + nameHeight)
        val totalWidth = resolvedWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    private fun resolvedContentLimit(availableOuterWidth: Int?): Int? {
        val explicit = width
        val extras = margin.horizontal + padding.horizontal + border.horizontal
        val constrainedByParent = availableOuterWidth?.let { (it - extras).coerceAtLeast(0) }
        return when {
            explicit != null && constrainedByParent != null -> minOf(explicit, constrainedByParent)
            explicit != null -> explicit
            else -> constrainedByParent
        }
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        addBorderCommands(out)
        out.add(
            RenderCommand.DrawItemStack(
                stack,
                contentX(),
                contentY(),
                contentWidth(),
                size,
                rotYDeg,
                rotXDeg,
            ),
        )
    }
}
