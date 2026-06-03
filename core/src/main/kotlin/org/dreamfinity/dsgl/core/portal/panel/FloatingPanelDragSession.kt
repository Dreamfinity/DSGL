package org.dreamfinity.dsgl.core.portal.panel

enum class FloatingPanelDragType {
    PanelMove,
    PanelResize,
    Transient,
}

enum class FloatingPanelResizeHandle {
    Left,
    Right,
    Top,
    Bottom,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

class FloatingPanelDragSession {
    var active: Boolean = false
        private set
    var ownerId: Any? = null
        private set
    var type: FloatingPanelDragType? = null
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
    var resizeHandle: FloatingPanelResizeHandle? = null
        private set

    fun begin(
        ownerId: Any,
        type: FloatingPanelDragType,
        pointerX: Int,
        pointerY: Int,
        panelState: FloatingPanelState,
        resizeHandle: FloatingPanelResizeHandle? = null,
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
