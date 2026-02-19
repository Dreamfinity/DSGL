package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Insets
import org.dreamfinity.dsgl.core.dom.layout.LayoutDirection
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand

/**
 * Layout container node for column, row, or stack layouts.
 */
class ContainerNode(
    var layout: LayoutDirection = LayoutDirection.Column,
    padding: Int = 0,
    var gap: Int = 0,
    var backgroundColor: Int? = null,
    key: Any? = null
) : DOMNode(key) {
    override val styleType: String = "div"

    init {
        this.padding = Insets.all(padding)
    }

    override fun measure(ctx: UiMeasureContext): Size {
        if (children.isEmpty()) {
            val contentWidth = width ?: 0
            val contentHeight = height ?: 0
            val totalWidth = contentWidth + padding.horizontal + border.horizontal
            val totalHeight = contentHeight + padding.vertical + border.vertical
            return Size(totalWidth, totalHeight)
        }

        val sizes = children.map { it.measure(ctx) }
        val gapTotal = gap * (children.size - 1).coerceAtLeast(0)
        val content = when (layout) {
            LayoutDirection.Column -> {
                val contentWidth = sizes.mapIndexed { index, size ->
                    size.width + children[index].margin.horizontal
                }.maxOrNull() ?: 0
                val contentHeight = sizes.mapIndexed { index, size ->
                    size.height + children[index].margin.vertical
                }.sum() + gapTotal
                Size(contentWidth, contentHeight)
            }
            LayoutDirection.Row -> {
                val contentWidth = sizes.mapIndexed { index, size ->
                    size.width + children[index].margin.horizontal
                }.sum() + gapTotal
                val contentHeight = sizes.mapIndexed { index, size ->
                    size.height + children[index].margin.vertical
                }.maxOrNull() ?: 0
                Size(contentWidth, contentHeight)
            }
            LayoutDirection.Stack -> {
                val contentWidth = sizes.mapIndexed { index, size ->
                    size.width + children[index].margin.horizontal
                }.maxOrNull() ?: 0
                val contentHeight = sizes.mapIndexed { index, size ->
                    size.height + children[index].margin.vertical
                }.maxOrNull() ?: 0
                Size(contentWidth, contentHeight)
            }
        }

        val contentWidth = width ?: content.width
        val contentHeight = height ?: content.height
        val totalWidth = contentWidth + padding.horizontal + border.horizontal
        val totalHeight = contentHeight + padding.vertical + border.vertical
        return Size(totalWidth, totalHeight)
    }

    override fun render(ctx: UiMeasureContext, x: Int, y: Int, width: Int, height: Int) {
        bounds = Rect(x, y, width, height)
        val contentX = contentX()
        val contentY = contentY()

        when (layout) {
            LayoutDirection.Column -> {
                var cursorY = contentY
                val availableWidth = contentWidth()
                children.forEach { child ->
                    val childSize = child.measure(ctx)
                    val childWidth = childSize.width
                    val childHeight = childSize.height
                    val childX = alignedChildX(child, contentX, availableWidth, childWidth)
                    val childY = cursorY + child.margin.top
                    child.render(ctx, childX, childY, childWidth, childHeight)
                    cursorY += childHeight + child.margin.vertical + gap
                }
            }
            LayoutDirection.Row -> {
                var cursorX = contentX
                val availableHeight = contentHeight()
                children.forEach { child ->
                    val childSize = child.measure(ctx)
                    val childWidth = childSize.width
                    val childHeight = childSize.height
                    val childX = cursorX + child.margin.left
                    val childY = alignedChildY(child, contentY, availableHeight, childHeight)
                    child.render(ctx, childX, childY, childWidth, childHeight)
                    cursorX += childWidth + child.margin.horizontal + gap
                }
            }
            LayoutDirection.Stack -> {
                val availableWidth = contentWidth()
                val availableHeight = contentHeight()
                children.forEach { child ->
                    val childSize = child.measure(ctx)
                    val childWidth = childSize.width
                    val childHeight = childSize.height
                    val childX = alignedChildX(child, contentX, availableWidth, childWidth)
                    val childY = alignedChildY(child, contentY, availableHeight, childHeight)
                    child.render(ctx, childX, childY, childWidth, childHeight)
                }
            }
        }
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        backgroundColor?.let {
            out.add(RenderCommand.DrawRect(bounds.x, bounds.y, bounds.width, bounds.height, it))
        }
        addBackgroundImageCommand(out)
        addBorderCommands(out)
        super.buildRenderCommands(ctx, out)
    }

    override fun defaultBackgroundColor(): Int? = backgroundColor

    override fun applyBackgroundColor(value: Int?) {
        backgroundColor = value
    }

    private fun alignedChildX(child: DOMNode, contentX: Int, availableWidth: Int, childWidth: Int): Int {
        val interactableWidth = (availableWidth - child.margin.horizontal).coerceAtLeast(0)
        val horizontalOffset = when (child.align) {
            org.dreamfinity.dsgl.core.style.StyleAlign.START -> 0
            org.dreamfinity.dsgl.core.style.StyleAlign.CENTER -> (interactableWidth - childWidth) / 2
            org.dreamfinity.dsgl.core.style.StyleAlign.END -> interactableWidth - childWidth
        }
        return contentX + child.margin.left + horizontalOffset.coerceAtLeast(0)
    }

    private fun alignedChildY(child: DOMNode, contentY: Int, availableHeight: Int, childHeight: Int): Int {
        val interactableHeight = (availableHeight - child.margin.vertical).coerceAtLeast(0)
        val verticalOffset = when (child.align) {
            org.dreamfinity.dsgl.core.style.StyleAlign.START -> 0
            org.dreamfinity.dsgl.core.style.StyleAlign.CENTER -> (interactableHeight - childHeight) / 2
            org.dreamfinity.dsgl.core.style.StyleAlign.END -> interactableHeight - childHeight
        }
        return contentY + child.margin.top + verticalOffset.coerceAtLeast(0)
    }
}
