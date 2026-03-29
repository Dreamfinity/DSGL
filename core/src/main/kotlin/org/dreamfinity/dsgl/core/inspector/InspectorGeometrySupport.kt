package org.dreamfinity.dsgl.core.inspector

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.UsedInteractionGeometryResolver
import org.dreamfinity.dsgl.core.dom.layout.Rect

internal data class InspectorNodeBoxes(
    val margin: Rect,
    val border: Rect,
    val padding: Rect,
    val content: Rect,
    val parentContent: Rect?
)

internal object InspectorGeometrySupport {
    fun computeBoxes(node: DOMNode): InspectorNodeBoxes {
        val borderRect = node.bounds
        val marginRect = Rect(
            x = borderRect.x - node.margin.left,
            y = borderRect.y - node.margin.top,
            width = (borderRect.width + node.margin.horizontal).coerceAtLeast(0),
            height = (borderRect.height + node.margin.vertical).coerceAtLeast(0)
        )
        val paddingRect = Rect(
            x = borderRect.x + node.border.left,
            y = borderRect.y + node.border.top,
            width = (borderRect.width - node.border.horizontal).coerceAtLeast(0),
            height = (borderRect.height - node.border.vertical).coerceAtLeast(0)
        )
        val contentRect = Rect(
            x = paddingRect.x + node.padding.left,
            y = paddingRect.y + node.padding.top,
            width = (paddingRect.width - node.padding.horizontal).coerceAtLeast(0),
            height = (paddingRect.height - node.padding.vertical).coerceAtLeast(0)
        )
        val parentContent = node.parent?.let(::contentRect)
        return InspectorNodeBoxes(
            margin = marginRect,
            border = borderRect,
            padding = paddingRect,
            content = contentRect,
            parentContent = parentContent
        )
    }

    fun computeHighlightBoxes(node: DOMNode): InspectorNodeBoxes {
        val geometry = UsedInteractionGeometryResolver.resolveNodeGeometry(node)
        val usedClip = geometry.usedClipRect
        val borderRect = clipRectToUsedClip(geometry.usedBorderRect, usedClip)
        val marginRect = clipRectToUsedClip(
            Rect(
                x = geometry.usedBorderRect.x - node.margin.left,
                y = geometry.usedBorderRect.y - node.margin.top,
                width = (geometry.usedBorderRect.width + node.margin.horizontal).coerceAtLeast(0),
                height = (geometry.usedBorderRect.height + node.margin.vertical).coerceAtLeast(0)
            ),
            usedClip
        )
        val paddingRect = clipRectToUsedClip(
            Rect(
                x = geometry.usedBorderRect.x + node.border.left,
                y = geometry.usedBorderRect.y + node.border.top,
                width = (geometry.usedBorderRect.width - node.border.horizontal).coerceAtLeast(0),
                height = (geometry.usedBorderRect.height - node.border.vertical).coerceAtLeast(0)
            ),
            usedClip
        )
        val contentRect = clipRectToUsedClip(
            Rect(
                x = paddingRect.x + node.padding.left,
                y = paddingRect.y + node.padding.top,
                width = (paddingRect.width - node.padding.horizontal).coerceAtLeast(0),
                height = (paddingRect.height - node.padding.vertical).coerceAtLeast(0)
            ),
            usedClip
        )
        val parentContent = node.parent?.let { parent ->
            clipRectToUsedClip(contentRect(parent), usedClip)
        }
        return InspectorNodeBoxes(
            margin = marginRect,
            border = borderRect,
            padding = paddingRect,
            content = contentRect,
            parentContent = parentContent
        )
    }

    private fun clipRectToUsedClip(rect: Rect, clip: Rect?): Rect {
        if (rect.width <= 0 || rect.height <= 0) {
            return Rect(0, 0, 0, 0)
        }
        if (clip == null) return rect
        return rect.intersection(clip) ?: Rect(0, 0, 0, 0)
    }

    fun contentRect(node: DOMNode): Rect {
        return Rect(
            x = node.bounds.x + node.border.left + node.padding.left,
            y = node.bounds.y + node.border.top + node.padding.top,
            width = (node.bounds.width - node.border.horizontal - node.padding.horizontal).coerceAtLeast(0),
            height = (node.bounds.height - node.border.vertical - node.padding.vertical).coerceAtLeast(0)
        )
    }
}
