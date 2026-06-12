package org.dreamfinity.dsgl.core.portal.panel

import org.dreamfinity.dsgl.core.dom.layout.Rect

class FloatingPanelState {
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
