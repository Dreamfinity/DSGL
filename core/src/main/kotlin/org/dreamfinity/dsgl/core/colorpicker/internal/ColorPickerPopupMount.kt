package org.dreamfinity.dsgl.core.colorpicker.internal

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupEngine
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanel
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelDragSession
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelState

internal class ColorPickerPopupMount(
    ownerId: Any,
    panelState: OverlayPanelState,
    dragSession: OverlayPanelDragSession,
    initialOwnerToken: Any = Any(),
) {
    val ownerToken: Any = initialOwnerToken
    val popupEngine: ColorPickerPopupEngine = ColorPickerPopupEngine()
    val overlayPanel: OverlayPanel =
        OverlayPanel(
            ownerId = ownerId,
            panelState = panelState,
            dragSession = dragSession,
        )

    val node: ColorPickerPopupOverlayNode =
        ColorPickerPopupOverlayNode(
            popupEngine = popupEngine,
            overlayPanel = overlayPanel,
        )

    val transientNode: ColorPickerTransientOverlayNode =
        ColorPickerTransientOverlayNode(popupEngine = popupEngine)
}
