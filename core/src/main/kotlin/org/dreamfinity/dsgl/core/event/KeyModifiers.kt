package org.dreamfinity.dsgl.core.event

/**
 * Tracks modifier key state.
 */
object KeyModifiers {
    @Volatile var shiftDown: Boolean = false
        private set
    @Volatile var controlDown: Boolean = false
        private set
    @Volatile var metaDown: Boolean = false
        private set

    private val macOs: Boolean = run {
        val osName = System.getProperty("os.name") ?: return@run false
        osName.lowercase().contains("mac")
    }

    val shortcutDown: Boolean
        get() = if (macOs) metaDown else controlDown

    /** Updates tracked modifier state based on key events. */
    fun update(keyCode: Int, down: Boolean) {
        when (keyCode) {
            KeyCodes.LSHIFT, KeyCodes.RSHIFT -> shiftDown = down
            KeyCodes.LCONTROL, KeyCodes.RCONTROL -> controlDown = down
            KeyCodes.LMETA, KeyCodes.RMETA -> metaDown = down
        }
    }

    /** Host-side synchronization for cases where key-up events are missed. */
    fun sync(shift: Boolean, control: Boolean, meta: Boolean) {
        shiftDown = shift
        controlDown = control
        metaDown = meta
    }
}
