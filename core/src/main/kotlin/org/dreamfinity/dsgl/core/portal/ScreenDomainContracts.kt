package org.dreamfinity.dsgl.core.portal

import org.dreamfinity.dsgl.core.render.RenderCommand

enum class ScreenDomainId {
    Application,
    System,
    Debug,
}

enum class ScreenDomainSurfaceRole {
    Root,
    Portal,
}

data class ScreenDomainSurface(
    val domain: ScreenDomainId,
    val role: ScreenDomainSurfaceRole,
)

object ScreenDomainSurfaces {
    val ApplicationRoot: ScreenDomainSurface =
        ScreenDomainSurface(ScreenDomainId.Application, ScreenDomainSurfaceRole.Root)
    val ApplicationPortal: ScreenDomainSurface =
        ScreenDomainSurface(ScreenDomainId.Application, ScreenDomainSurfaceRole.Portal)
    val SystemRoot: ScreenDomainSurface =
        ScreenDomainSurface(ScreenDomainId.System, ScreenDomainSurfaceRole.Root)
    val SystemPortal: ScreenDomainSurface =
        ScreenDomainSurface(ScreenDomainId.System, ScreenDomainSurfaceRole.Portal)
    val DebugRoot: ScreenDomainSurface =
        ScreenDomainSurface(ScreenDomainId.Debug, ScreenDomainSurfaceRole.Root)
    val DebugPortal: ScreenDomainSurface =
        ScreenDomainSurface(ScreenDomainId.Debug, ScreenDomainSurfaceRole.Portal)

    val allDomains: List<ScreenDomainId> =
        listOf(
            ScreenDomainId.Application,
            ScreenDomainId.System,
            ScreenDomainId.Debug,
        )

    val allSurfaces: List<ScreenDomainSurface> =
        allDomains.flatMap { domain ->
            listOf(
                rootSurface(domain),
                portalSurface(domain),
            )
        }

    val paintOrder: List<ScreenDomainSurface> =
        listOf(
            ApplicationRoot,
            ApplicationPortal,
            SystemRoot,
            SystemPortal,
            DebugRoot,
            DebugPortal,
        )

    val inputPriority: List<ScreenDomainSurface> =
        listOf(
            DebugPortal,
            DebugRoot,
            SystemPortal,
            SystemRoot,
            ApplicationPortal,
            ApplicationRoot,
        )

    fun rootSurface(domain: ScreenDomainId): ScreenDomainSurface =
        when (domain) {
            ScreenDomainId.Application -> ApplicationRoot
            ScreenDomainId.System -> SystemRoot
            ScreenDomainId.Debug -> DebugRoot
        }

    fun portalSurface(domain: ScreenDomainId): ScreenDomainSurface =
        when (domain) {
            ScreenDomainId.Application -> ApplicationPortal
            ScreenDomainId.System -> SystemPortal
            ScreenDomainId.Debug -> DebugPortal
        }

    fun portalSurfaceForDomain(ownerDomain: ScreenDomainId): ScreenDomainSurface = portalSurface(ownerDomain)

    @Suppress("UnusedParameter")
    fun portalSurfaceForDomain(ownerDomain: ScreenDomainId, cursorX: Int, cursorY: Int): ScreenDomainSurface =
        portalSurfaceForDomain(ownerDomain)

    fun firstInputConsumer(
        canConsume: (ScreenDomainSurface) -> Boolean,
        isSurfaceInputEnabled: (ScreenDomainSurface) -> Boolean = { true },
    ): ScreenDomainSurface? {
        inputPriority.forEach { surface ->
            if (!isSurfaceInputEnabled(surface)) return@forEach
            if (canConsume(surface)) return surface
        }
        return null
    }

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
        paintOrder.forEach { surface ->
            if (!shouldRenderSurface(surface)) return@forEach
            when (surface) {
                ApplicationRoot -> out.addAll(applicationRoot)
                ApplicationPortal -> out.addAll(applicationPortal)
                SystemRoot -> out.addAll(systemRoot)
                SystemPortal -> out.addAll(systemPortal)
                DebugRoot -> out.addAll(debugRoot)
                DebugPortal -> out.addAll(debugPortal)
            }
        }
    }
}
