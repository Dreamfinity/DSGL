package org.dreamfinity.dsgl.core.portal.system

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.SingleLineInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.internal.SystemInspectorPortalNode
import org.dreamfinity.dsgl.core.portal.input.SurfaceDomInputRouter
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SystemPortalDomBridgeTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @Test
    fun `renderer maps legacy commands to dom nodes`() {
        val host = ContainerNode(stackLayout = true, key = "host")
        val commands =
            listOf(
                RenderCommand.DrawRect(10, 12, 30, 14, 0xFF112233.toInt()),
                RenderCommand.DrawText("Hello", 18, 20, 0xFFEEDDCC.toInt()),
            )

        SystemPortalCommandDslRenderer.rebuildInto(host, commands, "test")

        assertEquals(2, host.children.size)
        assertTrue(host.children.all { it.styleType == "dsgl-system-raw-render-command" })
    }

    @Test
    fun `renderer reuses raw nodes instead of recreating them`() {
        val host = ContainerNode(stackLayout = true, key = "host")
        val first =
            listOf(
                RenderCommand.DrawRect(2, 4, 12, 10, 0xFF223344.toInt()),
                RenderCommand.DrawText("A", 6, 7, 0xFFFFFFFF.toInt()),
            )
        val second =
            listOf(
                RenderCommand.DrawRect(2, 4, 12, 10, 0xFF556677.toInt()),
                RenderCommand.DrawText("B", 6, 7, 0xFFFFFFFF.toInt()),
            )

        SystemPortalCommandDslRenderer.rebuildInto(host, first, "reuse")
        val firstNode0 = host.children[0]
        val firstNode1 = host.children[1]

        SystemPortalCommandDslRenderer.rebuildInto(host, second, "reuse")
        assertSame(firstNode0, host.children[0])
        assertSame(firstNode1, host.children[1])
    }

    @Test
    fun `system inspector portal creates native dom children from controller frame`() {
        val controller = InspectorController()
        controller.toggle()
        val root =
            ContainerNode(key = "root").apply {
                bounds = Rect(0, 0, 420, 280)
            }
        ContainerNode(key = "child")
            .apply {
                bounds = Rect(16, 18, 120, 28)
            }.applyParent(root)

        val portalNode = SystemInspectorPortalNode(controller)
        portalNode.bindInspectedTree(root, layoutRevision = 1L)
        portalNode.updateCursor(mouseX = 22, mouseY = 22, pointerCaptured = false)
        portalNode.render(ctx, 0, 0, 420, 280)

        assertTrue(portalNode.children.isNotEmpty())
        assertTrue(portalNode.children.none { it.styleType == "dsgl-system-raw-render-command" })
    }

    @Test
    fun `system inspector portal retains focused native input across frame rebuild`() {
        val controller = InspectorController()
        controller.toggle()
        val root =
            ContainerNode(key = "root").apply {
                bounds = Rect(0, 0, 1280, 720)
            }
        ContainerNode(key = "target")
            .apply {
                bounds = Rect(980, 140, 120, 30)
            }.applyParent(root)

        val portalNode = SystemInspectorPortalNode(controller)
        controller.onLayoutCommitted(root, 1L)
        controller.onCursorMoved(984, 144)
        controller.handleMouseDown(984, 144, MouseButton.LEFT)

        portalNode.bindInspectedTree(root, layoutRevision = 2L)
        portalNode.updateCursor(mouseX = 984, mouseY = 144, pointerCaptured = false)
        portalNode.render(ctx, 0, 0, 1280, 720)

        fun findFirstInput(node: DOMNode): SingleLineInputNode? {
            if (node is SingleLineInputNode) return node
            node.children.forEach { child ->
                val found = findFirstInput(child)
                if (found != null) return found
            }
            return null
        }

        val initialInput = findFirstInput(portalNode)
        assertNotNull(initialInput)
        val router = SurfaceDomInputRouter { portalNode }
        val clickX = initialInput.bounds.x + 2
        val clickY = initialInput.bounds.y + initialInput.bounds.height / 2
        assertTrue(router.handleMouseDown(clickX, clickY, MouseButton.LEFT))
        assertTrue(router.handleMouseUp(clickX, clickY, MouseButton.LEFT))

        val focusedAfterClick = FocusManager.focusedNode()
        assertNotNull(focusedAfterClick)
        val focusedKey = focusedAfterClick.key
        assertEquals(initialInput.key, focusedKey)

        portalNode.bindInspectedTree(root, layoutRevision = 3L)
        portalNode.updateCursor(mouseX = 984, mouseY = 144, pointerCaptured = false)
        portalNode.render(ctx, 0, 0, 1280, 720)

        val focusedAfterRebuild = FocusManager.focusedNode()
        assertTrue(focusedAfterRebuild is SingleLineInputNode)
        assertEquals(focusedKey, focusedAfterRebuild.key)
        assertTrue(focusedAfterRebuild !== initialInput)
    }

    @Test
    fun `system inspector portal mounts only while inspector is active`() {
        val controller = InspectorController()
        val root =
            ContainerNode(key = "root").apply {
                bounds = Rect(0, 0, 420, 280)
            }
        val portalNode = SystemInspectorPortalNode(controller)

        portalNode.bindInspectedTree(root, layoutRevision = 1L)
        portalNode.render(ctx, 0, 0, 420, 280)
        assertTrue(portalNode.children.isEmpty())

        controller.toggle()
        portalNode.render(ctx, 0, 0, 420, 280)
        assertTrue(portalNode.children.isNotEmpty())
        assertTrue(portalNode.children.none { it.styleType == "dsgl-system-raw-render-command" })

        controller.deactivate()
        portalNode.render(ctx, 0, 0, 420, 280)
        assertTrue(portalNode.children.isEmpty())
    }
}
