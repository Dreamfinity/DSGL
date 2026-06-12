package org.dreamfinity.dsgl.core.debug

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.PortalEntry
import org.dreamfinity.dsgl.core.portal.PortalEntryBounds
import org.dreamfinity.dsgl.core.portal.PortalEntryId
import org.dreamfinity.dsgl.core.portal.PortalEntryOrder
import org.dreamfinity.dsgl.core.portal.PortalEntryPlacement
import org.dreamfinity.dsgl.core.portal.PortalEntryState
import org.dreamfinity.dsgl.core.portal.PortalFrameContext
import org.dreamfinity.dsgl.core.portal.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleApplicationScope
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DebugDomainHostsTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        DomainSurfaceDebugState.resetAll()
        DomainSurfaceDebugState.setControlsEnabledTestOverride(null)
    }

    @Test
    fun `debug panel remains available when app and system portal surfaces are disabled`() {
        DomainSurfaceDebugState.setControlsEnabledTestOverride(true)
        DomainSurfaceDebugState.applicationPortalRenderEnabled = false
        DomainSurfaceDebugState.applicationPortalInputEnabled = false
        DomainSurfaceDebugState.systemPortalRenderEnabled = false
        DomainSurfaceDebugState.systemPortalInputEnabled = false
        val host = DebugDomainRootHost()

        host.render(ctx, 960, 540)
        val layout = host.debugLayout()
        val commands = host.paint(ctx)

        assertNotNull(layout)
        assertTrue(commands.any { it is RenderCommand.DrawText && it.text == "Debug Domain" })
        assertTrue(host.handleMouseDown(layout.panelRect.x + 3, layout.panelRect.y + 3, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(layout.panelRect.x + 3, layout.panelRect.y + 3, MouseButton.LEFT))
    }

    @Test
    fun `debug control host uses explicit debug style scope`() {
        val host = DebugDomainRootHost()

        assertEquals(StyleApplicationScope.Debug, host.debugStyleScope)
    }

    @Test
    fun `reset all restores domain-surface render and input toggles`() {
        DomainSurfaceDebugState.setControlsEnabledTestOverride(true)
        DomainSurfaceDebugState.applicationPortalRenderEnabled = false
        DomainSurfaceDebugState.applicationPortalInputEnabled = false
        DomainSurfaceDebugState.systemPortalRenderEnabled = false
        DomainSurfaceDebugState.systemPortalInputEnabled = false
        val host = DebugDomainRootHost()

        host.render(ctx, 960, 540)
        val layout = host.debugLayout() ?: error("layout missing")
        host.paint(ctx)
        assertTrue(host.handleMouseDown(layout.resetRect.x + 2, layout.resetRect.y + 2, MouseButton.LEFT))

        assertTrue(DomainSurfaceDebugState.applicationPortalRenderEnabled)
        assertTrue(DomainSurfaceDebugState.applicationPortalInputEnabled)
        assertTrue(DomainSurfaceDebugState.systemPortalRenderEnabled)
        assertTrue(DomainSurfaceDebugState.systemPortalInputEnabled)
    }

    @Test
    fun `debug panel toggles mutate independent app and system portal state`() {
        DomainSurfaceDebugState.setControlsEnabledTestOverride(true)
        DomainSurfaceDebugState.resetAll()
        val host = DebugDomainRootHost()

        host.render(ctx, 960, 540)
        val layout = host.debugLayout() ?: error("layout missing")
        host.paint(ctx)

        assertTrue(
            host.handleMouseDown(
                layout.appPortalRenderRect.x + 2,
                layout.appPortalRenderRect.y + 2,
                MouseButton.LEFT,
            ),
        )
        assertFalse(DomainSurfaceDebugState.applicationPortalRenderEnabled)
        assertTrue(DomainSurfaceDebugState.applicationPortalInputEnabled)
        assertTrue(DomainSurfaceDebugState.systemPortalRenderEnabled)
        assertTrue(DomainSurfaceDebugState.systemPortalInputEnabled)

        assertTrue(
            host.handleMouseDown(
                layout.systemPortalInputRect.x + 2,
                layout.systemPortalInputRect.y + 2,
                MouseButton.LEFT,
            ),
        )
        assertFalse(DomainSurfaceDebugState.systemPortalInputEnabled)
        assertFalse(DomainSurfaceDebugState.applicationPortalRenderEnabled)
    }

    @Test
    fun `debug panel status shows fps and frame time`() {
        DomainSurfaceDebugState.setControlsEnabledTestOverride(true)
        DomainSurfaceDebugState.updateFrameTiming(0.025)
        val host = DebugDomainRootHost()

        host.render(ctx, 960, 540)
        host.paint(ctx)
        val commands = host.paint(ctx)
        val drawTexts =
            commands
                .filterIsInstance<RenderCommand.DrawText>()
        val statusTexts =
            drawTexts
                .filter { it.sourceKey == "dsgl-debug-domain-status" }
                .map { it.text }
        val statusTextValue =
            assertNotNull(
                statusTexts.lastOrNull { it.isNotBlank() } ?: statusTexts.lastOrNull(),
                "draw texts: ${drawTexts.joinToString { "${it.sourceKey}:${it.text}" }}",
            )
        val expectedFps = DomainSurfaceDebugState.frameFps
        val expectedFrameMs = String.format(Locale.US, "%.1f", DomainSurfaceDebugState.frameTimeMs)
        val expectedWindowFps = DomainSurfaceDebugState.frameFpsWindow
        val expectedWindowFrameMs = String.format(Locale.US, "%.1f", DomainSurfaceDebugState.frameTimeWindowMs)
        assertTrue(statusTextValue.contains("FPS:$expectedFps"), "statusText='$statusTextValue'")
        assertTrue(statusTextValue.contains("(${expectedFrameMs}ms)"), "statusText='$statusTextValue'")
        assertTrue(statusTextValue.contains("AvgFPS:$expectedWindowFps"), "statusText='$statusTextValue'")
        assertTrue(statusTextValue.contains("(${expectedWindowFrameMs}ms)"), "statusText='$statusTextValue'")
    }

    @Test
    fun `toggle button label updates immediately after state change`() {
        DomainSurfaceDebugState.setControlsEnabledTestOverride(true)
        DomainSurfaceDebugState.resetAll()
        val host = DebugDomainRootHost()

        host.render(ctx, 960, 540)
        val layout = host.debugLayout() ?: error("layout missing")
        val initialText =
            host
                .paint(ctx)
                .filterIsInstance<RenderCommand.DrawText>()
                .lastOrNull { it.sourceKey == "dsgl-debug-domain-toggle-app-render" }
                ?.text
        assertEquals("ON", initialText)

        assertTrue(
            host.handleMouseDown(
                layout.appPortalRenderRect.x + 2,
                layout.appPortalRenderRect.y + 2,
                MouseButton.LEFT,
            ),
        )

        host.render(ctx, 960, 540)
        val updatedText =
            host
                .paint(ctx)
                .filterIsInstance<RenderCommand.DrawText>()
                .lastOrNull { it.sourceKey == "dsgl-debug-domain-toggle-app-render" }
                ?.text
        assertEquals("OFF", updatedText)
    }

    @Test
    fun `sliding window fps smooths immediate fps`() {
        DomainSurfaceDebugState.updateFrameTiming(0.010)
        DomainSurfaceDebugState.updateFrameTiming(0.030)
        val snapshot = DomainSurfaceDebugState.snapshot()

        assertEquals(33, snapshot.frameFps)
        assertEquals(30.0f, snapshot.frameTimeMs)
        assertEquals(50, snapshot.frameFpsWindow)
        assertEquals(20.0f, snapshot.frameTimeWindowMs)
    }

    @Test
    fun `controls visibility obeys debug-only toggle`() {
        DomainSurfaceDebugState.setControlsEnabledTestOverride(false)
        val host = DebugDomainRootHost()
        host.render(ctx, 960, 540)
        assertTrue(host.paint(ctx).isEmpty())

        DomainSurfaceDebugState.setControlsEnabledTestOverride(true)
        host.render(ctx, 960, 540)
        assertTrue(host.paint(ctx).isNotEmpty())
    }

    @Test
    fun `debug portal host starts empty but participates as debug portal surface`() {
        val host = DebugDomainPortalHost()

        host.onInputFrame(960, 540)
        host.render(ctx, 960, 540)

        assertEquals(ScreenDomainSurfaces.DebugPortal, host.surface)
        assertTrue(host.paint(ctx).isEmpty())
        assertFalse(host.handleMouseDown(12, 12, MouseButton.LEFT))
        assertTrue(host.debugActivePortalEntryIds.isEmpty())
    }

    @Test
    fun `debug portal host dispatches registered portal entries`() {
        val host = DebugDomainPortalHost()
        val entry = FakeDebugPortalEntry()
        host.debugRegisterPortalEntryForTests(entry)
        entry.activate()

        host.onInputFrame(960, 540)
        host.render(ctx, 960, 540)
        val consumed = host.handleMouseDown(14, 14, MouseButton.LEFT)
        val commands = host.paint(ctx)

        assertTrue(consumed)
        assertEquals(1, entry.inputFrameCalls)
        assertEquals(1, entry.renderCalls)
        assertEquals(1, entry.mouseDownCalls)
        assertEquals(listOf("debug.test"), host.debugActivePortalEntryIds)
        assertTrue(commands.any { it is RenderCommand.DrawRect && it.color == 0xFF336699.toInt() })
    }

    @Test
    fun `debug domain surfaces remain enabled in state even when app and system portals are disabled`() {
        DomainSurfaceDebugState.applicationPortalTintEnabled = false
        DomainSurfaceDebugState.applicationPortalRenderEnabled = false
        DomainSurfaceDebugState.applicationPortalInputEnabled = false
        DomainSurfaceDebugState.systemPortalRenderEnabled = false
        DomainSurfaceDebugState.systemPortalTintEnabled = false
        DomainSurfaceDebugState.systemPortalInputEnabled = false

        assertTrue(DomainSurfaceDebugState.isRenderEnabled(ScreenDomainSurfaces.DebugRoot))
        assertTrue(DomainSurfaceDebugState.isInputEnabled(ScreenDomainSurfaces.DebugRoot))
        assertTrue(DomainSurfaceDebugState.isRenderEnabled(ScreenDomainSurfaces.DebugPortal))
        assertTrue(DomainSurfaceDebugState.isInputEnabled(ScreenDomainSurfaces.DebugPortal))
        assertEquals(
            DomainSurfaceDebugSnapshot(
                applicationPortalRenderEnabled = false,
                applicationPortalTintEnabled = false,
                applicationPortalInputEnabled = false,
                systemPortalRenderEnabled = false,
                systemPortalTintEnabled = false,
                systemPortalInputEnabled = false,
                frameFps = 0,
                frameTimeMs = 0f,
                frameFpsWindow = 0,
                frameTimeWindowMs = 0f,
            ),
            DomainSurfaceDebugState.snapshot(),
        )
    }

    private class FakeDebugPortalEntry : PortalEntry {
        override val state: PortalEntryState =
            PortalEntryState(
                id = PortalEntryId("debug.test"),
                ownerToken = this,
                surface = ScreenDomainSurfaces.DebugPortal,
                order = PortalEntryOrder(zIndex = 0),
            )
        override val node = null
        var inputFrameCalls: Int = 0
            private set
        var renderCalls: Int = 0
            private set
        var mouseDownCalls: Int = 0
            private set

        fun activate() {
            state.activate(
                PortalEntryPlacement(
                    anchorBounds = Rect(10, 10, 20, 20),
                    bounds =
                        PortalEntryBounds(
                            viewportBounds = Rect(0, 0, 960, 540),
                            entryBounds = Rect(12, 12, 100, 80),
                        ),
                ),
            )
        }

        override fun onInputFrame(context: PortalFrameContext) {
            inputFrameCalls += 1
        }

        override fun render(ctx: UiMeasureContext, width: Int, height: Int) {
            renderCalls += 1
        }

        override fun paint(ctx: UiMeasureContext): List<RenderCommand> = listOf(RenderCommand.DrawRect(0, 0, 1, 1, 0xFF336699.toInt()))

        override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
            mouseDownCalls += 1
            return true
        }
    }
}
