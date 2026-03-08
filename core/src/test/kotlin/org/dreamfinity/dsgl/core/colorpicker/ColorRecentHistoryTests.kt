package org.dreamfinity.dsgl.core.colorpicker

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorRecentHistoryTests {
    @Test
    fun `adds most recent color to front and deduplicates`() {
        val history = ColorRecentHistory(capacity = 4)
        val red = RgbaColor(1f, 0f, 0f, 1f)
        val green = RgbaColor(0f, 1f, 0f, 1f)
        val blue = RgbaColor(0f, 0f, 1f, 1f)

        history.add(red)
        history.add(green)
        history.add(blue)
        history.add(green)

        val snapshot = history.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals(green.toArgbInt(), snapshot[0].toArgbInt())
        assertEquals(blue.toArgbInt(), snapshot[1].toArgbInt())
        assertEquals(red.toArgbInt(), snapshot[2].toArgbInt())
    }

    @Test
    fun `honors max capacity`() {
        val history = ColorRecentHistory(capacity = 3)
        repeat(5) { index ->
            history.add(RgbaColor(index / 10f, 0f, 0f, 1f))
        }

        val snapshot = history.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals(RgbaColor(0.4f, 0f, 0f, 1f).toArgbInt(), snapshot[0].toArgbInt())
        assertEquals(RgbaColor(0.3f, 0f, 0f, 1f).toArgbInt(), snapshot[1].toArgbInt())
        assertEquals(RgbaColor(0.2f, 0f, 0f, 1f).toArgbInt(), snapshot[2].toArgbInt())
    }
}
