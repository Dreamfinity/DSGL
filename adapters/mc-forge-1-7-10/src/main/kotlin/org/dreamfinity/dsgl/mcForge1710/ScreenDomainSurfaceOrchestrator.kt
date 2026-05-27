package org.dreamfinity.dsgl.mcForge1710

import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurface
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.render.RenderCommand

internal class ScreenDomainSurfaceOrchestrator {
    private val paintSurfaces: List<ScreenDomainSurface> = ScreenDomainSurfaces.paintOrder
    private val inputSurfaces: List<ScreenDomainSurface> = ScreenDomainSurfaces.inputPriority

    fun composePaintCommands(
        applicationRoot: List<RenderCommand>,
        applicationPortal: List<RenderCommand>,
        systemRoot: List<RenderCommand> = emptyList(),
        systemPortal: List<RenderCommand>,
        debugRoot: List<RenderCommand>,
        debugPortal: List<RenderCommand> = emptyList(),
        out: MutableList<RenderCommand>,
        shouldRenderSurface: (ScreenDomainSurface) -> Boolean = { true },
    ) {
        out.clear()
        paintSurfaces.forEach { surface ->
            if (!shouldRenderSurface(surface)) return@forEach
            when (surface) {
                ScreenDomainSurfaces.ApplicationRoot -> out.addAll(applicationRoot)
                ScreenDomainSurfaces.ApplicationPortal -> out.addAll(applicationPortal)
                ScreenDomainSurfaces.SystemRoot -> out.addAll(systemRoot)
                ScreenDomainSurfaces.SystemPortal -> out.addAll(systemPortal)
                ScreenDomainSurfaces.DebugRoot -> out.addAll(debugRoot)
                ScreenDomainSurfaces.DebugPortal -> out.addAll(debugPortal)
            }
        }
    }

    fun firstInputConsumer(
        canConsume: (ScreenDomainSurface) -> Boolean,
        isSurfaceInputEnabled: (ScreenDomainSurface) -> Boolean = { true },
    ): ScreenDomainSurface? {
        inputSurfaces.forEach { surface ->
            if (!isSurfaceInputEnabled(surface)) return@forEach
            if (canConsume(surface)) return surface
        }
        return null
    }
}
