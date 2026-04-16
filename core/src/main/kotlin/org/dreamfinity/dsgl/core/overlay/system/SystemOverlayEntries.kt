package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelDragSession
import org.dreamfinity.dsgl.core.overlay.panel.OverlayPanelState
import java.util.IdentityHashMap

enum class SystemOverlayEntryId {
    Inspector,
    ColorPickerPopup,
    ColorPickerTransient,
    PanelDemo,
    TransientSession
}

enum class SystemOverlayLane(
    val zOrder: Int
) {
    PanelContent(0),
    Transient(1)
}

class SystemOverlayEntryState(
    val id: SystemOverlayEntryId,
    val order: Int,
    val lane: SystemOverlayLane = SystemOverlayLane.PanelContent,
    val panelState: OverlayPanelState = OverlayPanelState(),
    val dragSession: OverlayPanelDragSession = OverlayPanelDragSession()
) {
    var active: Boolean = false
        internal set
}

internal data class SystemOverlayFrameContext(
    val inspectedRoot: DOMNode?,
    val inspectedLayoutRevision: Long,
    val cursorX: Int,
    val cursorY: Int,
    val inspectorPointerCaptured: Boolean
)

internal interface SystemOverlayEntry {
    val state: SystemOverlayEntryState
    val node: DOMNode

    fun participatesInDomInput(): Boolean = false

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
    val entryState: SystemOverlayEntryState = SystemOverlayEntryState(
        id = SystemOverlayEntryId.TransientSession,
        order = Int.MAX_VALUE
    )
)

class SystemOverlayTransientOwnershipRegistry {
    private val sessions: IdentityHashMap<Any, SystemOverlayTransientSession> = IdentityHashMap()

    fun resolve(ownerToken: Any): SystemOverlayTransientSession {
        return sessions.getOrPut(ownerToken) {
            SystemOverlayTransientSession(ownerToken = ownerToken)
        }
    }

    fun resolve(ownerToken: Any, cursorX: Int, cursorY: Int): SystemOverlayTransientSession {
        return resolve(ownerToken)
    }

    fun release(ownerToken: Any): Boolean {
        return sessions.remove(ownerToken) != null
    }

    fun clear() {
        sessions.clear()
    }

    fun activeSessions(): List<SystemOverlayTransientSession> {
        return sessions.values.toList()
    }
}

internal class SystemOverlayEntryRegistry(
    entries: List<SystemOverlayEntry>
) {
    private val orderedEntries: List<SystemOverlayEntry> = entries.sortedBy { it.state.order }
    private val byId: Map<SystemOverlayEntryId, SystemOverlayEntry> = orderedEntries.associateBy { it.state.id }

    fun allEntries(): List<SystemOverlayEntry> = orderedEntries

    fun entry(id: SystemOverlayEntryId): SystemOverlayEntry? = byId[id]
}
