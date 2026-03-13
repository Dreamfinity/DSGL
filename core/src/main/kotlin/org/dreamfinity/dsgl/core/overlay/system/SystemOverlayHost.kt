package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.colorpicker.*
import org.dreamfinity.dsgl.core.colorpicker.internal.InspectorColorPickerHost
import org.dreamfinity.dsgl.core.colorpicker.internal.SystemColorPickerOverlayNode
import org.dreamfinity.dsgl.core.colorpicker.internal.SystemColorPickerTransientOverlayNode
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.internal.SystemInspectorOverlayNode
import org.dreamfinity.dsgl.core.overlay.OverlayLayerHost
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.overlay.UiLayerId
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanel
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelDragType
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelStyle
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleApplicationScope

class SystemOverlayHost(
    private val inspectorController: InspectorController
) : OverlayLayerHost {
    override val layerId: UiLayerId = UiLayerId.SystemOverlay

    private val rootNode: SystemOverlayRootNode = SystemOverlayRootNode()
    private val inspectorEntry: SystemOverlayEntry = InspectorOverlayEntry(inspectorController)
    private val colorPickerEntry: ColorPickerOverlayEntry = ColorPickerOverlayEntry()
    private val colorPickerTransientEntry: SystemOverlayEntry = ColorPickerTransientOverlayEntry(colorPickerEntry)
    private val overlayPanelDemoEntry: OverlayPanelDemoOverlayEntry = OverlayPanelDemoOverlayEntry()
    private val entryRegistry: SystemOverlayEntryRegistry = SystemOverlayEntryRegistry(
        listOf(inspectorEntry, colorPickerEntry, colorPickerTransientEntry, overlayPanelDemoEntry)
    )
    private val transientOwnershipRegistry: SystemOverlayTransientOwnershipRegistry =
        SystemOverlayTransientOwnershipRegistry()
    private val tree: DomTree = DomTree(
        root = rootNode,
        styleScope = StyleApplicationScope.SystemOverlay
    )
    private var frameContext: SystemOverlayFrameContext = SystemOverlayFrameContext(
        inspectedRoot = null,
        inspectedLayoutRevision = 0L,
        cursorX = 0,
        cursorY = 0,
        inspectorPointerCaptured = false
    )
    private var knownViewportWidth: Int = 1
    private var knownViewportHeight: Int = 1
    private val domInputRouter: LayerDomInputRouter = LayerDomInputRouter(
        rootProvider = {
            if (activeEntriesTopFirst().any { it.participatesInDomInput() }) rootNode else null
        }
    )

    fun systemInspectorColorPickerPopupHost(): InspectorColorPickerHost {
        return colorPickerEntry
    }

    fun isSystemColorPickerOpen(): Boolean {
        return colorPickerEntry.isOpen()
    }

    fun captureSystemColorPickerEyedropperSample() {
        colorPickerEntry.captureEyedropperSample()
    }

    fun togglePanelDemo(anchorX: Int, anchorY: Int) {
        overlayPanelDemoEntry.toggle(anchorX, anchorY, knownViewportWidth, knownViewportHeight)
    }

    fun isOverlayPanelDemoOpen(): Boolean {
        return overlayPanelDemoEntry.isOpen()
    }

    fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
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
        inspectorPointerCaptured: Boolean
    ) {
        frameContext = SystemOverlayFrameContext(
            inspectedRoot = inspectedRoot,
            inspectedLayoutRevision = inspectedLayoutRevision,
            cursorX = cursorX,
            cursorY = cursorY,
            inspectorPointerCaptured = inspectorPointerCaptured
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

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> {
        return tree.paint(ctx, applyStyles = true)
    }

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
        if (dispatchManualInput { entry -> entry.handleMouseMove(mouseX, mouseY) }) {
            return true
        }
        return domInputRouter.handleMouseMove(mouseX, mouseY)
    }

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        if (dispatchManualInput { entry -> entry.handleMouseDown(mouseX, mouseY, button) }) {
            return true
        }
        return domInputRouter.handleMouseDown(mouseX, mouseY, button)
    }

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        if (dispatchManualInput { entry -> entry.handleMouseUp(mouseX, mouseY, button) }) {
            return true
        }
        return domInputRouter.handleMouseUp(mouseX, mouseY, button)
    }

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        if (dispatchManualInput { entry -> entry.handleMouseWheel(mouseX, mouseY, delta) }) {
            return true
        }
        return domInputRouter.handleMouseWheel(mouseX, mouseY, delta)
    }

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean {
        if (dispatchManualInput { entry -> entry.handleKeyDown(keyCode, keyChar) }) {
            return true
        }
        return domInputRouter.handleKeyDown(keyCode, keyChar)
    }

    override fun clearRefs() {
        tree.clearRefs()
        transientOwnershipRegistry.clear()
        colorPickerEntry.close()
        overlayPanelDemoEntry.close()
        domInputRouter.clear()
    }

    internal fun debugEntryState(id: SystemOverlayEntryId): SystemOverlayEntryState? {
        return entryRegistry.entry(id)?.state
    }

    internal fun debugEntryNode(id: SystemOverlayEntryId): DOMNode? {
        return entryRegistry.entry(id)?.node
    }

    internal fun debugRegisteredEntryIds(): List<SystemOverlayEntryId> {
        return entryRegistry.allEntries().map { it.state.id }
    }

    internal fun debugMountedEntryIds(): List<SystemOverlayEntryId> {
        val entriesByNode = entryRegistry.allEntries().associateBy { it.node }
        val mountedNodes = buildList {
            addAll(rootNode.mountedLaneNodes(SystemOverlayLane.PanelContent))
            addAll(rootNode.mountedLaneNodes(SystemOverlayLane.Transient))
        }
        return mountedNodes.mapNotNull { node ->
            entriesByNode[node]?.state?.id
        }
    }

    internal fun resolveTransientSession(ownerToken: Any): SystemOverlayTransientSession {
        return transientOwnershipRegistry.resolve(ownerToken)
    }

    internal fun resolveTransientSession(ownerToken: Any, cursorX: Int, cursorY: Int): SystemOverlayTransientSession {
        return transientOwnershipRegistry.resolve(ownerToken, cursorX, cursorY)
    }

    internal fun releaseTransientSession(ownerToken: Any): Boolean {
        return transientOwnershipRegistry.release(ownerToken)
    }

    internal fun debugTransientSessionCount(): Int {
        return transientOwnershipRegistry.activeSessions().size
    }

    internal fun debugSystemColorPickerHeaderRect(): Rect? {
        return colorPickerEntry.debugHeaderRect()
    }

    internal fun debugSystemColorPickerCloseRect(): Rect? {
        return colorPickerEntry.debugCloseRect()
    }

    internal fun debugSystemColorPickerBodyLayout(): ColorPickerLayout? {
        return colorPickerEntry.debugBodyLayout()
    }

    internal fun debugSystemColorPickerState(): ColorPickerState? {
        return colorPickerEntry.debugState()
    }

    internal fun debugSystemColorPickerPopupOwnerScope(): OverlayOwnerScope? {
        return colorPickerEntry.debugOwnerScope()
    }

    internal fun debugRootBounds(): Rect {
        return rootNode.bounds
    }

    private fun reconcileMountedEntries() {
        val activeEntries = entryRegistry.allEntries().filter { it.state.active }
        val panelNodes = activeEntries
            .filter { it.state.lane == SystemOverlayLane.PanelContent }
            .map { it.node }
        val transientNodes = activeEntries
            .filter { it.state.lane == SystemOverlayLane.Transient }
            .map { it.node }
        rootNode.setLaneChildren(
            panelNodes = panelNodes,
            transientNodes = transientNodes
        )
    }

    private fun activeEntriesTopFirst(): List<SystemOverlayEntry> {
        return entryRegistry.allEntries()
            .filter { it.state.active }
            .sortedWith(
                compareBy<SystemOverlayEntry> { it.state.lane.zOrder }
                    .thenBy { it.state.order }
            )
            .asReversed()
    }

    private inline fun dispatchManualInput(handler: (SystemOverlayEntry) -> Boolean): Boolean {
        return activeEntriesTopFirst()
            .asSequence()
            .filter { !it.participatesInDomInput() }
            .any(handler)
    }

    private class InspectorOverlayEntry(
        private val inspectorController: InspectorController
    ) : SystemOverlayEntry {
        override val state: SystemOverlayEntryState = SystemOverlayEntryState(
            id = SystemOverlayEntryId.Inspector,
            order = 100,
            lane = SystemOverlayLane.PanelContent
        )
        override val node: SystemInspectorOverlayNode = SystemInspectorOverlayNode(inspectorController)
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
            if (!state.active) {
                state.panelState.hide()
                state.dragSession.end()
                return
            }
            val panelRect = inspectorController.debugPanelRect()
            if (panelRect != null) {
                state.panelState.updateFromRect(panelRect)
            } else {
                state.panelState.show()
            }
            syncDragSession(
                entryState = state,
                dragging = inspectorController.isDraggingPanel,
                dragType = OverlayPanelDragType.PanelMove,
                pointerX = frame.cursorX,
                pointerY = frame.cursorY
            )
        }
    }

    private class ColorPickerOverlayEntry : SystemOverlayEntry, InspectorColorPickerHost {
        override val state: SystemOverlayEntryState = SystemOverlayEntryState(
            id = SystemOverlayEntryId.ColorPickerPopup,
            order = 200,
            lane = SystemOverlayLane.PanelContent
        )
        private val ownerToken: Any = Any()
        private val popupEngine: ColorPickerPopupEngine = ColorPickerPopupEngine()
        private val overlayPanel: OverlayPanel = OverlayPanel(
            ownerId = state.id,
            panelState = state.panelState,
            dragSession = state.dragSession
        )
        override val node: SystemColorPickerOverlayNode = SystemColorPickerOverlayNode(
            popupEngine = popupEngine,
            overlayPanel = overlayPanel
        )
        private val transientNode: SystemColorPickerTransientOverlayNode =
            SystemColorPickerTransientOverlayNode(popupEngine = popupEngine)
        private var draggable: Boolean = true
        private var viewportWidth: Int = 1
        private var viewportHeight: Int = 1

        override fun sync(frame: SystemOverlayFrameContext) {
            node.updateCursor(frame.cursorX, frame.cursorY)
            state.active = popupEngine.isOpenFor(ownerToken)
            if (!state.active) {
                state.panelState.hide()
                state.dragSession.end()
                return
            }
            overlayPanel.configure(
                title = popupEngine.debugTitle(ownerToken) ?: "Color Picker",
                draggable = draggable,
                style = popupEngine.debugStyle(ownerToken)
                    ?.let { toOverlayPanelStyle(it) }
                    ?: OverlayPanelStyle(),
                onClose = ::close
            )
            val panelRect = popupEngine.debugPanelRect(ownerToken)
            if (panelRect != null) {
                overlayPanel.syncPanelRect(panelRect)
            } else {
                state.panelState.show()
                overlayPanel.syncPanelRect(state.panelState.currentRectOrNull())
            }
            if (overlayPanel.handleMouseMove(
                    mouseX = frame.cursorX,
                    mouseY = frame.cursorY,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight
                ) { rect ->
                    popupEngine.forcePanelRect(ownerToken, rect)
                }
            ) {
                popupEngine.onCursorPosition(frame.cursorX, frame.cursorY)
            }
        }

        override fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
            this.viewportWidth = viewportWidth
            this.viewportHeight = viewportHeight
            popupEngine.onFrame(viewportWidth, viewportHeight)
        }

        override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
            if (!state.active) return false
            popupEngine.onCursorPosition(mouseX, mouseY)
            if (overlayPanel.handleMouseMove(
                    mouseX = mouseX,
                    mouseY = mouseY,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight
                ) { rect ->
                    popupEngine.forcePanelRect(ownerToken, rect)
                }
            ) {
                popupEngine.onCursorPosition(mouseX, mouseY)
                return true
            }
            return popupEngine.handleMouseMove(mouseX, mouseY)
        }

        override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
            if (!state.active) return false
            if (overlayPanel.handleMouseDown(mouseX, mouseY, button)) {
                return true
            }
            return popupEngine.handleMouseDown(mouseX, mouseY, button)
        }

        override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
            if (!state.active) return false
            if (overlayPanel.handleMouseUp(
                    mouseX = mouseX,
                    mouseY = mouseY,
                    button = button,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight
                ) { rect ->
                    popupEngine.forcePanelRect(ownerToken, rect)
                }
            ) {
                return true
            }
            return popupEngine.handleMouseUp(mouseX, mouseY, button)
        }

        override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
            if (!state.active) return false
            return popupEngine.handleMouseWheel(mouseX, mouseY, delta)
        }

        override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean {
            if (!state.active) return false
            return popupEngine.handleKeyDown(keyCode, keyChar)
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
            onClose: (() -> Unit)?
        ) {
            this.draggable = draggable
            popupEngine.open(
                ColorPickerPopupRequest(
                    owner = ownerToken,
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
                    onClose = onClose
                )
            )
        }

        override fun close() {
            popupEngine.close(ownerToken)
            state.dragSession.end()
            state.panelState.hide()
            state.active = false
        }

        override fun isOpen(): Boolean {
            return popupEngine.isOpenFor(ownerToken)
        }

        fun transientOverlayNode(): DOMNode {
            return transientNode
        }

        fun isTransientActive(): Boolean {
            val controller = popupEngine.debugController(ownerToken) ?: return false
            return controller.viewModeDropdownOpen() || controller.isEyedropperActive()
        }

        fun debugHeaderRect(): Rect? {
            return overlayPanel.headerRect()
        }

        fun debugCloseRect(): Rect? {
            return overlayPanel.closeRect()
        }

        fun debugBodyLayout(): ColorPickerLayout? {
            return popupEngine.debugBodyLayout(ownerToken)
        }

        fun debugState(): ColorPickerState? {
            return popupEngine.debugController(ownerToken)?.snapshot()
        }

        fun captureEyedropperSample() {
            popupEngine.captureEyedropperSample()
        }

        fun debugOwnerScope(): OverlayOwnerScope? {
            return popupEngine.debugOwnerScope(ownerToken)
        }
    }


    private class ColorPickerTransientOverlayEntry(
        private val panelEntry: ColorPickerOverlayEntry
    ) : SystemOverlayEntry {
        override val state: SystemOverlayEntryState = SystemOverlayEntryState(
            id = SystemOverlayEntryId.ColorPickerTransient,
            order = 210,
            lane = SystemOverlayLane.Transient
        )
        override val node: DOMNode = panelEntry.transientOverlayNode()

        override fun sync(frame: SystemOverlayFrameContext) {
            state.active = panelEntry.state.active && panelEntry.isTransientActive()
        }
    }

    private class OverlayPanelDemoOverlayEntry : SystemOverlayEntry {
        override val state: SystemOverlayEntryState = SystemOverlayEntryState(
            id = SystemOverlayEntryId.PanelDemo,
            order = 300,
            lane = SystemOverlayLane.PanelContent
        )
        private val overlayPanel: OverlayPanel = OverlayPanel(
            ownerId = state.id,
            panelState = state.panelState,
            dragSession = state.dragSession
        )
        private val demoNode: SystemOverlayPanelDemoNode = SystemOverlayPanelDemoNode(overlayPanel)
        override val node: DOMNode = demoNode
        private var opened: Boolean = false
        private var viewportWidth: Int = 1
        private var viewportHeight: Int = 1
        private var buttonClicks: Int = 0

        fun toggle(anchorX: Int, anchorY: Int, viewportWidth: Int, viewportHeight: Int) {
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
                onClose = ::close
            )
            overlayPanel.syncPanelRect(state.panelState.currentRectOrNull())
            demoNode.setButtonClicks(buttonClicks)
            overlayPanel.handleMouseMove(
                mouseX = frame.cursorX,
                mouseY = frame.cursorY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight
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
                    viewportHeight = viewportHeight
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
                    viewportHeight = viewportHeight
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
        private fun syncDragSession(
            entryState: SystemOverlayEntryState,
            dragging: Boolean,
            dragType: OverlayPanelDragType,
            pointerX: Int,
            pointerY: Int
        ) {
            if (dragging) {
                if (!entryState.dragSession.active) {
                    entryState.dragSession.begin(
                        ownerId = entryState.id,
                        type = dragType,
                        pointerX = pointerX,
                        pointerY = pointerY,
                        panelState = entryState.panelState
                    )
                } else {
                    entryState.dragSession.update(pointerX, pointerY)
                }
                return
            }
            entryState.dragSession.end()
        }

        private fun toOverlayPanelStyle(style: ColorPickerStyle): OverlayPanelStyle {
            return OverlayPanelStyle(
                panelBackgroundColor = style.panelBackgroundColor,
                panelBorderColor = style.panelBorderColor,
                panelShadowColor = style.panelShadowColor,
                headerBackgroundColor = style.buttonBackgroundColor,
                headerBorderColor = style.inputBorderColor,
                closeButtonBackgroundColor = style.buttonBackgroundColor,
                closeButtonBorderColor = style.inputBorderColor,
                textColor = style.textColor,
                fontSize = style.fontSize
            )
        }
    }
}

