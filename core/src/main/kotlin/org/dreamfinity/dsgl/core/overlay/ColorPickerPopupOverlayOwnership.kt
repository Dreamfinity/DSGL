package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest

object ColorPickerPopupOverlayOwnership {
    fun resolveLayer(request: ColorPickerPopupRequest): UiLayerId =
        OverlayLayerContracts.resolveTransientLayer(request.ownerScope)
}
