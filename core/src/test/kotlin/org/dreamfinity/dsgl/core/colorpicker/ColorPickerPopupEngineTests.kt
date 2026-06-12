package org.dreamfinity.dsgl.core.colorpicker

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.portal.ScreenDomainId
import org.dreamfinity.dsgl.core.portal.ScreenDomainSurfaces
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
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
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
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
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
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
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )
        val panel = engine.debugPanelRect(owner) ?: error("panel missing")
        val closeX = panel.x + panel.width - 14
        val closeY = panel.y + 8
        assertTrue(engine.handleMouseDown(closeX, closeY, MouseButton.LEFT))
        assertFalse(engine.isOpen())
    }

    @Test
    fun `popup can be closed and reopened for the same owner`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        engine.onFrame(640, 360)
        val request =
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(100, 80, 32, 20),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            )

        engine.open(request)
        assertNotNull(engine.debugPanelRect(owner))
        engine.close(owner)
        assertFalse(engine.isOpen())

        engine.open(request)
        assertTrue(engine.isOpen())
        assertNotNull(engine.debugPanelRect(owner))
        assertNotNull(engine.debugBodyLayout(owner))
    }

    @Test
    fun `syncing same owner keeps popup controller identity stable`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        engine.onFrame(640, 360)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(100, 80, 32, 20),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )

        val firstController = engine.debugController(owner) ?: error("controller missing")
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(120, 90, 32, 20),
                state = ColorPickerState(color = RgbaColor(0f, 0f, 0f, 1f), closeOnSelect = false),
            ),
        )

        val secondController = engine.debugController(owner) ?: error("controller missing")
        assertSame(firstController, secondController)
        assertTrue(engine.isOpen())
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
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
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
    fun `mouse up inside popup body is consumed to prevent lower-layer fallthrough`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        engine.onFrame(1000, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(180, 120, 32, 20),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )
        val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
        val x = layout.colorFieldRect.x + 12
        val y = layout.colorFieldRect.y + 12

        assertTrue(engine.handleMouseDown(x, y, MouseButton.LEFT))
        assertTrue(engine.handleMouseUp(x, y, MouseButton.LEFT))
        assertTrue(engine.isOpenFor(owner))
    }

    @Test
    fun `color field drag updates continuously across multiple move events`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        val previews = mutableListOf<RgbaColor>()
        var committed: RgbaColor? = null
        engine.onFrame(1000, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(180, 120, 32, 20),
                state = ColorPickerState(color = RgbaColor(1f, 0f, 0f, 1f), closeOnSelect = false),
                onPreview = { previews += it },
                onCommit = { committed = it },
            ),
        )
        val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
        val startX = layout.colorFieldRect.x + 4
        val startY = layout.colorFieldRect.y + layout.colorFieldRect.height - 4
        val midX = layout.colorFieldRect.x + layout.colorFieldRect.width / 2
        val midY = layout.colorFieldRect.y + layout.colorFieldRect.height / 2
        val endX = layout.colorFieldRect.x + layout.colorFieldRect.width - 4
        val endY = layout.colorFieldRect.y + 4

        assertTrue(engine.handleMouseDown(startX, startY, MouseButton.LEFT))
        assertTrue(engine.handleMouseMove(midX, midY))
        assertTrue(engine.handleMouseMove(endX, endY))
        assertTrue(engine.handleMouseUp(endX, endY, MouseButton.LEFT))

        assertTrue(previews.size >= 2)
        assertTrue(
            previews
                .map { it.toArgbInt() }
                .distinct()
                .size >= 2,
        )
        assertEquals(previews.last().toArgbInt(), committed?.toArgbInt())
    }

    @Test
    fun `hue drag updates continuously across multiple move events`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        var committed: RgbaColor? = null
        engine.onFrame(1000, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(180, 120, 32, 20),
                state = ColorPickerState(color = RgbaColor(1f, 0f, 0f, 1f), closeOnSelect = false),
                onCommit = { committed = it },
            ),
        )
        val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
        val startX = layout.hueRect.x + 4
        val midX = layout.hueRect.x + layout.hueRect.width / 3
        val endX = layout.hueRect.x + (layout.hueRect.width * 2) / 3
        val y = layout.hueRect.y + layout.hueRect.height / 2

        assertTrue(engine.handleMouseDown(startX, y, MouseButton.LEFT))
        assertTrue(engine.handleMouseMove(midX, y))
        val midColor =
            engine
                .debugController(owner)
                ?.snapshot()
                ?.color ?: error("controller missing")
        assertTrue(engine.handleMouseMove(endX, y))
        val endColor =
            engine
                .debugController(owner)
                ?.snapshot()
                ?.color ?: error("controller missing")
        assertTrue(engine.handleMouseUp(endX, y, MouseButton.LEFT))

        assertTrue(midColor.toArgbInt() != endColor.toArgbInt())
        assertEquals(endColor.toArgbInt(), committed?.toArgbInt())
    }

    @Test
    fun `alpha drag updates continuously across multiple move events`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        val previews = mutableListOf<RgbaColor>()
        var committed: RgbaColor? = null
        engine.onFrame(1000, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(180, 120, 32, 20),
                state =
                    ColorPickerState(
                        color = RgbaColor(0.5f, 0.4f, 0.3f, 1f),
                        closeOnSelect = false,
                        alphaEnabled = true,
                    ),
                onPreview = { previews += it },
                onCommit = { committed = it },
            ),
        )
        val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
        val alphaRect = layout.alphaRect ?: error("alpha rect missing")
        val startX = alphaRect.x + alphaRect.width - 4
        val midX = alphaRect.x + alphaRect.width / 2
        val endX = alphaRect.x + 4
        val y = alphaRect.y + alphaRect.height / 2

        assertTrue(engine.handleMouseDown(startX, y, MouseButton.LEFT))
        assertTrue(engine.handleMouseMove(midX, y))
        assertTrue(engine.handleMouseMove(endX, y))
        assertTrue(engine.handleMouseUp(endX, y, MouseButton.LEFT))

        assertTrue(previews.size >= 2)
        assertTrue(previews.first().a > previews.last().a)
        assertEquals(previews.last().toArgbInt(), committed?.toArgbInt())
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
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
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
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
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
                    onCommit = { commits++ },
                ),
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
        ScreenColorSamplerBridge.install(
            ScreenColorSampler { x, y ->
                val r = (x and 0xFF)
                val g = (y and 0xFF)
                val b = 0x44
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            },
        )
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
                    onCommit = { committed = it },
                ),
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
    fun `pipette capture updates preview continuously while moving`() {
        ScreenColorSamplerBridge.install(
            ScreenColorSampler { x, y ->
                val r = (x and 0xFF)
                val g = (y and 0xFF)
                val b = 0x55
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            },
        )
        try {
            val engine = ColorPickerPopupEngine()
            val owner = "owner"
            val previews = mutableListOf<RgbaColor>()
            engine.onFrame(900, 700)
            engine.open(
                ColorPickerPopupRequest(
                    owner = owner,
                    anchorRect = Rect(120, 80, 18, 18),
                    state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
                    onPreview = { previews += it },
                ),
            )
            val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
            assertTrue(engine.handleMouseDown(layout.pipetteRect.x + 2, layout.pipetteRect.y + 2, MouseButton.LEFT))

            engine.handleMouseMove(40, 72)
            engine.captureEyedropperSample()
            val first =
                engine
                    .debugController(owner)
                    ?.snapshot()
                    ?.color ?: error("controller missing")

            engine.handleMouseMove(96, 24)
            engine.captureEyedropperSample()
            val second =
                engine
                    .debugController(owner)
                    ?.snapshot()
                    ?.color ?: error("controller missing")

            assertNotEquals(first.toArgbInt(), second.toArgbInt())
            assertTrue(previews.isNotEmpty())
            assertEquals(second.toArgbInt(), previews.last().toArgbInt())
        } finally {
            ScreenColorSamplerBridge.install(null)
        }
    }

    @Test
    fun `app-owned pipette emits transient portal commands in application portal contract`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner-app"
        engine.onFrame(900, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                ownerDomain = ScreenDomainId.Application,
                anchorRect = Rect(120, 80, 18, 18),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )
        val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
        assertTrue(engine.handleMouseDown(layout.pipetteRect.x + 2, layout.pipetteRect.y + 2, MouseButton.LEFT))
        assertTrue(engine.handleMouseMove(layout.pipetteRect.x + 24, layout.pipetteRect.y + 24))

        val portalCommands = mutableListOf<RenderCommand>()
        engine.appendEyedropperPortalCommands(900, 700, portalCommands)

        assertTrue(portalCommands.isNotEmpty())
        assertEquals(ScreenDomainId.Application, engine.debugActiveOwnerDomain())
        assertEquals(
            ScreenDomainSurfaces.ApplicationPortal,
            ScreenDomainSurfaces.portalSurfaceForDomain(engine.debugActiveOwnerDomain()!!),
        )
    }

    @Test
    fun `system-owned pipette emits transient portal commands in system portal contract`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner-system"
        engine.onFrame(900, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                ownerDomain = ScreenDomainId.System,
                anchorRect = Rect(120, 80, 18, 18),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )
        val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
        assertTrue(engine.handleMouseDown(layout.pipetteRect.x + 2, layout.pipetteRect.y + 2, MouseButton.LEFT))
        assertTrue(engine.handleMouseMove(layout.pipetteRect.x + 24, layout.pipetteRect.y + 24))

        val portalCommands = mutableListOf<RenderCommand>()
        engine.appendEyedropperPortalCommands(900, 700, portalCommands)

        assertTrue(portalCommands.isNotEmpty())
        assertEquals(ScreenDomainId.System, engine.debugActiveOwnerDomain())
        assertEquals(
            ScreenDomainSurfaces.SystemPortal,
            ScreenDomainSurfaces.portalSurfaceForDomain(engine.debugActiveOwnerDomain()!!),
        )
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
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )
        val initialLayout = engine.debugBodyLayout(owner) ?: error("layout missing")
        assertTrue(initialLayout.modeOptionsRect == null)
        assertTrue(
            engine.handleMouseDown(
                initialLayout.modeSelectRect.x + 2,
                initialLayout.modeSelectRect.y + 2,
                MouseButton.LEFT,
            ),
        )
        val openedLayout = engine.debugBodyLayout(owner) ?: error("layout missing")
        assertNotNull(openedLayout.modeOptionsRect)
        assertTrue(openedLayout.modeOptions.isNotEmpty())
    }

    @Test
    fun `popup input editing updates color state and keeps popup usable`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        val previews = mutableListOf<RgbaColor>()
        var committed: RgbaColor? = null
        engine.onFrame(900, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(120, 80, 18, 18),
                state =
                    ColorPickerState(
                        color = RgbaColor.WHITE,
                        mode = ColorFormatMode.RGB,
                        alphaEnabled = true,
                        closeOnSelect = false,
                    ),
                onPreview = { previews += it },
                onCommit = { committed = it },
            ),
        )
        val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
        val redInput = layout.inputSlots.firstOrNull { it.key == "r" } ?: error("red input missing")
        val inputX = redInput.inputRect.x + 4
        val inputY = redInput.inputRect.y + 4

        assertTrue(engine.handleMouseDown(inputX, inputY, MouseButton.LEFT))
        assertTrue(engine.handleKeyDown(KeyCodes.DELETE))
        assertTrue(engine.handleKeyDown(0, '0'))
        assertTrue(engine.handleKeyDown(KeyCodes.ENTER))

        assertTrue(previews.isNotEmpty())
        assertEquals(0f, previews.last().r)
        assertEquals(previews.last().toArgbInt(), committed?.toArgbInt())

        val header = engine.debugHeaderRect(owner) ?: error("header missing")
        val dragStartX = header.x + 8
        val dragStartY = header.y + 8
        val panelBefore = engine.debugPanelRect(owner) ?: error("panel missing")
        assertTrue(engine.handleMouseDown(dragStartX, dragStartY, MouseButton.LEFT))
        assertTrue(engine.handleMouseMove(dragStartX + 40, dragStartY + 30))
        assertTrue(engine.handleMouseUp(dragStartX + 40, dragStartY + 30, MouseButton.LEFT))
        val panelAfter = engine.debugPanelRect(owner) ?: error("panel missing")
        assertNotEquals(panelBefore.x, panelAfter.x)
    }

    @Test
    fun `opened popup appends portal commands after frame sync`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(120, 80, 18, 18),
                title = "Popup Test",
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )
        engine.onFrame(900, 700)

        val out = ArrayList<RenderCommand>()
        engine.appendPortalCommands(out)

        assertTrue(out.isNotEmpty())
        assertTrue(out.any { it is RenderCommand.DrawText && it.text == "Popup Test" })
    }

    @Test
    fun `sync during drag does not cancel active field drag session`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        val previews = mutableListOf<RgbaColor>()
        engine.onFrame(1000, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(180, 120, 32, 20),
                state = ColorPickerState(color = RgbaColor(1f, 0f, 0f, 1f), closeOnSelect = false),
                onPreview = { previews += it },
            ),
        )
        val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
        val startX = layout.colorFieldRect.x + 4
        val startY = layout.colorFieldRect.y + layout.colorFieldRect.height - 4
        val midX = layout.colorFieldRect.x + layout.colorFieldRect.width / 2
        val midY = layout.colorFieldRect.y + layout.colorFieldRect.height / 2
        val endX = layout.colorFieldRect.x + layout.colorFieldRect.width - 4
        val endY = layout.colorFieldRect.y + 4

        assertTrue(engine.handleMouseDown(startX, startY, MouseButton.LEFT))
        assertTrue(engine.handleMouseMove(midX, midY))
        val midColor =
            engine
                .debugController(owner)
                ?.snapshot()
                ?.color ?: error("controller missing")

        engine.sync(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(180, 120, 32, 20),
                state = ColorPickerState(color = RgbaColor(0f, 1f, 0f, 1f), closeOnSelect = false),
            ),
        )
        assertTrue(engine.handleMouseMove(endX, endY))
        assertTrue(engine.handleMouseUp(endX, endY, MouseButton.LEFT))

        val finalColor =
            engine
                .debugController(owner)
                ?.snapshot()
                ?.color ?: error("controller missing")
        assertNotEquals(midColor.toArgbInt(), finalColor.toArgbInt())
        assertTrue(previews.size >= 2)
    }

    @Test
    fun `sync during eyedropper keeps eyedropper active`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        engine.onFrame(900, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(120, 80, 18, 18),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )
        val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
        assertTrue(engine.handleMouseDown(layout.pipetteRect.x + 2, layout.pipetteRect.y + 2, MouseButton.LEFT))
        assertTrue(engine.debugController(owner)?.isEyedropperActive() == true)

        engine.sync(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(120, 80, 18, 18),
                state = ColorPickerState(color = RgbaColor(0.2f, 0.3f, 0.4f, 1f), closeOnSelect = false),
            ),
        )

        assertTrue(engine.debugController(owner)?.isEyedropperActive() == true)
    }

    @Test
    fun `has active eyedropper reports popup sampler activity`() {
        val engine = ColorPickerPopupEngine()
        val owner = "owner"
        engine.onFrame(900, 700)
        engine.open(
            ColorPickerPopupRequest(
                owner = owner,
                anchorRect = Rect(120, 80, 18, 18),
                state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            ),
        )
        assertFalse(engine.hasActiveEyedropper())

        val layout = engine.debugBodyLayout(owner) ?: error("layout missing")
        assertTrue(engine.handleMouseDown(layout.pipetteRect.x + 2, layout.pipetteRect.y + 2, MouseButton.LEFT))

        assertTrue(engine.hasActiveEyedropper())
    }

    @Test
    fun `manager reuses same owner token`() {
        val fakeService = FakeColorPickerPortalService()
        val manager = ColorPickerPopupManager(portalService = fakeService)
        manager.open(
            anchorRect = Rect(10, 10, 10, 10),
            title = "A",
            state = ColorPickerState(RgbaColor.WHITE),
        )
        manager.open(
            anchorRect = Rect(20, 20, 10, 10),
            title = "B",
            state = ColorPickerState(RgbaColor(0f, 0f, 0f, 1f)),
        )

        assertEquals(2, fakeService.opened.size)
        val first = fakeService.opened[0]
        val second = fakeService.opened[1]
        assertTrue(first.owner === second.owner)
        manager.close()
        assertNotNull(fakeService.lastClosedOwner)
        assertTrue(fakeService.lastClosedOwner === first.owner)
    }

    @Test
    fun `manager popup owner scope defaults to application and supports explicit system owner scope`() {
        val fakeService = FakeColorPickerPortalService()
        val manager = ColorPickerPopupManager(portalService = fakeService)
        manager.open(
            anchorRect = Rect(10, 10, 10, 10),
            title = "App",
            state = ColorPickerState(RgbaColor.WHITE),
        )
        manager.open(
            ownerDomain = ScreenDomainId.System,
            anchorRect = Rect(20, 20, 10, 10),
            title = "System",
            state = ColorPickerState(RgbaColor.WHITE),
        )

        assertEquals(2, fakeService.opened.size)
        assertEquals(ScreenDomainId.Application, fakeService.opened[0].ownerDomain)
        assertEquals(ScreenDomainId.System, fakeService.opened[1].ownerDomain)
    }

    private class FakeColorPickerPortalService : ColorPickerPopupPortalService {
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
