package org.dreamfinity.dsgl.core.event

/**
 * Tracks modifier key state.
 */
object KeyModifiers {
    @Volatile var shiftDown: Boolean = false
        private set

    /** Updates tracked modifier state based on key events. */
    fun update(keyCode: Int, down: Boolean) {
        if (keyCode == KeyCodes.LSHIFT || keyCode == KeyCodes.RSHIFT) {
            shiftDown = down
        }
    }
}
