package org.dreamfinity.dsgl.core.event

object KeyModifiers {
    @Volatile var shiftDown: Boolean = false
        private set

    fun update(keyCode: Int, down: Boolean) {
        if (keyCode == KeyCodes.LSHIFT || keyCode == KeyCodes.RSHIFT) {
            shiftDown = down
        }
    }
}
