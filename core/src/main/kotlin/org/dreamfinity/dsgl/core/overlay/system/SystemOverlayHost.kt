package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupEngine
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerStyle
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.colorpicker.internal.SystemColorPickerOverlayNode
import org.dreamfinity.dsgl.core.colorpicker.internal.InspectorColorPickerHost
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.internal.SystemInspectorOverlayNode
import org.dreamfinity.dsgl.core.overlay.OverlayLayerHost
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.overlay.UiLayerId
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleApplicationScope

class SystemOverlayHost(
    private val inspectorController: InspectorController
) : OverlayLayerHost {
    override val layerId: UiLayerId = UiLayerId.SystemOverlay

    private val rootNode: SystemOverlayRootNode = SystemOverlayRootNode()
    private val inspectorEntry: SystemOverlayEntry = InspectorOverlayEntry(inspectorController)
    private val colorPickerEntry: ColorPickerOverlayEntry = ColorPickerOverlayEntry()
    private val entryRegistry: SystemOverlayEntryRegistry = SystemOverlayEntryRegistry(
        listOf(inspectorEntry, colorPickerEntry)
    )
    private val transientOwnershipRegistry: SystemOverlayTransientOwnershipRegistry = SystemOverlayTransientOwnershipRegistry()
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

    fun systemInspectorColorPickerPopupHost(): InspectorColorPickerHost {
        return colorPickerEntry
    }

    fun isSystemColorPickerOpen(): Boolean {
        return colorPickerEntry.isOpen()
    }

    fun captureSystemColorPickerEyedropperSample() {
        colorPickerEntry.captureEyedropperSample()
    }

    fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
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
        entryRegistry.allEntries().forEach { entry ->
            entry.sync(frameContext)
        }
        reconcileMountedEntries()
    }

    override fun render(ctx: UiMeasureContext, width: Int, height: Int) {
        tree.render(ctx, width, height)
    }

    override fun paint(ctx: UiMeasureContext): List<RenderCommand> {
        return tree.paint(ctx, applyStyles = true)
    }

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
        return activeEntriesTopFirst().any { entry -> entry.handleMouseMove(mouseX, mouseY) }
    }

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        return activeEntriesTopFirst().any { entry -> entry.handleMouseDown(mouseX, mouseY, button) }
    }

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
        return activeEntriesTopFirst().any { entry -> entry.handleMouseUp(mouseX, mouseY, button) }
    }

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean {
        return activeEntriesTopFirst().any { entry -> entry.handleMouseWheel(mouseX, mouseY, delta) }
    }

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean {
        return activeEntriesTopFirst().any { entry -> entry.handleKeyDown(keyCode, keyChar) }
    }

    override fun clearRefs() {
        tree.clearRefs()
        transientOwnershipRegistry.clear()
        colorPickerEntry.close()
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
        return rootNode.children.mapNotNull { child ->
            entriesByNode[child]?.state?.id
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

    internal fun debugSystemColorPickerPopupOwnerScope(): OverlayOwnerScope? {
        return colorPickerEntry.debugOwnerScope()
    }

    private fun reconcileMountedEntries() {
        val desiredNodes = entryRegistry.allEntries()
            .filter { it.state.active }
            .map { it.node }
        val currentNodes = rootNode.children
        val unchanged = currentNodes.size == desiredNodes.size &&
                currentNodes.indices.all { currentNodes[it] === desiredNodes[it] }
        if (unchanged) return
        currentNodes.forEach { node ->
            node.parent = null
        }
        currentNodes.clear()
        desiredNodes.forEach { node ->
            node.parent = rootNode
            currentNodes += node
        }
    }

    private fun activeEntriesTopFirst(): List<SystemOverlayEntry> {
        return entryRegistry.allEntries()
            .asReversed()
            .filter { it.state.active }
    }

    private class InspectorOverlayEntry(
        private val inspectorController: InspectorController
    ) : SystemOverlayEntry {
        override val state: SystemOverlayEntryState = SystemOverlayEntryState(
            id = SystemOverlayEntryId.Inspector,
            order = 100
        )
        override val node: SystemInspectorOverlayNode = SystemInspectorOverlayNode(inspectorController)

        override fun sync(frame: SystemOverlayFrameContext) {
            node.bindInspectedTree(frame.inspectedRoot, frame.inspectedLayoutRevision)
            node.updateCursor(frame.cursorX, frame.cursorY, frame.inspectorPointerCaptured)
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
                dragType = SystemOverlayDragType.PanelMove,
                pointerX = frame.cursorX,
                pointerY = frame.cursorY
            )
        }
    }

    private class ColorPickerOverlayEntry : SystemOverlayEntry, InspectorColorPickerHost {
        override val state: SystemOverlayEntryState = SystemOverlayEntryState(
            id = SystemOverlayEntryId.ColorPickerPopup,
            order = 200
        )
        private val ownerToken: Any = Any()
        private val popupEngine: ColorPickerPopupEngine = ColorPickerPopupEngine()
        override val node: SystemColorPickerOverlayNode = SystemColorPickerOverlayNode(popupEngine = popupEngine)
        private var draggable: Boolean = true

        override fun sync(frame: SystemOverlayFrameContext) {
            node.updateCursor(frame.cursorX, frame.cursorY)
            state.active = popupEngine.isOpenFor(ownerToken)
            if (!state.active) {
                state.panelState.hide()
                state.dragSession.end()
                return
            }
            if (state.dragSession.active) {
                state.dragSession.update(frame.cursorX, frame.cursorY)
                val dx = state.dragSession.currentPointerX - state.dragSession.startPointerX
                val dy = state.dragSession.currentPointerY - state.dragSession.startPointerY
                val draggedRect = Rect(
                    x = state.dragSession.startPanelX + dx,
                    y = state.dragSession.startPanelY + dy,
                    width = state.dragSession.startPanelWidth,
                    height = state.dragSession.startPanelHeight
                )
                popupEngine.forcePanelRect(ownerToken, draggedRect)
            }
            val panelRect = popupEngine.debugPanelRect(ownerToken)
            if (panelRect != null) {
                state.panelState.updateFromRect(panelRect)
            } else {
                state.panelState.show()
            }
        }

        override fun onInputFrame(viewportWidth: Int, viewportHeight: Int) {
            popupEngine.onFrame(viewportWidth, viewportHeight)
        }

        override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean {
            if (!state.active) return false
            popupEngine.onCursorPosition(mouseX, mouseY)
            if (state.dragSession.active) {
                state.dragSession.update(mouseX, mouseY)
                val dx = state.dragSession.currentPointerX - state.dragSession.startPointerX
                val dy = state.dragSession.currentPointerY - state.dragSession.startPointerY
                val draggedRect = Rect(
                    x = state.dragSession.startPanelX + dx,
                    y = state.dragSession.startPanelY + dy,
                    width = state.dragSession.startPanelWidth,
                    height = state.dragSession.startPanelHeight
                )
                popupEngine.forcePanelRect(ownerToken, draggedRect)
                popupEngine.onCursorPosition(mouseX, mouseY)
                return true
            }
            return popupEngine.handleMouseMove(mouseX, mouseY)
        }

        override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
            if (!state.active) return false
            if (button == MouseButton.LEFT && draggable) {
                val closeRect = popupEngine.debugCloseRect(ownerToken)
                if (closeRect != null && closeRect.contains(mouseX, mouseY)) {
                    return popupEngine.handleMouseDown(mouseX, mouseY, button)
                }
                val headerRect = popupEngine.debugHeaderRect(ownerToken)
                if (headerRect != null && headerRect.contains(mouseX, mouseY)) {
                    state.dragSession.begin(
                        entryId = state.id,
                        type = SystemOverlayDragType.PanelMove,
                        pointerX = mouseX,
                        pointerY = mouseY,
                        panelState = state.panelState
                    )
                    return true
                }
            }
            return popupEngine.handleMouseDown(mouseX, mouseY, button)
        }

        override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
            if (!state.active) return false
            if (button == MouseButton.LEFT && state.dragSession.active) {
                state.dragSession.update(mouseX, mouseY)
                state.dragSession.end()
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

        fun debugHeaderRect(): Rect? {
            return popupEngine.debugHeaderRect(ownerToken)
        }

        fun captureEyedropperSample() {
            popupEngine.captureEyedropperSample()
        }

        fun debugOwnerScope(): OverlayOwnerScope? {
            return popupEngine.debugOwnerScope(ownerToken)
        }
    }

    private companion object {
        private fun syncDragSession(
            entryState: SystemOverlayEntryState,
            dragging: Boolean,
            dragType: SystemOverlayDragType,
            pointerX: Int,
            pointerY: Int
        ) {
            if (dragging) {
                if (!entryState.dragSession.active) {
                    entryState.dragSession.begin(
                        entryId = entryState.id,
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
    }
}

