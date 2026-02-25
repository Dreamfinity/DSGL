package org.dreamfinity.dsgl.core.animation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EasingTests {
    @Test
    fun `cubic bezier endpoints match css semantics`() {
        val easing = cubicBezier(0.25f, 0.1f, 0.25f, 1f)
        assertEquals(0f, easing.map(0f))
        assertEquals(1f, easing.map(1f))
    }

    @Test
    fun `cubic bezier is monotonic for common curves`() {
        val easing = cubicBezier(0.42f, 0f, 0.58f, 1f)
        var previous = -1f
        for (i in 0..20) {
            val t = i / 20f
            val value = easing.map(t)
            assertTrue(value >= previous)
            previous = value
        }
    }

    @Test
    fun `preset constants use expected control points`() {
        val linearMid = Easings.LINEAR.map(0.5f)
        val easeMid = Easings.EASE.map(0.5f)
        val easeInMid = Easings.EASE_IN.map(0.5f)
        val easeOutMid = Easings.EASE_OUT.map(0.5f)
        val easeInOutMid = Easings.EASE_IN_OUT.map(0.5f)

        assertEquals(0.5f, linearMid)
        assertTrue(easeMid in 0.2f..0.9f)
        assertTrue(easeInMid < 0.6f)
        assertTrue(easeOutMid > 0.3f)
        assertTrue(easeInOutMid in 0.3f..0.7f)
    }
}

