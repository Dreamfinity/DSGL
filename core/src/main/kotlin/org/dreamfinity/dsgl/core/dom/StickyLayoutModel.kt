package org.dreamfinity.dsgl.core.dom

import org.dreamfinity.dsgl.core.style.CssLength
import org.dreamfinity.dsgl.core.style.StyleProperty

internal object StickyLayoutModel {
    enum class PositionedGeometryIntegrationPoint {
        ContainerRenderContainedChild
    }

    enum class StickyInsetAxisMode {
        Inactive,
        Top,
        Bottom
    }

    data class StickyInsetResolution(
        val mode: StickyInsetAxisMode,
        val sourceProperty: StyleProperty?,
        val value: CssLength?
    ) {
        val active: Boolean
            get() = mode != StickyInsetAxisMode.Inactive && sourceProperty != null && value != null
    }

    fun nearestStickyScrollContainerVertical(node: DOMNode): DOMNode {
        var current = node.parent
        while (current != null) {
            if (current.scrollContainerState().axisY.scrollContainer) {
                return current
            }
            current = current.parent
        }
        return PositionedLayoutModel.rootStackingScope(node)
    }

    fun stickyContainingBlock(node: DOMNode): DOMNode {
        return node.parent ?: PositionedLayoutModel.rootStackingScope(node)
    }

    fun resolveVerticalInsets(top: CssLength?, bottom: CssLength?): StickyInsetResolution {
        return when {
            top != null -> StickyInsetResolution(
                mode = StickyInsetAxisMode.Top,
                sourceProperty = StyleProperty.TOP,
                value = top
            )

            bottom != null -> StickyInsetResolution(
                mode = StickyInsetAxisMode.Bottom,
                sourceProperty = StyleProperty.BOTTOM,
                value = bottom
            )

            else -> StickyInsetResolution(
                mode = StickyInsetAxisMode.Inactive,
                sourceProperty = null,
                value = null
            )
        }
    }

    fun positionedGeometryIntegrationPoint(): PositionedGeometryIntegrationPoint {
        return PositionedGeometryIntegrationPoint.ContainerRenderContainedChild
    }
}

