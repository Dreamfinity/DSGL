package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.colorpicker.internal.ColorPickerPopupMount
import org.dreamfinity.dsgl.core.colorpicker.internal.ColorPickerPopupOverlayNode
import org.dreamfinity.dsgl.core.colorpicker.internal.SystemColorPickerPortalService
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.SingleLineInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorPanelState
import org.dreamfinity.dsgl.core.inspector.internal.SystemInspectorOverlayNode
import org.dreamfinity.dsgl.core.overlay.DomainPortalServices
import org.dreamfinity.dsgl.core.overlay.DomainSurfaceHost
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.overlay.PortalFrameContext
import org.dreamfinity.dsgl.core.overlay.PortalHost
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurface
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.overlay.input.dispatchManualThenDomFallback
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanel
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelStyle
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectPortalController
import org.dreamfinity.dsgl.core.style.StyleApplicationScope

@Suppress("TooManyFunctions")
class SystemOverlayHost(
    private val inspectorController: InspectorController,
) : DomainSurfaceHost {
    override val surface: ScreenDomainSurface = ScreenDomainSurfaces.SystemPortal

    private val rootNode: SystemOverlayRootNode = SystemOverlayRootNode()
    private val inspectorEntry: SystemOverlayEntry = InspectorOverlayEntry(inspectorController)
    private val colorPickerEntry: ColorPickerOverlayEntry = ColorPickerOverlayEntry()
    private val colorPickerTransientEntry: SystemOverlayEntry = ColorPickerTransientOverlayEntry(colorPickerEntry)
    private val entryRegistry: SystemOverlayEntryRegistry =
        SystemOverlayEntryRegistry(
            listOf(inspectorEntry, colorPickerEntry, colorPickerTransientEntry),
        )
    private val portalHost: PortalHost =
        PortalHost(ScreenDomainSurfaces.SystemPortal)
    private val portalEntries: List<SystemOverlayPortalEntry> =
        entryRegistry.allEntries().map(::SystemOverlayPortalEntry)
    private val transientOwnershipRegistry: SystemOverlayTransientOwnershipRegistry =
        SystemOverlayTransientOwnershipRegistry()
    private val systemSelectPortal: SelectPortalController =
        SelectPortalController(
            engine = DomainPortalServices.systemSelectEngine,
            ownerScope = OverlayOwnerScope.System,
            entryId = "system.select",
        )
    private val tree: DomTree =
        DomTree(
            root = rootNode,
            styleScope = StyleApplicationScope.SystemOverlay,
        )
    private var frameContext: SystemOverlayFrameContext =
        SystemOverlayFrameContext(
            inspectedRoot = null,
            inspectedLayoutRevision = 0L,
            cursorX = 0,
            cursorY = 0,
            inspectorPointerCaptured = false,
        )
    private var knownViewportWidth: Int = 1
    private var knownViewportHeight: Int = 1
    private val domInputRouter: LayerDomInputRouter =
        LayerDomInputRouter(
            rootProvider = {
                if (activeEntriesTopFirst().any { it.enablesDomInputFallbackRouting() }) rootNode else null
            },
        )

    init {
        portalEntries.forEach(portalHost::register)
    }

    fun systemInspectorColorPickerService(): SystemColorPickerPortalService = colorPickerEntry

    fun isSystemColorPickerOpen(): Boolean = colorPickerEntry.isOpen()

    fun captureSystemColorPickerEyedropperSample() {
        colorPickerEntry.captureEyedropperSample()
    }

    fun syncPortalFrame(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        viewportScale: Float,
    ) {
        systemSelectPortal.onFrame(
            measureContext = measureContext,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            viewportScale = viewportScale,
        )
    }

    fun appendPortalOverlayCommands(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>,
    ) {
        systemSelectPortal.appendCommands(
            measureContext = measureContext,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            out = out,
        )
    }

    fun hasOpenPortal(): Boolean = systemSelectPortal.isOpen()

    fun handlePortalKeyDown(keyCode: Int, keyChar: Char): Boolean = systemSelectPortal.handleKeyDown(keyCode, keyChar)

    fun handlePortalMouseMove(mouseX: Int, mouseY: Int): Boolean = systemSelectPortal.handleMouseMove(mouseX, mouseY)

    fun handlePortalMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        systemSelectPortal.handleMouseDown(mouseX, mouseY, button)

    fun handlePortalMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        systemSelectPortal.handleMouseUp(mouseX, mouseY, button)

    fun handlePortalMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        systemSelectPortal.handleMouseWheel(mouseX, mouseY, delta)

    override fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
        knownViewportWidth = viewportWidth.coerceAtLeast(1)
        knownViewportHeight = viewportHeight.coerceAtLeast(1)
        rootNode.setViewportBounds(knownViewportWidth, knownViewportHeight)
        portalHost.onInputFrame(PortalFrameContext(Rect(0, 0, knownViewportWidth, knownViewportHeight)))
        entryRegistry.allEntries().forEach { entry ->
            entry.onInputFrame(viewportWidth, viewportHeight)
        }
    }

    fun syncFrame(
        inspectedRoot: DOMNode?,
        inspectedLayoutRevision: Long,
        cursorX: Int,
        cursorY: Int,
        inspectorPointerCaptured: Boolean,
    ) {
        frameContext =
            SystemOverlayFrameContext(
                inspectedRoot = inspectedRoot,
                inspectedLayoutRevision = inspectedLayoutRevision,
                cursorX = cursorX,
                cursorY = cursorY,
                inspectorPointerCaptured = inspectorPointerCaptured,
            )
        rootNode.setViewportBounds(knownViewportWidth, knownViewportHeight)
        entryRegistry.allEntries().forEach { entry ->
            entry.sync(frameContext)
        }
        portalEntries.forEach { entry ->
            entry.syncPlacement(knownViewportWidth, knownViewportHeight)
        }
        reconcileMountedEntries()
    }

    override fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        knownViewportWidth = width.coerceAtLeast(1)
        knownViewportHeight = height.coerceAtLeast(1)
        rootNode.setViewportBounds(width, height)
        tree.render(ctx, width, height)
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> = tree.paint(ctx, applyStyles = true)

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean =
        dispatchManualThenDomFallback(
            manualDispatch = { dispatchManualInput { entry -> entry.handleMouseMove(mouseX, mouseY) } },
            domFallbackDispatch = { domInputRouter.handleMouseMove(mouseX, mouseY) },
        )

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        dispatchManualThenDomFallback(
            manualDispatch = { dispatchManualInput { entry -> entry.handleMouseDown(mouseX, mouseY, button) } },
            domFallbackDispatch = { domInputRouter.handleMouseDown(mouseX, mouseY, button) },
        )

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        dispatchManualThenDomFallback(
            manualDispatch = { dispatchManualInput { entry -> entry.handleMouseUp(mouseX, mouseY, button) } },
            domFallbackDispatch = { domInputRouter.handleMouseUp(mouseX, mouseY, button) },
        )

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        dispatchManualThenDomFallback(
            manualDispatch = { dispatchManualInput { entry -> entry.handleMouseWheel(mouseX, mouseY, delta) } },
            domFallbackDispatch = { domInputRouter.handleMouseWheel(mouseX, mouseY, delta) },
        )

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean =
        dispatchManualThenDomFallback(
            manualDispatch = { dispatchManualInput { entry -> entry.handleKeyDown(keyCode, keyChar) } },
            domFallbackDispatch = { domInputRouter.handleKeyDown(keyCode, keyChar) },
        )

    override fun clearRefs() {
        tree.clearRefs()
        transientOwnershipRegistry.clear()
        colorPickerEntry.close()
        systemSelectPortal.close()
        portalEntries.forEach { it.syncPlacement(knownViewportWidth, knownViewportHeight) }
        domInputRouter.clear()
    }

    internal fun debugEntryState(id: SystemOverlayEntryId): SystemOverlayEntryState? = entryRegistry.entry(id)?.state

    internal fun debugEntryNode(id: SystemOverlayEntryId): DOMNode? = entryRegistry.entry(id)?.node

    internal fun debugRegisteredEntryIds(): List<SystemOverlayEntryId> = entryRegistry.allEntries().map { it.state.id }

    internal fun debugRegisteredPortalEntryIds(): List<String> = portalEntries.map { it.state.id.value }

    internal fun debugActivePortalEntryIds(): List<String> = portalHost.entriesInPaintOrder().map { it.state.id.value }

    internal fun debugMountedEntryIds(): List<SystemOverlayEntryId> {
        val entriesByNode = portalEntries.associateBy { it.node }
        val mountedNodes =
            buildList {
                addAll(rootNode.mountedLaneNodes(SystemOverlayLane.PanelContent))
                addAll(rootNode.mountedLaneNodes(SystemOverlayLane.Transient))
            }
        return mountedNodes.mapNotNull { node ->
            entriesByNode[node]
                ?.systemEntry
                ?.state
                ?.id
        }
    }

    internal fun resolveTransientSession(ownerToken: Any): SystemOverlayTransientSession =
        transientOwnershipRegistry.resolve(ownerToken)

    internal fun resolveTransientSession(ownerToken: Any, cursorX: Int, cursorY: Int): SystemOverlayTransientSession =
        transientOwnershipRegistry.resolve(ownerToken, cursorX, cursorY)

    internal fun releaseTransientSession(ownerToken: Any): Boolean = transientOwnershipRegistry.release(ownerToken)

    internal fun debugTransientSessionCount(): Int = transientOwnershipRegistry.activeSessions().size

    internal fun debugSystemColorPickerHeaderRect(): Rect? = colorPickerEntry.debugHeaderRect()

    internal fun debugSystemColorPickerCloseRect(): Rect? = colorPickerEntry.debugCloseRect()

    internal fun debugSystemColorPickerBodyLayout(): ColorPickerLayout? = colorPickerEntry.debugBodyLayout()

    internal fun debugSystemColorPickerState(): ColorPickerState? = colorPickerEntry.debugState()

    internal fun debugSystemColorPickerPopupOwnerScope(): OverlayOwnerScope? = colorPickerEntry.debugOwnerScope()

    internal fun debugRootBounds(): Rect = rootNode.bounds

    private fun reconcileMountedEntries() {
        val activeEntries =
            portalHost.entriesInPaintOrder().mapNotNull {
                (it as? SystemOverlayPortalEntry)?.systemEntry
            }
        val panelNodes =
            activeEntries
                .filter { it.state.lane == SystemOverlayLane.PanelContent }
                .map { it.node }
        val transientNodes =
            activeEntries
                .filter { it.state.lane == SystemOverlayLane.Transient }
                .map { it.node }
        rootNode.setLaneChildren(
            panelNodes = panelNodes,
            transientNodes = transientNodes,
        )
    }

    private fun activeEntriesTopFirst(): List<SystemOverlayEntry> =
        portalHost
            .entriesInInputOrder()
            .mapNotNull { (it as? SystemOverlayPortalEntry)?.systemEntry }

    private inline fun dispatchManualInput(handler: (SystemOverlayEntry) -> Boolean): Boolean =
        activeEntriesTopFirst()
            .asSequence()
            .filter { entry -> !entry.participatesInDomInput() }
            .any(handler)

    private class InspectorOverlayEntry(
        private val inspectorController: InspectorController,
    ) : SystemOverlayEntry {
        override val state: SystemOverlayEntryState =
            SystemOverlayEntryState(
                id = SystemOverlayEntryId.Inspector,
                order = 100,
                lane = SystemOverlayLane.PanelContent,
            )
        private val overlayPanel: OverlayPanel =
            OverlayPanel(
                ownerId = state.id,
                panelState = state.panelState,
                dragSession = state.dragSession,
            )
        override val node: SystemInspectorOverlayNode =
            SystemInspectorOverlayNode(
                controller = inspectorController,
                overlayPanel = overlayPanel,
            )
        private var viewportWidth: Int = 1
        private var viewportHeight: Int = 1

        override fun participatesInDomInput(): Boolean = true

        override fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
            this.viewportWidth = viewportWidth.coerceAtLeast(1)
            this.viewportHeight = viewportHeight.coerceAtLeast(1)
        }

        override fun sync(frame: SystemOverlayFrameContext) {
            node.syncInputBounds(viewportWidth, viewportHeight)
            node.bindInspectedTree(frame.inspectedRoot, frame.inspectedLayoutRevision)
            node.updateCursor(frame.cursorX, frame.cursorY, frame.inspectorPointerCaptured)
            frame.inspectedRoot?.let { root ->
                inspectorController.onLayoutCommitted(root, frame.inspectedLayoutRevision)
            }
            state.active = inspectorController.active
            inspectorController.setOverlayPanelAuthorityEnabled(state.active)
            if (!state.active) {
                state.panelState.hide()
                state.dragSession.end()
                overlayPanel.syncPanelRect(null)
                inspectorController.onOverlayPanelPointerCaptureChanged(false)
                return
            }
            if (inspectorController.panelState == InspectorPanelState.Expanded) {
                overlayPanel.configure(
                    title = "Inspector",
                    draggable = true,
                    resizable = true,
                    minWidth = 240,
                    minHeight = 160,
                    style = inspectorPanelStyle(),
                    onClose = inspectorController::onPanelMinimizeTogglePressed,
                )
                val panelRect = inspectorController.overlayExpandedPanelRect()
                if (panelRect != null) {
                    inspectorController.onOverlayPanelRectChanged(panelRect, viewportWidth, viewportHeight)
                    overlayPanel.syncPanelRect(inspectorController.overlayExpandedPanelRect())
                } else {
                    state.panelState.show()
                    overlayPanel.syncPanelRect(state.panelState.currentRectOrNull())
                }
                val dragUpdatedByDomInput = node.consumeOverlayPanelDomDragUpdate()
                if (!dragUpdatedByDomInput) {
                    overlayPanel.handleMouseMove(
                        mouseX = frame.cursorX,
                        mouseY = frame.cursorY,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                    ) { rect ->
                        inspectorController.onOverlayPanelRectChanged(rect, viewportWidth, viewportHeight)
                    }
                }
            } else {
                state.panelState.hide()
                state.dragSession.end()
                overlayPanel.syncPanelRect(null)
                inspectorController.onOverlayPanelPointerCaptureChanged(false)
            }
        }
    }

    private class ColorPickerOverlayEntry :
        SystemOverlayEntry,
        SystemColorPickerPortalService {
        override val state: SystemOverlayEntryState =
            SystemOverlayEntryState(
                id = SystemOverlayEntryId.ColorPickerPopup,
                order = 200,
                lane = SystemOverlayLane.PanelContent,
            )
        private val popupMount: ColorPickerPopupMount =
            ColorPickerPopupMount(
                ownerId = state.id,
                panelState = state.panelState,
                dragSession = state.dragSession,
            )
        override val node: ColorPickerPopupOverlayNode = popupMount.node
        private var draggable: Boolean = true
        private var viewportWidth: Int = 1
        private var viewportHeight: Int = 1
        private var domDelegatedBodyPressActive: Boolean = false

        override fun enablesDomInputFallbackRouting(): Boolean = true

        override fun sync(frame: SystemOverlayFrameContext) {
            node.updateCursor(frame.cursorX, frame.cursorY)
            state.active = popupMount.popupEngine.isOpenFor(popupMount.ownerToken)
            if (!state.active) {
                state.panelState.hide()
                state.dragSession.end()
                node.resetDomInputRoutingReadiness()
                domDelegatedBodyPressActive = false
                return
            }
            popupMount.overlayPanel.configure(
                title = popupMount.popupEngine.debugTitle(popupMount.ownerToken) ?: "Color Picker",
                draggable = draggable,
                style =
                    popupMount.popupEngine
                        .debugStyle(popupMount.ownerToken)
                        ?.let { toOverlayPanelStyle(it) }
                        ?: OverlayPanelStyle(),
                onClose = ::close,
            )
            val panelRect = popupMount.popupEngine.debugPanelRect(popupMount.ownerToken)
            if (panelRect != null) {
                popupMount.overlayPanel.syncPanelRect(panelRect)
            } else {
                state.panelState.show()
                popupMount.overlayPanel.syncPanelRect(state.panelState.currentRectOrNull())
            }
            if (popupMount.overlayPanel.handleMouseMove(
                    mouseX = frame.cursorX,
                    mouseY = frame.cursorY,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                ) { rect ->
                    popupMount.popupEngine.forcePanelRect(popupMount.ownerToken, rect)
                }
            ) {
                popupMount.popupEngine.onCursorPosition(frame.cursorX, frame.cursorY)
            }
            node.syncInputFocusForDomEditing()
        }

        override fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
            this.viewportWidth = viewportWidth
            this.viewportHeight = viewportHeight
            popupMount.popupEngine.onFrame(viewportWidth, viewportHeight)
        }

        override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
            if (!state.active) return false
            if (domDelegatedBodyPressActive) return false
            popupMount.popupEngine.onCursorPosition(mouseX, mouseY)
            if (popupMount.overlayPanel.handleMouseMove(
                    mouseX = mouseX,
                    mouseY = mouseY,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                ) { rect ->
                    popupMount.popupEngine.forcePanelRect(popupMount.ownerToken, rect)
                }
            ) {
                popupMount.popupEngine.onCursorPosition(mouseX, mouseY)
                return true
            }
            return popupMount.popupEngine.handleMouseMove(mouseX, mouseY)
        }

        override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
            if (!state.active) return false
            if (button != MouseButton.LEFT) {
                domDelegatedBodyPressActive = false
            }
            if (popupMount.overlayPanel.handleMouseDown(mouseX, mouseY, button)) {
                return true
            }
            if (popupMount.popupEngine.shouldRouteSystemInputSlotMouseDownToDom(mouseX, mouseY, button)) {
                return popupMount.popupEngine.focusSystemInputSlotForDomEditing(mouseX, mouseY) { index ->
                    node.focusInputSlot(index, mouseX, mouseY)
                }
            }
            if (
                node.isDomInputRoutingReady() &&
                popupMount.popupEngine.shouldRouteSystemBodyIntentMouseDownToDom(mouseX, mouseY, button)
            ) {
                domDelegatedBodyPressActive = true
                return false
            }
            return popupMount.popupEngine.handleMouseDown(mouseX, mouseY, button)
        }

        override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
            if (!state.active) return false
            if (domDelegatedBodyPressActive && button == MouseButton.LEFT) {
                domDelegatedBodyPressActive = false
                return false
            }
            if (popupMount.overlayPanel.handleMouseUp(
                    mouseX = mouseX,
                    mouseY = mouseY,
                    button = button,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                ) { rect ->
                    popupMount.popupEngine.forcePanelRect(popupMount.ownerToken, rect)
                }
            ) {
                return true
            }
            return popupMount.popupEngine.handleMouseUp(mouseX, mouseY, button)
        }

        override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
            if (!state.active) return false
            return popupMount.popupEngine.handleMouseWheel(mouseX, mouseY, delta)
        }

        override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean {
            if (!state.active) return false
            if (shouldRouteSystemTextInputKeyDownToDom()) {
                return false
            }
            return popupMount.popupEngine.handleKeyDown(keyCode, keyChar)
        }

        private fun shouldRouteSystemTextInputKeyDownToDom(): Boolean {
            if (popupMount.popupEngine.debugOwnerScope(popupMount.ownerToken) != OverlayOwnerScope.System) return false
            val focused = FocusManager.focusedNode() ?: return false
            if (focused !is SingleLineInputNode) return false
            val key = focused.key as? String ?: return false
            return key.startsWith("dsgl-system-color-picker-input-value-")
        }

        override fun open(
            anchorRect: Rect,
            title: String,
            state: ColorPickerState,
            style: ColorPickerStyle,
            width: Int,
            draggable: Boolean,
            closeOnOutsideClick: Boolean,
            onPreview: ((RgbaColor) -> Unit)?,
            onChange: ((RgbaColor) -> Unit)?,
            onCommit: ((RgbaColor) -> Unit)?,
            onClose: (() -> Unit)?,
        ) {
            this.draggable = draggable
            popupMount.popupEngine.open(
                ColorPickerPopupRequest(
                    owner = popupMount.ownerToken,
                    ownerScope = OverlayOwnerScope.System,
                    anchorRect = anchorRect,
                    title = title,
                    state = state,
                    style = style,
                    width = width,
                    draggable = false,
                    closeOnOutsideClick = closeOnOutsideClick,
                    onPreview = onPreview,
                    onChange = onChange,
                    onCommit = onCommit,
                    onClose = onClose,
                ),
            )
        }

        override fun close() {
            popupMount.popupEngine.close(popupMount.ownerToken)
            state.dragSession.end()
            state.panelState.hide()
            state.active = false
            node.resetDomInputRoutingReadiness()
            domDelegatedBodyPressActive = false
        }

        override fun isOpen(): Boolean = popupMount.popupEngine.isOpenFor(popupMount.ownerToken)

        fun transientOverlayNode(): DOMNode = popupMount.transientNode

        fun isTransientActive(): Boolean {
            val controller = popupMount.popupEngine.debugController(popupMount.ownerToken) ?: return false
            return controller.viewModeDropdownOpen() || controller.isEyedropperActive()
        }

        fun debugHeaderRect(): Rect? = popupMount.overlayPanel.headerRect()

        fun debugCloseRect(): Rect? = popupMount.overlayPanel.closeRect()

        fun debugBodyLayout(): ColorPickerLayout? = popupMount.popupEngine.debugBodyLayout(popupMount.ownerToken)

        fun debugState(): ColorPickerState? =
            popupMount.popupEngine
                .debugController(popupMount.ownerToken)
                ?.snapshot()

        fun captureEyedropperSample() {
            if (popupMount.popupEngine.captureEyedropperSample()) {
                popupMount.node.invalidateColorState()
                popupMount.transientNode.invalidateColorState()
            }
        }

        fun debugOwnerScope(): OverlayOwnerScope? = popupMount.popupEngine.debugOwnerScope(popupMount.ownerToken)
    }

    private class ColorPickerTransientOverlayEntry(
        private val panelEntry: ColorPickerOverlayEntry,
    ) : SystemOverlayEntry {
        override val state: SystemOverlayEntryState =
            SystemOverlayEntryState(
                id = SystemOverlayEntryId.ColorPickerTransient,
                order = 210,
                lane = SystemOverlayLane.Transient,
            )
        override val node: DOMNode = panelEntry.transientOverlayNode()

        override fun sync(frame: SystemOverlayFrameContext) {
            state.active = panelEntry.state.active && panelEntry.isTransientActive()
        }
    }

    private companion object {
        private fun inspectorPanelStyle(): OverlayPanelStyle =
            OverlayPanelStyle(
                headerHeight = 52,
                panelPadding = 6,
                resizeHandleSize = 8,
                panelBackgroundColor = 0xE0141820.toInt(),
                panelBorderColor = 0xCC425062.toInt(),
                panelShadowColor = 0x7A0C1118,
                headerBackgroundColor = 0x222D3846,
                headerBorderColor = 0x553F4A57,
                closeButtonBackgroundColor = 0x3346596E,
                closeButtonBorderColor = 0x775E738C,
                textColor = 0xFFE6EDF6.toInt(),
                fontSize = 24,
                closeGlyph = "-",
            )

        private fun toOverlayPanelStyle(style: ColorPickerStyle): OverlayPanelStyle =
            OverlayPanelStyle(
                panelBackgroundColor = style.panelBackgroundColor,
                panelBorderColor = style.panelBorderColor,
                panelShadowColor = style.panelShadowColor,
                headerBackgroundColor = style.buttonBackgroundColor,
                headerBorderColor = style.inputBorderColor,
                closeButtonBackgroundColor = style.buttonBackgroundColor,
                closeButtonBorderColor = style.inputBorderColor,
                textColor = style.textColor,
                fontSize = style.fontSize,
            )
    }
}
