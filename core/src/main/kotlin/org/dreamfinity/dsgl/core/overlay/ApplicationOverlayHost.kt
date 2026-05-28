package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupEngine
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPortalController
import org.dreamfinity.dsgl.core.components.modal.internal.ModalPortalController
import org.dreamfinity.dsgl.core.contextmenu.ContextMenuEngine
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectEngine
import org.dreamfinity.dsgl.core.select.SelectPortalController
import org.dreamfinity.dsgl.core.style.StyleApplicationScope

class ApplicationOverlayHost(
    contextMenuEngine: ContextMenuEngine = DomainPortalServices.applicationContextMenuEngine,
    selectEngine: SelectEngine = DomainPortalServices.applicationSelectEngine,
    colorPickerEngine: ColorPickerPopupEngine = DomainPortalServices.applicationColorPickerEngine,
) : DomainSurfaceHost {
    override val surface: ScreenDomainSurface = ScreenDomainSurfaces.ApplicationPortal

    internal val rootNode: ApplicationOverlayRootNode = ApplicationOverlayRootNode()
    private val tree: DomTree =
        DomTree(
            root = rootNode,
            styleScope = StyleApplicationScope.Application,
        )
    internal val domInputRouter: LayerDomInputRouter =
        LayerDomInputRouter(
            rootProvider = { rootNode },
        )
    internal val contextMenuPortal: ContextMenuPortalController =
        ContextMenuPortalController(contextMenuEngine)
    internal val applicationSelectPortal: SelectPortalController =
        SelectPortalController(
            engine = selectEngine,
            ownerScope = OverlayOwnerScope.Application,
            entryId = "application.select",
        )
    internal val applicationColorPickerPortal: ColorPickerPortalController =
        ColorPickerPortalController(colorPickerEngine)
    internal val modalPortal: ModalPortalController = ModalPortalController()
    internal val floatingWindowPortal: ApplicationFloatingWindowPortalController =
        ApplicationFloatingWindowPortalController()
    private var modalPortalWasActive: Boolean = false

    override fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
        rootNode.setViewportBounds(
            width = viewportWidth.coerceAtLeast(1),
            height = viewportHeight.coerceAtLeast(1),
        )
        floatingWindowPortal.onInputFrame(viewportWidth, viewportHeight)
    }

    override fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        rootNode.setViewportBounds(width, height)
        floatingWindowPortal.sync(rootNode, width, height)
        modalPortal.sync(rootNode, width, height)
        closeStaleFloatingPortalsAfterModalOpen()
        tree.render(ctx, width, height)
        modalPortal.commitActivePortals()
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> = tree.paint(ctx, applyStyles = true)

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = domInputRouter.handleMouseMove(mouseX, mouseY)

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val isConsumedByDOM = domInputRouter.handleMouseDown(mouseX, mouseY, button)
        val isConsumedByPolicy = modalPortal.handlePointerPolicy(mouseX, mouseY, button, pressed = true)
        return isConsumedByDOM || isConsumedByPolicy
    }

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        val isConsumedByDOM = domInputRouter.handleMouseUp(mouseX, mouseY, button)
        val isConsumedByPolicy = modalPortal.handlePointerPolicy(mouseX, mouseY, button, pressed = false)
        return isConsumedByDOM || isConsumedByPolicy
    }

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        domInputRouter.handleMouseWheel(mouseX, mouseY, delta)

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = domInputRouter.handleKeyDown(keyCode, keyChar)

    override fun clearRefs() {
        tree.clearRefs()
        domInputRouter.clear()
        contextMenuPortal.close()
        applicationSelectPortal.close()
        applicationColorPickerPortal.close()
        modalPortal.close()
        floatingWindowPortal.clearRefs()
        modalPortalWasActive = false
    }

    private fun closeStaleFloatingPortalsAfterModalOpen() {
        val modalActive = modalPortal.hasActivePortal()
        if (modalActive && !modalPortalWasActive) {
            closeFloatingPortals()
        }
        modalPortalWasActive = modalActive
    }
}

internal fun ApplicationOverlayHost.debugRootBounds(): Rect = rootNode.bounds

fun ApplicationOverlayHost.syncPortalFrame(
    measureContext: UiMeasureContext,
    viewportWidth: Int,
    viewportHeight: Int,
    viewportScale: Float,
    mouseX: Int,
    mouseY: Int,
) {
    contextMenuPortal.onFrame(measureContext, viewportWidth, viewportHeight, viewportScale)
    applicationSelectPortal.onFrame(measureContext, viewportWidth, viewportHeight, viewportScale)
    applicationColorPickerPortal.onFrame(viewportWidth, viewportHeight, mouseX, mouseY)
    floatingWindowPortal.onFrameCursor(viewportWidth, viewportHeight, mouseX, mouseY)
}

