package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.colorpicker.internal.SystemColorPickerOverlayNode
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.internal.SystemInspectorOverlayNode
import org.dreamfinity.dsgl.core.overlay.OverlayLayerHost
import org.dreamfinity.dsgl.core.overlay.UiLayerId
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleApplicationScope

class SystemOverlayHost(
    private val inspectorController: InspectorController
) : OverlayLayerHost {
    override val layerId: UiLayerId = UiLayerId.SystemOverlay

    private val rootNode: SystemOverlayRootNode = SystemOverlayRootNode()
    private val inspectorEntry: SystemOverlayEntry = InspectorOverlayEntry(inspectorController)
    private val colorPickerEntry: SystemOverlayEntry = ColorPickerOverlayEntry()
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

    override fun clearRefs() {
        tree.clearRefs()
        transientOwnershipRegistry.clear()
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

    private class ColorPickerOverlayEntry : SystemOverlayEntry {
        override val state: SystemOverlayEntryState = SystemOverlayEntryState(
            id = SystemOverlayEntryId.ColorPickerPopup,
            order = 200
        )
        override val node: SystemColorPickerOverlayNode = SystemColorPickerOverlayNode()

        override fun sync(frame: SystemOverlayFrameContext) {
            node.updateCursor(frame.cursorX, frame.cursorY)
            state.active = ColorPickerRuntime.engine.isOpen()
            if (!state.active) {
                state.panelState.hide()
                state.dragSession.end()
                return
            }
            val panelRect = ColorPickerRuntime.engine.debugActivePanelRect()
            if (panelRect != null) {
                state.panelState.updateFromRect(panelRect)
            } else {
                state.panelState.show()
            }
            syncDragSession(
                entryState = state,
                dragging = ColorPickerRuntime.engine.debugIsDraggingPopup(),
                dragType = SystemOverlayDragType.PanelMove,
                pointerX = frame.cursorX,
                pointerY = frame.cursorY
            )
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

