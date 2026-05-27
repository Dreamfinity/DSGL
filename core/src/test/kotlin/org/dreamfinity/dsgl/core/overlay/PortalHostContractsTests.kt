package org.dreamfinity.dsgl.core.overlay

import org.dreamfinity.dsgl.core.dom.DOMNode
import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.FocusManager
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.inspector.InspectorController
import org.dreamfinity.dsgl.core.overlay.system.SystemOverlayHost
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortalHostContractsTests {
    private val ctx =
        object : UiMeasureContext {
            override val fontHeight: Int = 9

            override fun measureText(text: String): Int = text.length * 6

            override fun paint(commands: List<RenderCommand>) = Unit
        }

    @AfterTest
    fun cleanup() {
        FocusManager.clearFocus()
    }

    @Test
    fun `multiple portal entries preserve deterministic paint and input order`() {
        val host = PortalHost(applicationSurface())
        val low = FakePortalEntry("low", FakePortalEntryConfig(order = PortalEntryOrder(zIndex = 10)))
        val high = FakePortalEntry("high", FakePortalEntryConfig(order = PortalEntryOrder(zIndex = 20)))
        val sameZLater =
            FakePortalEntry(
                "same-z-later",
                FakePortalEntryConfig(order = PortalEntryOrder(zIndex = 10, sequence = 1)),
            )
        host.register(high)
        host.register(sameZLater)
        host.register(low)

        low.activate()
        high.activate()
        sameZLater.activate()

        assertEquals(listOf("low", "same-z-later", "high"), host.entriesInPaintOrder().map { it.state.id.value })
        assertEquals(listOf("high", "same-z-later", "low"), host.entriesInInputOrder().map { it.state.id.value })
    }

    @Test
    fun `entry cleanup removes refs and active state`() {
        val host = PortalHost(applicationSurface())
        val entry = FakePortalEntry("entry")
        host.register(entry)
        entry.activate()

        assertTrue(host.unregister(entry.state.id))

        assertEquals(1, entry.clearRefsCalls)
        assertEquals(1, entry.closeCalls)
        assertFalse(entry.state.active)
        assertNull(entry.state.placement)
        assertTrue(host.entriesInPaintOrder().isEmpty())
        assertFalse(host.unregister(entry.state.id))
    }

    @Test
    fun `higher priority input consumes before lower priority entries`() {
        val host = PortalHost(applicationSurface())
        val low = FakePortalEntry("low", FakePortalEntryConfig(order = PortalEntryOrder(zIndex = 1)))
        val high =
            FakePortalEntry(
                id = "high",
                config =
                    FakePortalEntryConfig(
                        order = PortalEntryOrder(zIndex = 2),
                        consumeMouseDown = true,
                    ),
            )
        host.register(low)
        host.register(high)
        low.activate()
        high.activate()

        assertTrue(host.dispatchInput { it.handleMouseDown(24, 28, MouseButton.LEFT) })

        assertEquals(1, high.mouseDownCalls)
        assertEquals(0, low.mouseDownCalls)
    }

    @Test
    fun `input falls through portal entries until one consumes`() {
        val host = PortalHost(applicationSurface())
        val low =
            FakePortalEntry(
                id = "low",
                config =
                    FakePortalEntryConfig(
                        order = PortalEntryOrder(zIndex = 1),
                        consumeMouseDown = true,
                    ),
            )
        val high = FakePortalEntry("high", FakePortalEntryConfig(order = PortalEntryOrder(zIndex = 2)))
        host.register(low)
        host.register(high)
        low.activate()
        high.activate()

        assertTrue(host.dispatchInput { it.handleMouseDown(24, 28, MouseButton.LEFT) })

        assertEquals(1, high.mouseDownCalls)
        assertEquals(1, low.mouseDownCalls)
    }

    @Test
    fun `bounds and viewport are explicit and never default to hidden origin placement`() {
        val valid =
            PortalEntryPlacement(
                anchorBounds = Rect(12, 14, 20, 18),
                bounds =
                    PortalEntryBounds(
                        viewportBounds = Rect(0, 0, 320, 240),
                        entryBounds = Rect(18, 24, 120, 80),
                    ),
            )
        val entry = FakePortalEntry("entry")
        entry.state.activate(valid)

        assertEquals(valid, entry.state.placement)
        assertFailsWith<IllegalArgumentException> {
            PortalEntryBounds(
                viewportBounds = Rect(0, 0, 0, 240),
                entryBounds = Rect(18, 24, 120, 80),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PortalEntryBounds(
                viewportBounds = Rect(0, 0, 320, 240),
                entryBounds = Rect(0, 0, 0, 0),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PortalFrameContext(viewportBounds = Rect(0, 0, 0, 0))
        }
    }

    @Test
    fun `focus policy declares preserve request and trap without changing global focus by default`() {
        val preserve = FakePortalEntry("preserve", FakePortalEntryConfig(focusPolicy = PortalFocusPolicy.Preserve))
        val request = FakePortalEntry("request", FakePortalEntryConfig(focusPolicy = PortalFocusPolicy.RequestFocus))
        val trap = FakePortalEntry("trap", FakePortalEntryConfig(focusPolicy = PortalFocusPolicy.TrapFocus))

        preserve.activate()
        request.activate()
        trap.activate()

        assertEquals(PortalFocusPolicy.Preserve, preserve.state.focusPolicy)
        assertEquals(PortalFocusPolicy.RequestFocus, request.state.focusPolicy)
        assertEquals(PortalFocusPolicy.TrapFocus, trap.state.focusPolicy)
        assertNull(FocusManager.focusedNode())
    }

    @Test
    fun `domain surface adapter maps current application and system hosts to portal surfaces`() {
        assertEquals(
            ScreenDomainSurfaces.ApplicationPortal,
            DomainSurfacePortalHostAdapter(ApplicationOverlayHost()).surface,
        )
        assertEquals(
            ScreenDomainSurfaces.SystemPortal,
            DomainSurfacePortalHostAdapter(SystemOverlayHost(InspectorController())).surface,
        )
    }

    @Test
    fun `portal host rejects entries for another domain surface`() {
        val host = PortalHost(applicationSurface())
        val systemEntry =
            FakePortalEntry(
                id = "system",
                config =
                    FakePortalEntryConfig(
                        surface = ScreenDomainSurface(ScreenDomainId.System, ScreenDomainSurfaceRole.Portal),
                    ),
            )

        assertFailsWith<IllegalArgumentException> {
            host.register(systemEntry)
        }
    }

    @Test
    fun `render and paint use active entries in paint order`() {
        val host = PortalHost(applicationSurface())
        val renderOrder = ArrayList<String>()
        val low =
            FakePortalEntry(
                id = "low",
                config =
                    FakePortalEntryConfig(
                        order = PortalEntryOrder(zIndex = 1),
                        paintColor = 0xFF000001.toInt(),
                        renderOrder = renderOrder,
                    ),
            )
        val high =
            FakePortalEntry(
                id = "high",
                config =
                    FakePortalEntryConfig(
                        order = PortalEntryOrder(zIndex = 2),
                        paintColor = 0xFF000002.toInt(),
                        renderOrder = renderOrder,
                    ),
            )
        host.register(high)
        host.register(low)
        low.activate()
        high.activate()

        host.render(ctx, 320, 240)
        val commands = host.paint(ctx)

        assertEquals(listOf("low", "high"), renderOrder)
        assertEquals(
            listOf(0xFF000001.toInt(), 0xFF000002.toInt()),
            commands.map { (it as RenderCommand.DrawRect).color },
        )
    }

    private fun applicationSurface(): ScreenDomainSurface = ScreenDomainSurface(ScreenDomainId.Application, ScreenDomainSurfaceRole.Portal)

    private class FakePortalEntry(
        id: String,
        private val config: FakePortalEntryConfig = FakePortalEntryConfig(),
    ) : PortalEntry {
        override val state: PortalEntryState =
            PortalEntryState(
                id = PortalEntryId(id),
                ownerToken = config.ownerToken,
                surface = config.surface,
                order = config.order,
                focusPolicy = config.focusPolicy,
            )
        override val node: DOMNode? = null
        var clearRefsCalls: Int = 0
            private set
        var closeCalls: Int = 0
            private set
        var mouseDownCalls: Int = 0
            private set
        var renderCalls: Int = 0
            private set

        fun activate() {
            state.activate(
                PortalEntryPlacement(
                    anchorBounds = Rect(10, 10, 20, 20),
                    bounds =
                        PortalEntryBounds(
                            viewportBounds = Rect(0, 0, 320, 240),
                            entryBounds = Rect(12, 12, 100, 80),
                        ),
                ),
            )
        }

        override fun render(ctx: UiMeasureContext, width: Int, height: Int) {
            renderCalls += 1
            config.renderOrder?.add(state.id.value)
        }

        override fun paint(ctx: UiMeasureContext): List<RenderCommand> = listOf(RenderCommand.DrawRect(0, 0, 1, 1, config.paintColor))

        override fun clearRefs() {
            clearRefsCalls += 1
        }

        override fun close() {
            closeCalls += 1
            super.close()
        }

        override fun handleMouseDown(mouseX: Int, mouseY: Int, button: MouseButton): Boolean {
            mouseDownCalls += 1
            return config.consumeMouseDown
        }
    }

    private data class FakePortalEntryConfig(
        val ownerToken: Any = Any(),
        val surface: ScreenDomainSurface = ScreenDomainSurface(ScreenDomainId.Application, ScreenDomainSurfaceRole.Portal),
        val order: PortalEntryOrder = PortalEntryOrder(zIndex = 0),
        val focusPolicy: PortalFocusPolicy = PortalFocusPolicy.Preserve,
        val consumeMouseDown: Boolean = false,
        val paintColor: Int = 0xFFFFFFFF.toInt(),
        val renderOrder: MutableList<String>? = null,
    )
}