fun ApplicationOverlayHost.appendPortalOverlayCommands(
    measureContext: UiMeasureContext,
    viewportWidth: Int,
    viewportHeight: Int,
    out: MutableList<RenderCommand>,
) {
    applicationSelectPortal.appendCommands(measureContext, viewportWidth, viewportHeight, out)
    contextMenuPortal.appendCommands(measureContext, viewportWidth, viewportHeight, out)
    applicationColorPickerPortal.appendCommands(measureContext, viewportWidth, viewportHeight, out)
}

fun ApplicationOverlayHost.closeFloatingPortals() {
    contextMenuPortal.close()
    applicationSelectPortal.close()
    applicationColorPickerPortal.close()
    floatingWindowPortal.close()
}

fun ApplicationOverlayHost.hasOpenContextMenuPortal(): Boolean = contextMenuPortal.isOpen()

fun ApplicationOverlayHost.hasOpenSelectPortal(): Boolean = applicationSelectPortal.isOpen()

fun ApplicationOverlayHost.hasOpenColorPickerPortal(): Boolean = applicationColorPickerPortal.isOpen

fun ApplicationOverlayHost.hasActiveColorPickerEyedropper(): Boolean = applicationColorPickerPortal.hasActiveEyedropper

fun ApplicationOverlayHost.captureColorPickerEyedropperSample() {
    applicationColorPickerPortal.captureEyedropperSample()
}

fun ApplicationOverlayHost.toggleFloatingWindowDemo(anchorX: Int, anchorY: Int) {
    floatingWindowPortal.toggle(anchorX, anchorY)
}

fun ApplicationOverlayHost.isFloatingWindowDemoOpen(): Boolean = floatingWindowPortal.open

fun ApplicationOverlayHost.hasDomPointerTargetAt(mouseX: Int, mouseY: Int): Boolean =
    domInputRouter.hasPointerTargetAt(mouseX, mouseY)

fun ApplicationOverlayHost.handlePortalKeyDownBeforeDom(keyCode: Int, keyChar: Char): Boolean =
    applicationColorPickerPortal.handleKeyDown(keyCode, keyChar)

fun ApplicationOverlayHost.handlePortalKeyDownAfterDom(keyCode: Int, keyChar: Char): Boolean =
    applicationSelectPortal.handleKeyDown(keyCode, keyChar) ||
        contextMenuPortal.handleKeyDown(keyCode)

fun ApplicationOverlayHost.handlePortalPointerBeforeDom(
    mouseX: Int,
    mouseY: Int,
    dWheel: Int,
    button: MouseButton?,
    pressed: Boolean,
): Boolean = handlePortalPointer(applicationColorPickerPortal, mouseX, mouseY, dWheel, button, pressed)

fun ApplicationOverlayHost.handlePortalPointerAfterDom(
    mouseX: Int,
    mouseY: Int,
    dWheel: Int,
    button: MouseButton?,
    pressed: Boolean,
): Boolean =
    handlePortalPointer(contextMenuPortal, mouseX, mouseY, dWheel, button, pressed) ||
        handlePortalPointer(applicationSelectPortal, mouseX, mouseY, dWheel, button, pressed)

private fun handlePortalPointer(
    portal: PortalPointerDispatch,
    mouseX: Int,
    mouseY: Int,
    dWheel: Int,
    button: MouseButton?,
    pressed: Boolean,
): Boolean {
    if (dWheel != 0 && portal.handleMouseWheel(mouseX, mouseY, dWheel)) return true
    if (button != null) {
        return if (pressed) {
            portal.handleMouseDown(mouseX, mouseY, button)
        } else {
            portal.handleMouseUp(mouseX, mouseY, button)
        }
    }
    return portal.handleMouseMove(mouseX, mouseY)
}

internal interface PortalPointerDispatch {
    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean
}

internal class ContextMenuPortalController(
    private val engine: ContextMenuEngine,
) : PortalPointerDispatch {
    private val portalHost: PortalHost =
        PortalHost(ScreenDomainSurfaces.ApplicationPortal)
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

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean =
        portalHost.dispatchInput { it.handleMouseMove(mouseX, mouseY) }

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        portalHost.dispatchInput { it.handleMouseDown(mouseX, mouseY, button) }

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        portalHost.dispatchInput { it.handleMouseUp(mouseX, mouseY, button) }

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
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
            surface = ScreenDomainSurfaces.ApplicationPortal,
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
