package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.contextmenu.ContextMenuRuntime
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
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayEntryId
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayHost
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayPanelDemoNode
import org.dreamfinity.dsgl.core.render.RenderCommand
import org.dreamfinity.dsgl.core.select.SelectEntry
import org.dreamfinity.dsgl.core.select.SelectOpenRequest
import org.dreamfinity.dsgl.core.select.SelectRuntime
import org.dreamfinity.dsgl.core.select.selectModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveLayerInteractionPathTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanupContextMenuRuntime() {
        ContextMenuRuntime.engine.closeAll()
        SelectRuntime.host.closeAll()
    }

    @Test
    fun `runtime layer path resolves in debug system app-overlay app-root order`() {
        val callOrder = ArrayList<UiLayerId>(4)
        val harness =
            LiveLayerInputHarness(
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
                },
            )
        val consumedBy =
            harness.dispatchMouseDown(10, 10, MouseButton.LEFT) {
                callOrder += UiLayerId.ApplicationRoot
                true
            }

        assertEquals(UiLayerId.ApplicationRoot, consumedBy)
        assertEquals(
            listOf(UiLayerId.Debug, UiLayerId.SystemOverlay, UiLayerId.ApplicationOverlay, UiLayerId.ApplicationRoot),
            callOrder,
        )
    }

    @Test
    fun `debug layer consumption prevents lower-layer fallthrough`() {
        var systemReceived = false
        var appOverlayReceived = false
        var appRootReceived = false
        val harness =
            LiveLayerInputHarness(
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
            harness.dispatchMouseDown(12, 14, MouseButton.LEFT) {
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
        systemHost.syncFrame(
            root,
            inspectedLayoutRevision = 1L,
            cursorX = 244,
            cursorY = 186,
            inspectorPointerCaptured = false,
        )

        val entryState = systemHost.debugEntryState(SystemOverlayEntryId.PanelDemo) ?: error("panel demo state missing")
        val panelRect = entryState.panelState.currentRectOrNull() ?: error("panel demo rect missing")
        val harness =
            LiveLayerInputHarness(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { x, y, button -> systemHost.handleMouseDown(x, y, button) },
                applicationOverlayHandler = { _, _, _ -> false },
            )
        var appRootReceived = false
        val consumedBy =
            harness.dispatchMouseDown(panelRect.x + 20, panelRect.y + 70, MouseButton.LEFT) {
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

        val harness =
            LiveLayerInputHarness(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { x, y, button -> systemHost.handleMouseDown(x, y, button) },
                applicationOverlayHandler = { _, _, _ -> false },
            )

        var appRootReceivedOutside = false
        val consumedOutside =
            harness.dispatchMouseDown(outsideX, outsideY, MouseButton.LEFT) {
                appRootReceivedOutside = true
                true
            }
        assertEquals(UiLayerId.ApplicationRoot, consumedOutside)
        assertTrue(appRootReceivedOutside)
    }

    @Test
    fun `application overlay consumption prevents app-root fallthrough`() {
        val harness =
            LiveLayerInputHarness(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { _, _, _ -> false },
                applicationOverlayHandler = { _, _, _ -> true },
            )
        var appRootReceived = false
        val consumedBy =
            harness.dispatchMouseDown(24, 30, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(UiLayerId.ApplicationOverlay, consumedBy)
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

        val harness =
            LiveLayerInputHarness(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { _, _, _ -> false },
                applicationOverlayHandler = { x, y, button ->
                    applicationOverlayHost.handleMouseDown(x, y, button)
                },
            )
        var appRootReceived = false
        val consumedBy =
            harness.dispatchMouseDown(50, 50, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(UiLayerId.ApplicationOverlay, consumedBy)
        assertFalse(appRootReceived)
    }

    @Test
    fun `application context menu is rendered and consumed through application portal path`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 180)
        var actionHits = 0
        ContextMenuRuntime.host.openAtCursor(
            contextMenu(id = "portal.context") {
                item("Run") {
                    onClick { actionHits += 1 }
                }
            },
            x = 24,
            y = 24,
        )

        applicationOverlayHost.contextMenuOnFrame(ctx, 320, 180, 1f)
        val commands = ArrayList<RenderCommand>()
        applicationOverlayHost.appendContextMenuOverlayCommands(ctx, 320, 180, commands)
        val firstEntryRect = ContextMenuRuntime.engine.debugEntryRect(levelIndex = 0, entryIndex = 0)
        assertNotNull(firstEntryRect)

        val consumedByMenu =
            applicationOverlayHost.handleContextMenuMouseDown(
                mouseX = firstEntryRect.x + 1,
                mouseY = firstEntryRect.y + 1,
                button = MouseButton.LEFT,
            )

        assertTrue(commands.isNotEmpty())
        assertTrue(consumedByMenu)
        assertEquals(1, actionHits)
        assertFalse(applicationOverlayHost.isContextMenuOpen())
    }

    @Test
    fun `application context menu portal blocks app-root fallthrough on outside dismiss`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 180)
        ContextMenuRuntime.host.openAtCursor(
            contextMenu(id = "portal.dismiss") {
                item("Run")
                item("Build")
            },
            x = 24,
            y = 24,
        )
        applicationOverlayHost.contextMenuOnFrame(ctx, 320, 180, 1f)
        val panel = ContextMenuRuntime.engine.debugPanelRect(0)
        assertNotNull(panel)
        val outsideX = panel.x + panel.width + 24
        val outsideY = panel.y + panel.height + 24

        val harness =
            LiveLayerInputHarness(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { _, _, _ -> false },
                applicationOverlayHandler = { x, y, button ->
                    applicationOverlayHost.handleContextMenuMouseDown(x, y, button)
                },
            )
        var appRootReceived = false
        val consumedBy =
            harness.dispatchMouseDown(outsideX, outsideY, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(UiLayerId.ApplicationOverlay, consumedBy)
        assertFalse(appRootReceived)
        assertFalse(applicationOverlayHost.isContextMenuOpen())
    }

    @Test
    fun `application context menu portal consumes wheel and escape while open`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 180)
        ContextMenuRuntime.host.openAtCursor(
            contextMenu(id = "portal.keyboard") {
                item("Run")
                item("Build")
            },
            x = 24,
            y = 24,
        )
        applicationOverlayHost.contextMenuOnFrame(ctx, 320, 180, 1f)

        assertTrue(applicationOverlayHost.handleContextMenuMouseWheel(26, 26, -120))
        assertTrue(applicationOverlayHost.handleContextMenuKeyDown(KeyCodes.ESCAPE))
        assertFalse(applicationOverlayHost.isContextMenuOpen())
    }

    @Test
    fun `application select is rendered and consumed through application portal path`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 180)
        var selected: String? = null
        val owner = "application-select-portal"
        SelectRuntime.host.open(selectRequest(owner, OverlayOwnerScope.Application) { selected = it })

        applicationOverlayHost.applicationSelectOnFrame(ctx, 320, 180, 1f)
        val commands = ArrayList<RenderCommand>()
        applicationOverlayHost.appendApplicationSelectOverlayCommands(ctx, 320, 180, commands)
        val panel = SelectRuntime.applicationEngine.debugPanelRect(owner)
        assertNotNull(panel)

        val style = SelectRuntime.applicationEngine.currentStyle()
        val consumed =
            applicationOverlayHost.handleApplicationSelectMouseDown(
                mouseX = panel.x + style.panelPaddingX + 1,
                mouseY = panel.y + style.panelPaddingY + 1,
                button = MouseButton.LEFT,
            )

        assertTrue(commands.isNotEmpty())
        assertTrue(consumed)
        assertEquals("a", selected)
    }

    @Test
    fun `application select portal blocks app-root fallthrough on outside dismiss`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 180)
        val owner = "application-select-dismiss"
        SelectRuntime.host.open(selectRequest(owner, OverlayOwnerScope.Application))
        applicationOverlayHost.applicationSelectOnFrame(ctx, 320, 180, 1f)
        val panel = SelectRuntime.applicationEngine.debugPanelRect(owner)
        assertNotNull(panel)
        val outsideX = panel.x + panel.width + 24
        val outsideY = panel.y + panel.height + 24
        val harness =
            LiveLayerInputHarness(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { _, _, _ -> false },
                applicationOverlayHandler = { x, y, button ->
                    applicationOverlayHost.handleApplicationSelectMouseDown(x, y, button)
                },
            )

        var appRootReceived = false
        val consumedBy =
            harness.dispatchMouseDown(outsideX, outsideY, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(UiLayerId.ApplicationOverlay, consumedBy)
        assertFalse(appRootReceived)
    }

    @Test
    fun `application select portal consumes wheel typeahead and escape`() {
        val applicationOverlayHost = ApplicationOverlayHost()
        applicationOverlayHost.onInputFrame(320, 120)
        val owner = "application-select-keyboard"
        var selected: String? = null
        SelectRuntime.host.open(
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
        applicationOverlayHost.applicationSelectOnFrame(ctx, 320, 120, 1f)
        val panel = SelectRuntime.applicationEngine.debugPanelRect(owner)
        assertNotNull(panel)

        assertTrue(applicationOverlayHost.handleApplicationSelectMouseWheel(panel.x + 2, panel.y + 2, -120))
        assertTrue(applicationOverlayHost.handleApplicationSelectKeyDown(0, 'd'))
        assertTrue(applicationOverlayHost.handleApplicationSelectKeyDown(KeyCodes.ENTER, Char.MIN_VALUE))
        assertEquals("d", selected)

        SelectRuntime.host.open(selectRequest(owner, OverlayOwnerScope.Application))
        applicationOverlayHost.applicationSelectOnFrame(ctx, 320, 120, 1f)
        assertTrue(applicationOverlayHost.handleApplicationSelectKeyDown(KeyCodes.ESCAPE, Char.MIN_VALUE))
    }

    @Test
    fun `system select is rendered and consumed through system portal path`() {
        val systemHost = SystemOverlayHost(InspectorController())
        systemHost.onInputFrame(320, 180)
        val owner = "system-select-portal"
        var selected: String? = null
        SelectRuntime.host.open(selectRequest(owner, OverlayOwnerScope.System) { selected = it })

        systemHost.systemSelectOnFrame(ctx, 320, 180, 1f)
        val commands = ArrayList<RenderCommand>()
        systemHost.appendSystemSelectOverlayCommands(ctx, 320, 180, commands)
        val panel = SelectRuntime.systemEngine.debugPanelRect(owner)
        assertNotNull(panel)
        val style = SelectRuntime.systemEngine.currentStyle()

        val harness =
            LiveLayerInputHarness(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { x, y, button ->
                    systemHost.handleSystemSelectMouseDown(x, y, button)
                },
                applicationOverlayHandler = { _, _, _ -> false },
            )
        var appRootReceived = false
        val consumedBy =
            harness.dispatchMouseDown(
                panel.x + style.panelPaddingX + 1,
                panel.y + style.panelPaddingY + 1,
                MouseButton.LEFT,
            ) {
                appRootReceived = true
                true
            }

        assertTrue(commands.isNotEmpty())
        assertEquals(UiLayerId.SystemOverlay, consumedBy)
        assertFalse(appRootReceived)
        assertEquals("a", selected)
        assertFalse(SelectRuntime.applicationEngine.isOpenFor(owner))
    }

    @Test
    fun `select owner migration preserves application system routing`() {
        val owner = "select-owner-migration"
        SelectRuntime.host.open(selectRequest(owner, OverlayOwnerScope.Application))
        assertTrue(SelectRuntime.applicationEngine.isOpenFor(owner))
        assertFalse(SelectRuntime.systemEngine.isOpenFor(owner))

        SelectRuntime.host.open(selectRequest(owner, OverlayOwnerScope.System))

        assertFalse(SelectRuntime.applicationEngine.isOpenFor(owner))
        assertTrue(SelectRuntime.systemEngine.isOpenFor(owner))
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
        val harness =
            LiveLayerInputHarness(
                debugHandler = { _, _, _ -> false },
                systemOverlayHandler = { x, y, button -> systemHost.handleMouseDown(x, y, button) },
                applicationOverlayHandler = { _, _, _ -> false },
            )
        var appRootReceived = false
        val consumedBy =
            harness.dispatchMouseDown(buttonRect.x + 1, buttonRect.y + 1, MouseButton.LEFT) {
                appRootReceived = true
                true
            }

        assertEquals(UiLayerId.SystemOverlay, consumedBy)
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

    private class LiveLayerInputHarness(
        private val debugHandler: (Int, Int, MouseButton) -> Boolean,
        private val systemOverlayHandler: (Int, Int, MouseButton) -> Boolean,
        private val applicationOverlayHandler: (Int, Int, MouseButton) -> Boolean,
    ) {
        fun dispatchMouseDown(
            mouseX: Int,
            mouseY: Int,
            button: MouseButton,
            applicationRootHandler: () -> Boolean,
        ): UiLayerId? =
            OverlayLayerContracts.firstInputConsumer(
                canConsume = { layer ->
                    when (layer) {
                        UiLayerId.Debug -> debugHandler(mouseX, mouseY, button)
                        UiLayerId.SystemOverlay -> systemOverlayHandler(mouseX, mouseY, button)
                        UiLayerId.ApplicationOverlay -> applicationOverlayHandler(mouseX, mouseY, button)
                        UiLayerId.ApplicationRoot -> applicationRootHandler()
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
