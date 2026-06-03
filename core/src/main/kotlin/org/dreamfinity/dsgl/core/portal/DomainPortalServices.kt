package org.dreamfinity.dsgl.core.portal

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupEngine
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuEngine
import org.dreamfinity.dsgl.core.select.SelectEngine
import org.dreamfinity.dsgl.core.select.SelectOpenRequest

object DomainPortalServices {
    val applicationContextMenuEngine: ContextMenuEngine = ContextMenuEngine()
    val applicationSelectEngine: SelectEngine = SelectEngine()
    val systemSelectEngine: SelectEngine = SelectEngine()
    val applicationColorPickerEngine: ColorPickerPopupEngine = ColorPickerPopupEngine()

    fun selectEngineFor(ownerDomain: ScreenDomainId): SelectEngine =
        when (ownerDomain) {
            ScreenDomainId.Application -> applicationSelectEngine
            ScreenDomainId.System -> systemSelectEngine
            ScreenDomainId.Debug -> error("Debug domain select portals are not supported yet")
        }

    fun openSelect(request: SelectOpenRequest) {
        val target = selectEngineFor(request.ownerDomain)
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

    fun isSelectClosingFor(owner: Any): Boolean =
        applicationSelectEngine.isClosingFor(owner) || systemSelectEngine.isClosingFor(owner)

    fun isSelectOpenFor(owner: Any): Boolean =
        applicationSelectEngine.isOpenFor(owner) || systemSelectEngine.isOpenFor(owner)

    fun isAnySelectOpen(): Boolean = applicationSelectEngine.isOpen() || systemSelectEngine.isOpen()
}
