package org.dreamfinity.dsgl.core.overlay.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.SingleLineInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.font.FontRegistry
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.internal.SystemInspectorOverlayNode
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.render.TextRenderStyle
import org.dreamfinity.dsgl.core.text.TextMetricsResolver

class SystemOverlayDomBridgeTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @Test
    fun `renderer maps legacy commands to dom nodes`() {
        val host = ContainerNode(stackLayout = true, key = "host")
        val commands = listOf(
            RenderCommand.DrawRect(10, 12, 30, 14, 0xFF112233.toInt()),
            drawText("Hello", 18, 20, 0xFFEEDDCC.toInt())
        )

        SystemOverlayCommandDslRenderer.rebuildInto(host, commands, "test")

        assertEquals(2, host.children.size)
        assertTrue(host.children.all { it.styleType == "dsgl-system-raw-render-command" })
    }

    @Test
    fun `renderer reuses raw nodes instead of recreating them`() {
        val host = ContainerNode(stackLayout = true, key = "host")
        val first = listOf(
            RenderCommand.DrawRect(2, 4, 12, 10, 0xFF223344.toInt()),
            drawText("A", 6, 7, 0xFFFFFFFF.toInt())
        )
        val second = listOf(
            RenderCommand.DrawRect(2, 4, 12, 10, 0xFF556677.toInt()),
            drawText("B", 6, 7, 0xFFFFFFFF.toInt())
        )

        SystemOverlayCommandDslRenderer.rebuildInto(host, first, "reuse")
        val firstNode0 = host.children[0]
        val firstNode1 = host.children[1]

        SystemOverlayCommandDslRenderer.rebuildInto(host, second, "reuse")
        assertSame(firstNode0, host.children[0])
        assertSame(firstNode1, host.children[1])
    }

    @Test
    fun `system inspector overlay creates native dom children from controller frame`() {
        val controller = InspectorController()
        controller.toggle()
        val root = ContainerNode(key = "root").apply {
            bounds = Rect(0, 0, 420, 280)
        }
        ContainerNode(key = "child").apply {
            bounds = Rect(16, 18, 120, 28)
        }.applyParent(root)

        val overlay = SystemInspectorOverlayNode(controller)
        overlay.bindInspectedTree(root, layoutRevision = 1L)
        overlay.updateCursor(mouseX = 22, mouseY = 22, pointerCaptured = false)
        overlay.render(ctx, 0, 0, 420, 280)

        assertTrue(overlay.children.isNotEmpty())
        assertTrue(overlay.children.none { it.styleType == "dsgl-system-raw-render-command" })
    }

    @Test
    fun `system inspector overlay retains focused native input across frame rebuild`() {
        val controller = InspectorController()
        controller.toggle()
        val root = ContainerNode(key = "root").apply {
            bounds = Rect(0, 0, 1280, 720)
        }
        ContainerNode(key = "target").apply {
            bounds = Rect(980, 140, 120, 30)
        }.applyParent(root)

        val overlay = SystemInspectorOverlayNode(controller)
        controller.onLayoutCommitted(root, 1L)
        controller.onCursorMoved(984, 144)
        controller.handleMouseDown(984, 144, org.dreamfinity.dsgl.core.event.MouseButton.LEFT)

        overlay.bindInspectedTree(root, layoutRevision = 2L)
        overlay.updateCursor(mouseX = 984, mouseY = 144, pointerCaptured = false)
        overlay.render(ctx, 0, 0, 1280, 720)

        fun findFirstInput(node: org.dreamfinity.dsgl.core.dom.DOMNode): SingleLineInputNode? {
            if (node is SingleLineInputNode) return node
            node.children.forEach { child ->
                val found = findFirstInput(child)
                if (found != null) return found
            }
            return null
        }

        val initialInput = findFirstInput(overlay)
        assertNotNull(initialInput)
        val router = LayerDomInputRouter { overlay }
        val clickX = initialInput.bounds.x + 2
        val clickY = initialInput.bounds.y + initialInput.bounds.height / 2
        assertTrue(router.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(router.handleMouseUp(clickX, clickY, MouseButton.LEFT))

        val focusedAfterClick = FocusManager.focusedNode()
        assertNotNull(focusedAfterClick)
        val focusedKey = focusedAfterClick.key
        assertEquals(initialInput.key, focusedKey)

        overlay.bindInspectedTree(root, layoutRevision = 3L)
        overlay.updateCursor(mouseX = 984, mouseY = 144, pointerCaptured = false)
        overlay.render(ctx, 0, 0, 1280, 720)

        val focusedAfterRebuild = FocusManager.focusedNode()
        assertTrue(focusedAfterRebuild is SingleLineInputNode)
        assertEquals(focusedKey, focusedAfterRebuild.key)
        assertTrue(focusedAfterRebuild !== initialInput)
    }
    @Test
    fun `system inspector overlay mounts only while inspector is active`() {
        val controller = InspectorController()
        val root = ContainerNode(key = "root").apply {
            bounds = Rect(0, 0, 420, 280)
        }
        val overlay = SystemInspectorOverlayNode(controller)

        overlay.bindInspectedTree(root, layoutRevision = 1L)
        overlay.render(ctx, 0, 0, 420, 280)
        assertTrue(overlay.children.isEmpty())

        controller.toggle()
        overlay.render(ctx, 0, 0, 420, 280)
        assertTrue(overlay.children.isNotEmpty())
        assertTrue(overlay.children.none { it.styleType == "dsgl-system-raw-render-command" })

        controller.deactivate()
        overlay.render(ctx, 0, 0, 420, 280)
        assertTrue(overlay.children.isEmpty())
    }

    private fun drawText(text: String, x: Int, y: Int, color: Int): RenderCommand.DrawText {
        return RenderCommand.DrawText(
            text = text,
            x = x,
            y = y,
            metrics = TextMetricsResolver.resolve(
                ctx = ctx,
                fontId = null,
                fontSizePx = FontRegistry.resolveFontSize(null)
            ),
            baseStyle = TextRenderStyle(color = color)
        )
    }
}




