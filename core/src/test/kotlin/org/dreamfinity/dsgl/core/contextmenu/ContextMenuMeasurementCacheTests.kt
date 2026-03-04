package org.dreamfinity.dsgl.core.contextmenu

import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ContextMenuMeasurementCacheTests {
    private val ctx = object : UiMeasureContext {
        override fun measureText(text: String): Int = text.length * 6
        override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 6
        override val fontHeight: Int = 9
        override fun fontHeight(fontId: String?, fontSize: Int?): Int = 9
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @Test
    fun `measurement reuses cached value for unchanged inputs`() {
        val cache = ContextMenuMeasurementCache()
        val model = contextMenu(id = "cache.same") {
            item("Copy")
            item("Paste")
        }
        val first = cache.measure(model.token, model.entries, ContextMenuStyle(), ctx, 1f)
        val second = cache.measure(model.token, model.entries, ContextMenuStyle(), ctx, 1f)

        assertSame(first, second)
        assertEquals(1L, cache.computeCount)
    }

    @Test
    fun `measurement recomputes when resolved content changes`() {
        val cache = ContextMenuMeasurementCache()
        var dynamicLabel = "Open"
        val model = contextMenu(id = "cache.dynamic") {
            item({ dynamicLabel })
        }

        val first = cache.measure(model.token, model.entries, ContextMenuStyle(), ctx, 1f)
        dynamicLabel = "Open in new window"
        val second = cache.measure(model.token, model.entries, ContextMenuStyle(), ctx, 1f)

        assertEquals(2L, cache.computeCount)
        assertEquals(first.panelWidth < second.panelWidth, true)
    }

    @Test
    fun `measurement recomputes when style hash changes`() {
        val cache = ContextMenuMeasurementCache()
        val model = contextMenu(id = "cache.style") {
            item("Run")
            item("Rename")
        }

        cache.measure(model.token, model.entries, ContextMenuStyle(), ctx, 1f)
        cache.measure(model.token, model.entries, ContextMenuStyle(minPanelWidth = 220), ctx, 1f)

        assertEquals(2L, cache.computeCount)
    }
}
