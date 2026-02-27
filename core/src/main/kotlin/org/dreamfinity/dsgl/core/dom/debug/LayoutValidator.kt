package org.dreamfinity.dsgl.core.dom.debug

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.TextNode
import org.dreamfinity.dsgl.core.dom.elements.support.TextLayoutEngine
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.TextWrap

data class LayoutViolation(
    val code: String,
    val nodeKey: Any?,
    val parentKey: Any?,
    val message: String,
    val nodeBounds: Rect,
    val parentContentBounds: Rect?
)

object LayoutValidator {
    fun validate(root: DOMNode, ctx: UiMeasureContext): List<LayoutViolation> {
        if (!LayoutDebug.validateLayouts) return emptyList()
        val out = ArrayList<LayoutViolation>(8)
        walk(node = root, parent = null, parentContent = null, ctx = ctx, out = out)
        LayoutDebug.lastViolationCount = out.size
        if (out.isNotEmpty() && LayoutDebug.logViolations) {
            out.forEach { violation ->
                println(
                    "[DSGL-Layout] ${violation.code} key=${violation.nodeKey} parent=${violation.parentKey} " +
                        "node=${rectLabel(violation.nodeBounds)} parentContent=${rectLabel(violation.parentContentBounds)} :: " +
                        violation.message
                )
            }
        }
        return out
    }

    fun appendDebugCommands(
        root: DOMNode,
        violations: List<LayoutViolation>,
        out: MutableList<RenderCommand>
    ) {
        if (!LayoutDebug.drawBounds) return
        val violatingKeys = violations.mapNotNull { it.nodeKey }.toSet()
        walkDraw(root, violatingKeys, out)
    }

    private fun walk(
        node: DOMNode,
        parent: DOMNode?,
        parentContent: Rect?,
        ctx: UiMeasureContext,
        out: MutableList<LayoutViolation>
    ) {
        if (node.display == Display.None) return
        validateRectShape(node = node, parent = parent, out = out)
        if (parentContent != null) {
            validateContainment(node = node, parent = parent, parentContent = parentContent, out = out)
        }
        validateTextMetrics(node = node, ctx = ctx, parent = parent, out = out)

        val content = contentRect(node)
        node.children.forEach { child ->
            walk(
                node = child,
                parent = node,
                parentContent = content,
                ctx = ctx,
                out = out
            )
        }
    }

    private fun validateRectShape(node: DOMNode, parent: DOMNode?, out: MutableList<LayoutViolation>) {
        if (node.bounds.width < 0 || node.bounds.height < 0) {
            out += LayoutViolation(
                code = "NEGATIVE_SIZE",
                nodeKey = node.key,
                parentKey = parent?.key,
                message = "Negative bounds size is invalid.",
                nodeBounds = node.bounds,
                parentContentBounds = parent?.let { contentRect(it) }
            )
        }

        val right = node.bounds.x.toLong() + node.bounds.width.toLong()
        val bottom = node.bounds.y.toLong() + node.bounds.height.toLong()
        if (right !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
            bottom !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
        ) {
            out += LayoutViolation(
                code = "INT_OVERFLOW",
                nodeKey = node.key,
                parentKey = parent?.key,
                message = "Bounds exceed Int range.",
                nodeBounds = node.bounds,
                parentContentBounds = parent?.let { contentRect(it) }
            )
        }
    }

    private fun validateContainment(
        node: DOMNode,
        parent: DOMNode?,
        parentContent: Rect,
        out: MutableList<LayoutViolation>
    ) {
        val outer = outerRect(node)
        if (!containsRect(parentContent, outer)) {
            out += LayoutViolation(
                code = "CHILD_OUTSIDE_PARENT_CONTENT",
                nodeKey = node.key,
                parentKey = parent?.key,
                message = "Child outer rect escapes parent content box.",
                nodeBounds = outer,
                parentContentBounds = parentContent
            )
        }
    }

    private fun validateTextMetrics(
        node: DOMNode,
        ctx: UiMeasureContext,
        parent: DOMNode?,
        out: MutableList<LayoutViolation>
    ) {
        when (node) {
            is TextNode -> validateLineStack(
                text = node.text,
                wrap = node.textWrap,
                rect = contentRect(node),
                ctx = ctx,
                node = node,
                parent = parent,
                out = out
            )

            is ButtonNode -> validateLineStack(
                text = node.text,
                wrap = node.textWrap,
                rect = contentRect(node),
                ctx = ctx,
                node = node,
                parent = parent,
                out = out
            )
        }
    }

