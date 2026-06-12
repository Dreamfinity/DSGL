package org.dreamfinity.dsgl.core.dom.elements

import org.dreamfinity.dsgl.core.DomTree
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Display
import org.dreamfinity.dsgl.core.style.StyleEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SizeConstraintLayoutTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 10

            override fun measureText(text: String): Int = text.length * 6

            override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * ((fontSize ?: 10) / 2).coerceAtLeast(1)

            override fun fontHeight(fontId: String?, fontSize: Int?): Int = fontSize ?: 10

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        StyleEngine.setStylesDirectory(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `generic min-width clamps resolved width`() {
        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "child").applyParent(root)
        child.applyStyle {
            display = Display.Inline
            minWidth = 90.px
        }

        DomTree(root).render(ctx, 220, 120)

        assertEquals(90, child.bounds.width)
    }

    @Test
    fun `generic min-height clamps resolved height`() {
        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "child").applyParent(root)
        child.applyStyle {
            display = Display.Inline
            minHeight = 36.px
        }

        DomTree(root).render(ctx, 220, 120)

        assertEquals(36, child.bounds.height)
    }

    @Test
    fun `generic max-width clamps resolved width`() {
        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "child").applyParent(root)
        child.applyStyle {
            display = Display.Inline
            width = 180.px
            maxWidth = 70.px
        }

        DomTree(root).render(ctx, 220, 120)

        assertEquals(70, child.bounds.width)
    }

    @Test
    fun `generic max-height clamps resolved height`() {
        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "child").applyParent(root)
        child.applyStyle {
            display = Display.Inline
            height = 90.px
            maxHeight = 30.px
        }

        DomTree(root).render(ctx, 220, 120)

        assertEquals(30, child.bounds.height)
    }

    @Test
    fun `combined min and max constraints clamp into range`() {
        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "child").applyParent(root)
        child.applyStyle {
            display = Display.Inline
            width = 240.px
            height = 10.px
            minWidth = 60.px
            maxWidth = 120.px
            minHeight = 20.px
            maxHeight = 40.px
        }

        DomTree(root).render(ctx, 260, 140)

        assertEquals(120, child.bounds.width)
        assertEquals(20, child.bounds.height)
    }

    @Test
    fun `conflicting min max constraints resolve deterministically with min winning`() {
        val root = ContainerNode(key = "root")
        val child = ContainerNode(key = "child").applyParent(root)
        child.applyStyle {
            display = Display.Inline
            width = 10.px
            height = 10.px
            minWidth = 80.px
            maxWidth = 30.px
            minHeight = 55.px
            maxHeight = 20.px
        }

        DomTree(root).render(ctx, 260, 160)

        assertEquals(80, child.bounds.width)
        assertEquals(55, child.bounds.height)
    }

    @Test
    fun `block flow consumer path uses clamped sizes for layout progression`() {
        val root = ContainerNode(key = "root")
        val first = ContainerNode(key = "first").applyParent(root)
        first.applyStyle {
            maxWidth = 80.px
            height = 100.px
            maxHeight = 30.px
        }
        val second = ContainerNode(key = "second").applyParent(root)
        second.applyStyle {
            height = 12.px
        }

        DomTree(root).render(ctx, 220, 160)

        assertEquals(80, first.bounds.width)
        assertEquals(30, first.bounds.height)
        assertEquals(first.bounds.y + first.bounds.height, second.bounds.y)
    }
}
