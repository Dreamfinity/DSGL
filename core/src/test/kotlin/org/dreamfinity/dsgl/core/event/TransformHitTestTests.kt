package org.dreamfinity.dsgl.core.event

import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.style.UiTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransformHitTestTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<org.dreamfinity.dsgl.core.render.RenderCommand>) = Unit
    }

    @Test
    fun `hover chain follows translated transform`() {
        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "child").applyParent(root)
        child.width = 40
        child.height = 20
        child.transform = UiTransform(translateX = 50f, translateY = 0f)

        root.render(ctx, 0, 0, 200, 100)

        val moved = collectHoverChain(root, 60, 10)
        assertTrue(moved.any { it.key == "child" })

        val original = collectHoverChain(root, 10, 10)
        assertEquals(false, original.any { it.key == "child" })
    }
}
