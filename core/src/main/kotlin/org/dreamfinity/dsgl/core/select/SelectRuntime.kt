package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope

object SelectRuntime {
    val applicationEngine: SelectEngine = SelectEngine()
    val systemEngine: SelectEngine = SelectEngine()
    val engine: SelectEngine = applicationEngine
    val host: SelectHost = RoutedSelectHost()

    fun engineFor(ownerScope: OverlayOwnerScope): SelectEngine {
        return when (ownerScope) {
            OverlayOwnerScope.Application -> applicationEngine
            OverlayOwnerScope.System -> systemEngine
        }
    }

    private class RoutedSelectHost : SelectHost {
        override fun open(request: SelectOpenRequest) {
            val target = engineFor(request.ownerScope)
            val other = if (target === applicationEngine) systemEngine else applicationEngine
            other.close(request.owner)
            target.open(request)
        }

        override fun close(owner: Any) {
            applicationEngine.close(owner)
            systemEngine.close(owner)
        }

        override fun closeAll() {
            applicationEngine.closeAll()
            systemEngine.closeAll()
        }

        override fun isOpenFor(owner: Any): Boolean {
            return applicationEngine.isOpenFor(owner) || systemEngine.isOpenFor(owner)
        }

        override fun isOpen(): Boolean {
            return applicationEngine.isOpen() || systemEngine.isOpen()
        }
    }
}
