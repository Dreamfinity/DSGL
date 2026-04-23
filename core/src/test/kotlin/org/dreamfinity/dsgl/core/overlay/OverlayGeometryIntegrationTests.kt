package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupEngine
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayHost
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.style.StyleEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OverlayGeometryIntegrationTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @Test
    fun `system overlay root uses full game viewport bounds in live host path`() {
        StyleEngine.setViewportSize(120, 90)
        val host = SystemOverlayHost(InspectorController())

        host.onInputFrame(1280, 720)
        host.render(ctx, 1280, 720)

        assertEquals(Rect(0, 0, 1280, 720), host.debugRootBounds())
    }

    @Test
    fun `application overlay root uses full top-level window viewport bounds`() {
        StyleEngine.setViewportSize(160, 120)
        val host = ApplicationOverlayHost()

        host.render(ctx, 1024, 768)

        assertEquals(Rect(0, 0, 1024, 768), host.debugRootBounds())
    }

    @Test
    fun `mouse-open popup placement uses valid viewport and anchor space`() {
        val engine = ColorPickerPopupEngine()
        val owner = "mouse-owner"
        val anchor = Rect(420, 260, 24, 18)

        engine.onFrame(1280, 720)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = anchor,
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )

        val panel = engine.debugPanelRect(owner)
        assertNotNull(panel)
        assertNotEquals(2, panel.x)
        assertNotEquals(2, panel.y)
        assertTrue(panel.x >= 8)
        assertTrue(panel.y >= anchor.y)
    }

    @Test
    fun `keyboard-open popup placement does not collapse to top-left after viewport initializes`() {
        val engine = ColorPickerPopupEngine()
        val owner = "keyboard-owner"
        val anchor = Rect(360, 220, 1, 1)

        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = anchor,
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )
        val preFramePanel = engine.debugPanelRect(owner)
        assertNotNull(preFramePanel)
        assertEquals(anchor.x, preFramePanel.x)

        engine.onFrame(1280, 720)
        val panel = engine.debugPanelRect(owner)
        assertNotNull(panel)
        assertNotEquals(2, panel.x)
        assertNotEquals(2, panel.y)
        assertTrue(panel.x >= 8)
        assertTrue(panel.y >= 8)
    }

    @Test
    fun `anti-stale-viewport placement keeps anchor intent before first frame`() {
        val engine = ColorPickerPopupEngine()
        val owner = "stale-owner"
        val anchor = Rect(512, 320, 10, 10)

        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = anchor,
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )

        val panel = engine.debugPanelRect(owner)
        assertNotNull(panel)
        assertEquals(anchor.x, panel.x)
        assertEquals(anchor.y + anchor.height, panel.y)
    }

    @Test
    fun `reopen remembered popup position remains clamped for current overlay viewport`() {
        val engine = ColorPickerPopupEngine()
        val owner = "remember-owner"

        engine.onFrame(1400, 900)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(420, 260, 24, 18),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )
        engine.forcePanelRect(owner, Rect(980, 200, 320, 340))
        engine.close(owner)

        engine.onFrame(640, 360)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(20, 20, 10, 10),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )
        val reopened = engine.debugPanelRect(owner)
        assertNotNull(reopened)
        assertEquals(318, reopened.x)
        assertTrue(reopened.y >= 2)
    }
}
