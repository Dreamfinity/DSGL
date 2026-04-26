package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.colorpicker.internal.ColorPickerPopupMount
import org.dreamfinity.dsgl.core.colorpicker.internal.ColorPickerPopupOverlayNode
import org.dreamfinity.dsgl.core.colorpicker.internal.InspectorColorPickerHost
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.elements.SingleLineInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorPanelState
import org.dreamfinity.dsgl.core.inspector.internal.SystemInspectorOverlayNode
import org.dreamfinity.dsgl.core.overlay.OverlayLayerHost
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.overlay.UiLayerId
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.overlay.input.dispatchManualThenDomFallback
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanel
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelStyle
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectRuntime
import org.dreamfinity.dsgl.core.style.StyleApplicationScope

class SystemOverlayHost(
    private val inspectorController: InspectorController,
) : OverlayLayerHost {
    override val layerId: UiLayerId = UiLayerId.SystemOverlay

    private val rootNode: SystemOverlayRootNode = SystemOverlayRootNode()
    private val inspectorEntry: SystemOverlayEntry = InspectorOverlayEntry(inspectorController)
    private val colorPickerEntry: ColorPickerOverlayEntry = ColorPickerOverlayEntry()
    private val colorPickerTransientEntry: SystemOverlayEntry = ColorPickerTransientOverlayEntry(colorPickerEntry)
    private val overlayPanelDemoEntry: OverlayPanelDemoOverlayEntry = OverlayPanelDemoOverlayEntry()
    private val entryRegistry: SystemOverlayEntryRegistry =
        SystemOverlayEntryRegistry(
            listOf(inspectorEntry, colorPickerEntry, colorPickerTransientEntry, overlayPanelDemoEntry),
        )
    private val transientOwnershipRegistry: SystemOverlayTransientOwnershipRegistry =
        SystemOverlayTransientOwnershipRegistry()
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

    fun systemInspectorColorPickerPopupHost(): InspectorColorPickerHost = colorPickerEntry

    fun isSystemColorPickerOpen(): Boolean = colorPickerEntry.isOpen()

    fun captureSystemColorPickerEyedropperSample() {
        colorPickerEntry.captureEyedropperSample()
    }

    fun togglePanelDemo(anchorX: Int, anchorY: Int) {
        overlayPanelDemoEntry.toggle(anchorX, anchorY, knownViewportWidth, knownViewportHeight)
    }

    fun isOverlayPanelDemoOpen(): Boolean = overlayPanelDemoEntry.isOpen()

    fun systemSelectOnFrame(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        viewportScale: Float,
    ) {
        SelectRuntime.systemEngine.onFrame(
            measureContext = measureContext,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            viewportScale = viewportScale,
        )
    }

    fun appendSystemSelectOverlayCommands(
        measureContext: UiMeasureContext,
        viewportWidth: Int,
        viewportHeight: Int,
        out: MutableList<RenderCommand>,
    ) {
        SelectRuntime.systemEngine.appendOverlayCommands(
            measureContext = measureContext,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            out = out,
        )
    }

    fun isSystemSelectOpen(): Boolean = SelectRuntime.systemEngine.isOpen()

    fun handleSystemSelectKeyDown(keyCode: Int, keyChar: Char): Boolean =
        SelectRuntime.systemEngine.handleKeyDown(keyCode, keyChar)

    fun handleSystemSelectMouseMove(mouseX: Int, mouseY: Int): Boolean =
        SelectRuntime.systemEngine.handleMouseMove(mouseX, mouseY)

    fun handleSystemSelectMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        SelectRuntime.systemEngine.handleMouseDown(mouseX, mouseY, button)

    fun handleSystemSelectMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        SelectRuntime.systemEngine.handleMouseUp(mouseX, mouseY, button)

    fun handleSystemSelectMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        SelectRuntime.systemEngine.handleMouseWheel(mouseX, mouseY, delta)

    override fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
        knownViewportWidth = viewportWidth.coerceAtLeast(1)
        knownViewportHeight = viewportHeight.coerceAtLeast(1)
        rootNode.setViewportBounds(knownViewportWidth, knownViewportHeight)
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
        overlayPanelDemoEntry.close()
        domInputRouter.clear()
    }

    internal fun debugEntryState(id: SystemOverlayEntryId): SystemOverlayEntryState? = entryRegistry.entry(id)?.state

    internal fun debugEntryNode(id: SystemOverlayEntryId): DOMNode? = entryRegistry.entry(id)?.node

    internal fun debugRegisteredEntryIds(): List<SystemOverlayEntryId> = entryRegistry.allEntries().map { it.state.id }

    internal fun debugMountedEntryIds(): List<SystemOverlayEntryId> {
        val entriesByNode = entryRegistry.allEntries().associateBy { it.node }
        val mountedNodes =
            buildList {
                addAll(rootNode.mountedLaneNodes(SystemOverlayLane.PanelContent))
                addAll(rootNode.mountedLaneNodes(SystemOverlayLane.Transient))
            }
        return mountedNodes.mapNotNull { node ->
            entriesByNode[node]?.state?.id
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
        val activeEntries = entryRegistry.allEntries().filter { it.state.active }
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
        entryRegistry
            .allEntries()
            .filter { it.state.active }
            .sortedWith(
                compareBy<SystemOverlayEntry> { it.state.lane.zOrder }
                    .thenBy { it.state.order },
            ).asReversed()

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
        InspectorColorPickerHost {
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

        override fun enablesDomInputFallbackRouting(): Boolean = true

        override fun sync(frame: SystemOverlayFrameContext) {
            node.updateCursor(frame.cursorX, frame.cursorY)
            state.active = popupMount.popupEngine.isOpenFor(popupMount.ownerToken)
            if (!state.active) {
                state.panelState.hide()
                state.dragSession.end()
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
            if (popupMount.overlayPanel.handleMouseDown(mouseX, mouseY, button)) {
                return true
            }
            if (popupMount.popupEngine.shouldRouteSystemInputSlotMouseDownToDom(mouseX, mouseY, button)) {
                return popupMount.popupEngine.focusSystemInputSlotForDomEditing(mouseX, mouseY) { index ->
                    node.focusInputSlot(index, mouseX, mouseY)
                }
            }
            return popupMount.popupEngine.handleMouseDown(mouseX, mouseY, button)
        }

        override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
            if (!state.active) return false
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
            popupMount.popupEngine.captureEyedropperSample()
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

    private class OverlayPanelDemoOverlayEntry : SystemOverlayEntry {
        override val state: SystemOverlayEntryState =
            SystemOverlayEntryState(
                id = SystemOverlayEntryId.PanelDemo,
                order = 300,
                lane = SystemOverlayLane.PanelContent,
            )
        private val overlayPanel: OverlayPanel =
            OverlayPanel(
                ownerId = state.id,
                panelState = state.panelState,
                dragSession = state.dragSession,
            )
        private val demoNode: SystemOverlayPanelDemoNode = SystemOverlayPanelDemoNode(overlayPanel)
        override val node: DOMNode = demoNode
        private var opened: Boolean = false
        private var viewportWidth: Int = 1
        private var viewportHeight: Int = 1
        private var buttonClicks: Int = 0

        fun toggle(
            anchorX: Int,
            anchorY: Int,
            viewportWidth: Int,
            viewportHeight: Int,
        ) {
            if (opened) {
                close()
                return
            }
            this.viewportWidth = viewportWidth.coerceAtLeast(1)
            this.viewportHeight = viewportHeight.coerceAtLeast(1)
            val width = 300
            val height = 190
            val maxX = (this.viewportWidth - width - 2).coerceAtLeast(2)
            val maxY = (this.viewportHeight - height - 2).coerceAtLeast(2)
            val x = anchorX.coerceIn(2, maxX)
            val y = anchorY.coerceIn(2, maxY)
            state.panelState.updateFromRect(Rect(x, y, width, height))
            opened = true
            state.active = true
        }

        fun close() {
            opened = false
            state.active = false
            state.dragSession.end()
            state.panelState.hide()
        }

        fun isOpen(): Boolean = opened

        override fun sync(frame: SystemOverlayFrameContext) {
            state.active = opened
            if (!state.active) {
                state.panelState.hide()
                state.dragSession.end()
                return
            }
            overlayPanel.configure(
                title = "Overlay PanelF",
                draggable = true,
                style = OverlayPanelStyle(fontSize = 16),
                onClose = ::close,
            )
            overlayPanel.syncPanelRect(state.panelState.currentRectOrNull())
            demoNode.setButtonClicks(buttonClicks)
            overlayPanel.handleMouseMove(
                mouseX = frame.cursorX,
                mouseY = frame.cursorY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            ) { rect ->
                state.panelState.updateFromRect(rect)
            }
        }

        override fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
            this.viewportWidth = viewportWidth
            this.viewportHeight = viewportHeight
        }

        override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
            if (!state.active) return false
            if (overlayPanel.handleMouseMove(
                    mouseX = mouseX,
                    mouseY = mouseY,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                ) { rect ->
                    state.panelState.updateFromRect(rect)
                }
            ) {
                return true
            }
            val panelRect = state.panelState.currentRectOrNull() ?: return false
            return panelRect.contains(mouseX, mouseY)
        }

        override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
            if (!state.active) return false
            if (overlayPanel.handleMouseDown(mouseX, mouseY, button)) {
                return true
            }
            val panelRect = state.panelState.currentRectOrNull() ?: return false
            if (!panelRect.contains(mouseX, mouseY)) {
                return false
            }
            val buttonRect = demoNode.buttonRect()
            if (button == MouseButton.LEFT && buttonRect != null && buttonRect.contains(mouseX, mouseY)) {
                buttonClicks += 1
                demoNode.setButtonClicks(buttonClicks)
                return true
            }
            return true
        }

        override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
            if (!state.active) return false
            if (overlayPanel.handleMouseUp(
                    mouseX = mouseX,
                    mouseY = mouseY,
                    button = button,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                ) { rect ->
                    state.panelState.updateFromRect(rect)
                }
            ) {
                return true
            }
            val panelRect = state.panelState.currentRectOrNull() ?: return false
            return panelRect.contains(mouseX, mouseY)
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
