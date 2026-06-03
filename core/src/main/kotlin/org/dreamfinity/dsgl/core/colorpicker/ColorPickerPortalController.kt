package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.PortalDismissPolicy
import org.dreamfinity.dsgl.core.portal.PortalEntry
import org.dreamfinity.dsgl.core.portal.PortalEntryBounds
import org.dreamfinity.dsgl.core.portal.PortalEntryId
import org.dreamfinity.dsgl.core.portal.PortalEntryOrder
import org.dreamfinity.dsgl.core.portal.PortalEntryPlacement
import org.dreamfinity.dsgl.core.portal.PortalEntryState
import org.dreamfinity.dsgl.core.portal.PortalFocusPolicy
import org.dreamfinity.dsgl.core.portal.PortalHost
import org.dreamfinity.dsgl.core.portal.PortalInputPolicy
import org.dreamfinity.dsgl.core.portal.PortalPointerDispatch
import org.dreamfinity.dsgl.core.portal.ScreenDomainId
import org.dreamfinity.dsgl.core.portal.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.render.RenderCommand

internal class ColorPickerPortalController(
    private val engine: ColorPickerPopupEngine,
) : PortalPointerDispatch {
    private val portalHost: PortalHost =
        PortalHost(ScreenDomainSurfaces.ApplicationPortal)
    private val entry: ColorPickerPortalEntry = ColorPickerPortalEntry(engine)

    init {
        portalHost.register(entry)
    }

    fun onFrame(
        viewportWidth: Int,
        viewportHeight: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        entry.onFrame(viewportWidth, viewportHeight, mouseX, mouseY)
    }

    fun appendCommands(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>,
    ) {
        entry.updatePaintContext(viewportWidth, viewportHeight)
        entry.syncActivePlacement()
        out += portalHost.paint(measureContext)
    }

    fun close() {
        entry.close()
    }

    val isOpen: Boolean
        get() = entry.isApplicationPopupOpen

    val hasActiveEyedropper: Boolean
        get() = isOpen && engine.hasActiveEyedropper()

    fun captureEyedropperSample() {
        if (isOpen) {
            engine.captureEyedropperSample()
        }
    }

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

    fun handleKeyUp(keyCode: Int, keyChar: Char): Boolean =
        portalHost.dispatchInput { it.handleKeyUp(keyCode, keyChar) }
}

private class ColorPickerPortalEntry(
    private val engine: ColorPickerPopupEngine,
) : PortalEntry {
    override val state: PortalEntryState =
        PortalEntryState(
            id = PortalEntryId("application.color-picker"),
            ownerToken = engine,
            surface = ScreenDomainSurfaces.ApplicationPortal,
            order = PortalEntryOrder(zIndex = 0),
            dismissPolicy = PortalDismissPolicy.EscapeOrOutsidePointerDown,
            inputPolicy = PortalInputPolicy.ManualOnly,
            focusPolicy = PortalFocusPolicy.Preserve,
        )
    override val node: DOMNode? = null
    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1

    fun onFrame(
        viewportWidth: Int,
        viewportHeight: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        updatePaintContext(viewportWidth, viewportHeight)
        if (!isApplicationPopupOpen) {
            state.deactivate()
            return
        }
        engine.onFrame(this.viewportWidth, this.viewportHeight)
        engine.onCursorPosition(mouseX, mouseY)
        syncActivePlacement()
    }

    fun updatePaintContext(viewportWidth: Int, viewportHeight: Int) {
        this.viewportWidth = viewportWidth.coerceAtLeast(1)
        this.viewportHeight = viewportHeight.coerceAtLeast(1)
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> {
        if (!isApplicationPopupOpen) {
            state.deactivate()
            return emptyList()
        }
        val commands = ArrayList<RenderCommand>()
        engine.appendPortalCommands(commands)
        syncActivePlacement()
        return commands
    }

    override fun close() {
        if (isApplicationPopupOpen) {
            engine.closeAll()
        }
        state.deactivate()
    }

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean =
        if (isApplicationPopupOpen) {
            engine.handleMouseMove(mouseX, mouseY).also { syncActivePlacement() }
        } else {
            false
        }

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        if (isApplicationPopupOpen) {
            engine.handleMouseDown(mouseX, mouseY, button).also { syncActivePlacement() }
        } else {
            false
        }

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        if (isApplicationPopupOpen) {
            engine.handleMouseUp(mouseX, mouseY, button).also { syncActivePlacement() }
        } else {
            false
        }

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        if (isApplicationPopupOpen) {
            engine.handleMouseWheel(mouseX, mouseY, delta).also { syncActivePlacement() }
        } else {
            false
        }

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean =
        if (isApplicationPopupOpen) {
            engine.handleKeyDown(keyCode, keyChar).also { syncActivePlacement() }
        } else {
            false
        }

    fun syncActivePlacement() {
        if (!isApplicationPopupOpen) {
            state.deactivate()
            return
        }
        val panelRect = engine.debugActivePanelRect() ?: return
        state.activate(
            PortalEntryPlacement(
                anchorBounds = null,
                bounds =
                    PortalEntryBounds(
                        viewportBounds = Rect(0, 0, viewportWidth, viewportHeight),
                        entryBounds = panelRect,
                    ),
            ),
        )
    }

    val isApplicationPopupOpen: Boolean
        get() = engine.isOpen() && engine.debugActiveOwnerDomain() == ScreenDomainId.Application
}
