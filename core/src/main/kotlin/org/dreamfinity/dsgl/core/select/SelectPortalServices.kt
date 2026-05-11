package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope

object SelectPortalServices {
    val applicationEngine: SelectEngine = SelectEngine()
    val systemEngine: SelectEngine = SelectEngine()
    val engine: SelectEngine = applicationEngine

    fun engineFor(ownerScope: OverlayOwnerScope): SelectEngine =
        when (ownerScope) {
            OverlayOwnerScope.Application -> applicationEngine
            OverlayOwnerScope.System -> systemEngine
        }

    fun open(request: SelectOpenRequest) {
        val target = engineFor(request.ownerScope)
        val other = if (target === applicationEngine) systemEngine else applicationEngine
        other.close(request.owner)
        target.open(request)
    }

    fun close(owner: Any) {
        applicationEngine.close(owner)
        systemEngine.close(owner)
    }

    fun closeAll() {
        applicationEngine.closeAll()
        systemEngine.closeAll()
    }

    fun isOpenFor(owner: Any): Boolean = applicationEngine.isOpenFor(owner) || systemEngine.isOpenFor(owner)

    fun isOpen(): Boolean = applicationEngine.isOpen() || systemEngine.isOpen()
}
