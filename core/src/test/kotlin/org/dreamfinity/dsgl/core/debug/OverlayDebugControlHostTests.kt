package org.dreamfinity.dsgl.core.debug

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.overlay.UiLayerId
import java.util.Locale

class OverlayDebugControlHostTests {
    private val ctx = object : UiMeasureContext {
        override val fontHeight: Int = 9
        override fun measureText(text: String): Int = text.length * 6
        override fun paint(commands: List<RenderCommand>) = Unit
    }
    @AfterTest
    fun cleanup() {
        OverlayLayerDebugState.resetAll()
        OverlayLayerDebugState.setControlsEnabledTestOverride(null)
    }

    @Test
    fun `debug panel remains available when app and system overlays are disabled`() {
        OverlayLayerDebugState.setControlsEnabledTestOverride(true)
        OverlayLayerDebugState.applicationOverlayRenderEnabled = false
        OverlayLayerDebugState.applicationOverlayInputEnabled = false
        OverlayLayerDebugState.systemOverlayRenderEnabled = false
        OverlayLayerDebugState.systemOverlayInputEnabled = false
        val host = OverlayDebugControlHost()

        host.render(960, 540)
        val layout = host.debugLayout()
        val commands = host.paint(ctx)

        assertNotNull(layout)
        assertTrue(commands.any { it is RenderCommand.DrawText && it.text == "Overlay Debug" })
        assertTrue(host.handleMouseDown(layout.panelRect.x + 3, layout.panelRect.y + 3, MouseButton.LEFT))
        assertTrue(host.handleMouseUp(layout.panelRect.x + 3, layout.panelRect.y + 3, MouseButton.LEFT))
    }

    @Test
    fun `reset all restores overlay render and input toggles`() {
        OverlayLayerDebugState.setControlsEnabledTestOverride(true)
        OverlayLayerDebugState.applicationOverlayRenderEnabled = false
        OverlayLayerDebugState.applicationOverlayInputEnabled = false
        OverlayLayerDebugState.systemOverlayRenderEnabled = false
        OverlayLayerDebugState.systemOverlayInputEnabled = false
        val host = OverlayDebugControlHost()

        host.render(960, 540)
        val layout = host.debugLayout() ?: error("layout missing")
        assertTrue(host.handleMouseDown(layout.resetRect.x + 2, layout.resetRect.y + 2, MouseButton.LEFT))

        assertTrue(OverlayLayerDebugState.applicationOverlayRenderEnabled)
        assertTrue(OverlayLayerDebugState.applicationOverlayInputEnabled)
        assertTrue(OverlayLayerDebugState.systemOverlayRenderEnabled)
        assertTrue(OverlayLayerDebugState.systemOverlayInputEnabled)
    }

    @Test
    fun `debug panel toggles mutate independent app and system overlay state`() {
        OverlayLayerDebugState.setControlsEnabledTestOverride(true)
        OverlayLayerDebugState.resetAll()
        val host = OverlayDebugControlHost()

        host.render(960, 540)
        val layout = host.debugLayout() ?: error("layout missing")

        assertTrue(host.handleMouseDown(layout.appOverlayRenderRect.x + 2, layout.appOverlayRenderRect.y + 2, MouseButton.LEFT))
        assertFalse(OverlayLayerDebugState.applicationOverlayRenderEnabled)
        assertTrue(OverlayLayerDebugState.applicationOverlayInputEnabled)
        assertTrue(OverlayLayerDebugState.systemOverlayRenderEnabled)
        assertTrue(OverlayLayerDebugState.systemOverlayInputEnabled)

        assertTrue(host.handleMouseDown(layout.systemOverlayInputRect.x + 2, layout.systemOverlayInputRect.y + 2, MouseButton.LEFT))
        assertFalse(OverlayLayerDebugState.systemOverlayInputEnabled)
        assertFalse(OverlayLayerDebugState.applicationOverlayRenderEnabled)
    }

