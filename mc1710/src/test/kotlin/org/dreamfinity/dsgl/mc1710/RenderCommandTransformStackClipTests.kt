package org.dreamfinity.dsgl.mc1710

import kotlin.test.Test
import kotlin.test.assertEquals
import org.dreamfinity.dsgl.core.render.RenderCommand

class RenderCommandTransformStackClipTests {
    @Test
    fun `transformed clip uses edge-based rounding so fractional max edge stays covered`() {
        val stack = RenderCommandTransformStack()
        stack.push(
            RenderCommand.PushTransform(
                originX = 0f,
                originY = 0f,
                translateX = 0f,
                translateY = 0f,
                scaleX = 1.5f,
                scaleY = 1.5f,
                rotateDeg = 0f
            )
        )

        val resolved = stack.resolveClipRect(x = 1, y = 2, width = 10, height = 6)

        assertEquals(1, resolved.x)
        assertEquals(3, resolved.y)
        assertEquals(16, resolved.width)
        assertEquals(9, resolved.height)
    }
}
