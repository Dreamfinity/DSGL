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

internal enum class ScreenDomainId {
    Application,
    System,
    Debug,
}

internal enum class ScreenDomainSurfaceRole {
    Root,
    Portal,
}

internal data class ScreenDomainSurface(
    val domain: ScreenDomainId,
    val role: ScreenDomainSurfaceRole,
)

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

    internal val paintSurfaces: List<ScreenDomainSurface> = paintOrder.map(::domainSurfaceForLayer)

    internal val inputSurfaces: List<ScreenDomainSurface> = inputPriority.map(::domainSurfaceForLayer)

    fun resolveTransientLayer(ownerScope: OverlayOwnerScope): UiLayerId =
        when (ownerScope) {
            OverlayOwnerScope.Application -> UiLayerId.ApplicationOverlay
            OverlayOwnerScope.System -> UiLayerId.SystemOverlay
        }

    internal fun domainSurfaceForLayer(layer: UiLayerId): ScreenDomainSurface =
        when (layer) {
            UiLayerId.ApplicationRoot ->
                ScreenDomainSurface(
                    domain = ScreenDomainId.Application,
                    role = ScreenDomainSurfaceRole.Root,
                )

            UiLayerId.ApplicationOverlay ->
                ScreenDomainSurface(
                    domain = ScreenDomainId.Application,
                    role = ScreenDomainSurfaceRole.Portal,
                )

            UiLayerId.SystemOverlay ->
                ScreenDomainSurface(
                    domain = ScreenDomainId.System,
                    role = ScreenDomainSurfaceRole.Portal,
                )

            UiLayerId.Debug ->
                ScreenDomainSurface(
                    domain = ScreenDomainId.Debug,
                    role = ScreenDomainSurfaceRole.Root,
                )
        }

    internal fun portalSurfaceForOwner(ownerScope: OverlayOwnerScope): ScreenDomainSurface =
        domainSurfaceForLayer(resolveTransientLayer(ownerScope))

    @Suppress("UnusedParameter")
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
