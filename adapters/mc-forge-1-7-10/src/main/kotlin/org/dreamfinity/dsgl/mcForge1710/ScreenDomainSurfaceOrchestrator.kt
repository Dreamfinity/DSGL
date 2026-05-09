package org.dreamfinity.dsgl.mcForge1710

import org.dreamfinity.dsgl.core.overlay.OverlayLayerContracts
import org.dreamfinity.dsgl.core.overlay.UiLayerId
import org.dreamfinity.dsgl.core.render.RenderCommand

internal class ScreenDomainSurfaceOrchestrator {
    private val paintSurfaces: List<RuntimeDomainSurface> =
        OverlayLayerContracts.paintOrder.map(RuntimeDomainSurface::fromLayer)
    private val inputSurfaces: List<RuntimeDomainSurface> =
        OverlayLayerContracts.inputPriority.map(RuntimeDomainSurface::fromLayer)

    fun composePaintCommands(
        applicationRoot: List<RenderCommand>,
        applicationPortal: List<RenderCommand>,
        systemPortal: List<RenderCommand>,
        debugRoot: List<RenderCommand>,
        out: MutableList<RenderCommand>,
        shouldRenderLayer: (UiLayerId) -> Boolean = { true },
    ) {
        out.clear()
        paintSurfaces.forEach { surface ->
            if (!shouldRenderLayer(surface.layer)) return@forEach
            when (surface) {
                RuntimeDomainSurface.ApplicationRoot -> out.addAll(applicationRoot)
                RuntimeDomainSurface.ApplicationPortal -> out.addAll(applicationPortal)
                RuntimeDomainSurface.SystemPortal -> out.addAll(systemPortal)
                RuntimeDomainSurface.DebugRoot -> out.addAll(debugRoot)
            }
        }
    }

    fun firstInputConsumer(
        canConsume: (UiLayerId) -> Boolean,
        isLayerInputEnabled: (UiLayerId) -> Boolean = { true },
    ): UiLayerId? {
        inputSurfaces.forEach { surface ->
            if (!isLayerInputEnabled(surface.layer)) return@forEach
            if (canConsume(surface.layer)) return surface.layer
        }
        return null
    }
}

private enum class RuntimeDomainSurface(
    val layer: UiLayerId,
) {
    ApplicationRoot(UiLayerId.ApplicationRoot),
    ApplicationPortal(UiLayerId.ApplicationOverlay),
    SystemPortal(UiLayerId.SystemOverlay),
    DebugRoot(UiLayerId.Debug),
    ;

    companion object {
        fun fromLayer(layer: UiLayerId): RuntimeDomainSurface =
            when (layer) {
                UiLayerId.ApplicationRoot -> ApplicationRoot
                UiLayerId.ApplicationOverlay -> ApplicationPortal
                UiLayerId.SystemOverlay -> SystemPortal
                UiLayerId.Debug -> DebugRoot
            }
    }
}
