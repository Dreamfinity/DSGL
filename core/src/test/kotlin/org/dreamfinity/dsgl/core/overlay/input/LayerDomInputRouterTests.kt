package org.dreamfinity.dsgl.core.overlay.input

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.input.ClipboardAccess
import org.dreamfinity.dsgl.core.input.ClipboardBridge

class LayerDomInputRouterTests {
    private val clipboard = RecordingClipboardAccess()

    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
        KeyModifiers.sync(shift = false, control = false, meta = false)
        ClipboardBridge.install(null)
    }

    @Test
    fun `text input editing semantics stay consistent across all layers`() {
        ClipboardBridge.install(clipboard)
        listOf("app-dom", "app-overlay", "system-overlay").forEach { layer ->
            clipboard.value = ""
            val (root, router) = createLayerRouter(layer)
            val input = TextInputNode(text = "abcdef", key = "$layer-input").apply {
                bounds = Rect(20, 20, 180, 24)
            }
            input.applyParent(root)

            assertTrue(router.handleMouseDown(24, 24, MouseButton.LEFT))
            assertTrue(FocusManager.isFocused(input))

            KeyModifiers.sync(shift = false, control = true, meta = false)
            assertTrue(router.handleKeyDown(KeyCodes.A, 'a'))
            KeyModifiers.sync(shift = false, control = false, meta = false)
            assertTrue(router.handleKeyDown(KeyCodes.BACKSPACE, 0.toChar()))
            assertEquals("", input.text)

            assertTrue(router.handleKeyDown(KeyCodes.X, 'x'))
            assertTrue(router.handleKeyDown(KeyCodes.Z, 'y'))
            assertEquals("xy", input.text)

            KeyModifiers.sync(shift = true, control = false, meta = false)
            assertTrue(router.handleKeyDown(KeyCodes.LEFT, 0.toChar()))
            KeyModifiers.sync(shift = false, control = true, meta = false)
            assertTrue(router.handleKeyDown(KeyCodes.C, 'c'))
            assertEquals("y", clipboard.value)

            KeyModifiers.sync(shift = false, control = true, meta = false)
            assertTrue(router.handleKeyDown(KeyCodes.A, 'a'))
            assertTrue(router.handleKeyDown(KeyCodes.X, 'x'))
            assertEquals("", input.text)
            assertTrue(router.handleKeyDown(KeyCodes.V, 'v'))
            assertEquals("xy", input.text)

            input.text = "layer"
            assertTrue(router.handleMouseDown(22, 24, MouseButton.LEFT))
            assertTrue(router.handleMouseMove(70, 24))
            assertTrue(router.handleMouseUp(70, 24, MouseButton.LEFT))
            KeyModifiers.sync(shift = false, control = true, meta = false)
            assertTrue(router.handleKeyDown(KeyCodes.C, 'c'))
            assertTrue(clipboard.value.isNotEmpty())
        }
    }

    @Test
    fun `dropdown-like topmost option wins and blocks click-through across layers`() {
        listOf("app-dom", "app-overlay", "system-overlay").forEach { layer ->
            val (root, router) = createLayerRouter(layer)
            var underClicks = 0
            var topClicks = 0

            val under = ButtonNode("under", key = "$layer-under").apply {
                bounds = Rect(40, 40, 120, 24)
                onClick { underClicks += 1 }
            }
            under.applyParent(root)

            val top = ButtonNode("top", key = "$layer-top").apply {
                bounds = Rect(40, 40, 120, 24)
                onClick { topClicks += 1 }
            }
            top.applyParent(root)

            assertTrue(router.handleMouseMove(50, 48))
            assertTrue(router.handleMouseDown(50, 48, MouseButton.LEFT))
            assertTrue(router.handleMouseUp(50, 48, MouseButton.LEFT))

            assertEquals(1, topClicks)
            assertEquals(0, underClicks)
        }
    }

    @Test
    fun `keyboard dispatch follows focused node root membership`() {
        val (rootA, routerA) = createLayerRouter("layer-a")
        val (rootB, routerB) = createLayerRouter("layer-b")

        val inputA = TextInputNode(text = "a", key = "a-input").apply {
            bounds = Rect(10, 10, 100, 20)
        }
        inputA.applyParent(rootA)
        val inputB = TextInputNode(text = "b", key = "b-input").apply {
            bounds = Rect(10, 10, 100, 20)
        }
        inputB.applyParent(rootB)

        FocusManager.requestFocus(inputA)
        assertTrue(routerA.handleKeyDown(KeyCodes.Z, 'z'))
        assertFalse(routerB.handleKeyDown(KeyCodes.X, 'x'))
        assertEquals("az", inputA.text)
        assertEquals("b", inputB.text)
    }

    @Test
    fun `pointer drag capture is generic for header and thumb style controls`() {
        listOf("header-drag", "thumb-drag").forEach { key ->
            val (root, router) = createLayerRouter(key)
            var dragEvents = 0
            val dragNode = ContainerNode(key = "$key-node").apply {
                bounds = Rect(60, 20, 90, 20)
                onMouseDrag = { dragEvents += 1 }
            }
            dragNode.applyParent(root)

            assertTrue(router.handleMouseDown(64, 28, MouseButton.LEFT))
            assertTrue(router.handleMouseMove(240, 28))
            assertTrue(router.handleMouseUp(240, 28, MouseButton.LEFT))
            assertTrue(dragEvents > 0)
        }
    }


    @Test
    fun `release after drag does not synthesize click on hovered target`() {
        val (root, router) = createLayerRouter("drag-release")
        var buttonClicks = 0

        val dragSurface = ContainerNode(key = "drag-surface").apply {
            bounds = Rect(20, 20, 80, 24)
            onMouseMove = {}
        }
        dragSurface.applyParent(root)

        val releaseButton = ButtonNode("release", key = "release-button").apply {
            bounds = Rect(120, 20, 100, 24)
            onClick { buttonClicks += 1 }
        }
        releaseButton.applyParent(root)

        assertTrue(router.handleMouseDown(24, 24, MouseButton.LEFT))
        assertTrue(router.handleMouseMove(130, 24))
        assertTrue(router.handleMouseUp(130, 24, MouseButton.LEFT))
        assertEquals(0, buttonClicks)
    }
    @Test
    fun `mouse up stays consumed after press when pointer is released outside targets`() {
        val (root, router) = createLayerRouter("outside-release")
        var buttonClicks = 0

        val button = ButtonNode("press", key = "outside-release-button").apply {
            bounds = Rect(20, 20, 100, 24)
            onClick { buttonClicks += 1 }
        }
        button.applyParent(root)

        assertTrue(router.handleMouseDown(24, 24, MouseButton.LEFT))
        router.handleMouseMove(480, 320)
        assertTrue(router.handleMouseUp(480, 320, MouseButton.LEFT))
        assertEquals(0, buttonClicks)
    }
    private fun createLayerRouter(key: String): Pair<ContainerNode, LayerDomInputRouter> {
        val root = ContainerNode(key = "$key-root").apply {
            bounds = Rect(0, 0, 320, 200)
        }
        return root to LayerDomInputRouter { root }
    }

    private class RecordingClipboardAccess : ClipboardAccess {
        var value: String = ""

        override fun readText(): String = value

        override fun writeText(value: String) {
            this.value = value
        }
    }
}





