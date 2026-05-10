package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuEngine
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuRuntime
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectPortalController
import org.dreamfinity.dsgl.core.select.SelectRuntime
import org.dreamfinity.dsgl.core.style.StyleApplicationScope

class ApplicationOverlayHost : OverlayLayerHost {
    override val layerId: UiLayerId = UiLayerId.ApplicationOverlay

    private val rootNode: ApplicationOverlayRootNode = ApplicationOverlayRootNode()
    private val tree: DomTree =
        DomTree(
            root = rootNode,
            styleScope = StyleApplicationScope.Application,
        )
    private val domInputRouter: LayerDomInputRouter =
        LayerDomInputRouter(
            rootProvider = { rootNode },
        )
    internal val contextMenuPortal: ContextMenuPortalController =
        ContextMenuPortalController(ContextMenuRuntime.engine)
    internal val applicationSelectPortal: SelectPortalController =
        SelectPortalController(
            engine = SelectRuntime.applicationEngine,
            ownerScope = OverlayOwnerScope.Application,
            entryId = "application.select",
        )

    override fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
        rootNode.setViewportBounds(
            width = viewportWidth.coerceAtLeast(1),
            height = viewportHeight.coerceAtLeast(1),
        )
    }

    override fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        rootNode.setViewportBounds(width, height)
        tree.render(ctx, width, height)
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> = tree.paint(ctx, applyStyles = true)

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = domInputRouter.handleMouseMove(mouseX, mouseY)

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        domInputRouter.handleMouseDown(mouseX, mouseY, button)

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        domInputRouter.handleMouseUp(mouseX, mouseY, button)

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        domInputRouter.handleMouseWheel(mouseX, mouseY, delta)

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = domInputRouter.handleKeyDown(keyCode, keyChar)

    override fun clearRefs() {
        tree.clearRefs()
        domInputRouter.clear()
        contextMenuPortal.close()
        applicationSelectPortal.close()
    }

    internal fun debugRootBounds(): Rect = rootNode.bounds
}

internal class ContextMenuPortalController(
    private val engine: ContextMenuEngine,
) {
    private val portalHost: PortalHost =
        PortalHost(OverlayLayerContracts.portalSurfaceForOwner(OverlayOwnerScope.Application))
    private val entry: ContextMenuPortalEntry = ContextMenuPortalEntry(engine)

    init {
        portalHost.register(entry)
    }

    fun onFrame(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        viewportScale: Float,
    ) {
        entry.onFrame(measureContext, viewportWidth, viewportHeight, viewportScale)
    }

    fun appendCommands(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>,
    ) {
        entry.updatePaintContext(measureContext, viewportWidth, viewportHeight)
        out += portalHost.paint(measureContext)
    }

    fun close() {
        entry.close()
    }

    fun isOpen(): Boolean = engine.isOpen()

    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean =
        portalHost.dispatchInput { it.handleMouseMove(mouseX, mouseY) }

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        portalHost.dispatchInput { it.handleMouseDown(mouseX, mouseY, button) }

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        portalHost.dispatchInput { it.handleMouseUp(mouseX, mouseY, button) }

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        portalHost.dispatchInput { it.handleMouseWheel(mouseX, mouseY, delta) }

    fun handleKeyDown(keyCode: Int): Boolean =
        portalHost.dispatchInput {
            it.handleKeyDown(keyCode, Char.MIN_VALUE)
        }
}

