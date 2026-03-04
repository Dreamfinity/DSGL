package org.dreamfinity.dsgl.core.contextmenu

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

class ContextMenuEngineTests {
    private class FakeClock(
        var now: Long = 0L
    ) : ContextMenuClock {
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
    fun `stack supports open submenu switch esc pop and outside close`() {
        val clock = FakeClock()
        val style = ContextMenuStyle(
            hoverOpenDelayMs = 80L,
            submenuCloseDelayMs = 120L
        )
        val engine = ContextMenuEngine(clock = clock)
        engine.setStyle(style)
        val model = contextMenu(id = "state.machine") {
            submenu("More", id = "more") {
                item("A")
                item("B")
            }
            submenu("Tools", id = "tools") {
                item("Hammer")
            }
            item("Leaf", id = "leaf")
        }

        engine.openAtCursor(model, 20, 20)
        engine.onFrame(ctx, 320, 180, 1f)
        assertEquals(1, engine.snapshot().levelCount)

        val moreRect = requireEntryRect(engine, 0, 0)
        engine.handleMouseMove(centerX(moreRect), centerY(moreRect))
        clock.advance(style.hoverOpenDelayMs + 1L)
        engine.onFrame(ctx, 320, 180, 1f)
        assertEquals(2, engine.snapshot().levelCount)

        val toolsRect = requireEntryRect(engine, 0, 1)
        engine.handleMouseMove(centerX(toolsRect), centerY(toolsRect))
        clock.advance(style.hoverOpenDelayMs + 1L)
        engine.onFrame(ctx, 320, 180, 1f)
        assertEquals(2, engine.snapshot().levelCount)
        assertEquals(1, engine.snapshot().hoveredIndices.first())

        assertTrue(engine.handleKeyDown(KeyCodes.ESCAPE))
        assertEquals(1, engine.snapshot().levelCount)

        assertTrue(engine.handleKeyDown(KeyCodes.ESCAPE))
        assertFalse(engine.isOpen())

        engine.openAtCursor(model, 20, 20)
        engine.onFrame(ctx, 320, 180, 1f)
        assertTrue(engine.handleMouseDown(310, 170, MouseButton.LEFT))
        assertFalse(engine.isOpen())
    }

    @Test
    fun `overlay consumes pointer before base dispatch when menu is open`() {
        val clock = FakeClock()
        val engine = ContextMenuEngine(clock = clock)
        val model = contextMenu(id = "overlay.order") {
            item("Run")
            item("Build")
        }
        engine.openAtCursor(model, 24, 24)
        engine.onFrame(ctx, 320, 180, 1f)

        val firstRect = requireEntryRect(engine, 0, 0)
        var baseDispatchCalled = false
        val consumed = engine.handleMouseDown(centerX(firstRect), centerY(firstRect), MouseButton.LEFT)
        if (!consumed) {
            baseDispatchCalled = true
        }

        assertTrue(consumed)
        assertFalse(baseDispatchCalled)
    }

    @Test
    fun `keyboard navigation opens submenu and triggers leaf action`() {
        val clock = FakeClock()
        val engine = ContextMenuEngine(clock = clock)
        var actionHits = 0
        val model = contextMenu(id = "keyboard") {
            submenu("RootSub") {
                item("Leaf") {
                    onClick { actionHits += 1 }
                }
            }
        }

        engine.openAtCursor(model, 20, 20)
        engine.onFrame(ctx, 320, 180, 1f)

        assertTrue(engine.handleKeyDown(KeyCodes.DOWN))
        assertTrue(engine.handleKeyDown(KeyCodes.RIGHT))
        assertEquals(2, engine.snapshot().levelCount)
        assertTrue(engine.handleKeyDown(KeyCodes.DOWN))
        assertTrue(engine.handleKeyDown(KeyCodes.ENTER))
        assertEquals(1, actionHits)
        assertFalse(engine.isOpen())
    }

    private fun requireEntryRect(engine: ContextMenuEngine, level: Int, index: Int): Rect {
        val rect = engine.debugEntryRect(level, index)
        assertNotNull(rect)
        return rect
    }

    private fun centerX(rect: Rect): Int = rect.x + rect.width / 2
    private fun centerY(rect: Rect): Int = rect.y + rect.height / 2
}
