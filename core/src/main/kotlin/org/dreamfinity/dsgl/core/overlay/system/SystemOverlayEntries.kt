package org.dreamfinity.dsgl.core.overlay.system

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.MouseButton
import java.util.IdentityHashMap

enum class SystemOverlayEntryId {
    Inspector,
    ColorPickerPopup,
    PanelDemo,
    TransientSession
}

enum class SystemOverlayDragType {
    PanelMove,
    PanelResize,
    Transient
}

class SystemOverlayPanelState {
    var visible: Boolean = false
        private set
    var x: Int = 0
        private set
    var y: Int = 0
        private set
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    fun show() {
        visible = true
    }

    fun hide() {
        visible = false
    }

    fun setPosition(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    fun setSize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(0)
        this.height = height.coerceAtLeast(0)
    }

    fun updateFromRect(rect: Rect) {
        show()
        setPosition(rect.x, rect.y)
        setSize(rect.width, rect.height)
    }

    fun currentRectOrNull(): Rect? {
        if (!visible) return null
        return Rect(x, y, width, height)
    }
}

class SystemOverlayDragSession {
    var active: Boolean = false
        private set
    var entryId: SystemOverlayEntryId? = null
        private set
    var type: SystemOverlayDragType? = null
        private set
    var startPointerX: Int = 0
        private set
    var startPointerY: Int = 0
        private set
    var currentPointerX: Int = 0
        private set
    var currentPointerY: Int = 0
        private set
    var startPanelX: Int = 0
        private set
    var startPanelY: Int = 0
        private set
    var startPanelWidth: Int = 0
        private set
    var startPanelHeight: Int = 0
        private set

    fun begin(
        entryId: SystemOverlayEntryId,
        type: SystemOverlayDragType,
        pointerX: Int,
        pointerY: Int,
        panelState: SystemOverlayPanelState
    ) {
        active = true
        this.entryId = entryId
        this.type = type
        startPointerX = pointerX
        startPointerY = pointerY
        currentPointerX = pointerX
        currentPointerY = pointerY
        startPanelX = panelState.x
        startPanelY = panelState.y
        startPanelWidth = panelState.width
        startPanelHeight = panelState.height
    }

    fun update(pointerX: Int, pointerY: Int) {
        if (!active) return
        currentPointerX = pointerX
        currentPointerY = pointerY
    }

    fun end() {
        active = false
        entryId = null
        type = null
    }
}

class SystemOverlayEntryState(
    val id: SystemOverlayEntryId,
    val order: Int,
    val panelState: SystemOverlayPanelState = SystemOverlayPanelState(),
    val dragSession: SystemOverlayDragSession = SystemOverlayDragSession()
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
