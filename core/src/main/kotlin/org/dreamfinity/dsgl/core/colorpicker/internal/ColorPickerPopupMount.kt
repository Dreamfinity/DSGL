package org.dreamfinity.dsgl.core.colorpicker.internal

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupEngine
import org.dreamfinity.dsgl.core.portal.panel.FloatingPanel
import org.dreamfinity.dsgl.core.portal.panel.FloatingPanelDragSession
import org.dreamfinity.dsgl.core.portal.panel.FloatingPanelState

internal class ColorPickerPopupMount(
    ownerId: Any,
    panelState: FloatingPanelState,
    dragSession: FloatingPanelDragSession,
    initialOwnerToken: Any = Any(),
) {
    val ownerToken: Any = initialOwnerToken
    val popupEngine: ColorPickerPopupEngine = ColorPickerPopupEngine()
    val floatingPanel: FloatingPanel =
        FloatingPanel(
            ownerId = ownerId,
            panelState = panelState,
            dragSession = dragSession,
        )

    val node: ColorPickerPopupPortalNode =
        ColorPickerPopupPortalNode(
            popupEngine = popupEngine,
            floatingPanel = floatingPanel,
        )

    val transientNode: ColorPickerTransientPortalNode =
        ColorPickerTransientPortalNode(popupEngine = popupEngine)
}
