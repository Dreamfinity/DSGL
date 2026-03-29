package org.dreamfinity.dsgl.core.overlay.panel

enum class OverlayPanelDragType {
    PanelMove,
    PanelResize,
    Transient
}

enum class OverlayPanelResizeHandle {
    Left,
    Right,
    Top,
    Bottom,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight
}

class OverlayPanelDragSession {
    var active: Boolean = false
        private set
    var ownerId: Any? = null
        private set
    var type: OverlayPanelDragType? = null
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
    var resizeHandle: OverlayPanelResizeHandle? = null
        private set

    fun begin(
        ownerId: Any,
        type: OverlayPanelDragType,
        pointerX: Int,
        pointerY: Int,
        panelState: OverlayPanelState,
        resizeHandle: OverlayPanelResizeHandle? = null
    ) {
        active = true
        this.ownerId = ownerId
        this.type = type
        this.resizeHandle = resizeHandle
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
        ownerId = null
        type = null
        resizeHandle = null
    }
}
