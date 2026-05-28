package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.colorpicker.ColorPickerPopupRequest
import org.dreamfinity.dsgl.core.colorpicker.ColorPickerState
import org.dreamfinity.dsgl.core.colorpicker.RgbaColor
import org.dreamfinity.dsgl.core.colorpicker.ScreenColorSampler
import org.dreamfinity.dsgl.core.colorpicker.ScreenColorSamplerBridge
import org.dreamfinity.dsgl.core.contextmenu.contextMenu
import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.applyParent
import org.dreamfinity.dsgl.core.dom.elements.ButtonNode
import org.dreamfinity.dsgl.core.dom.elements.ContainerNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.overlay.DomainPortalServices
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayEntryId
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayHost
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayPanelDemoNode
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectEntry
import org.dreamfinity.dsgl.core.select.SelectOpenRequest
import org.dreamfinity.dsgl.core.select.selectModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class LiveLayerInteractionPathTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanupDomainContextMenuPortalService() {
        DomainPortalServices.applicationContextMenuEngine.closeAll()
        DomainPortalServices.applicationColorPickerEngine.closeAll()
        ScreenColorSamplerBridge.install(null)
        DomainPortalServices.closeAllSelects()
    }

    @Test
    fun `runtime input path resolves in full domain surface order`() {
        val callOrder = ArrayList<ScreenDomainSurface>(6)
        val fixture =
            LiveLayerInputFixture(
                debugPortalHandler = { _, _, _ ->
                    callOrder += ScreenDomainSurfaces.DebugPortal
                    false
                },
                debugHandler = { _, _, _ ->
                    callOrder += ScreenDomainSurfaces.DebugRoot
                    false
                },
                systemOverlayHandler = { _, _, _ ->
                    callOrder += ScreenDomainSurfaces.SystemPortal
                    false
                },
                systemRootHandler = { _, _, _ ->
                    callOrder += ScreenDomainSurfaces.SystemRoot
                    false
                },
                applicationOverlayHandler = { _, _, _ ->
                    callOrder += ScreenDomainSurfaces.ApplicationPortal
                    false
                },
            )
        val consumedBy =
            fixture.dispatchMouseDown(10, 10, MouseButton.LEFT) {
                callOrder += ScreenDomainSurfaces.ApplicationRoot
                true
            }

        assertEquals(ScreenDomainSurfaces.ApplicationRoot, consumedBy)
        assertEquals(
            listOf(
                ScreenDomainSurfaces.DebugPortal,
                ScreenDomainSurfaces.DebugRoot,
                ScreenDomainSurfaces.SystemPortal,
                ScreenDomainSurfaces.SystemRoot,
                ScreenDomainSurfaces.ApplicationPortal,
                ScreenDomainSurfaces.ApplicationRoot,
            ),
            callOrder,
        )
    }

    @Test
    fun `debug portal consumption prevents lower-domain fallthrough`() {
        var debugRootReceived = false
        var systemReceived = false
        var appOverlayReceived = false
        var appRootReceived = false
        val fixture =
            LiveLayerInputFixture(
                debugPortalHandler = { _, _, _ -> true },
                debugHandler = { _, _, _ ->
                    debugRootReceived = true
                    false
                },
                systemOverlayHandler = { _, _, _ ->
                    systemReceived = true
                    false
                },
                applicationOverlayHandler = { _, _, _ ->
                    appOverlayReceived = true
                    false
                },
            )
        val consumedBy =
            fixture.dispatchMouseDown(12, 14, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(ScreenDomainSurfaces.DebugPortal, consumedBy)
        assertFalse(debugRootReceived)
        assertFalse(systemReceived)
        assertFalse(appOverlayReceived)
        assertFalse(appRootReceived)
    }

    @Test
    fun `debug root consumption prevents lower-domain fallthrough`() {
        var systemReceived = false
        var appOverlayReceived = false
        var appRootReceived = false
        val fixture =
            LiveLayerInputFixture(
                debugHandler = { _, _, _ -> true },
                systemOverlayHandler = { _, _, _ ->
                    systemReceived = true
                    false
                },
                applicationOverlayHandler = { _, _, _ ->
                    appOverlayReceived = true
                    false
                },
            )
        val consumedBy =
            fixture.dispatchMouseDown(12, 14, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(ScreenDomainSurfaces.DebugRoot, consumedBy)
        assertFalse(systemReceived)
        assertFalse(appOverlayReceived)
        assertFalse(appRootReceived)
    }

    @Test
    fun `system root consumption prevents lower-domain fallthrough`() {
        var appOverlayReceived = false
        var appRootReceived = false
        val fixture =
            LiveLayerInputFixture(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { _, _, _ -> false },
                systemRootHandler = { _, _, _ -> true },
                applicationOverlayHandler = { _, _, _ ->
                    appOverlayReceived = true
                    false
                },
            )
        val consumedBy =
            fixture.dispatchMouseDown(12, 14, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(ScreenDomainSurfaces.SystemRoot, consumedBy)
        assertFalse(appOverlayReceived)
        assertFalse(appRootReceived)
    }

    @Test
    fun `system overlay consumption prevents lower-layer fallthrough`() {
        val systemHost = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()
        systemHost.onInputFrame(1280, 720)
        systemHost.togglePanelDemo(anchorX = 240, anchorY = 180)
        systemHost.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 244,
            cursorY = 186,
            inspectorPointerCaptured = false,
        )

        val entryState = systemHost.debugEntryState(SystemOverlayEntryId.PanelDemo) ?: error("panel demo state missing")
        val panelRect = entryState.panelState.currentRectOrNull() ?: error("panel demo rect missing")
        val fixture =
            LiveLayerInputFixture(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { x, y, button -> systemHost.handleMouseDown(x, y, button) },
                applicationOverlayHandler = { _, _, _ -> false },
            )
        var appRootReceived = false
        val consumedBy =
            fixture.dispatchMouseDown(panelRect.x + 20, panelRect.y + 70, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(ScreenDomainSurfaces.SystemPortal, consumedBy)
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
        systemHost.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 984,
            cursorY = 144,
            inspectorPointerCaptured = false,
        )
        systemHost.render(ctx, 1280, 720)

        val panelRect = inspector.overlayPanelRect() ?: error("inspector panel rect missing")
        val outsideX = if (panelRect.x > 40) panelRect.x - 20 else panelRect.x + panelRect.width + 20
        val outsideY = (panelRect.y + panelRect.height / 2).coerceIn(1, 719)
        assertFalse(panelRect.contains(outsideX, outsideY))

        val fixture =
            LiveLayerInputFixture(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { x, y, button -> systemHost.handleMouseDown(x, y, button) },
                applicationOverlayHandler = { _, _, _ -> false },
            )

        var appRootReceivedOutside = false
        val consumedOutside =
            fixture.dispatchMouseDown(outsideX, outsideY, MouseButton.LEFT) {
                appRootReceivedOutside = true
                true
            }
        assertEquals(ScreenDomainSurfaces.ApplicationRoot, consumedOutside)
        assertTrue(appRootReceivedOutside)
    }

    @Test
    fun `application overlay consumption prevents app-root fallthrough`() {
        val fixture =
            LiveLayerInputFixture(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { _, _, _ -> false },
                applicationOverlayHandler = { _, _, _ -> true },
            )
        var appRootReceived = false
        val consumedBy =
            fixture.dispatchMouseDown(24, 30, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedBy)
        assertFalse(appRootReceived)
    }

    @Test
    fun `application overlay host dom bridge consumes mounted node and blocks app-root fallthrough`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(1280, 720)
        var clicks = 0
        ButtonNode("Overlay", key = "app-overlay-button")
            .apply {
                bounds = Rect(40, 44, 120, 24)
                onClick { clicks += 1 }
            }.applyParent(applicationOverlayRoot(applicationOverlayHost))

        assertTrue(applicationOverlayHost.handleMouseDown(50, 50, MouseButton.LEFT))
        assertTrue(applicationOverlayHost.handleMouseUp(50, 50, MouseButton.LEFT))
        assertEquals(1, clicks)

        val fixture =
            LiveLayerInputFixture(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { _, _, _ -> false },
                applicationOverlayHandler = { x, y, button ->
                    applicationOverlayHost.handleMouseDown(x, y, button)
                },
            )
        var appRootReceived = false
        val consumedBy =
            fixture.dispatchMouseDown(50, 50, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedBy)
        assertFalse(appRootReceived)
    }

    @Test
    fun `application context menu is rendered and consumed through application portal path`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 180)
        var actionHits = 0
        DomainPortalServices.applicationContextMenuEngine.openAtCursor(
            contextMenu(id = "portal.context") {
                item("Run") {
                    onClick { actionHits += 1 }
                }
            },
            x = 24,
            y = 24,
        )

        applicationOverlayHost.syncPortalFrame(ctx, 320, 180, 1f, 24, 24)
        val commands = ArrayList<RenderCommand>()
        applicationOverlayHost.appendPortalOverlayCommands(ctx, 320, 180, commands)
        val firstEntryRect = DomainPortalServices.applicationContextMenuEngine.debugEntryRect(levelIndex = 0, entryIndex = 0)
        assertNotNull(firstEntryRect)

        val consumedByMenu =
            applicationOverlayHost.handlePortalPointerAfterDom(
                mouseX = firstEntryRect.x + 1,
                mouseY = firstEntryRect.y + 1,
                dWheel = 0,
                button = MouseButton.LEFT,
                pressed = true,
            )

        assertTrue(commands.isNotEmpty())
        assertTrue(consumedByMenu)
        assertEquals(1, actionHits)
        assertFalse(applicationOverlayHost.hasOpenContextMenuPortal())
    }

    @Test
    fun `application context menu portal blocks app-root fallthrough on outside dismiss`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 180)
        DomainPortalServices.applicationContextMenuEngine.openAtCursor(
            contextMenu(id = "portal.dismiss") {
                item("Run")
                item("Build")
            },
            x = 24,
            y = 24,
        )
        applicationOverlayHost.syncPortalFrame(ctx, 320, 180, 1f, 24, 24)
        val panel = DomainPortalServices.applicationContextMenuEngine.debugPanelRect(0)
        assertNotNull(panel)
        val outsideX = panel.x + panel.width + 24
        val outsideY = panel.y + panel.height + 24

        val fixture =
            LiveLayerInputFixture(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { _, _, _ -> false },
                applicationOverlayHandler = { x, y, button ->
                    applicationOverlayHost.handlePortalPointerAfterDom(x, y, 0, button, true)
                },
            )
        var appRootReceived = false
        val consumedBy =
            fixture.dispatchMouseDown(outsideX, outsideY, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedBy)
        assertFalse(appRootReceived)
        assertFalse(applicationOverlayHost.hasOpenContextMenuPortal())
    }

    @Test
    fun `application context menu portal consumes wheel and escape while open`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 180)
        DomainPortalServices.applicationContextMenuEngine.openAtCursor(
            contextMenu(id = "portal.keyboard") {
                item("Run")
                item("Build")
            },
            x = 24,
            y = 24,
        )
        applicationOverlayHost.syncPortalFrame(ctx, 320, 180, 1f, 24, 24)

        assertTrue(applicationOverlayHost.handlePortalPointerAfterDom(26, 26, -120, null, false))
        assertTrue(applicationOverlayHost.handlePortalKeyDownAfterDom(KeyCodes.ESCAPE, Char.MIN_VALUE))
        assertFalse(applicationOverlayHost.hasOpenContextMenuPortal())
    }

    @Test
    fun `application select is rendered and consumed through application portal path`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 180)
        var selected: String? = null
        val owner = "application-select-portal"
        DomainPortalServices.openSelect(selectRequest(owner, OverlayOwnerScope.Application) { selected = it })

        applicationOverlayHost.syncPortalFrame(ctx, 320, 180, 1f, 0, 0)
        val commands = ArrayList<RenderCommand>()
        applicationOverlayHost.appendPortalOverlayCommands(ctx, 320, 180, commands)
        val panel = DomainPortalServices.applicationSelectEngine.debugPanelRect(owner)
        assertNotNull(panel)

        val style = DomainPortalServices.applicationSelectEngine.currentStyle()
        val consumed =
            applicationOverlayHost.handlePortalPointerAfterDom(
                mouseX = panel.x + style.panelPaddingX + 1,
                mouseY = panel.y + style.panelPaddingY + 1,
                dWheel = 0,
                button = MouseButton.LEFT,
                pressed = true,
            )

        assertTrue(commands.isNotEmpty())
        assertTrue(consumed)
        assertEquals(null, selected)
        assertTrue(
            applicationOverlayHost.handlePortalPointerAfterDom(
                mouseX = panel.x + style.panelPaddingX + 1,
                mouseY = panel.y + style.panelPaddingY + 1,
                dWheel = 0,
                button = MouseButton.LEFT,
                pressed = false,
            ),
        )
        assertEquals("a", selected)
    }

    @Test
    fun `application select portal blocks app-root fallthrough on outside dismiss`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 180)
        val owner = "application-select-dismiss"
        DomainPortalServices.openSelect(selectRequest(owner, OverlayOwnerScope.Application))
        applicationOverlayHost.syncPortalFrame(ctx, 320, 180, 1f, 0, 0)
        val panel = DomainPortalServices.applicationSelectEngine.debugPanelRect(owner)
        assertNotNull(panel)
        val outsideX = panel.x + panel.width + 24
        val outsideY = panel.y + panel.height + 24
        val fixture =
            LiveLayerInputFixture(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { _, _, _ -> false },
                applicationOverlayHandler = { x, y, button ->
                    applicationOverlayHost.handlePortalPointerAfterDom(x, y, 0, button, true)
                },
            )

        var appRootReceived = false
        val consumedBy =
            fixture.dispatchMouseDown(outsideX, outsideY, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedBy)
        assertFalse(appRootReceived)
    }

    @Test
    fun `application select portal consumes wheel typeahead and escape`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 120)
        val owner = "application-select-keyboard"
        var selected: String? = null
        DomainPortalServices.openSelect(
            selectRequest(
                owner = owner,
                ownerScope = OverlayOwnerScope.Application,
                entries =
                    listOf(
                        SelectEntry.Option("a", labelProvider = { "Alpha" }),
                        SelectEntry.Option("b", labelProvider = { "Beta" }),
                        SelectEntry.Option("c", labelProvider = { "Charlie" }),
                        SelectEntry.Option("d", labelProvider = { "Delta" }),
                        SelectEntry.Option("e", labelProvider = { "Echo" }),
                        SelectEntry.Option("f", labelProvider = { "Foxtrot" }),
                    ),
                onSelect = { selected = it },
            ),
        )
        applicationOverlayHost.syncPortalFrame(ctx, 320, 120, 1f, 0, 0)
        val panel = DomainPortalServices.applicationSelectEngine.debugPanelRect(owner)
        assertNotNull(panel)

        assertTrue(applicationOverlayHost.handlePortalPointerAfterDom(panel.x + 2, panel.y + 2, -120, null, false))
        assertTrue(applicationOverlayHost.handlePortalKeyDownAfterDom(0, 'd'))
        assertTrue(applicationOverlayHost.handlePortalKeyDownAfterDom(KeyCodes.ENTER, Char.MIN_VALUE))
        assertEquals("d", selected)

        DomainPortalServices.openSelect(selectRequest(owner, OverlayOwnerScope.Application))
        applicationOverlayHost.syncPortalFrame(ctx, 320, 120, 1f, 0, 0)
        assertTrue(applicationOverlayHost.handlePortalKeyDownAfterDom(KeyCodes.ESCAPE, Char.MIN_VALUE))
    }

    @Test
    fun `application color picker is rendered and consumed through application portal path`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(360, 240)
        val owner = "application-color-picker-portal"
        DomainPortalServices.applicationColorPickerEngine.open(colorPickerRequest(owner, OverlayOwnerScope.Application))

        applicationOverlayHost.syncPortalFrame(ctx, 360, 240, 1f, 42, 48)
        val commands = ArrayList<RenderCommand>()
        applicationOverlayHost.appendPortalOverlayCommands(ctx, 360, 240, commands)
        val layout = DomainPortalServices.applicationColorPickerEngine.debugBodyLayout(owner)
        assertNotNull(layout)

        val fixture =
            LiveLayerInputFixture(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { _, _, _ -> false },
                applicationOverlayHandler = { x, y, button ->
                    applicationOverlayHost.handlePortalPointerBeforeDom(x, y, 0, button, true)
                },
            )
        var appRootReceived = false
        val consumedBy =
            fixture.dispatchMouseDown(
                layout.colorFieldRect.x + 4,
                layout.colorFieldRect.y + 4,
                MouseButton.LEFT,
            ) {
                appRootReceived = true
                true
            }

        assertTrue(commands.isNotEmpty())
        assertEquals(ScreenDomainSurfaces.ApplicationPortal, consumedBy)
        assertFalse(appRootReceived)
        assertTrue(applicationOverlayHost.hasOpenColorPickerPortal())
    }

    @Test
    fun `application color picker portal preserves drag close and eyedropper capture hooks`() {
        ScreenColorSamplerBridge.install(ScreenColorSampler { x, y -> (0xFF shl 24) or (x shl 16) or (y shl 8) or 0x44 })
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(480, 320)
        val owner = "application-color-picker-drag-eyedropper"
        var committed: RgbaColor? = null
        DomainPortalServices.applicationColorPickerEngine.open(
            colorPickerRequest(owner, OverlayOwnerScope.Application) {
                committed = it
            },
        )
        applicationOverlayHost.syncPortalFrame(ctx, 480, 320, 1f, 120, 80)

        val panelBefore = DomainPortalServices.applicationColorPickerEngine.debugPanelRect(owner) ?: error("panel missing")
        val header = DomainPortalServices.applicationColorPickerEngine.debugHeaderRect(owner) ?: error("header missing")
        val dragStartX = header.x + 6
        val dragStartY = header.y + 6
        assertTrue(applicationOverlayHost.handlePortalPointerBeforeDom(dragStartX, dragStartY, 0, MouseButton.LEFT, true))
        assertTrue(
            applicationOverlayHost.handlePortalPointerBeforeDom(
                mouseX = dragStartX + 40,
                mouseY = dragStartY + 30,
                dWheel = 0,
                button = null,
                pressed = false,
            ),
        )
        assertTrue(
            applicationOverlayHost.handlePortalPointerBeforeDom(
                mouseX = dragStartX + 40,
                mouseY = dragStartY + 30,
                dWheel = 0,
                button = MouseButton.LEFT,
                pressed = false,
            ),
        )
        val panelAfter = DomainPortalServices.applicationColorPickerEngine.debugPanelRect(owner) ?: error("panel missing")
        assertNotEquals(panelBefore.x, panelAfter.x)

        val layout = DomainPortalServices.applicationColorPickerEngine.debugBodyLayout(owner) ?: error("layout missing")
        assertTrue(
            applicationOverlayHost.handlePortalPointerBeforeDom(
                mouseX = layout.pipetteRect.x + 2,
                mouseY = layout.pipetteRect.y + 2,
                dWheel = 0,
                button = MouseButton.LEFT,
                pressed = true,
            ),
        )
        assertTrue(applicationOverlayHost.hasActiveColorPickerEyedropper())
        assertTrue(applicationOverlayHost.handlePortalPointerBeforeDom(25, 52, 0, null, false))
        applicationOverlayHost.captureColorPickerEyedropperSample()
        val firstExpected = RgbaColor.fromArgbInt((0xFF shl 24) or (25 shl 16) or (52 shl 8) or 0x44)
        assertEquals(
            firstExpected.toArgbInt(),
            DomainPortalServices.applicationColorPickerEngine
                .debugController(owner)
                ?.snapshot()
                ?.color
                ?.toArgbInt(),
        )

        assertTrue(applicationOverlayHost.handlePortalPointerBeforeDom(31, 64, 0, null, false))
        applicationOverlayHost.captureColorPickerEyedropperSample()
        assertTrue(applicationOverlayHost.handlePortalPointerBeforeDom(31, 64, 0, MouseButton.LEFT, true))
        assertTrue(applicationOverlayHost.handlePortalPointerBeforeDom(31, 64, 0, MouseButton.LEFT, false))
        val expected = RgbaColor.fromArgbInt((0xFF shl 24) or (31 shl 16) or (64 shl 8) or 0x44)
        assertEquals(expected.toArgbInt(), committed?.toArgbInt())

        val closeRect = DomainPortalServices.applicationColorPickerEngine.debugCloseRect(owner) ?: error("close missing")
        assertTrue(
            applicationOverlayHost.handlePortalPointerBeforeDom(
                mouseX = closeRect.x + 1,
                mouseY = closeRect.y + 1,
                dWheel = 0,
                button = MouseButton.LEFT,
                pressed = true,
            ),
        )
        assertFalse(applicationOverlayHost.hasOpenColorPickerPortal())
    }

    @Test
    fun `application color picker portal does not consume system owned popup`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(360, 240)
        val owner = "system-color-picker-owner"
        DomainPortalServices.applicationColorPickerEngine.open(colorPickerRequest(owner, OverlayOwnerScope.System))

        applicationOverlayHost.syncPortalFrame(ctx, 360, 240, 1f, 42, 48)
        val commands = ArrayList<RenderCommand>()
        applicationOverlayHost.appendPortalOverlayCommands(ctx, 360, 240, commands)
        val panel = DomainPortalServices.applicationColorPickerEngine.debugPanelRect(owner)
        assertNotNull(panel)

        assertTrue(DomainPortalServices.applicationColorPickerEngine.isOpenFor(owner))
        assertFalse(applicationOverlayHost.hasOpenColorPickerPortal())
        assertFalse(applicationOverlayHost.handlePortalPointerBeforeDom(panel.x + 2, panel.y + 2, 0, MouseButton.LEFT, true))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `system select is rendered and consumed through system portal path`() {
        val systemHost = SystemOverlayHost(InspectorController())
        systemHost.onInputFrame(320, 180)
        val owner = "system-select-portal"
        var selected: String? = null
        DomainPortalServices.openSelect(selectRequest(owner, OverlayOwnerScope.System) { selected = it })

        systemHost.syncPortalFrame(ctx, 320, 180, 1f)
        val commands = ArrayList<RenderCommand>()
        systemHost.appendPortalOverlayCommands(ctx, 320, 180, commands)
        val panel = DomainPortalServices.systemSelectEngine.debugPanelRect(owner)
        assertNotNull(panel)
        val style = DomainPortalServices.systemSelectEngine.currentStyle()

        val fixture =
            LiveLayerInputFixture(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { x, y, button ->
                    systemHost.handlePortalMouseDown(x, y, button)
                },
                applicationOverlayHandler = { _, _, _ -> false },
            )
        var appRootReceived = false
        val consumedBy =
            fixture.dispatchMouseDown(
                panel.x + style.panelPaddingX + 1,
                panel.y + style.panelPaddingY + 1,
                MouseButton.LEFT,
            ) {
                appRootReceived = true
                true
            }

        assertTrue(commands.isNotEmpty())
        assertEquals(ScreenDomainSurfaces.SystemPortal, consumedBy)
        assertFalse(appRootReceived)
        assertEquals(null, selected)
        assertTrue(
            systemHost.handlePortalMouseUp(
                panel.x + style.panelPaddingX + 1,
                panel.y + style.panelPaddingY + 1,
                MouseButton.LEFT,
            ),
        )
        assertEquals("a", selected)
        assertFalse(DomainPortalServices.applicationSelectEngine.isOpenFor(owner))
    }

    @Test
    fun `select owner migration preserves application system routing`() {
        val owner = "select-owner-migration"
        DomainPortalServices.openSelect(selectRequest(owner, OverlayOwnerScope.Application))
        assertTrue(DomainPortalServices.applicationSelectEngine.isOpenFor(owner))
        assertFalse(DomainPortalServices.systemSelectEngine.isOpenFor(owner))

        DomainPortalServices.openSelect(selectRequest(owner, OverlayOwnerScope.System))

        assertFalse(DomainPortalServices.applicationSelectEngine.isOpenFor(owner))
        assertTrue(DomainPortalServices.systemSelectEngine.isOpenFor(owner))
    }

    @Test
    fun `rendered system overlay content is reachable through same live interaction path`() {
        val systemHost = SystemOverlayHost(InspectorController())
        val root = inspectedRoot()
        systemHost.onInputFrame(1280, 720)
        systemHost.togglePanelDemo(anchorX = 260, anchorY = 200)
        systemHost.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 260,
            cursorY = 200,
            inspectorPointerCaptured = false,
        )
        systemHost.render(ctx, 1280, 720)

        val demoNode =
            systemHost.debugEntryNode(SystemOverlayEntryId.PanelDemo) as? SystemOverlayPanelDemoNode
                ?: error("panel demo node missing")
        val buttonRect = demoNode.buttonRect()
        assertNotNull(buttonRect)
        val fixture =
            LiveLayerInputFixture(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { x, y, button -> systemHost.handleMouseDown(x, y, button) },
                applicationOverlayHandler = { _, _, _ -> false },
            )
        var appRootReceived = false
        val consumedBy =
            fixture.dispatchMouseDown(buttonRect.x + 1, buttonRect.y + 1, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(ScreenDomainSurfaces.SystemPortal, consumedBy)
        assertFalse(appRootReceived)
    }

    private fun inspectedRoot(): ContainerNode {
        val root = ContainerNode(key = "root")
        root.bounds = Rect(0, 0, 1280, 720)
        ContainerNode(key = "child")
            .apply {
                bounds = Rect(20, 20, 120, 32)
            }.applyParent(root)
        return root
    }

    private fun selectRequest(
        owner: Any,
        ownerScope: OverlayOwnerScope,
        entries: List<SelectEntry> =
            listOf(
                SelectEntry.Option("a", labelProvider = { "Alpha" }),
                SelectEntry.Option("b", labelProvider = { "Beta" }),
            ),
        onSelect: ((String) -> Unit)? = null,
    ): SelectOpenRequest {
        val model =
            selectModel(id = "live-layer-select") {
                entries.forEach { entry ->
                    when (entry) {
                        is SelectEntry.Option -> option(entry.id, entry.labelProvider)
                        is SelectEntry.Group -> group(entry.labelProvider, entry.id) {}
                        is SelectEntry.Separator -> separator(entry.id)
                    }
                }
            }
        return SelectOpenRequest(
            owner = owner,
            modelToken = model.token,
            entries = entries,
            selectedId = null,
            anchorRect = Rect(24, 24, 100, 18),
            closeOnSelect = true,
            onSelect = onSelect,
            ownerScope = ownerScope,
        )
    }

    private fun colorPickerRequest(owner: Any, ownerScope: OverlayOwnerScope, onCommit: ((RgbaColor) -> Unit)? = null): ColorPickerPopupRequest =
        ColorPickerPopupRequest(
            owner = owner,
            ownerScope = ownerScope,
            anchorRect = Rect(32, 36, 24, 18),
            state = ColorPickerState(color = RgbaColor.WHITE, closeOnSelect = false),
            onCommit = onCommit,
        )

    private class LiveLayerInputFixture(
        private val debugHandler: (Int, Int, MouseButton) -> Boolean,
        private val systemOverlayHandler: (Int, Int, MouseButton) -> Boolean,
        private val applicationOverlayHandler: (Int, Int, MouseButton) -> Boolean,
        private val debugPortalHandler: (Int, Int, MouseButton) -> Boolean = { _, _, _ -> false },
        private val systemRootHandler: (Int, Int, MouseButton) -> Boolean = { _, _, _ -> false },
    ) {
        fun dispatchMouseDown(
            mouseX: Int,
            mouseY: Int,
            button: MouseButton,
            applicationRootHandler: () -> Boolean,
        ): ScreenDomainSurface? =
            ScreenDomainSurfaces.firstInputConsumer(
                canConsume = { surface ->
                    when (surface) {
                        ScreenDomainSurfaces.DebugPortal -> debugPortalHandler(mouseX, mouseY, button)
                        ScreenDomainSurfaces.DebugRoot -> debugHandler(mouseX, mouseY, button)
                        ScreenDomainSurfaces.SystemPortal -> systemOverlayHandler(mouseX, mouseY, button)
                        ScreenDomainSurfaces.SystemRoot -> systemRootHandler(mouseX, mouseY, button)
                        ScreenDomainSurfaces.ApplicationPortal -> applicationOverlayHandler(mouseX, mouseY, button)
                        ScreenDomainSurfaces.ApplicationRoot -> applicationRootHandler()
                        else -> false
                    }
                },
            )
    }

    private fun applicationOverlayRoot(host: ApplicationOverlayHost): DOMNode {
        val field = ApplicationOverlayHost::class.java.getDeclaredField("rootNode")
        field.isAccessible = true
        return field.get(host) as DOMNode
    }
}
