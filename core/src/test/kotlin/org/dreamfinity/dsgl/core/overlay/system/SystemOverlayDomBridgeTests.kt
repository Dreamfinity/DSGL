package org.dreamfinity.dsgl.core.overlay.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.internal.SystemInspectorOverlayNode
import org.dreamfinity.dsgl.core.render.RenderCommand

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
            RenderCommand.DrawText("Hello", 18, 20, 0xFFEEDDCC.toInt())
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
            RenderCommand.DrawText("A", 6, 7, 0xFFFFFFFF.toInt())
        )
        val second = listOf(
            RenderCommand.DrawRect(2, 4, 12, 10, 0xFF556677.toInt()),
            RenderCommand.DrawText("B", 6, 7, 0xFFFFFFFF.toInt())
        )

        SystemOverlayCommandDslRenderer.rebuildInto(host, first, "reuse")
        val firstNode0 = host.children[0]
        val firstNode1 = host.children[1]

        SystemOverlayCommandDslRenderer.rebuildInto(host, second, "reuse")
        assertSame(firstNode0, host.children[0])
        assertSame(firstNode1, host.children[1])
    }

    @Test
    fun `system inspector overlay creates dom children from controller frame`() {
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
        assertTrue(overlay.children.all { it.styleType == "dsgl-system-raw-render-command" })
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

        controller.deactivate()
        overlay.render(ctx, 0, 0, 420, 280)
        assertTrue(overlay.children.isEmpty())
    }
}

