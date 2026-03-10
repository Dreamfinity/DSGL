package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.render.RenderCommand

enum class UiLayerId {
    ApplicationRoot,
    ApplicationOverlay,
    SystemOverlay
}

enum class OverlayOwnerScope {
    Application,
    System
}

object OverlayLayerContracts {
    val paintOrder: List<UiLayerId> = listOf(
        UiLayerId.ApplicationRoot,
        UiLayerId.ApplicationOverlay,
        UiLayerId.SystemOverlay
    )

    val inputPriority: List<UiLayerId> = listOf(
        UiLayerId.SystemOverlay,
        UiLayerId.ApplicationOverlay,
        UiLayerId.ApplicationRoot
    )

    fun resolveTransientLayer(ownerScope: OverlayOwnerScope): UiLayerId {
        return when (ownerScope) {
            OverlayOwnerScope.Application -> UiLayerId.ApplicationOverlay
            OverlayOwnerScope.System -> UiLayerId.SystemOverlay
        }
    }

    fun resolveTransientLayer(ownerScope: OverlayOwnerScope, cursorX: Int, cursorY: Int): UiLayerId {
        return resolveTransientLayer(ownerScope)
    }

    fun firstInputConsumer(canConsume: (UiLayerId) -> Boolean): UiLayerId? {
        inputPriority.forEach { layer ->
            if (canConsume(layer)) return layer
        }
        return null
    }

    fun composePaintCommands(
        applicationRoot: List<RenderCommand>,
        applicationOverlay: List<RenderCommand>,
        systemOverlay: List<RenderCommand>,
        out: MutableList<RenderCommand>
    ) {
        out.clear()
        paintOrder.forEach { layer ->
            when (layer) {
                UiLayerId.ApplicationRoot -> out.addAll(applicationRoot)
                UiLayerId.ApplicationOverlay -> out.addAll(applicationOverlay)
                UiLayerId.SystemOverlay -> out.addAll(systemOverlay)
            }
        }
    }
}
