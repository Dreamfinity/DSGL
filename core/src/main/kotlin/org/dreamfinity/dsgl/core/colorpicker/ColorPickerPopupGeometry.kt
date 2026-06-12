package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.contextmenu.PopupPlacement
import org.dreamfinity.dsgl.core.contextmenu.PopupPlacementRequest
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size

internal data class ColorPickerPopupFrame(
    val panelRect: Rect,
    val headerRect: Rect,
    val bodyRect: Rect,
    val closeRect: Rect,
)

internal class ColorPickerPopupPositionStore {
    private val rememberedPanelPositions: MutableMap<Any, Rect> = HashMap()

    fun remember(owner: Any, rect: Rect) {
        rememberedPanelPositions[owner] = rect
    }

    fun remembered(owner: Any): Rect? = rememberedPanelPositions[owner]
}

internal object ColorPickerPopupGeometry {
    private const val MIN_VALID_VIEWPORT_SIZE = 2

    fun hasValidViewport(viewportWidth: Int, viewportHeight: Int): Boolean =
        viewportWidth >= MIN_VALID_VIEWPORT_SIZE && viewportHeight >= MIN_VALID_VIEWPORT_SIZE

    fun resolvePanelRect(
        owner: Any,
        anchorRect: Rect,
        width: Int,
        height: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        keepPosition: Boolean,
        currentRect: Rect?,
        store: ColorPickerPopupPositionStore,
    ): Rect {
        val viewportReady = hasValidViewport(viewportWidth, viewportHeight)
        if (keepPosition && currentRect != null) {
            val next = currentRect.copy(width = width, height = height)
            return if (viewportReady) clampPanel(next, viewportWidth, viewportHeight) else next
        }

        val remembered = store.remembered(owner)
        if (remembered != null) {
            val next = remembered.copy(width = width, height = height)
            return if (viewportReady) clampPanel(next, viewportWidth, viewportHeight) else next
        }

        if (!viewportReady) {
            return Rect(
                x = anchorRect.x,
                y = anchorRect.y + anchorRect.height,
                width = width,
                height = height,
            )
        }

        val placement =
            PopupPlacement.resolve(
                PopupPlacementRequest(
                    preferredRect =
                        Rect(
                            anchorRect.x,
                            anchorRect.y + anchorRect.height,
                            width,
                            height,
                        ),
                    popupSize = Size(width, height),
                    viewport = Rect(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1)),
                    padding = 8,
                    horizontalFlipX = anchorRect.x + anchorRect.width - width,
                ),
            )
        return placement.rect
    }

    fun buildFrame(panelRect: Rect, headerHeight: Int, panelPadding: Int): ColorPickerPopupFrame {
        val headerRect = Rect(panelRect.x, panelRect.y, panelRect.width, headerHeight)
        val bodyRect =
            Rect(
                panelRect.x + panelPadding,
                panelRect.y + headerHeight + panelPadding,
                (panelRect.width - panelPadding * 2).coerceAtLeast(1),
                (panelRect.height - headerHeight - panelPadding * 2).coerceAtLeast(1),
            )
        val closeRect = Rect(panelRect.x + panelRect.width - 20, panelRect.y + 4, 16, 16)
        return ColorPickerPopupFrame(
            panelRect = panelRect,
            headerRect = headerRect,
            bodyRect = bodyRect,
            closeRect = closeRect,
        )
    }

    fun clampPanel(rect: Rect, viewportWidth: Int, viewportHeight: Int): Rect {
        if (!hasValidViewport(viewportWidth, viewportHeight)) {
            return rect
        }
        val minX = 2
        val minY = 2
        val maxX = (viewportWidth - rect.width - 2).coerceAtLeast(2)
        val maxY = (viewportHeight - rect.height - 2).coerceAtLeast(2)
        return Rect(
            x = rect.x.coerceIn(minX, maxX),
            y = rect.y.coerceIn(minY, maxY),
            width = rect.width,
            height = rect.height,
        )
    }
}
