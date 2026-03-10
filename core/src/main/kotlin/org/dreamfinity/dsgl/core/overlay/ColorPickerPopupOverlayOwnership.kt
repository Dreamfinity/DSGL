package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest

object ColorPickerPopupOverlayOwnership {
    fun resolveLayer(request: ColorPickerPopupRequest): UiLayerId {
        return OverlayLayerContracts.resolveTransientLayer(request.ownerScope)
    }
}
