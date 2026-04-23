package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.render.RenderCommand

enum class UiLayerId {
    Debug,
    ApplicationRoot,
    ApplicationOverlay,
    SystemOverlay,
}

enum class OverlayOwnerScope {
    Application,
    System,
}

object OverlayLayerContracts {
    val paintOrder: List<UiLayerId> =
        listOf(
            UiLayerId.ApplicationRoot,
            UiLayerId.ApplicationOverlay,
            UiLayerId.SystemOverlay,
            UiLayerId.Debug,
        )

    val inputPriority: List<UiLayerId> =
        listOf(
            UiLayerId.Debug,
            UiLayerId.SystemOverlay,
            UiLayerId.ApplicationOverlay,
            UiLayerId.ApplicationRoot,
        )

    fun resolveTransientLayer(ownerScope: OverlayOwnerScope): UiLayerId =
        when (ownerScope) {
            OverlayOwnerScope.Application -> UiLayerId.ApplicationOverlay
            OverlayOwnerScope.System -> UiLayerId.SystemOverlay
        }

    fun resolveTransientLayer(ownerScope: OverlayOwnerScope, cursorX: Int, cursorY: Int): UiLayerId =
        resolveTransientLayer(ownerScope)

    fun firstInputConsumer(
        canConsume: (UiLayerId) -> Boolean,
        isLayerInputEnabled: (UiLayerId) -> Boolean = { true },
    ): UiLayerId? {
        inputPriority.forEach { layer ->
            if (!isLayerInputEnabled(layer)) return@forEach
            if (canConsume(layer)) return layer
        }
        return null
    }

    fun composePaintCommands(
        applicationRoot: List<RenderCommand>,
        applicationOverlay: List<RenderCommand>,
        systemOverlay: List<RenderCommand>,
        debug: List<RenderCommand>,
        out: MutableList<RenderCommand>,
        shouldRenderLayer: (UiLayerId) -> Boolean = { true },
    ) {
        out.clear()
        paintOrder.forEach { layer ->
            if (!shouldRenderLayer(layer)) return@forEach
            when (layer) {
                UiLayerId.ApplicationRoot -> out.addAll(applicationRoot)
                UiLayerId.ApplicationOverlay -> out.addAll(applicationOverlay)
                UiLayerId.SystemOverlay -> out.addAll(systemOverlay)
                UiLayerId.Debug -> out.addAll(debug)
            }
        }
    }
}