    private fun validateLineStack(
        text: String,
        wrap: TextWrap,
        rect: Rect,
        ctx: UiMeasureContext,
        node: DOMNode,
        parent: DOMNode?,
        out: MutableList<LayoutViolation>
    ) {
        val maxWidth = if (wrap == TextWrap.Wrap) rect.width.coerceAtLeast(0) else null
        val lineHeight = ctx.fontHeight(node.fontId, node.fontSize).coerceAtLeast(1)
        val layout = TextLayoutEngine.layout(
            text = text,
            maxWidth = maxWidth,
            wrap = wrap,
            fontHeight = lineHeight,
            measureText = { value -> ctx.measureText(value, node.fontId, node.fontSize) }
        )

        var previousBottom = 0
        layout.lines.forEachIndexed { index, _ ->
            val top = index * layout.lineHeight
            if (top < previousBottom) {
                out += LayoutViolation(
                    code = "TEXT_LINE_COLLISION",
                    nodeKey = node.key,
                    parentKey = parent?.key,
                    message = "Wrapped line boxes overlap.",
                    nodeBounds = node.bounds,
                    parentContentBounds = parent?.let { contentRect(it) }
                )
                return
            }
            previousBottom = top + layout.lineHeight
        }

        if (rect.height < layout.totalHeight) {
            out += LayoutViolation(
                code = "TEXT_HEIGHT_UNDERSIZED",
                nodeKey = node.key,
                parentKey = parent?.key,
                message = "Measured content height (${rect.height}) is smaller than line stack (${layout.totalHeight}).",
                nodeBounds = node.bounds,
                parentContentBounds = parent?.let { contentRect(it) }
            )
        }
    }

    private fun walkDraw(node: DOMNode, violatingKeys: Set<Any>, out: MutableList<RenderCommand>) {
        if (node.display == Display.None) return
        addOutline(out, contentRect(node), 0x7700AEEF)
        addOutline(out, outerRect(node), 0x6688C15B)
        val key = node.key
        if (key != null && key in violatingKeys) {
            addOutline(out, outerRect(node), 0xCCFF3B30.toInt())
        }
        node.children.forEach { child ->
            walkDraw(child, violatingKeys, out)
        }
    }

    private fun addOutline(out: MutableList<RenderCommand>, rect: Rect, color: Int) {
        if (rect.width <= 0 || rect.height <= 0) return
        out += RenderCommand.DrawRect(rect.x, rect.y, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y + rect.height - 1, rect.width, 1, color)
        out += RenderCommand.DrawRect(rect.x, rect.y, 1, rect.height, color)
        out += RenderCommand.DrawRect(rect.x + rect.width - 1, rect.y, 1, rect.height, color)
    }

    private fun containsRect(outer: Rect, inner: Rect): Boolean {
        if (inner.width < 0 || inner.height < 0) return false
        val outerLeft = outer.x.toLong()
        val outerTop = outer.y.toLong()
        val outerRight = outer.x.toLong() + outer.width.toLong()
        val outerBottom = outer.y.toLong() + outer.height.toLong()
        val innerLeft = inner.x.toLong()
        val innerTop = inner.y.toLong()
        val innerRight = inner.x.toLong() + inner.width.toLong()
        val innerBottom = inner.y.toLong() + inner.height.toLong()
        return innerLeft >= outerLeft &&
            innerTop >= outerTop &&
            innerRight <= outerRight &&
            innerBottom <= outerBottom
    }

    private fun contentRect(node: DOMNode): Rect {
        val x = node.bounds.x + node.border.left + node.padding.left
        val y = node.bounds.y + node.border.top + node.padding.top
        val width = (node.bounds.width - node.border.horizontal - node.padding.horizontal).coerceAtLeast(0)
        val height = (node.bounds.height - node.border.vertical - node.padding.vertical).coerceAtLeast(0)
        return Rect(x, y, width, height)
    }

    private fun outerRect(node: DOMNode): Rect {
        val x = node.bounds.x - node.margin.left
        val y = node.bounds.y - node.margin.top
        val width = (node.bounds.width + node.margin.horizontal).coerceAtLeast(0)
        val height = (node.bounds.height + node.margin.vertical).coerceAtLeast(0)
        return Rect(x, y, width, height)
    }

    private fun rectLabel(rect: Rect?): String {
        if (rect == null) return "n/a"
        return "(${rect.x},${rect.y},${rect.width},${rect.height})"
    }
}
