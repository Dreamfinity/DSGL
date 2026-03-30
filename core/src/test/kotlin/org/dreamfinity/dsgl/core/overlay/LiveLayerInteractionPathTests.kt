package org.dreamfinity.dsgl.core.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayEntryId
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayHost
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayPanelDemoNode
import org.dreamfinity.dsgl.core.render.RenderCommand

class LiveLayerInteractionPathTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @Test
    fun `runtime layer path resolves in debug system app-overlay app-root order`() {
        val callOrder = ArrayList<UiLayerId>(4)
        val harness = LiveLayerInputHarness(
            debugHandler = { _, _, _ ->
                callOrder += UiLayerId.Debug
                false
            },
            systemOverlayHandler = { _, _, _ ->
                callOrder += UiLayerId.SystemOverlay
                false
            },
            applicationOverlayHandler = { _, _, _ ->
                callOrder += UiLayerId.ApplicationOverlay
                false
            }
        )
        val consumedBy = harness.dispatchMouseDown(10, 10, MouseButton.LEFT) {
            callOrder += UiLayerId.ApplicationRoot
            true
        }

        assertEquals(UiLayerId.ApplicationRoot, consumedBy)
        assertEquals(
            listOf(UiLayerId.Debug, UiLayerId.SystemOverlay, UiLayerId.ApplicationOverlay, UiLayerId.ApplicationRoot),
            callOrder
        )
    }

    @Test
    fun `debug layer consumption prevents lower-layer fallthrough`() {
        var systemReceived = false
        var appOverlayReceived = false
        var appRootReceived = false
        val harness = LiveLayerInputHarness(
            debugHandler = { _, _, _ -> true },
            systemOverlayHandler = { _, _, _ ->
                systemReceived = true
                false
            },
            applicationOverlayHandler = { _, _, _ ->
                appOverlayReceived = true
                false
            }
        )
        val consumedBy = harness.dispatchMouseDown(12, 14, MouseButton.LEFT) {
            appRootReceived = true
            true
        }

        assertEquals(UiLayerId.Debug, consumedBy)
        assertFalse(systemReceived)
        assertFalse(appOverlayReceived)
        assertFalse(appRootReceived)
    }

    @Test
    fun `system overlay consumption prevents lower-layer fallthrough`() {
        val systemHost = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()
        systemHost.onInputFrame(1280, 720)
        systemHost.togglePanelDemo(anchorX = 240, anchorY = 180)
        systemHost.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 244, cursorY = 186, inspectorPointerCaptured = false)

        val entryState = systemHost.debugEntryState(SystemOverlayEntryId.PanelDemo) ?: error("panel demo state missing")
        val panelRect = entryState.panelState.currentRectOrNull() ?: error("panel demo rect missing")
        val harness = LiveLayerInputHarness(
            debugHandler = { _, _, _ -> false },
            systemOverlayHandler = { x, y, button -> systemHost.handleMouseDown(x, y, button) },
            applicationOverlayHandler = { _, _, _ -> false }
        )
        var appRootReceived = false
        val consumedBy = harness.dispatchMouseDown(panelRect.x + 20, panelRect.y + 70, MouseButton.LEFT) {
            appRootReceived = true
            true
        }

        assertEquals(UiLayerId.SystemOverlay, consumedBy)
        assertFalse(appRootReceived)
    }

    @Test
    fun `locked inspector consumes only inside panel and falls through outside panel`() {
        val inspector = InspectorController()
        val systemHost = SystemOverlayHost(inspector)
        val root = inspectedRoot()
        systemHost.onInputFrame(1280, 720)
        inspector.toggle()
        inspector.setPickMode(false)
        systemHost.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 984, cursorY = 144, inspectorPointerCaptured = false)
        systemHost.render(ctx, 1280, 720)

        val panelRect = inspector.overlayPanelRect() ?: error("inspector panel rect missing")
        val outsideX = if (panelRect.x > 40) panelRect.x - 20 else panelRect.x + panelRect.width + 20
        val outsideY = (panelRect.y + panelRect.height / 2).coerceIn(1, 719)
        assertFalse(panelRect.contains(outsideX, outsideY))

        val harness = LiveLayerInputHarness(
            debugHandler = { _, _, _ -> false },
            systemOverlayHandler = { x, y, button -> systemHost.handleMouseDown(x, y, button) },
            applicationOverlayHandler = { _, _, _ -> false }
        )

        var appRootReceivedOutside = false
        val consumedOutside = harness.dispatchMouseDown(outsideX, outsideY, MouseButton.LEFT) {
            appRootReceivedOutside = true
            true
        }
        assertEquals(UiLayerId.ApplicationRoot, consumedOutside)
        assertTrue(appRootReceivedOutside)
    }
    @Test
    fun `application overlay consumption prevents app-root fallthrough`() {
        val harness = LiveLayerInputHarness(
            debugHandler = { _, _, _ -> false },
            systemOverlayHandler = { _, _, _ -> false },
            applicationOverlayHandler = { _, _, _ -> true }
        )
        var appRootReceived = false
        val consumedBy = harness.dispatchMouseDown(24, 30, MouseButton.LEFT) {
            appRootReceived = true
            true
        }

        assertEquals(UiLayerId.ApplicationOverlay, consumedBy)
        assertFalse(appRootReceived)
    }

    @Test
    fun `rendered system overlay content is reachable through same live interaction path`() {
        val systemHost = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()
        systemHost.onInputFrame(1280, 720)
        systemHost.togglePanelDemo(anchorX = 260, anchorY = 200)
        systemHost.syncFrame(root, inspectedLayoutRevision = 1L, cursorX = 260, cursorY = 200, inspectorPointerCaptured = false)
        systemHost.render(ctx, 1280, 720)

        val demoNode = systemHost.debugEntryNode(SystemOverlayEntryId.PanelDemo) as? SystemOverlayPanelDemoNode
            ?: error("panel demo node missing")
        val buttonRect = demoNode.buttonRect()
        assertNotNull(buttonRect)
        val harness = LiveLayerInputHarness(
            debugHandler = { _, _, _ -> false },
            systemOverlayHandler = { x, y, button -> systemHost.handleMouseDown(x, y, button) },
            applicationOverlayHandler = { _, _, _ -> false }
        )
        var appRootReceived = false
        val consumedBy = harness.dispatchMouseDown(buttonRect.x + 1, buttonRect.y + 1, MouseButton.LEFT) {
            appRootReceived = true
            true
        }

        assertEquals(UiLayerId.SystemOverlay, consumedBy)
        assertFalse(appRootReceived)
    }

    private fun inspectedRoot(): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1280, 720)
        ContainerNode(key = "child").apply {
            bounds = Rect(20, 20, 120, 32)
        }.applyParent(root)
        return root
    }

    private class LiveLayerInputHarness(
        private val debugHandler: (Int, Int, MouseButton) -> Boolean,
        private val systemOverlayHandler: (Int, Int, MouseButton) -> Boolean,
        private val applicationOverlayHandler: (Int, Int, MouseButton) -> Boolean
    ) {
        fun dispatchMouseDown(
            mouseX: Int,
            mouseY: Int,
            button: MouseButton,
            applicationRootHandler: () -> Boolean
        ): UiLayerId? {
            return OverlayLayerContracts.firstInputConsumer(
                canConsume = { layer ->
                    when (layer) {
                        UiLayerId.Debug -> debugHandler(mouseX, mouseY, button)
                        UiLayerId.SystemOverlay -> systemOverlayHandler(mouseX, mouseY, button)
                        UiLayerId.ApplicationOverlay -> applicationOverlayHandler(mouseX, mouseY, button)
                        UiLayerId.ApplicationRoot -> applicationRootHandler()
                    }
                }
            )
        }
    }
}

