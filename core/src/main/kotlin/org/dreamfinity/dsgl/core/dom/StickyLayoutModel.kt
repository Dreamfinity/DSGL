package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.style.CssLength
import org.dreamfinity.dsgl.core.style.StyleProperty

internal object StickyLayoutModel {
    enum class PositionedGeometryIntegrationPoint {
        SharedUsedGeometryTransform,
    }

    enum class StickyInsetAxisMode {
        Inactive,
        Top,
        Bottom,
    }

    enum class StickyHorizontalInsetAxisMode {
        Inactive,
        Left,
        Right,
    }

    data class StickyInsetResolution(
        val mode: StickyInsetAxisMode,
        val sourceProperty: StyleProperty?,
        val value: CssLength?,
    ) {
        val active: Boolean
            get() = mode != StickyInsetAxisMode.Inactive && sourceProperty != null && value != null
    }

    data class StickyHorizontalInsetResolution(
        val mode: StickyHorizontalInsetAxisMode,
        val sourceProperty: StyleProperty?,
        val value: CssLength?,
    ) {
        val active: Boolean
            get() = mode != StickyHorizontalInsetAxisMode.Inactive && sourceProperty != null && value != null
    }

    data class StickyReferenceScrollContainers(
        val horizontal: DOMNode,
        val vertical: DOMNode,
    )

    fun nearestStickyScrollContainers(
        node: DOMNode,
        resolveHorizontal: Boolean = true,
        resolveVertical: Boolean = true,
    ): StickyReferenceScrollContainers {
        val root = PositionedLayoutModel.rootStackingScope(node)
        var horizontal: DOMNode? = if (resolveHorizontal) null else root
        var vertical: DOMNode? = if (resolveVertical) null else root
        var current = node.parent
        while (current != null && (horizontal == null || vertical == null)) {
            val state = current.scrollContainerState()
            if (horizontal == null && state.axisX.scrollContainer) {
                horizontal = current
            }
            if (vertical == null && state.axisY.scrollContainer) {
                vertical = current
            }
            current = current.parent
        }
        return StickyReferenceScrollContainers(
            horizontal = horizontal ?: root,
            vertical = vertical ?: root,
        )
    }

    fun nearestStickyScrollContainerHorizontal(node: DOMNode): DOMNode =
        nearestStickyScrollContainers(
            node = node,
            resolveHorizontal = true,
            resolveVertical = false,
        ).horizontal

    fun nearestStickyScrollContainerVertical(node: DOMNode): DOMNode =
        nearestStickyScrollContainers(
            node = node,
            resolveHorizontal = false,
            resolveVertical = true,
        ).vertical

    fun stickyContainingBlock(node: DOMNode): DOMNode = node.parent ?: PositionedLayoutModel.rootStackingScope(node)

    fun resolveHorizontalInsets(left: CssLength?, right: CssLength?): StickyHorizontalInsetResolution =
        when {
            left != null ->
                StickyHorizontalInsetResolution(
                    mode = StickyHorizontalInsetAxisMode.Left,
                    sourceProperty = StyleProperty.LEFT,
                    value = left,
                )

            right != null ->
                StickyHorizontalInsetResolution(
                    mode = StickyHorizontalInsetAxisMode.Right,
                    sourceProperty = StyleProperty.RIGHT,
                    value = right,
                )

            else ->
                StickyHorizontalInsetResolution(
                    mode = StickyHorizontalInsetAxisMode.Inactive,
                    sourceProperty = null,
                    value = null,
                )
        }

    fun resolveVerticalInsets(top: CssLength?, bottom: CssLength?): StickyInsetResolution =
        when {
            top != null ->
                StickyInsetResolution(
                    mode = StickyInsetAxisMode.Top,
                    sourceProperty = StyleProperty.TOP,
                    value = top,
                )

            bottom != null ->
                StickyInsetResolution(
                    mode = StickyInsetAxisMode.Bottom,
                    sourceProperty = StyleProperty.BOTTOM,
                    value = bottom,
                )

            else ->
                StickyInsetResolution(
                    mode = StickyInsetAxisMode.Inactive,
                    sourceProperty = null,
                    value = null,
                )
        }

    fun positionedGeometryIntegrationPoint(): PositionedGeometryIntegrationPoint =
        PositionedGeometryIntegrationPoint.SharedUsedGeometryTransform

    fun resolveVerticalVisualOffsetPx(
        baseY: Int,
        nodeHeight: Int,
        viewportRect: Rect,
        containingBlockRect: Rect,
        insetResolution: StickyInsetResolution,
        insetPx: Int,
    ): Int {
        if (!insetResolution.active) return 0

        val minY = containingBlockRect.y
        val maxY = (containingBlockRect.y + containingBlockRect.height - nodeHeight).coerceAtLeast(minY)
        val targetY =
            when (insetResolution.mode) {
                StickyInsetAxisMode.Top -> {
                    val thresholdY = viewportRect.y + insetPx
                    maxOf(baseY, thresholdY)
                }

                StickyInsetAxisMode.Bottom -> {
                    val thresholdY = viewportRect.y + viewportRect.height - insetPx - nodeHeight
                    minOf(baseY, thresholdY)
                }

                StickyInsetAxisMode.Inactive -> baseY
            }
        val finalY = targetY.coerceIn(minY, maxY)
        return finalY - baseY
    }

    fun resolveHorizontalVisualOffsetPx(
        baseX: Int,
        nodeWidth: Int,
        viewportRect: Rect,
        containingBlockRect: Rect,
        insetResolution: StickyHorizontalInsetResolution,
        insetPx: Int,
    ): Int {
        if (!insetResolution.active) return 0

        val minX = containingBlockRect.x
        val maxX = (containingBlockRect.x + containingBlockRect.width - nodeWidth).coerceAtLeast(minX)
        val targetX =
            when (insetResolution.mode) {
                StickyHorizontalInsetAxisMode.Left -> {
                    val thresholdX = viewportRect.x + insetPx
                    maxOf(baseX, thresholdX)
                }

                StickyHorizontalInsetAxisMode.Right -> {
                    val thresholdX = viewportRect.x + viewportRect.width - insetPx - nodeWidth
                    minOf(baseX, thresholdX)
                }

                StickyHorizontalInsetAxisMode.Inactive -> baseX
            }
        val finalX = targetX.coerceIn(minX, maxX)
        return finalX - baseX
    }
}
