package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.Size
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.event.MouseDownEvent
import org.dreamfinity.dsgl.core.event.MouseMoveEvent
import org.dreamfinity.dsgl.core.event.MouseUpEvent
import org.dreamfinity.dsgl.core.event.MouseWheelEvent
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
import org.dreamfinity.dsgl.core.overlay.PortalInsidePointerPolicy
import org.dreamfinity.dsgl.core.overlay.PortalPointerDispatch
import org.dreamfinity.dsgl.core.overlay.PortalPointerPolicyResult
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.overlay.evaluateOutsidePointerDown
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand

@Suppress("TooManyFunctions")
internal class SelectPortalController(
    private val engine: SelectEngine,
    ownerScope: OverlayOwnerScope,
    entryId: String,
) : PortalPointerDispatch {
    private val portalHost: PortalHost =
        PortalHost(ScreenDomainSurfaces.portalSurfaceForOwner(ownerScope))
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
        portalHost.render(measureContext, viewportWidth, viewportHeight)
    }

    fun appendCommands(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>,
    ) {
        entry.onFrame(measureContext, viewportWidth, viewportHeight, viewportScale = 1f)
        portalHost.render(measureContext, viewportWidth, viewportHeight)
        out += portalHost.paint(measureContext)
    }

    fun close() {
        entry.close()
    }

    fun isOpen(): Boolean = engine.isOpen()

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean =
        portalHost.dispatchInput { it.handleMouseMove(mouseX, mouseY) }

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        if (portalHost.dispatchInput { it.handleMouseDown(mouseX, mouseY, button) }) {
            true
        } else {
            val outside = portalHost.evaluateOutsidePointerDown(mouseX, mouseY)
            if (outside?.shouldClose == true) {
                outside.entry.state
                    .dismiss(outside.entry)
            }
            false
        }

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        portalHost.dispatchInput { it.handleMouseUp(mouseX, mouseY, button) }

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        portalHost.dispatchInput { it.handleMouseWheel(mouseX, mouseY, delta) }

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean =
        portalHost.dispatchInput { it.handleKeyDown(keyCode, keyChar) }

    fun handleKeyUp(keyCode: Int, keyChar: Char): Boolean =
        portalHost.dispatchInput { it.handleKeyUp(keyCode, keyChar) }

    internal fun debugPortalState(mouseX: Int, mouseY: Int): SelectPortalDebugState =
        SelectPortalDebugState(
            node = entry.node,
            state = entry.state,
            outsidePointerPolicy = portalHost.evaluateOutsidePointerDown(mouseX, mouseY),
        )
}

internal data class SelectPortalDebugState(
    val node: DOMNode,
    val state: PortalEntryState,
    val outsidePointerPolicy: PortalPointerPolicyResult?,
)

