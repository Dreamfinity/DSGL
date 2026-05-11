package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.OverlayLayerContracts
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.overlay.PortalDismissPolicy
import org.dreamfinity.dsgl.core.overlay.PortalEntry
import org.dreamfinity.dsgl.core.overlay.PortalEntryBounds
import org.dreamfinity.dsgl.core.overlay.PortalEntryId
import org.dreamfinity.dsgl.core.overlay.PortalEntryOrder
import org.dreamfinity.dsgl.core.overlay.PortalEntryPlacement
import org.dreamfinity.dsgl.core.overlay.PortalEntryState
import org.dreamfinity.dsgl.core.overlay.PortalFocusPolicy
import org.dreamfinity.dsgl.core.overlay.PortalHost
import org.dreamfinity.dsgl.core.overlay.PortalInputPolicy
import org.dreamfinity.dsgl.core.overlay.PortalPointerDispatch
import org.dreamfinity.dsgl.core.render.RenderCommand

internal class SelectPortalController(
    private val engine: SelectEngine,
    ownerScope: OverlayOwnerScope,
    entryId: String,
) : PortalPointerDispatch {
    private val portalHost: PortalHost =
        PortalHost(OverlayLayerContracts.portalSurfaceForOwner(ownerScope))
    private val entry: SelectPortalEntry =
        SelectPortalEntry(
            engine = engine,
            ownerScope = ownerScope,
            entryId = entryId,
        )

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
        entry.syncActivePlacement()
        out += portalHost.paint(measureContext)
    }

    fun close() {
        entry.close()
    }

    fun isOpen(): Boolean = engine.isOpen()

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean =
        portalHost.dispatchInput { it.handleMouseMove(mouseX, mouseY) }

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        portalHost.dispatchInput { it.handleMouseDown(mouseX, mouseY, button) }

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        portalHost.dispatchInput { it.handleMouseUp(mouseX, mouseY, button) }

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        portalHost.dispatchInput { it.handleMouseWheel(mouseX, mouseY, delta) }

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean =
        portalHost.dispatchInput { it.handleKeyDown(keyCode, keyChar) }
}

private class SelectPortalEntry(
    private val engine: SelectEngine,
    ownerScope: OverlayOwnerScope,
    entryId: String,
) : PortalEntry {
    override val state: PortalEntryState =
        PortalEntryState(
            id = PortalEntryId(entryId),
            ownerToken = engine,
            surface = OverlayLayerContracts.portalSurfaceForOwner(ownerScope),
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

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean =
        engine.handleMouseMove(mouseX, mouseY).also { syncActivePlacement() }

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        engine.handleMouseDown(mouseX, mouseY, button).also { syncActivePlacement() }

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        engine.handleMouseUp(mouseX, mouseY, button).also { syncActivePlacement() }

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        engine.handleMouseWheel(mouseX, mouseY, delta).also { syncActivePlacement() }

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean =
        engine.handleKeyDown(keyCode, keyChar).also { syncActivePlacement() }

    fun syncActivePlacement() {
        if (!engine.isOpen()) {
            state.deactivate()
            return
        }
        val owner = engine.snapshot().owner ?: return
        val panelRect = engine.debugPanelRect(owner) ?: return
        val anchorRect = engine.debugAnchorRect(owner)
        state.activate(
            PortalEntryPlacement(
                anchorBounds = anchorRect,
                bounds =
                    PortalEntryBounds(
                        viewportBounds = Rect(0, 0, viewportWidth, viewportHeight),
                        entryBounds = panelRect,
                    ),
            ),
        )
    }
}
