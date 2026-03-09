package org.dreamfinity.dsgl.core.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.colorpicker.ColorFormatMode
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerRuntime
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.colorpicker.internal.SystemColorPickerOverlayNode
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
    fun `system color picker overlay creates dom children from runtime frame`() {
        val overlay = SystemColorPickerOverlayNode()
        try {
            ColorPickerRuntime.engine.open(
                ColorPickerPopupRequest(
                    owner = "test-owner",
                    anchorRect = Rect(20, 24, 24, 18),
                    title = "Test Picker",
                    state = ColorPickerState(
                        color = RgbaColor(0.4f, 0.7f, 0.2f, 1f),
                        previous = RgbaColor(0.4f, 0.7f, 0.2f, 1f),
                        mode = ColorFormatMode.HEX,
                        alphaEnabled = true,
                        closeOnSelect = false
                    )
                )
            )

            overlay.updateCursor(28, 32)
            overlay.render(ctx, 0, 0, 500, 360)
            assertTrue(overlay.children.isNotEmpty())
            assertTrue(overlay.children.all { it.styleType == "dsgl-system-raw-render-command" })
        } finally {
            ColorPickerRuntime.engine.closeAll()
        }
    }

    @Test
    fun `system color picker overlay mounts closes and remounts with popup lifecycle`() {
        val overlay = SystemColorPickerOverlayNode()
        val request = ColorPickerPopupRequest(
            owner = "test-owner",
            anchorRect = Rect(20, 24, 24, 18),
            title = "Test Picker",
            state = ColorPickerState(
                color = RgbaColor(0.4f, 0.7f, 0.2f, 1f),
                previous = RgbaColor(0.4f, 0.7f, 0.2f, 1f),
                mode = ColorFormatMode.HEX,
                alphaEnabled = true,
                closeOnSelect = false
            )
        )
        try {
            ColorPickerRuntime.engine.open(request)
            overlay.updateCursor(28, 32)
            overlay.render(ctx, 0, 0, 500, 360)
            assertTrue(overlay.children.isNotEmpty())

            ColorPickerRuntime.engine.close("test-owner")
            overlay.render(ctx, 0, 0, 500, 360)
            assertTrue(overlay.children.isEmpty())

            ColorPickerRuntime.engine.open(request)
            overlay.render(ctx, 0, 0, 500, 360)
            assertTrue(overlay.children.isNotEmpty())
        } finally {
            ColorPickerRuntime.engine.closeAll()
        }
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

    @Test
    fun `system overlay raw command nodes do not accumulate across popup open close`() {
        val overlay = SystemColorPickerOverlayNode()
        SystemOverlayDebugCounters.setEnabled(true)
        SystemOverlayDebugCounters.reset()
        try {
            repeat(6) { iteration ->
                ColorPickerRuntime.engine.open(
                    ColorPickerPopupRequest(
                        owner = "owner-$iteration",
                        anchorRect = Rect(32, 28, 20, 18),
                        title = "Picker $iteration",
                        state = ColorPickerState(
                            color = RgbaColor(0.2f, 0.3f, 0.6f, 0.9f),
                            previous = RgbaColor(0.2f, 0.3f, 0.6f, 0.9f),
                            mode = ColorFormatMode.RGB,
                            alphaEnabled = true,
                            closeOnSelect = false
                        )
                    )
                )
                overlay.updateCursor(40 + iteration, 42 + iteration)
                overlay.render(ctx, 0, 0, 640, 360)
                ColorPickerRuntime.engine.close("owner-$iteration")
                overlay.render(ctx, 0, 0, 640, 360)
            }
            val stats = SystemOverlayDebugCounters.snapshot()
            assertEquals(0L, stats.rawNodeActive)
            assertTrue(stats.rawNodeCreated > 0L)
            assertEquals(stats.rawNodeCreated, stats.rawNodeRemoved)
        } finally {
            ColorPickerRuntime.engine.closeAll()
            SystemOverlayDebugCounters.setEnabled(false)
            SystemOverlayDebugCounters.reset()
        }
    }
}
