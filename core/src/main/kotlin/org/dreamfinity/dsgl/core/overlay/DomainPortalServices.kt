package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupEngine
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuEngine
import org.dreamfinity.dsgl.core.select.SelectEngine
import org.dreamfinity.dsgl.core.select.SelectOpenRequest

object DomainPortalServices {
    val applicationContextMenuEngine: ContextMenuEngine = ContextMenuEngine()
    val applicationSelectEngine: SelectEngine = SelectEngine()
    val systemSelectEngine: SelectEngine = SelectEngine()
    val applicationColorPickerEngine: ColorPickerPopupEngine = ColorPickerPopupEngine()

    fun selectEngineFor(ownerScope: OverlayOwnerScope): SelectEngine =
        when (ownerScope) {
            OverlayOwnerScope.Application -> applicationSelectEngine
            OverlayOwnerScope.System -> systemSelectEngine
        }

    fun openSelect(request: SelectOpenRequest) {
        val target = selectEngineFor(request.ownerScope)
        val other = if (target === applicationSelectEngine) systemSelectEngine else applicationSelectEngine
        other.close(request.owner)
        target.open(request)
    }

    fun closeSelect(owner: Any) {
        applicationSelectEngine.close(owner)
        systemSelectEngine.close(owner)
    }

    fun closeAllSelects() {
        applicationSelectEngine.closeAll()
        systemSelectEngine.closeAll()
    }

    fun isSelectOpenFor(owner: Any): Boolean =
        applicationSelectEngine.isOpenFor(owner) || systemSelectEngine.isOpenFor(owner)

    fun isAnySelectOpen(): Boolean = applicationSelectEngine.isOpen() || systemSelectEngine.isOpen()
}
