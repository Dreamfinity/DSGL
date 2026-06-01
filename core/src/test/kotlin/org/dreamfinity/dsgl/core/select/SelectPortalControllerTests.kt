package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.overlay.OverlayOwnerScope
import org.dreamfinity.dsgl.core.overlay.PortalDismissPolicy
import org.dreamfinity.dsgl.core.overlay.PortalInputPolicy
import org.dreamfinity.dsgl.core.overlay.PortalPointerRegion
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SelectPortalControllerTests {
    private class FakeClock(
        var now: Long = 0L,
    ) : SelectClock {
        override fun nowMs(): Long = now

        fun advance(ms: Long) {
            now += ms
        }
    }

    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 6

            override fun fontHeight(fontId: String?, fontSize: Int?): Int = 9

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @Test
    fun `select portal entry exposes a DOM node with generic portal policies`() {
        val fixture = openSelect()
        val commands = ArrayList<RenderCommand>()

        fixture.controller.appendCommands(ctx, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, commands)

        val panel = fixture.engine.debugPanelRect(fixture.owner)
        val debug = fixture.controller.debugPortalState(mouseX = 2, mouseY = 2)
        assertNotNull(panel)
        assertEquals(
            panel,
            debug.node.bounds,
        )
        assertEquals(
            PortalInputPolicy.DomOnly,
            debug.state.inputPolicy,
        )
        assertEquals(
            PortalDismissPolicy.EscapeOrOutsidePointerDown,
            debug.state.dismissPolicy,
        )
        assertTrue(debug.state.active)
        assertTrue(commands.isNotEmpty())
    }

    @Test
    fun `inside option release is handled through the portal DOM node`() {
        val fixture = openSelect()
        val panel = requireNotNull(fixture.engine.debugPanelRect(fixture.owner))
        val style = fixture.engine.currentStyle()
        val optionX = panel.x + style.panelPaddingX + style.rowPaddingX + 1
        val optionY = panel.y + style.panelPaddingY + 1

        assertTrue(fixture.controller.handleMouseDown(optionX, optionY, MouseButton.LEFT))
        assertEquals(null, fixture.selected)
        assertTrue(fixture.controller.handleMouseUp(optionX, optionY, MouseButton.LEFT))

        assertEquals("a", fixture.selected)
    }

    @Test
    fun `option press released outside does not select`() {
        val fixture = openSelect()
        val panel = requireNotNull(fixture.engine.debugPanelRect(fixture.owner))
        val style = fixture.engine.currentStyle()
        val optionX = panel.x + style.panelPaddingX + style.rowPaddingX + 1
        val optionY = panel.y + style.panelPaddingY + 1
        val outsideY = panel.y + panel.height + 8

        assertTrue(fixture.controller.handleMouseDown(optionX, optionY, MouseButton.LEFT))
        fixture.controller.handleMouseMove(optionX, outsideY)
        assertTrue(fixture.controller.handleMouseUp(optionX, outsideY, MouseButton.LEFT))

        assertEquals(null, fixture.selected)
        assertTrue(fixture.engine.isOpen())
    }

    @Test
    fun `outside press uses generic portal policy closes and passes through`() {
        val fixture = openSelect()
        val outsideX = 2
        val outsideY = 2

        val policy =
            fixture.controller
                .debugPortalState(outsideX, outsideY)
                .outsidePointerPolicy
        assertNotNull(policy)
        assertEquals(PortalPointerRegion.OutsideEntry, policy.region)
        assertTrue(policy.shouldClose)
        assertTrue(policy.consumed)
        assertFalse(fixture.controller.handleMouseDown(outsideX, outsideY, MouseButton.LEFT))

        fixture.clock.advance(CLOSE_DURATION_MS + 1)
        fixture.controller.onFrame(ctx, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, 1f)

        assertFalse(fixture.engine.isOpen())
    }

    @Test
    fun `wheel inside select portal DOM updates scroll offset`() {
        val fixture = openSelect(optionCount = 48)
        val panel = requireNotNull(fixture.engine.debugPanelRect(fixture.owner))
        val style = fixture.engine.currentStyle()
        val wheelX = panel.x + style.panelPaddingX + style.rowPaddingX + 1
        val wheelY = panel.y + style.panelPaddingY + 1
        val before =
            fixture.engine
                .snapshot()
                .scrollOffset

        assertTrue(fixture.controller.handleMouseWheel(wheelX, wheelY, delta = -120))

        assertTrue(
            fixture.engine
                .snapshot()
                .scrollOffset > before,
        )
    }

    @Test
    fun `scrollbar drag is handled inside select portal DOM without outside dismiss`() {
        val fixture = openSelect(optionCount = 48)
        val track = requireNotNull(fixture.engine.debugScrollbarTrackRect(fixture.owner))
        val downX = track.x + track.width / 2
        val downY = track.y + track.height / 2
        val before =
            fixture.engine
                .snapshot()
                .scrollOffset

        val policy =
            fixture.controller
                .debugPortalState(downX, downY)
                .outsidePointerPolicy
        assertNotNull(policy)
        assertEquals(PortalPointerRegion.InsideEntry, policy.region)
        assertFalse(policy.shouldClose)
        assertTrue(policy.consumed)
        assertTrue(fixture.controller.handleMouseDown(downX, downY, MouseButton.LEFT))
        assertTrue(fixture.engine.isOpen())

        assertTrue(fixture.controller.handleMouseMove(downX, downY + 24))
        assertTrue(
            fixture.engine
                .snapshot()
                .scrollOffset > before,
        )
        assertTrue(fixture.controller.handleMouseUp(downX, downY + 24, MouseButton.LEFT))
        assertTrue(fixture.engine.isOpen())
    }

    @Test
    fun `scrollbar drag remains captured when pointer leaves select portal bounds`() {
        val fixture = openSelect(optionCount = 48)
        val panel = requireNotNull(fixture.engine.debugPanelRect(fixture.owner))
        val track = requireNotNull(fixture.engine.debugScrollbarTrackRect(fixture.owner))
        val downX = track.x + track.width / 2
        val downY = track.y + 2
        val outsideY = panel.y + panel.height + 24
        val before =
            fixture.engine
                .snapshot()
                .scrollOffset

        assertTrue(fixture.controller.handleMouseDown(downX, downY, MouseButton.LEFT))
        assertTrue(fixture.engine.isOpen())
        assertTrue(fixture.engine.isScrollbarDragging())

        assertTrue(fixture.controller.handleMouseMove(downX, outsideY))
        assertTrue(
            fixture.engine
                .snapshot()
                .scrollOffset > before,
        )
        assertTrue(fixture.engine.isOpen())

        assertTrue(fixture.controller.handleMouseUp(downX, outsideY, MouseButton.LEFT))
        assertTrue(fixture.engine.isOpen())
        assertFalse(fixture.engine.isScrollbarDragging())
    }

    @Test
    fun `deactivating while scrollbar drag is captured does not recurse`() {
        val fixture = openSelect(optionCount = 48)
        val track = requireNotNull(fixture.engine.debugScrollbarTrackRect(fixture.owner))
        val downX = track.x + track.width / 2
        val downY = track.y + track.height / 2

        assertTrue(fixture.controller.handleMouseDown(downX, downY, MouseButton.LEFT))
        assertTrue(fixture.engine.isScrollbarDragging())

        fixture.engine.closeAll()
        fixture.controller.onFrame(ctx, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, 1f)

        assertFalse(fixture.engine.isOpen())
        assertFalse(
            fixture.controller
                .debugPortalState(downX, downY)
                .state
                .active,
        )
    }

    private fun openSelect(optionCount: Int = 2): SelectPortalFixture {
        val clock = FakeClock()
        val engine = SelectEngine(clock = clock)
        engine.setStyle(
            SelectStyle(
                openDurationMs = OPEN_DURATION_MS,
                closeDurationMs = CLOSE_DURATION_MS,
                maxPanelHeightPadding = 10,
            ),
        )
        val controller =
            SelectPortalController(
                engine = engine,
                ownerScope = OverlayOwnerScope.Application,
                entryId = "test.select",
            )
        val model =
            selectModel {
                repeat(optionCount) { index ->
                    option(('a'.code + index).toChar().toString(), "Option $index")
                }
            }
        var selected: String? = null
        val owner = "select.portal.test"
        engine.open(
            SelectOpenRequest(
                owner = owner,
                modelToken = model.token,
                entries = model.entries,
                selectedId = null,
                anchorRect = Rect(30, 30, 90, 18),
                closeOnSelect = true,
                onSelect = { selected = it },
            ),
        )
        clock.advance(OPEN_DURATION_MS + 1)
        controller.onFrame(ctx, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, 1f)
        return SelectPortalFixture(clock, engine, controller, owner) { selected }
    }

    private data class SelectPortalFixture(
        val clock: FakeClock,
        val engine: SelectEngine,
        val controller: SelectPortalController,
        val owner: Any,
        val selectedProvider: () -> String?,
    ) {
        val selected: String?
            get() = selectedProvider()
    }

    private companion object {
        const val VIEWPORT_WIDTH = 320
        const val VIEWPORT_HEIGHT = 180
        const val OPEN_DURATION_MS = 1L
        const val CLOSE_DURATION_MS = 1L
    }
}
