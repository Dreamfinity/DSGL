package org.dreamfinity.dsgl.core.contextmenu

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size

data class PopupPlacementRequest(
    val preferredRect: Rect,
    val popupSize: Size,
    val viewport: Rect,
    val padding: Int = 6,
    val horizontalFlipX: Int? = null,
)

data class PopupPlacementResult(
    val rect: Rect,
    val flippedHorizontally: Boolean,
    val clampedVertically: Boolean,
)

object PopupPlacement {
    fun resolve(request: PopupPlacementRequest): PopupPlacementResult {
        val minX = request.viewport.x + request.padding
        val minY = request.viewport.y + request.padding
        val maxX = request.viewport.x + request.viewport.width - request.padding
        val maxY = request.viewport.y + request.viewport.height - request.padding
        val widthLimit = (maxX - minX).coerceAtLeast(1)
        val heightLimit = (maxY - minY).coerceAtLeast(1)
        val resolvedWidth =
            request.popupSize.width
                .coerceIn(1, widthLimit)
        val resolvedHeight =
            request.popupSize.height
                .coerceIn(1, heightLimit)
        val maxPanelX = (maxX - resolvedWidth).coerceAtLeast(minX)
        val maxPanelY = (maxY - resolvedHeight).coerceAtLeast(minY)

        val preferredX = request.preferredRect.x
        val preferredY = request.preferredRect.y
        val overflowRight = preferredX + resolvedWidth > maxX

        var flipped = false
        var x = preferredX
        if (overflowRight && request.horizontalFlipX != null) {
            val flippedCandidate = request.horizontalFlipX
            if (flippedCandidate >= minX && flippedCandidate + resolvedWidth <= maxX) {
                x = flippedCandidate
                flipped = true
            } else {
                x = flippedCandidate.coerceIn(minX, maxPanelX)
                flipped = true
            }
        }
        x = x.coerceIn(minX, maxPanelX)

        val clampedY = preferredY.coerceIn(minY, maxPanelY)
        val verticallyClamped = clampedY != preferredY

        return PopupPlacementResult(
            rect = Rect(x, clampedY, resolvedWidth, resolvedHeight),
            flippedHorizontally = flipped,
            clampedVertically = verticallyClamped,
        )
    }
}
