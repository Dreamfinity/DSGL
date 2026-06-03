package org.dreamfinity.dsgl.core.portal.system

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.PortalDismissPolicy
import org.dreamfinity.dsgl.core.portal.PortalEntry
import org.dreamfinity.dsgl.core.portal.PortalEntryBounds
import org.dreamfinity.dsgl.core.portal.PortalEntryId
import org.dreamfinity.dsgl.core.portal.PortalEntryOrder
import org.dreamfinity.dsgl.core.portal.PortalEntryPlacement
import org.dreamfinity.dsgl.core.portal.PortalEntryState
import org.dreamfinity.dsgl.core.portal.PortalFocusPolicy
import org.dreamfinity.dsgl.core.portal.PortalInputPolicy
import org.dreamfinity.dsgl.core.portal.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.portal.panel.FloatingPanelDragSession
import org.dreamfinity.dsgl.core.portal.panel.FloatingPanelState
import java.util.IdentityHashMap

enum class SystemPortalEntryId {
    Inspector,
    ColorPickerPopup,
    ColorPickerTransient,
    TransientSession,
}

enum class SystemPortalLane(
    val zOrder: Int,
) {
    PanelContent(0),
    Transient(1),
}

class SystemPortalEntryState(
    val id: SystemPortalEntryId,
    val order: Int,
    val lane: SystemPortalLane = SystemPortalLane.PanelContent,
    val panelState: FloatingPanelState = FloatingPanelState(),
    val dragSession: FloatingPanelDragSession = FloatingPanelDragSession(),
) {
    var active: Boolean = false
        internal set
}

internal data class SystemPortalFrameContext(
    val inspectedRoot: DOMNode?,
    val inspectedLayoutRevision: Long,
    val cursorX: Int,
    val cursorY: Int,
    val inspectorPointerCaptured: Boolean,
)

internal interface SystemPortalEntry {
    val state: SystemPortalEntryState
    val node: DOMNode

    fun participatesInDomInput(): Boolean = false

    fun enablesDomInputFallbackRouting(): Boolean = participatesInDomInput()

    fun sync(frame: SystemPortalFrameContext)

    fun onInputFrame(viewportWidth: Int, viewportHeight: Int) = Unit

    fun handleMouseMove(mouseX: Int, mouseY: Int): Boolean = false

    fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    fun handleMouseUp(mouseX: Int, mouseY: Int, button: MouseButton): Boolean = false

    fun handleMouseWheel(mouseX: Int, mouseY: Int, delta: Int): Boolean = false

    fun handleKeyDown(keyCode: Int, keyChar: Char): Boolean = false

    fun handleKeyUp(keyCode: Int, keyChar: Char): Boolean = false
}

class SystemPortalTransientSession(
    val ownerToken: Any,
    val entryState: SystemPortalEntryState =
        SystemPortalEntryState(
            id = SystemPortalEntryId.TransientSession,
            order = Int.MAX_VALUE,
        ),
)

class SystemPortalTransientOwnershipRegistry {
    private val sessions: IdentityHashMap<Any, SystemPortalTransientSession> = IdentityHashMap()

    fun resolve(ownerToken: Any): SystemPortalTransientSession =
        sessions.getOrPut(ownerToken) {
            SystemPortalTransientSession(ownerToken = ownerToken)
        }

    @Suppress("UnusedParameter")
    fun resolve(ownerToken: Any, cursorX: Int, cursorY: Int): SystemPortalTransientSession = resolve(ownerToken)

    fun release(ownerToken: Any): Boolean = sessions.remove(ownerToken) != null

    fun clear() {
        sessions.clear()
    }

    fun activeSessions(): List<SystemPortalTransientSession> = sessions.values.toList()
}

internal class SystemPortalEntryRegistry(
    entries: List<SystemPortalEntry>,
) {
    private val orderedEntries: List<SystemPortalEntry> = entries.sortedBy { it.state.order }
    private val byId: Map<SystemPortalEntryId, SystemPortalEntry> = orderedEntries.associateBy { it.state.id }

    fun allEntries(): List<SystemPortalEntry> = orderedEntries

    fun entry(id: SystemPortalEntryId): SystemPortalEntry? = byId[id]
}

internal class SystemPortalEntryAdapter(
    private val entry: SystemPortalEntry,
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

    val systemEntry: SystemPortalEntry
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

    override fun handleKeyUp(keyCode: Int, keyChar: Char): Boolean = entry.handleKeyUp(keyCode, keyChar)

    private fun SystemPortalEntry.inputPolicy(): PortalInputPolicy =
        when {
            participatesInDomInput() || enablesDomInputFallbackRouting() -> PortalInputPolicy.ManualThenDomFallback
            else -> PortalInputPolicy.ManualOnly
        }

    private fun SystemPortalEntry.resolvePortalEntryBounds(viewportWidth: Int, viewportHeight: Int): Rect {
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
