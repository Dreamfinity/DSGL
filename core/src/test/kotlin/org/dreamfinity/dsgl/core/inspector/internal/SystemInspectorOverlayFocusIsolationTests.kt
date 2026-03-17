package org.dreamfinity.dsgl.core.inspector.internal

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.input.ClipboardAccess
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.inspector.InspectorMode
import org.dreamfinity.dsgl.core.overlay.input.LayerDomInputRouter
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayHost
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine
import org.dreamfinity.dsgl.core.style.StyleProperty

class SystemInspectorOverlayFocusIsolationTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    private val clipboard = RecordingClipboardAccess()

    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
        KeyModifiers.sync(shift = false, control = false, meta = false)
        ClipboardBridge.install(null)
        StyleEngine.clearAllInspectorOverrides()
        StyleEngine.clearCache()
    }

    @Test
    fun `text input keeps focus while inspector is mounted and rendered`() {
        val appRoot = ContainerNode(key = "app-root").apply {
            bounds = Rect(0, 0, 1280, 720)
        }
        val input = TextInputNode(text = "value", key = "app-input").apply {
            bounds = Rect(24, 24, 180, 24)
        }
        input.applyParent(appRoot)
        val router = LayerDomInputRouter { appRoot }

        val (inspector, overlayNode, inspectedRoot) = createMountedInspector()
        renderInspectorFrame(overlayNode, inspectedRoot, 1L)

        assertTrue(router.handleMouseDown(30, 30, MouseButton.LEFT))
        assertTrue(router.handleMouseUp(30, 30, MouseButton.LEFT))
        assertTrue(FocusManager.isFocused(input))

        repeat(4) { frame ->
            renderInspectorFrame(overlayNode, inspectedRoot, 2L + frame)
            assertTrue(FocusManager.isFocused(input))
        }

        assertTrue(router.handleKeyDown(KeyCodes.Z, 'z'))
        assertEquals("vzalue", input.text)
        assertTrue(inspector.active)
    }

    @Test
    fun `text selection drag and keyboard edit work while inspector is mounted`() {
        ClipboardBridge.install(clipboard)

        val appRoot = ContainerNode(key = "app-root").apply {
            bounds = Rect(0, 0, 1280, 720)
        }
        val input = TextInputNode(text = "abcdef", key = "app-input").apply {
            bounds = Rect(24, 24, 180, 24)
        }
        input.applyParent(appRoot)
        val router = LayerDomInputRouter { appRoot }

        val (_, overlayNode, inspectedRoot) = createMountedInspector()
        renderInspectorFrame(overlayNode, inspectedRoot, 1L)

        assertTrue(router.handleMouseDown(28, 30, MouseButton.LEFT))
        assertTrue(router.handleMouseMove(70, 30))
        renderInspectorFrame(overlayNode, inspectedRoot, 2L)
        assertTrue(router.handleMouseMove(88, 30))
        assertTrue(router.handleMouseUp(88, 30, MouseButton.LEFT))

        KeyModifiers.sync(shift = false, control = true, meta = false)
        assertTrue(router.handleKeyDown(KeyCodes.C, 'c'))
        assertTrue(clipboard.value.isNotEmpty())
        KeyModifiers.sync(shift = false, control = false, meta = false)

        assertTrue(router.handleKeyDown(KeyCodes.BACKSPACE, 0.toChar()))
        assertNotEquals("abcdef", input.text)
    }

    @Test
    fun `range drag works while inspector is mounted`() {
        val appRoot = ContainerNode(key = "app-root").apply {
            bounds = Rect(0, 0, 1280, 720)
        }
        val range = RangeInputNode(value = 0L, min = 0L, max = 100L, key = "app-range")
        range.applyParent(appRoot)
        range.render(ctx, 24, 24, 120, 12)
        val router = LayerDomInputRouter { appRoot }

        val (_, overlayNode, inspectedRoot) = createMountedInspector()
        renderInspectorFrame(overlayNode, inspectedRoot, 1L)

        assertTrue(router.handleMouseDown(24, 30, MouseButton.LEFT))
        renderInspectorFrame(overlayNode, inspectedRoot, 2L)
        assertTrue(router.handleMouseMove(200, 30))
        renderInspectorFrame(overlayNode, inspectedRoot, 3L)
        assertTrue(router.handleMouseUp(200, 30, MouseButton.LEFT))
        assertTrue(range.value > 0L)
    }

    @Test
    fun `focused app control keeps keyboard routing with inspector mounted`() {
        val appRoot = ContainerNode(key = "app-root").apply {
            bounds = Rect(0, 0, 1280, 720)
        }
        val input = TextInputNode(text = "x", key = "app-input").apply {
            bounds = Rect(24, 24, 180, 24)
        }
        input.applyParent(appRoot)
        val router = LayerDomInputRouter { appRoot }

        val (_, overlayNode, inspectedRoot) = createMountedInspector()
        renderInspectorFrame(overlayNode, inspectedRoot, 1L)

        assertTrue(router.handleMouseDown(30, 30, MouseButton.LEFT))
        assertTrue(router.handleMouseUp(30, 30, MouseButton.LEFT))
        assertTrue(FocusManager.isFocused(input))

        repeat(3) { frame ->
            renderInspectorFrame(overlayNode, inspectedRoot, 2L + frame)
            assertTrue(router.handleKeyDown(KeyCodes.Z, 'z'))
        }

        assertEquals("xzzz", input.text)
    }

    @Test
    fun `inspector still handles its own input interactions after focus isolation fix`() {
        val inspector = InspectorController()
        val host = SystemOverlayHost(inspector)
        val root = inspectedRoot()

        inspector.toggle()
        inspector.setPickMode(false)
        host.onInputFrame(1280, 720)
        host.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 40, cursorY = 30, inspectorPointerCaptured = false)
        host.render(ctx, 1280, 720)

        val pickRect = inspector.debugPickToggleBounds() ?: error("pick toggle missing")
        assertTrue(host.handleMouseDown(pickRect.x + 2, pickRect.y + 2, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(pickRect.x + 2, pickRect.y + 2, MouseButton.LEFT))
        assertEquals(InspectorMode.Pick, inspector.mode)
    }

    private fun createMountedInspector(): Triple<InspectorController, SystemInspectorOverlayNode, DOMNode> {
        val inspector = InspectorController()
        inspector.toggle()
        inspector.setPickMode(false)
        val overlayNode = SystemInspectorOverlayNode(inspector)
        val inspectedRoot = inspectedRoot()
        return Triple(inspector, overlayNode, inspectedRoot)
    }

    private fun renderInspectorFrame(node: SystemInspectorOverlayNode, inspectedRoot: DOMNode, revision: Long) {
        node.bindInspectedTree(inspectedRoot, revision)
        node.updateCursor(mouseX = 32, mouseY = 32, pointerCaptured = false)
        node.render(ctx, 0, 0, 1280, 720)
    }

    private fun inspectedRoot(): ContainerNode {
        val root = ContainerNode(key = "root").apply {
            bounds = Rect(0, 0, 1280, 720)
        }
        val target = ContainerNode(key = "target").apply {
            bounds = Rect(980, 140, 120, 30)
        }
        target.applyParent(root)
        StyleEngine.setInspectorOverrideLiteral(target, StyleProperty.BACKGROUND_COLOR, "#FF112233").getOrThrow()
        return root
    }

    private class RecordingClipboardAccess : ClipboardAccess {
        var value: String = ""
        override fun readText(): String = value
        override fun writeText(value: String) {
            this.value = value
        }
    }
}
