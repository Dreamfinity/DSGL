package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest

object ColorPickerPopupOverlayOwnership {
    fun resolveSurface(request: ColorPickerPopupRequest): ScreenDomainSurface =
        ScreenDomainSurfaces.portalSurfaceForOwner(request.ownerScope)
}
