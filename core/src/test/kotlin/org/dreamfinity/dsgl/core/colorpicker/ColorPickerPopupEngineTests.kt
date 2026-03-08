package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.MouseButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ColorPickerPopupEngineTests {
    @Test
    fun `outside click does not close popup by default`() {
        val engine = ColorPickerPopupEngine()
        engine.onFrame(800, 600)
        engine.open(
            ColorPickerPopupRequest(
                owner = "owner",
                anchorRect = Rect(120, 80, 40, 18),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false)
            )
        )

        assertTrue(engine.isOpen())
        val consumed = engine.handleMouseDown(10, 10, MouseButton.LEFT)
        assertFalse(consumed)
        assertTrue(engine.isOpen())
    }

    @Test
    fun `escape does not close popup when idle`() {
        val engine = ColorPickerPopupEngine()
        engine.onFrame(640, 360)
        engine.open(
            ColorPickerPopupRequest(
                owner = "owner",
                anchorRect = Rect(40, 40, 20, 20),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false)
            )
        )

        assertFalse(engine.handleKeyDown(KeyCodes.ESCAPE))
        assertTrue(engine.isOpen())
    }

    @Test
    fun `close button closes popup`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        engine.onFrame(640, 360)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(100, 80, 32, 20),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false)
            )
        )
        val panel = engine.debugPanelRect(owner) ?: error("panel missing")
        val closeX = panel.x + panel.width - 14
        val closeY = panel.y + 8
        assertTrue(engine.handleMouseDown(closeX, closeY, MouseButton.LEFT))
        assertFalse(engine.isOpen())
    }

    @Test
    fun `header drag moves popup and field drag does not`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        engine.onFrame(1000, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(180, 120, 32, 20),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false)
            )
        )
        val panelBeforeFieldDrag = engine.debugPanelRect(owner) ?: error("panel missing")
        val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
        val fieldX = layout.colorFieldRect.x + layout.colorFieldRect.width / 2
        val fieldY = layout.colorFieldRect.y + layout.colorFieldRect.height / 2
        assertTrue(engine.handleMouseDown(fieldX, fieldY, MouseButton.LEFT))
        assertTrue(engine.handleMouseMove(fieldX + 50, fieldY + 30))
        assertTrue(engine.handleMouseUp(fieldX + 50, fieldY + 30, MouseButton.LEFT))
        val panelAfterFieldDrag = engine.debugPanelRect(owner) ?: error("panel missing")
        assertEquals(panelBeforeFieldDrag.x, panelAfterFieldDrag.x)
        assertEquals(panelBeforeFieldDrag.y, panelAfterFieldDrag.y)

        val header = engine.debugHeaderRect(owner) ?: error("header missing")
        val dragStartX = header.x + 8
        val dragStartY = header.y + 8
        assertTrue(engine.handleMouseDown(dragStartX, dragStartY, MouseButton.LEFT))
        assertTrue(engine.handleMouseMove(dragStartX + 60, dragStartY + 40))
        assertTrue(engine.handleMouseUp(dragStartX + 60, dragStartY + 40, MouseButton.LEFT))
        val panelAfterHeaderDrag = engine.debugPanelRect(owner) ?: error("panel missing")
        assertNotEquals(panelAfterFieldDrag.x, panelAfterHeaderDrag.x)
    }

    @Test
    fun `reopen preserves popup position`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        engine.onFrame(1200, 800)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(120, 100, 20, 20),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false)
            )
        )
        val header = engine.debugHeaderRect(owner) ?: error("header missing")
        val startX = header.x + 6
        val startY = header.y + 6
        engine.handleMouseDown(startX, startY, MouseButton.LEFT)
        engine.handleMouseMove(startX + 140, startY + 90)
        engine.handleMouseUp(startX + 140, startY + 90, MouseButton.LEFT)
        val moved = engine.debugPanelRect(owner) ?: error("panel missing")
        engine.close(owner)

        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(20, 20, 16, 16),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false)
            )
        )
        val reopened = engine.debugPanelRect(owner) ?: error("panel missing")
        assertEquals(moved.x, reopened.x)
        assertEquals(moved.y, reopened.y)
    }

    @Test
    fun `pipette flow does not close popup`() {
        ScreenColorSamplerBridge.install(ScreenColorSampler { _, _ -> 0xFF112233.toInt() })
        try {
            val engine = ColorPickerPopupEngine()
            val owner = "owner"
            var commits = 0
            engine.onFrame(900, 700)
            engine.open(
                ColorPickerPopupRequest(
                    owner = owner,
                    anchorRect = Rect(120, 80, 18, 18),
                    state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
                    onCommit = { commits++ }
                )
            )
            val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
            assertTrue(engine.handleMouseDown(layout.pipetteRect.x + 2, layout.pipetteRect.y + 2, MouseButton.LEFT))
            assertTrue(engine.isOpen())
            assertTrue(engine.handleMouseDown(4, 4, MouseButton.LEFT))
            assertEquals(1, commits)
            assertTrue(engine.handleMouseUp(4, 4, MouseButton.LEFT))
            assertTrue(engine.isOpen())

            val layout2 = engine.debugBodyLayout(owner) ?: error("layout missing")
            assertTrue(engine.handleMouseDown(layout2.pipetteRect.x + 2, layout2.pipetteRect.y + 2, MouseButton.LEFT))
            assertTrue(engine.handleMouseDown(6, 6, MouseButton.RIGHT))
            assertTrue(engine.handleMouseUp(6, 6, MouseButton.RIGHT))
            assertEquals(1, commits)
            assertTrue(engine.isOpen())
        } finally {
            ScreenColorSamplerBridge.install(null)
        }
    }

    @Test
    fun `pipette samples from capture pass before commit`() {
        ScreenColorSamplerBridge.install(ScreenColorSampler { x, y ->
            val r = (x and 0xFF)
            val g = (y and 0xFF)
            val b = 0x44
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        })
        try {
            val engine = ColorPickerPopupEngine()
            val owner = "owner"
            var committed: RgbaColor? = null
            engine.onFrame(900, 700)
            engine.open(
                ColorPickerPopupRequest(
                    owner = owner,
                    anchorRect = Rect(120, 80, 18, 18),
                    state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
                    onCommit = { committed = it }
                )
            )
            val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
            assertTrue(engine.handleMouseDown(layout.pipetteRect.x + 2, layout.pipetteRect.y + 2, MouseButton.LEFT))
            assertTrue(engine.handleMouseMove(25, 52))
            engine.captureEyedropperSample()
            assertTrue(engine.handleMouseDown(25, 52, MouseButton.LEFT))
            assertTrue(engine.handleMouseUp(25, 52, MouseButton.LEFT))
            val expected = RgbaColor.fromArgbInt((0xFF shl 24) or (25 shl 16) or (52 shl 8) or 0x44)
            assertEquals(expected.toArgbInt(), committed?.toArgbInt())
            assertTrue(engine.isOpen())
        } finally {
            ScreenColorSamplerBridge.install(null)
        }
    }

    @Test
    fun `mode selector opens dropdown in popup engine`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        engine.onFrame(900, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(120, 80, 18, 18),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false)
            )
        )
        val initialLayout = engine.debugBodyLayout(owner) ?: error("layout missing")
        assertTrue(initialLayout.modeOptionsRect == null)
        assertTrue(
            engine.handleMouseDown(
                initialLayout.modeSelectRect.x + 2,
                initialLayout.modeSelectRect.y + 2,
                MouseButton.LEFT
            )
        )
        val openedLayout = engine.debugBodyLayout(owner) ?: error("layout missing")
        assertNotNull(openedLayout.modeOptionsRect)
        assertTrue(openedLayout.modeOptions.isNotEmpty())
    }

    @Test
    fun `manager reuses same owner token`() {
        val fakeHost = FakeColorPickerHost()
        val manager = ColorPickerPopupManager(host = fakeHost)
        manager.open(
            anchorRect = Rect(10, 10, 10, 10),
            title = "A",
            state = ColorPickerState(RgbaColor.WHITE)
        )
        manager.open(
            anchorRect = Rect(20, 20, 10, 10),
            title = "B",
            state = ColorPickerState(RgbaColor(0f, 0f, 0f, 1f))
        )

        assertEquals(2, fakeHost.opened.size)
        val first = fakeHost.opened[0]
        val second = fakeHost.opened[1]
        assertTrue(first.owner === second.owner)
        manager.close()
        assertNotNull(fakeHost.lastClosedOwner)
        assertTrue(fakeHost.lastClosedOwner === first.owner)
    }

    private class FakeColorPickerHost : ColorPickerPopupHost {
        val opened: MutableList<ColorPickerPopupRequest> = ArrayList()
        var lastClosedOwner: Any? = null

        override fun open(request: ColorPickerPopupRequest) {
            opened += request
        }

        override fun close(owner: Any) {
            lastClosedOwner = owner
        }

        override fun closeAll() {
            lastClosedOwner = "all"
        }

        override fun isOpenFor(owner: Any): Boolean = false

        override fun isOpen(): Boolean = false
    }
}
