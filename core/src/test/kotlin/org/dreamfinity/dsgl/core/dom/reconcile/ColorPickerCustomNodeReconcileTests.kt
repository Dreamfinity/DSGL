package org.dreamfinity.dsgl.core.dom.reconcile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerStyle
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.colorpicker.internal.AlphaSurfaceNode
import org.dreamfinity.dsgl.core.colorpicker.internal.ColorFieldSurfaceNode
import org.dreamfinity.dsgl.core.colorpicker.internal.ColorSwatchSurfaceNode
import org.dreamfinity.dsgl.core.colorpicker.internal.HueSurfaceNode
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand

class ColorPickerCustomNodeReconcileTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @Test
    fun `color field custom bind state syncs on reconcile reuse`() {
        val styleA = ColorPickerStyle(inputBorderColor = 0xFF334455.toInt())
        val styleB = ColorPickerStyle(inputBorderColor = 0xFF7799AA.toInt())
        val currentRoot = ContainerNode(key = "root")
        val retained = ColorFieldSurfaceNode(key = "field").applyParent(currentRoot)
        retained.bind(style = styleA, color = RgbaColor(1f, 0f, 0f, 1f), hueDeg = 0f)
        val before = renderCommands(retained)

        val templateRoot = ContainerNode(key = "root")
        val template = ColorFieldSurfaceNode(key = "field").applyParent(templateRoot)
        template.bind(style = styleB, color = RgbaColor(0f, 1f, 0f, 1f), hueDeg = 120f)
        val expected = renderCommands(template)
        assertNotEquals(before, expected)

        DomReconciler.reconcile(currentRoot, templateRoot)
        val retainedAfter = currentRoot.children.single() as ColorFieldSurfaceNode
        val after = renderCommands(retainedAfter)

        assertSame(retained, retainedAfter)
        assertEquals(expected, after)
    }

    @Test
    fun `hue slider custom bind state syncs on reconcile reuse`() {
        val style = ColorPickerStyle()
        val currentRoot = ContainerNode(key = "root")
        val retained = HueSurfaceNode(key = "hue").applyParent(currentRoot)
        retained.bind(style = style, hueDeg = 12f)
        val before = renderCommands(retained)

        val templateRoot = ContainerNode(key = "root")
        val template = HueSurfaceNode(key = "hue").applyParent(templateRoot)
        template.bind(style = style, hueDeg = 222f)
        val expected = renderCommands(template)
        assertNotEquals(before, expected)

        DomReconciler.reconcile(currentRoot, templateRoot)
        val retainedAfter = currentRoot.children.single() as HueSurfaceNode
        val after = renderCommands(retainedAfter)

        assertSame(retained, retainedAfter)
        assertEquals(expected, after)
    }

    @Test
    fun `alpha slider custom bind state syncs on reconcile reuse`() {
        val style = ColorPickerStyle()
        val currentRoot = ContainerNode(key = "root")
        val retained = AlphaSurfaceNode(key = "alpha").applyParent(currentRoot)
        retained.bind(style = style, color = RgbaColor(0.5f, 0.4f, 0.3f, 1f))
        val before = renderCommands(retained)

        val templateRoot = ContainerNode(key = "root")
        val template = AlphaSurfaceNode(key = "alpha").applyParent(templateRoot)
        template.bind(style = style, color = RgbaColor(0.5f, 0.4f, 0.3f, 0.2f))
        val expected = renderCommands(template)
        assertNotEquals(before, expected)

        DomReconciler.reconcile(currentRoot, templateRoot)
        val retainedAfter = currentRoot.children.single() as AlphaSurfaceNode
        val after = renderCommands(retainedAfter)

        assertSame(retained, retainedAfter)
        assertEquals(expected, after)
    }

    @Test
    fun `color swatch custom bind state syncs on reconcile reuse`() {
        val style = ColorPickerStyle()
        val currentRoot = ContainerNode(key = "root")
        val retained = ColorSwatchSurfaceNode(key = "swatch").applyParent(currentRoot)
        retained.bind(style = style, color = RgbaColor(1f, 0f, 0f, 1f), highlighted = false)
        val before = renderCommands(retained)

        val templateRoot = ContainerNode(key = "root")
        val template = ColorSwatchSurfaceNode(key = "swatch").applyParent(templateRoot)
        template.bind(style = style, color = RgbaColor(0f, 0f, 1f, 1f), highlighted = true)
        val expected = renderCommands(template)
        assertNotEquals(before, expected)

        DomReconciler.reconcile(currentRoot, templateRoot)
        val retainedAfter = currentRoot.children.single() as ColorSwatchSurfaceNode
        val after = renderCommands(retainedAfter)

        assertSame(retained, retainedAfter)
        assertEquals(expected, after)
    }

    private fun renderCommands(node: DOMNode): List<RenderCommand> {
        node.render(ctx, NODE_RECT.x, NODE_RECT.y, NODE_RECT.width, NODE_RECT.height)
        return buildList {
            node.buildRenderCommands(ctx, this)
        }
    }

    private companion object {
        val NODE_RECT: Rect = Rect(12, 24, 56, 14)
    }
}