    @Test
    fun `debug panel status shows fps and frame time`() {
        OverlayLayerDebugState.setControlsEnabledTestOverride(true)
        OverlayLayerDebugState.updateFrameTiming(0.025)
        val host = OverlayDebugControlHost()

        host.render(960, 540)
        host.paint(ctx)
        val commands = host.paint(ctx)
        val drawTexts = commands
            .filterIsInstance<RenderCommand.DrawText>()
        val statusTexts = drawTexts
            .filter { it.sourceKey == "dsgl-overlay-debug-status" }
            .map { it.text }
        val statusTextValue = assertNotNull(
            statusTexts.lastOrNull { it.isNotBlank() } ?: statusTexts.lastOrNull(),
            "draw texts: ${drawTexts.joinToString { "${it.sourceKey}:${it.text}" }}"
        )
        val expectedFps = OverlayLayerDebugState.frameFps
        val expectedFrameMs = String.format(Locale.US, "%.1f", OverlayLayerDebugState.frameTimeMs)
        val expectedWindowFps = OverlayLayerDebugState.frameFpsWindow
        val expectedWindowFrameMs = String.format(Locale.US, "%.1f", OverlayLayerDebugState.frameTimeWindowMs)
        assertTrue(statusTextValue.contains("FPS:$expectedFps"), "statusText='$statusTextValue'")
        assertTrue(statusTextValue.contains("(${expectedFrameMs}ms)"), "statusText='$statusTextValue'")
        assertTrue(statusTextValue.contains("AvgFPS:$expectedWindowFps"), "statusText='$statusTextValue'")
        assertTrue(statusTextValue.contains("(${expectedWindowFrameMs}ms)"), "statusText='$statusTextValue'")
    }

    @Test
    fun `toggle button label updates immediately after state change`() {
        OverlayLayerDebugState.setControlsEnabledTestOverride(true)
        OverlayLayerDebugState.resetAll()
        val host = OverlayDebugControlHost()

        host.render(960, 540)
        val layout = host.debugLayout() ?: error("layout missing")
        val initialText = host.paint(ctx)
            .filterIsInstance<RenderCommand.DrawText>()
            .lastOrNull { it.sourceKey == "dsgl-overlay-debug-toggle-app-render" }
            ?.text
        assertEquals("ON", initialText)

        assertTrue(host.handleMouseDown(layout.appOverlayRenderRect.x + 2, layout.appOverlayRenderRect.y + 2, MouseButton.LEFT))

        host.render(960, 540)
        val updatedText = host.paint(ctx)
            .filterIsInstance<RenderCommand.DrawText>()
            .lastOrNull { it.sourceKey == "dsgl-overlay-debug-toggle-app-render" }
            ?.text
        assertEquals("OFF", updatedText)
    }

    @Test
    fun `sliding window fps smooths immediate fps`() {
        OverlayLayerDebugState.updateFrameTiming(0.010)
        OverlayLayerDebugState.updateFrameTiming(0.030)
        val snapshot = OverlayLayerDebugState.snapshot()

        assertEquals(33, snapshot.frameFps)
        assertEquals(30.0f, snapshot.frameTimeMs)
        assertEquals(50, snapshot.frameFpsWindow)
        assertEquals(20.0f, snapshot.frameTimeWindowMs)
    }

    @Test
    fun `controls visibility obeys debug-only toggle`() {
        OverlayLayerDebugState.setControlsEnabledTestOverride(false)
        val host = OverlayDebugControlHost()
        host.render(960, 540)
        assertTrue(host.paint(ctx).isEmpty())

        OverlayLayerDebugState.setControlsEnabledTestOverride(true)
        host.render(960, 540)
        assertTrue(host.paint(ctx).isNotEmpty())
    }

    @Test
    fun `debug layer remains enabled in state even when app and system layers are disabled`() {
        OverlayLayerDebugState.applicationOverlayTintEnabled = false
        OverlayLayerDebugState.applicationOverlayRenderEnabled = false
        OverlayLayerDebugState.applicationOverlayInputEnabled = false
        OverlayLayerDebugState.systemOverlayRenderEnabled = false
        OverlayLayerDebugState.systemOverlayTintEnabled = false
        OverlayLayerDebugState.systemOverlayInputEnabled = false

        assertTrue(OverlayLayerDebugState.isRenderEnabled(UiLayerId.Debug))
        assertTrue(OverlayLayerDebugState.isInputEnabled(UiLayerId.Debug))
        assertEquals(
            OverlayLayerDebugSnapshot(
                applicationOverlayRenderEnabled = false,
                applicationOverlayTintEnabled = false,
                applicationOverlayInputEnabled = false,
                systemOverlayRenderEnabled = false,
                systemOverlayTintEnabled = false,
                systemOverlayInputEnabled = false,
                frameFps = 0,
                frameTimeMs = 0f,
                frameFpsWindow = 0,
                frameTimeWindowMs = 0f
            ),
            OverlayLayerDebugState.snapshot()
        )
    }
}
