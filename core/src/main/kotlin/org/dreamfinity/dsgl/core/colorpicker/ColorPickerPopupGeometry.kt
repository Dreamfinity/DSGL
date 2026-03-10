package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.contextmenu.PopupPlacement
import org.dreamfinity.dsgl.core.contextmenu.PopupPlacementRequest
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size

internal data class ColorPickerPopupFrame(
    val panelRect: Rect,
    val headerRect: Rect,
    val bodyRect: Rect,
    val closeRect: Rect
)

internal class ColorPickerPopupPositionStore {
    private val rememberedPanelPositions: MutableMap<Any, Rect> = HashMap()

    fun remember(owner: Any, rect: Rect) {
        rememberedPanelPositions[owner] = rect
    }

    fun remembered(owner: Any): Rect? = rememberedPanelPositions[owner]
}

internal object ColorPickerPopupGeometry {
    fun resolvePanelRect(
        owner: Any,
        anchorRect: Rect,
        width: Int,
        height: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        keepPosition: Boolean,
        currentRect: Rect?,
        store: ColorPickerPopupPositionStore
    ): Rect {
        if (keepPosition && currentRect != null) {
            return clampPanel(currentRect.copy(width = width, height = height), viewportWidth, viewportHeight)
        }

        val remembered = store.remembered(owner)
        if (remembered != null) {
            return clampPanel(remembered.copy(width = width, height = height), viewportWidth, viewportHeight)
        }

        val placement = PopupPlacement.resolve(
            PopupPlacementRequest(
                preferredRect = Rect(
                    anchorRect.x,
                    anchorRect.y + anchorRect.height,
                    width,
                    height
                ),
                popupSize = Size(width, height),
                viewport = Rect(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1)),
                padding = 8,
                horizontalFlipX = anchorRect.x + anchorRect.width - width
            )
        )
        return placement.rect
    }

    fun buildFrame(panelRect: Rect, headerHeight: Int, panelPadding: Int): ColorPickerPopupFrame {
        val headerRect = Rect(panelRect.x, panelRect.y, panelRect.width, headerHeight)
        val bodyRect = Rect(
            panelRect.x + panelPadding,
            panelRect.y + headerHeight + panelPadding,
            (panelRect.width - panelPadding * 2).coerceAtLeast(1),
            (panelRect.height - headerHeight - panelPadding * 2).coerceAtLeast(1)
        )
        val closeRect = Rect(panelRect.x + panelRect.width - 20, panelRect.y + 4, 16, 16)
        return ColorPickerPopupFrame(
            panelRect = panelRect,
            headerRect = headerRect,
            bodyRect = bodyRect,
            closeRect = closeRect
        )
    }

    fun clampPanel(rect: Rect, viewportWidth: Int, viewportHeight: Int): Rect {
        val minX = 2
        val minY = 2
        val maxX = (viewportWidth - rect.width - 2).coerceAtLeast(2)
        val maxY = (viewportHeight - rect.height - 2).coerceAtLeast(2)
        return Rect(
            x = rect.x.coerceIn(minX, maxX),
            y = rect.y.coerceIn(minY, maxY),
            width = rect.width,
            height = rect.height
        )
    }
}
