package org.dreamfinity.dsgl.core.overlay.input

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.onInput
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.elements.RangeInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextInputNode
import org.dreamfinity.dsgl.core.dom.elements.TextAreaNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.KeyModifiers
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.input.ClipboardAccess
import org.dreamfinity.dsgl.core.input.ClipboardBridge
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.Overflow

class LayerDomInputRouterTests {
    private val clipboard = RecordingClipboardAccess()
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) {}
    }


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
    fun `unkeyed drag capture remains active across pointer move`() {
        val (root, router) = createLayerRouter("unkeyed-drag")
        var dragEvents = 0
        val dragNode = ContainerNode().apply {
            bounds = Rect(60, 20, 90, 20)
            onMouseDrag = { dragEvents += 1 }
        }
        dragNode.applyParent(root)

        assertTrue(router.handleMouseDown(64, 28, MouseButton.LEFT))
        assertTrue(router.handleMouseMove(260, 28))
        assertTrue(router.handleMouseUp(260, 28, MouseButton.LEFT))
        assertTrue(dragEvents > 0)
    }

    @Test
    fun `range input drag updates value when pointer leaves bounds`() {
        val (root, router) = createLayerRouter("range-drag")
        val range = RangeInputNode(value = 0L, min = 0L, max = 100L, key = null)
        range.applyParent(root)
        range.render(ctx, 20, 20, 120, 12)

        assertTrue(router.handleMouseDown(20, 26, MouseButton.LEFT))
        assertTrue(router.handleMouseMove(220, 26))
        assertTrue(router.handleMouseUp(220, 26, MouseButton.LEFT))
        assertTrue(range.value > 0L)
    }


    @Test
    fun `range input drag survives unkeyed rerender replacement`() {
        val (root, router) = createLayerRouter("range-rerender")
        var model = 0L

        lateinit var mount: (Long) -> RangeInputNode
        mount = { value ->
            RangeInputNode(value = value, min = 0L, max = 100L, key = null).apply {
                render(ctx, 20, 20, 120, 12)
                onInput { event ->
                    model = (event.parsedValue as? Long) ?: model
                    root.children.clear()
                    mount(model).applyParent(root)
                }
            }
        }

        mount(model).applyParent(root)

        assertTrue(router.handleMouseDown(20, 26, MouseButton.LEFT))
        assertTrue(router.handleMouseMove(80, 26))
        assertTrue(router.handleMouseMove(220, 26))
        assertTrue(router.handleMouseUp(220, 26, MouseButton.LEFT))
        assertEquals(100L, model)
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

    @Test
    fun `wheel over non-handling interactive child bubbles to ancestor wheel handler`() {
        val (root, router) = createLayerRouter("wheel-bubble")
        var wheelEvents = 0

        val scrollHost = ContainerNode(key = "wheel-host").apply {
            bounds = Rect(10, 10, 220, 140)
            onMouseWheel = { event ->
                wheelEvents += 1
                event.cancelled = true
            }
        }
        scrollHost.applyParent(root)

        val input = TextInputNode(text = "value", key = "wheel-input").apply {
            bounds = Rect(24, 24, 150, 24)
        }
        input.applyParent(scrollHost)

        assertTrue(router.handleMouseDown(28, 28, MouseButton.LEFT))
        assertTrue(router.handleMouseWheel(28, 28, -120))
        assertEquals(1, wheelEvents)
    }
    @Test
    fun `focused textarea does not steal wheel from hovered control`() {
        val (root, router) = createLayerRouter("wheel-focused-textarea")
        var hostWheelEvents = 0

        val scrollHost = ContainerNode(key = "wheel-focused-host").apply {
            bounds = Rect(10, 10, 260, 180)
            onMouseWheel = { event ->
                hostWheelEvents += 1
                event.cancelled = true
            }
        }
        scrollHost.applyParent(root)

        val textArea = TextAreaNode(text = "first\nsecond\nthird", key = "wheel-focused-textarea").apply {
            bounds = Rect(24, 24, 160, 48)
        }
        textArea.applyParent(scrollHost)

        val input = TextInputNode(text = "value", key = "wheel-focused-input").apply {
            bounds = Rect(24, 92, 150, 24)
        }
        input.applyParent(scrollHost)

        assertTrue(router.handleMouseDown(28, 28, MouseButton.LEFT))
        assertTrue(FocusManager.isFocused(textArea))

        assertTrue(router.handleMouseWheel(30, 100, -120))
        assertEquals(1, hostWheelEvents)
    }

    @Test
    fun `wheel without cancellation is not consumed`() {
        val (root, router) = createLayerRouter("wheel-unhandled")
        val input = TextInputNode(text = "value", key = "wheel-unhandled-input").apply {
            bounds = Rect(24, 24, 150, 24)
        }
        input.applyParent(root)

        assertTrue(router.handleMouseDown(28, 28, MouseButton.LEFT))
        assertFalse(router.handleMouseWheel(28, 28, -120))
    }
    @Test
    fun `wheel axis semantics stay consistent across layers`() {
        listOf("app-dom", "app-overlay", "system-overlay").forEach { layer ->
            val (root, router) = createLayerRouter("wheel-axis-$layer")
            val viewport = ContainerNode(key = "$layer-scroll").apply {
                bounds = Rect(20, 20, 150, 70)
                overflowX = Overflow.Auto
                overflowY = Overflow.Auto
            }
            viewport.applyParent(root)
            ContainerNode(key = "$layer-scroll-content").apply {
                bounds = Rect(20, 20, 320, 220)
            }.applyParent(viewport)
            val wheelTarget = ButtonNode("wheel", key = "$layer-wheel-target").apply {
                bounds = Rect(26, 26, 64, 20)
                onClick { }
            }
            wheelTarget.applyParent(viewport)

            val wheelX = wheelTarget.bounds.x + 1
            val wheelY = wheelTarget.bounds.y + 1

            KeyModifiers.sync(shift = false, control = false, meta = false)
            assertTrue(router.handleMouseWheel(wheelX, wheelY, -120))
            viewport.advanceScrollAnimationsRecursively(1.0 / 60.0)
            viewport.consumeScrollLayoutDirtyRecursively()
            val verticalState = viewport.scrollContainerState()
            assertTrue(verticalState.scrollY > 0)
            assertEquals(0, verticalState.scrollX)

            viewport.setScrollOffsets(0, 0)
            KeyModifiers.sync(shift = true, control = false, meta = false)
            assertTrue(router.handleMouseWheel(wheelX, wheelY, -120))
            viewport.advanceScrollAnimationsRecursively(1.0 / 60.0)
            viewport.consumeScrollLayoutDirtyRecursively()
            val horizontalState = viewport.scrollContainerState()
            assertTrue(horizontalState.scrollX > 0)
            assertEquals(0, horizontalState.scrollY)
        }
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