private class SelectPortalEntry(
    private val engine: SelectEngine,
    ownerScope: OverlayOwnerScope,
    entryId: String,
) : PortalEntry {
    override val state: PortalEntryState =
        PortalEntryState(
            id = PortalEntryId(entryId),
            ownerToken = engine,
            surface = ScreenDomainSurfaces.portalSurfaceForOwner(ownerScope),
            order = PortalEntryOrder(zIndex = 0),
            dismissPolicy = PortalDismissPolicy.EscapeOrOutsidePointerDown,
            inputPolicy = PortalInputPolicy.DomOnly,
            focusPolicy = PortalFocusPolicy.Preserve,
            insidePointerPolicy = PortalInsidePointerPolicy.ConsumePointerDown,
        ).apply {
            dismissAction = {
                val owner = engine.snapshot().owner
                if (owner != null) {
                    engine.close(owner)
                } else {
                    engine.closeAll()
                }
                syncActivePlacement()
            }
        }
    private val popupNode: SelectPortalNode =
        SelectPortalNode(
            engine = engine,
            onInputHandled = {
                if (!clearingDomInputRouter) {
                    syncActivePlacement()
                }
            },
        )
    override val node: DOMNode = popupNode
    private val domInputRouter: LayerDomInputRouter = LayerDomInputRouter { node }
    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1
    private var measureContext: UiMeasureContext? = null
    private var clearingDomInputRouter: Boolean = false

    fun onFrame(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        viewportScale: Float,
    ) {
        this.measureContext = measureContext
        this.viewportWidth = viewportWidth.coerceAtLeast(1)
        this.viewportHeight = viewportHeight.coerceAtLeast(1)
        popupNode.viewportWidth = this.viewportWidth
        popupNode.viewportHeight = this.viewportHeight
        engine.onFrame(
            measureContext = measureContext,
            viewportWidth = this.viewportWidth,
            viewportHeight = this.viewportHeight,
            viewportScale = viewportScale,
        )
        syncActivePlacement()
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> {
        if (!engine.isOpen()) {
            state.deactivate()
            return emptyList()
        }
        val commands = ArrayList<RenderCommand>()
        node.appendRenderCommands(measureContext ?: ctx, commands)
        syncActivePlacement()
        return commands
    }

    override fun close() {
        clearingDomInputRouter = true
        try {
            domInputRouter.clear()
        } finally {
            clearingDomInputRouter = false
        }
        engine.closeAll()
        state.deactivate()
    }

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean =
        dispatchWithSyncedPlacement {
            domInputRouter.handleMouseMove(mouseX, mouseY)
        }

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        dispatchWithSyncedPlacement {
            domInputRouter.handleMouseDown(mouseX, mouseY, button)
        }

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        dispatchWithSyncedPlacement {
            domInputRouter.handleMouseUp(mouseX, mouseY, button)
        }

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        dispatchWithSyncedPlacement {
            domInputRouter.handleMouseWheel(mouseX, mouseY, delta)
        }

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean =
        engine.handleKeyDown(keyCode, keyChar).also { syncActivePlacement() }

    fun syncActivePlacement() {
        if (!engine.isOpen()) {
            clearingDomInputRouter = true
            try {
                domInputRouter.clear()
            } finally {
                clearingDomInputRouter = false
            }
            state.deactivate()
            return
        }
        val owner = engine.snapshot().owner ?: return
        val panelRect = engine.debugPanelRect(owner) ?: return
        val anchorRect = engine.debugAnchorRect(owner)
        popupNode.bounds = panelRect
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

    private fun dispatchWithSyncedPlacement(dispatch: () -> Boolean): Boolean {
        syncActivePlacement()
        val handled =
            FocusManager.preservePointerFocus {
                dispatch()
            }
        syncActivePlacement()
        return handled
    }
}

private class SelectPortalNode(
    private val engine: SelectEngine,
    private val onInputHandled: () -> Unit,
) : DOMNode(key = "dsgl-select-portal-popup") {
    override val styleType: String = "select-portal"
    var viewportWidth: Int = 1
    var viewportHeight: Int = 1

    init {
        onMouseMove = ::handleMove
        onMouseDown = ::handleDown
        onMouseUp = ::handleUp
        onMouseWheel = ::handleWheel
    }

    override fun measure(ctx: UiMeasureContext): Size {
        val owner = engine.snapshot().owner
        val panel = owner?.let(engine::debugPanelRect)
        return Size(panel?.width ?: 0, panel?.height ?: 0)
    }

    override fun buildRenderCommands(ctx: UiMeasureContext, out: MutableList<RenderCommand>) {
        if (!engine.isOpen()) return
        engine.appendOverlayCommands(
            measureContext = ctx,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            out = out,
        )
    }

    override fun shouldCapturePointerDrag(mouseX: Int, mouseY: Int): Boolean =
        engine.isScrollbarDragging() || engine.shouldCaptureScrollbarDrag(mouseX, mouseY)

    override fun continuePointerCapture(
        mouseX: Int,
        mouseY: Int,
        mouseDX: Int,
        mouseDY: Int,
        button: MouseButton,
    ) {
        if (engine.handleMouseMove(mouseX, mouseY)) {
            onInputHandled()
        }
    }

    override fun endPointerCapture(mouseX: Int, mouseY: Int, button: MouseButton) {
        if (engine.handleMouseUp(mouseX, mouseY, button)) {
            onInputHandled()
        }
    }

    override fun cancelPointerCapture() {
        engine.cancelScrollbarDrag()
        onInputHandled()
    }

    private fun handleMove(event: MouseMoveEvent) {
        if (engine.handleMouseMove(event.mouseX, event.mouseY)) {
            event.cancelled = true
            onInputHandled()
        }
    }

    private fun handleDown(event: MouseDownEvent) {
        if (engine.handlePortalMouseDown(event.mouseX, event.mouseY, event.mouseButton)) {
            event.cancelled = true
            onInputHandled()
        }
    }

    private fun handleUp(event: MouseUpEvent) {
        if (engine.handleMouseUp(event.mouseX, event.mouseY, event.mouseButton)) {
            event.cancelled = true
            onInputHandled()
        }
    }

    private fun handleWheel(event: MouseWheelEvent) {
        if (engine.handleMouseWheel(event.mouseX, event.mouseY, event.dWheel)) {
            event.cancelled = true
            onInputHandled()
        }
    }
}
