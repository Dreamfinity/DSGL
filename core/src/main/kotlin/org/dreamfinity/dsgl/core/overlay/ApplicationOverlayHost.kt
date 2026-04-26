package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuRuntime
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleApplicationScope

class ApplicationOverlayHost : OverlayLayerHost {
    override val layerId: UiLayerId = UiLayerId.ApplicationOverlay

    private val rootNode: ApplicationOverlayRootNode = ApplicationOverlayRootNode()
    private val tree: DomTree =
        DomTree(
            root = rootNode,
            styleScope = StyleApplicationScope.Application,
        )

    override fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        rootNode.setViewportBounds(width, height)
        tree.render(ctx, width, height)
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> = tree.paint(ctx, applyStyles = true)

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = false

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean = false

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = false

    override fun clearRefs() {
        tree.clearRefs()
    }

    internal fun debugRootBounds(): Rect = rootNode.bounds
}

fun ApplicationOverlayHost.contextMenuOnFrame(
    measureContext: UiMeasureContext,
    viewportWidth: Int,
    viewportHeight: Int,
    viewportScale: Float,
) {
    ContextMenuRuntime.engine.onFrame(
        measureContext = measureContext,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        viewportScale = viewportScale,
    )
}

fun ApplicationOverlayHost.appendContextMenuOverlayCommands(
    measureContext: UiMeasureContext,
    viewportWidth: Int,
    viewportHeight: Int,
    out: MutableList<RenderCommand>,
) {
    ContextMenuRuntime.engine.appendOverlayCommands(
        measureContext = measureContext,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        out = out,
    )
}

fun ApplicationOverlayHost.closeContextMenus() {
    ContextMenuRuntime.engine.closeAll()
}

fun ApplicationOverlayHost.isContextMenuOpen(): Boolean = ContextMenuRuntime.engine.isOpen()

fun ApplicationOverlayHost.handleContextMenuMouseMove(mouseX: Int, mouseY: Int): Boolean =
    ContextMenuRuntime.engine.handleMouseMove(mouseX, mouseY)

fun ApplicationOverlayHost.handleContextMenuMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
    ContextMenuRuntime.engine.handleMouseDown(mouseX, mouseY, button)

fun ApplicationOverlayHost.handleContextMenuMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
    ContextMenuRuntime.engine.handleMouseUp(mouseX, mouseY, button)

fun ApplicationOverlayHost.handleContextMenuMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
    ContextMenuRuntime.engine.handleMouseWheel(mouseX, mouseY, delta)

fun ApplicationOverlayHost.handleContextMenuKeyDown(keyCode: Int): Boolean =
    ContextMenuRuntime.engine.handleKeyDown(keyCode)
