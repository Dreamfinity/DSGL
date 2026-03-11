package org.dreamfinity.dsgl.core.debug

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand

class OverlayDebugControlHostTests {
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
        val commands = host.paint()

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
    fun `controls visibility obeys debug-only toggle`() {
        OverlayLayerDebugState.setControlsEnabledTestOverride(false)
        val host = OverlayDebugControlHost()
        host.render(960, 540)
        assertTrue(host.paint().isEmpty())

        OverlayLayerDebugState.setControlsEnabledTestOverride(true)
        host.render(960, 540)
        assertTrue(host.paint().isNotEmpty())
    }

    @Test
    fun `debug layer remains enabled in state even when app and system layers are disabled`() {
        OverlayLayerDebugState.applicationOverlayTintEnabled = false
        OverlayLayerDebugState.applicationOverlayRenderEnabled = false
        OverlayLayerDebugState.applicationOverlayInputEnabled = false
        OverlayLayerDebugState.systemOverlayRenderEnabled = false
        OverlayLayerDebugState.systemOverlayTintEnabled = false
        OverlayLayerDebugState.systemOverlayInputEnabled = false

        assertTrue(OverlayLayerDebugState.isRenderEnabled(org.dreamfinity.dsgl.core.overlay.UiLayerId.Debug))
        assertTrue(OverlayLayerDebugState.isInputEnabled(org.dreamfinity.dsgl.core.overlay.UiLayerId.Debug))
        assertEquals(
            OverlayLayerDebugSnapshot(
                applicationOverlayRenderEnabled = false,
                applicationOverlayTintEnabled = false,
                applicationOverlayInputEnabled = false,
                systemOverlayRenderEnabled = false,
                systemOverlayTintEnabled = false,
                systemOverlayInputEnabled = false
            ),
            OverlayLayerDebugState.snapshot()
        )
    }
}
