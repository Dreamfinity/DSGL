package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.PortalDismissPolicy
import org.dreamfinity.dsgl.core.overlay.PortalEntry
import org.dreamfinity.dsgl.core.overlay.PortalEntryBounds
import org.dreamfinity.dsgl.core.overlay.PortalEntryId
import org.dreamfinity.dsgl.core.overlay.PortalEntryOrder
import org.dreamfinity.dsgl.core.overlay.PortalEntryPlacement
import org.dreamfinity.dsgl.core.overlay.PortalEntryState
import org.dreamfinity.dsgl.core.overlay.PortalFocusPolicy
import org.dreamfinity.dsgl.core.overlay.PortalInputPolicy
import org.dreamfinity.dsgl.core.overlay.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelDragSession
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelState
import java.util.IdentityHashMap

enum class SystemOverlayEntryId {
    Inspector,
    ColorPickerPopup,
    ColorPickerTransient,
    PanelDemo,
    TransientSession,
}

enum class SystemOverlayLane(
    val zOrder: Int,
) {
    PanelContent(0),
    Transient(1),
}

class SystemOverlayEntryState(
    val id: SystemOverlayEntryId,
    val order: Int,
    val lane: SystemOverlayLane = SystemOverlayLane.PanelContent,
    val panelState: OverlayPanelState = OverlayPanelState(),
    val dragSession: OverlayPanelDragSession = OverlayPanelDragSession(),
) {
    var active: Boolean = false
        internal set
}

internal data class SystemOverlayFrameContext(
    val inspectedRoot: DOMNode?,
    val inspectedLayoutRevision: Long,
    val cursorX: Int,
    val cursorY: Int,
    val inspectorPointerCaptured: Boolean,
)

internal interface SystemOverlayEntry {
    val state: SystemOverlayEntryState
    val node: DOMNode

    fun participatesInDomInput(): Boolean = false

    fun enablesDomInputFallbackRouting(): Boolean = participatesInDomInput()

    fun sync(frame: SystemOverlayFrameContext)

    fun onInputFrame(viewportWidth: Int, viewportHeight: Int) = Unit

    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = false

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean = false

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = false
}

class SystemOverlayTransientSession(
    val ownerToken: Any,
    val entryState: SystemOverlayEntryState =
        SystemOverlayEntryState(
            id = SystemOverlayEntryId.TransientSession,
            order = Int.MAX_VALUE,
        ),
)

class SystemOverlayTransientOwnershipRegistry {
    private val sessions: IdentityHashMap<Any, SystemOverlayTransientSession> = IdentityHashMap()

    fun resolve(ownerToken: Any): SystemOverlayTransientSession =
        sessions.getOrPut(ownerToken) {
            SystemOverlayTransientSession(ownerToken = ownerToken)
        }

    @Suppress("UnusedParameter")
    fun resolve(ownerToken: Any, cursorX: Int, cursorY: Int): SystemOverlayTransientSession = resolve(ownerToken)

    fun release(ownerToken: Any): Boolean = sessions.remove(ownerToken) != null

    fun clear() {
        sessions.clear()
    }

    fun activeSessions(): List<SystemOverlayTransientSession> = sessions.values.toList()
}

internal class SystemOverlayEntryRegistry(
    entries: List<SystemOverlayEntry>,
) {
    private val orderedEntries: List<SystemOverlayEntry> = entries.sortedBy { it.state.order }
    private val byId: Map<SystemOverlayEntryId, SystemOverlayEntry> = orderedEntries.associateBy { it.state.id }

    fun allEntries(): List<SystemOverlayEntry> = orderedEntries

    fun entry(id: SystemOverlayEntryId): SystemOverlayEntry? = byId[id]
}

internal class SystemOverlayPortalEntry(
    private val entry: SystemOverlayEntry,
) : PortalEntry {
    override val state: PortalEntryState =
        PortalEntryState(
            id = PortalEntryId("system.${entry.state.id.name}"),
            ownerToken = entry.state,
            surface = ScreenDomainSurfaces.SystemPortal,
            order =
                PortalEntryOrder(
                    zIndex = entry.state.lane.zOrder,
                    sequence = entry.state.order,
                ),
            dismissPolicy = PortalDismissPolicy.None,
            inputPolicy = entry.inputPolicy(),
            focusPolicy = PortalFocusPolicy.Preserve,
        )
    override val node: DOMNode = entry.node

    val systemEntry: SystemOverlayEntry
        get() = entry

    fun syncPlacement(viewportWidth: Int, viewportHeight: Int) {
        if (!entry.state.active) {
            state.deactivate()
            return
        }
        state.activate(
            PortalEntryPlacement(
                anchorBounds = null,
                bounds =
                    PortalEntryBounds(
                        viewportBounds = Rect(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1)),
                        entryBounds = entry.resolvePortalEntryBounds(viewportWidth, viewportHeight),
                    ),
            ),
        )
    }

    override fun close() {
        entry.state.active = false
        entry.state.panelState
            .hide()
        entry.state.dragSession
            .end()
        state.deactivate()
    }

    override fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = entry.handleMouseMove(mouseX, mouseY)

    override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        entry.handleMouseDown(mouseX, mouseY, button)

    override fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean =
        entry.handleMouseUp(mouseX, mouseY, button)

    override fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean =
        entry.handleMouseWheel(mouseX, mouseY, delta)

    override fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = entry.handleKeyDown(keyCode, keyChar)

    private fun SystemOverlayEntry.inputPolicy(): PortalInputPolicy =
        when {
            participatesInDomInput() || enablesDomInputFallbackRouting() -> PortalInputPolicy.ManualThenDomFallback
            else -> PortalInputPolicy.ManualOnly
        }

    private fun SystemOverlayEntry.resolvePortalEntryBounds(viewportWidth: Int, viewportHeight: Int): Rect {
        val panelBounds = state.panelState.currentRectOrNull()
        if (panelBounds != null && panelBounds.width > 0 && panelBounds.height > 0) {
            return panelBounds
        }
        if (node.bounds.width > 0 && node.bounds.height > 0) {
            return node.bounds
        }
        return Rect(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1))
    }
}