private class ContextMenuPortalEntry(
    private val engine: ContextMenuEngine,
) : PortalEntry {
    override val state: PortalEntryState =
        PortalEntryState(
            id = PortalEntryId("application.context-menu"),
            ownerToken = engine,
            surface = OverlayLayerContracts.portalSurfaceForOwner(OverlayOwnerScope.Application),
            order = PortalEntryOrder(zIndex = 0),
            dismissPolicy = PortalDismissPolicy.EscapeOrOutsidePointerDown,
            inputPolicy = PortalInputPolicy.ManualOnly,
            focusPolicy = PortalFocusPolicy.Preserve,
        )
    override val node: DOMNode? = null
    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1
    private var measureContext: UiMeasureContext? = null

    fun onFrame(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        viewportScale: Float,
    ) {
        updatePaintContext(measureContext, viewportWidth, viewportHeight)
        engine.onFrame(
            measureContext = measureContext,
            viewportWidth = this.viewportWidth,
            viewportHeight = this.viewportHeight,
            viewportScale = viewportScale,
        )
        syncActivePlacement()
    }

    fun updatePaintContext(measureContext: UiMeasureContext, viewportWidth: Int, viewportHeight: Int) {
        this.measureContext = measureContext
        this.viewportWidth = viewportWidth.coerceAtLeast(1)
        this.viewportHeight = viewportHeight.coerceAtLeast(1)
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> {
        if (!engine.isOpen()) {
            state.deactivate()
            return emptyList()
        }
        val commands = ArrayList<RenderCommand>()
        engine.appendOverlayCommands(
            measureContext = measureContext ?: ctx,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            out = commands,
        )
        syncActivePlacement()
        return commands
    }

    override fun close() {
        engine.closeAll()
        state.deactivate()
    }

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = engine.handleMouseMove(mouseX, mouseY)

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        engine.handleMouseDown(mouseX, mouseY, button).also { syncActivePlacement() }

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        engine.handleMouseUp(mouseX, mouseY, button).also { syncActivePlacement() }

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        engine.handleMouseWheel(mouseX, mouseY, delta).also { syncActivePlacement() }

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean =
        engine.handleKeyDown(keyCode).also { syncActivePlacement() }

    private fun syncActivePlacement() {
        if (!engine.isOpen()) {
            state.deactivate()
            return
        }
        val panelRect = engine.debugPanelRect(0) ?: return
        state.activate(
            PortalEntryPlacement(
                anchorBounds = null,
                bounds =
                    PortalEntryBounds(
                        viewportBounds = Rect(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1)),
                        entryBounds = panelRect,
                    ),
            ),
        )
    }
}

fun ApplicationOverlayHost.contextMenuOnFrame(
    measureContext: UiMeasureContext,
    viewportWidth: Int,
    viewportHeight: Int,
    viewportScale: Float,
) {
    contextMenuPortal.onFrame(
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
    contextMenuPortal.appendCommands(
        measureContext = measureContext,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        out = out,
    )
}

fun ApplicationOverlayHost.closeContextMenus() {
    contextMenuPortal.close()
}

fun ApplicationOverlayHost.isContextMenuOpen(): Boolean = contextMenuPortal.isOpen()

fun ApplicationOverlayHost.handleContextMenuMouseMove(mouseX: Int, mouseY: Int): Boolean =
    contextMenuPortal.handleMouseMove(mouseX, mouseY)

fun ApplicationOverlayHost.handleContextMenuMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
    contextMenuPortal.handleMouseDown(mouseX, mouseY, button)

fun ApplicationOverlayHost.handleContextMenuMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
    contextMenuPortal.handleMouseUp(mouseX, mouseY, button)

fun ApplicationOverlayHost.handleContextMenuMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
    contextMenuPortal.handleMouseWheel(mouseX, mouseY, delta)

fun ApplicationOverlayHost.handleContextMenuKeyDown(keyCode: Int): Boolean = contextMenuPortal.handleKeyDown(keyCode)

fun ApplicationOverlayHost.applicationSelectOnFrame(
    measureContext: UiMeasureContext,
    viewportWidth: Int,
    viewportHeight: Int,
    viewportScale: Float,
) {
    applicationSelectPortal.onFrame(
        measureContext = measureContext,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        viewportScale = viewportScale,
    )
}

fun ApplicationOverlayHost.appendApplicationSelectOverlayCommands(
    measureContext: UiMeasureContext,
    viewportWidth: Int,
    viewportHeight: Int,
    out: MutableList<RenderCommand>,
) {
    applicationSelectPortal.appendCommands(
        measureContext = measureContext,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        out = out,
    )
}

fun ApplicationOverlayHost.isApplicationSelectOpen(): Boolean = applicationSelectPortal.isOpen()

fun ApplicationOverlayHost.handleApplicationSelectKeyDown(keyCode: Int, keyChar: Char): Boolean =
    applicationSelectPortal.handleKeyDown(keyCode, keyChar)

fun ApplicationOverlayHost.handleApplicationSelectMouseMove(mouseX: Int, mouseY: Int): Boolean =
    applicationSelectPortal.handleMouseMove(mouseX, mouseY)

fun ApplicationOverlayHost.handleApplicationSelectMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
    applicationSelectPortal.handleMouseDown(mouseX, mouseY, button)

fun ApplicationOverlayHost.handleApplicationSelectMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
    applicationSelectPortal.handleMouseUp(mouseX, mouseY, button)

fun ApplicationOverlayHost.handleApplicationSelectMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
    applicationSelectPortal.handleMouseWheel(mouseX, mouseY, delta)
