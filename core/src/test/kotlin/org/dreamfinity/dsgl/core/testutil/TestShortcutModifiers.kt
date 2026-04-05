package org.dreamfinity.dsgl.core.testutil

import org.dreamfinity.dsgl.core.event.KeyModifiers

private val isMacOsForShortcutTests: Boolean = run {
    val osName = System.getProperty("os.name") ?: return@run false
    osName.lowercase().contains("mac")
}

internal fun syncNoModifiers() {
    KeyModifiers.sync(shift = false, control = false, meta = false)
}

internal fun syncShiftOnly() {
    KeyModifiers.sync(shift = true, control = false, meta = false)
}

internal fun syncShortcutHeld(shift: Boolean = false) {
    if (isMacOsForShortcutTests) {
        KeyModifiers.sync(shift = shift, control = false, meta = true)
    } else {
        KeyModifiers.sync(shift = shift, control = true, meta = false)
    }
}
