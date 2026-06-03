package org.dreamfinity.dsgl.core.portal

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest

object ColorPickerPopupPortalOwnership {
    fun resolveSurface(request: ColorPickerPopupRequest): ScreenDomainSurface =
        ScreenDomainSurfaces.portalSurfaceForDomain(request.ownerDomain)
}
