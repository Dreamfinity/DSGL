package org.dreamfinity.dsgl.core.colorpicker

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorPickerStateTests {
    @Test
    fun `disables alpha by forcing opaque color`() {
        val state = ColorPickerState(
            color = RgbaColor(0.2f, 0.4f, 0.6f, 0.3f),
            alphaEnabled = false
        )
        val updated = state.withColor(RgbaColor(0.1f, 0.2f, 0.3f, 0.1f))
        assertEquals(1f, updated.color.a)
    }

    @Test
    fun `restore previous and commit current work`() {
        val initial = ColorPickerState(color = RgbaColor(1f, 0f, 0f, 1f))
        val changed = initial.withColor(RgbaColor(0f, 1f, 0f, 1f))
        val restored = changed.withRestoredPrevious()
        assertEquals(initial.color.toArgbInt(), restored.color.toArgbInt())

        val committed = changed.withCommittedCurrent()
        assertEquals(changed.color.toArgbInt(), committed.previous.toArgbInt())
    }
}
