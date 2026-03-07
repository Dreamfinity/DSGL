package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.dom.layout.Rect
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.event.KeyCodes
import org.dreamfinity.dsgl.core.event.MouseButton
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

class SelectEngineTests {
    private class FakeClock(
        var now: Long = 0L
    ) : SelectClock {
        override fun nowMs(): Long = now
        fun advance(ms: Long) {
            now += ms
        }
    }

    private val ctx = object : UiMeasureContext {
        override fun measureText(text: String): Int = text.length * 6
        override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 6
        override val fontHeight: Int = 9
        override fun fontHeight(fontId: String?, fontSize: Int?): Int = 9
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @Test
    fun `popup placement clamps and flips above when bottom space is not enough`() {
        val clock = FakeClock()
        val owner = "select.placement"
        val engine = SelectEngine(clock = clock)
        engine.setStyle(
            SelectStyle(
                openDurationMs = 1L,
                closeDurationMs = 1L,
                viewportPadding = 6
            )
        )
        val model = selectModel {
            repeat(16) { index ->
                option(id = "id.$index", label = "Option $index")
            }
        }
        val anchor = Rect(180, 96, 72, 18)
        engine.open(
            SelectOpenRequest(
                owner = owner,
                modelToken = model.token,
                entries = model.entries,
                selectedId = "id.0",
                anchorRect = anchor,
                closeOnSelect = true
            )
        )
        engine.onFrame(ctx, viewportWidth = 260, viewportHeight = 120, viewportScale = 1f)

        val panel = engine.debugPanelRect(owner)
        assertNotNull(panel)
        assertTrue(panel.width >= anchor.width)
        assertTrue(panel.x >= 6)
        assertTrue(panel.x + panel.width <= 260 - 6)
        assertTrue(panel.y <= anchor.y)
    }

    @Test
    fun `state machine supports outside click close and esc close`() {
        val clock = FakeClock()
        val owner = "select.state"
        val engine = SelectEngine(clock = clock)
        engine.setStyle(SelectStyle(openDurationMs = 1L, closeDurationMs = 10L))
        val model = selectModel {
            option("a", "A")
            option("b", "B")
        }
        engine.open(
            SelectOpenRequest(
                owner = owner,
                modelToken = model.token,
                entries = model.entries,
                selectedId = "a",
                anchorRect = Rect(30, 30, 90, 18),
                closeOnSelect = true
            )
        )
        engine.onFrame(ctx, 320, 180, 1f)
        clock.advance(2L)
        engine.onFrame(ctx, 320, 180, 1f)
        assertTrue(engine.isOpen())

        assertTrue(engine.handleMouseDown(4, 4, MouseButton.LEFT))
        assertTrue(engine.isOpen())
        clock.advance(11L)
        engine.onFrame(ctx, 320, 180, 1f)
        assertFalse(engine.isOpen())

        engine.open(
            SelectOpenRequest(
                owner = owner,
                modelToken = model.token,
                entries = model.entries,
                selectedId = "a",
                anchorRect = Rect(30, 30, 90, 18),
                closeOnSelect = true
            )
        )
        engine.onFrame(ctx, 320, 180, 1f)
        assertTrue(engine.handleKeyDown(KeyCodes.ESCAPE))
        clock.advance(11L)
        engine.onFrame(ctx, 320, 180, 1f)
        assertFalse(engine.isOpen())
    }

    @Test
    fun `keyboard navigation skips disabled options and enter selects`() {
        val clock = FakeClock()
        val owner = "select.keyboard"
        val engine = SelectEngine(clock = clock)
        engine.setStyle(SelectStyle(openDurationMs = 1L, closeDurationMs = 1L))
        val model = selectModel {
            option("a", "Alpha")
            option("b", "Beta") { enabled(false) }
            option("c", "Charlie")
        }
        var selected: String? = null
        engine.open(
            SelectOpenRequest(
                owner = owner,
                modelToken = model.token,
                entries = model.entries,
                selectedId = "a",
                anchorRect = Rect(24, 24, 96, 18),
                closeOnSelect = true,
                onSelect = { selected = it }
            )
        )
        engine.onFrame(ctx, 320, 180, 1f)

        assertTrue(engine.handleKeyDown(KeyCodes.DOWN))
        assertTrue(engine.handleKeyDown(KeyCodes.ENTER))
        assertEquals("c", selected)
        clock.advance(2L)
        engine.onFrame(ctx, 320, 180, 1f)
        assertFalse(engine.isOpen())
    }

    @Test
    fun `typeahead jumps to matching option`() {
        val clock = FakeClock()
        val owner = "select.typeahead"
        val engine = SelectEngine(clock = clock)
        engine.setStyle(SelectStyle(openDurationMs = 1L, closeDurationMs = 1L, typeAheadResetMs = 300L))
        val model = selectModel {
            option("apple", "Apple")
            option("banana", "Banana")
            option("blueberry", "Blueberry")
            option("cherry", "Cherry")
        }
        var selected: String? = null
        engine.open(
            SelectOpenRequest(
                owner = owner,
                modelToken = model.token,
                entries = model.entries,
                selectedId = null,
                anchorRect = Rect(10, 10, 90, 18),
                closeOnSelect = true,
                onSelect = { selected = it }
            )
        )
        engine.onFrame(ctx, 320, 180, 1f)

        assertTrue(engine.handleKeyDown(0, 'b'))
        assertTrue(engine.handleKeyDown(KeyCodes.ENTER))
        assertEquals("banana", selected)
    }

    @Test
    fun `animation progresses with fake clock for open and close`() {
        val clock = FakeClock()
        val owner = "select.animation"
        val engine = SelectEngine(clock = clock)
        engine.setStyle(SelectStyle(openDurationMs = 100L, closeDurationMs = 80L))
        val model = selectModel {
            option("one", "One")
            option("two", "Two")
        }
        engine.open(
            SelectOpenRequest(
                owner = owner,
                modelToken = model.token,
                entries = model.entries,
                selectedId = null,
                anchorRect = Rect(20, 20, 96, 18),
                closeOnSelect = true
            )
        )
        engine.onFrame(ctx, 320, 180, 1f)
        assertTrue(engine.snapshot().animationProgress <= 0.01f)

        clock.advance(50L)
        engine.onFrame(ctx, 320, 180, 1f)
        val half = engine.snapshot().animationProgress
        assertTrue(half > 0f && half < 1f)

        clock.advance(60L)
        engine.onFrame(ctx, 320, 180, 1f)
        assertEquals(1f, engine.snapshot().animationProgress)

        engine.close(owner)
        clock.advance(40L)
        engine.onFrame(ctx, 320, 180, 1f)
        val closing = engine.snapshot().animationProgress
        assertTrue(closing > 0f && closing < 1f)

        clock.advance(50L)
        engine.onFrame(ctx, 320, 180, 1f)
        assertFalse(engine.isOpen())
    }

    @Test
    fun `overlay consumes pointer before base dispatch when select is open`() {
        val owner = "select.overlay.order"
        val engine = SelectEngine()
        val model = selectModel {
            option("x", "X")
            option("y", "Y")
        }
        engine.open(
            SelectOpenRequest(
                owner = owner,
                modelToken = model.token,
                entries = model.entries,
                selectedId = "x",
                anchorRect = Rect(26, 26, 88, 18),
                closeOnSelect = true
            )
        )
        engine.onFrame(ctx, 320, 180, 1f)
        val panel = engine.debugPanelRect(owner)
        assertNotNull(panel)
        val consumed = engine.handleMouseDown(panel.x + 4, panel.y + 4, MouseButton.LEFT)
        assertTrue(consumed)
    }

    @Test
    fun `wheel scroll changes offset even when selected option stays near top`() {
        val owner = "select.wheel.scroll"
        val engine = SelectEngine()
        engine.setStyle(SelectStyle(openDurationMs = 1L, closeDurationMs = 1L, wheelStepRows = 1))
        val model = selectModel {
            repeat(40) { index ->
                option("id-$index", "Item $index")
            }
        }
        engine.open(
            SelectOpenRequest(
                owner = owner,
                modelToken = model.token,
                entries = model.entries,
                selectedId = "id-0",
                anchorRect = Rect(20, 20, 90, 18),
                closeOnSelect = true
            )
        )
        engine.onFrame(ctx, 320, 140, 1f)
        val panel = engine.debugPanelRect(owner)
        assertNotNull(panel)

        val before = engine.snapshot().scrollOffset
        assertTrue(engine.handleMouseWheel(panel.x + 8, panel.y + 8, delta = -120))
        val after = engine.snapshot().scrollOffset

        assertNotEquals(before, after)
        assertTrue(after > before)
    }

    @Test
    fun `scrollbar is rendered only when popup content overflows`() {
        val trackColor = 0x13572468
        val thumbColor = 0x24681357
        val overflowEngine = SelectEngine()
        overflowEngine.setStyle(
            SelectStyle(
                openDurationMs = 1L,
                closeDurationMs = 1L,
                maxPanelHeightPadding = 8,
                scrollbarTrackColor = trackColor,
                scrollbarThumbColor = thumbColor
            )
        )
        val overflowModel = selectModel {
            repeat(50) { index ->
                option("id-$index", "Option $index")
            }
        }
        overflowEngine.open(
            SelectOpenRequest(
                owner = "select.scrollbar.overflow",
                modelToken = overflowModel.token,
                entries = overflowModel.entries,
                selectedId = "id-0",
                anchorRect = Rect(18, 16, 100, 18),
                closeOnSelect = true
            )
        )
        val overflowCommands = mutableListOf<RenderCommand>()
        overflowEngine.appendOverlayCommands(ctx, 240, 120, overflowCommands)
        assertTrue(overflowCommands.any { it is RenderCommand.DrawRect && it.color == trackColor })
        assertTrue(overflowCommands.any { it is RenderCommand.DrawRect && it.color == thumbColor })
        val pushClips = overflowCommands.count { it is RenderCommand.PushClip }
        val popClips = overflowCommands.count { it is RenderCommand.PopClip }
        assertEquals(pushClips, popClips)

        val noOverflowEngine = SelectEngine()
        noOverflowEngine.setStyle(
            SelectStyle(
                openDurationMs = 1L,
                closeDurationMs = 1L,
                maxPanelHeightPadding = 8,
                scrollbarTrackColor = trackColor,
                scrollbarThumbColor = thumbColor
            )
        )
        val noOverflowModel = selectModel {
            option("a", "A")
            option("b", "B")
            option("c", "C")
        }
        noOverflowEngine.open(
            SelectOpenRequest(
                owner = "select.scrollbar.fit",
                modelToken = noOverflowModel.token,
                entries = noOverflowModel.entries,
                selectedId = "a",
                anchorRect = Rect(18, 16, 100, 18),
                closeOnSelect = true
            )
        )
        val noOverflowCommands = mutableListOf<RenderCommand>()
        noOverflowEngine.appendOverlayCommands(ctx, 240, 200, noOverflowCommands)
        assertFalse(noOverflowCommands.any { it is RenderCommand.DrawRect && it.color == trackColor })
        assertFalse(noOverflowCommands.any { it is RenderCommand.DrawRect && it.color == thumbColor })
        val pushClipsNoOverflow = noOverflowCommands.count { it is RenderCommand.PushClip }
        val popClipsNoOverflow = noOverflowCommands.count { it is RenderCommand.PopClip }
        assertEquals(pushClipsNoOverflow, popClipsNoOverflow)
    }

    @Test
    fun `dragging scrollbar updates scroll offset`() {
        val owner = "select.scrollbar.drag"
        val engine = SelectEngine()
        engine.setStyle(
            SelectStyle(
                openDurationMs = 1L,
                closeDurationMs = 1L,
                maxPanelHeightPadding = 8,
                scrollbarWidth = 8
            )
        )
        val model = selectModel {
            repeat(64) { index ->
                option("id-$index", "Option $index")
            }
        }
        engine.open(
            SelectOpenRequest(
                owner = owner,
                modelToken = model.token,
                entries = model.entries,
                selectedId = "id-0",
                anchorRect = Rect(20, 20, 120, 18),
                closeOnSelect = true
            )
        )
        engine.onFrame(ctx, 300, 140, 1f)
        val panel = engine.debugPanelRect(owner)
        assertNotNull(panel)
        val style = engine.currentStyle()
        val trackX = panel.x + panel.width - style.panelPaddingX - (style.scrollbarWidth / 2).coerceAtLeast(1)
        val trackTop = panel.y + style.panelPaddingY
        val trackBottom = panel.y + panel.height - style.panelPaddingY - 1

        val before = engine.snapshot().scrollOffset
        assertTrue(engine.handleMouseDown(trackX, trackTop + 2, MouseButton.LEFT))
        assertTrue(engine.handleMouseMove(trackX, trackBottom))
        val afterDrag = engine.snapshot().scrollOffset
        assertTrue(afterDrag > before)
        assertTrue(engine.handleMouseUp(trackX, trackBottom, MouseButton.LEFT))
    }
}
