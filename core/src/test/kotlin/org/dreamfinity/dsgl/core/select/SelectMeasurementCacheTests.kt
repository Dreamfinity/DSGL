package org.dreamfinity.dsgl.core.select

import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectMeasurementCacheTests {
    private val ctx = object : UiMeasureContext {
        override fun measureText(text: String): Int = text.length * 7
        override fun measureText(text: String, fontId: String?, fontSize: Int?): Int = text.length * 7
        override val fontHeight: Int = 9
        override fun fontHeight(fontId: String?, fontSize: Int?): Int = 9
        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @Test
    fun `measurement cache reuses computation when inputs unchanged`() {
        val cache = SelectMeasurementCache()
        val model = selectModel {
            option("a", "Alpha")
            option("b", "Beta")
        }
        val style = SelectStyle()

        val first = cache.measure(
            modelToken = model.token,
            entries = model.entries,
            style = style,
            ctx = ctx,
            dpiScale = 1f,
            fontId = null,
            fontSize = null
        )
        val second = cache.measure(
            modelToken = model.token,
            entries = model.entries,
            style = style,
            ctx = ctx,
            dpiScale = 1f,
            fontId = null,
            fontSize = null
        )

        assertEquals(1L, cache.computeCount)
        assertEquals(first.panelWidth, second.panelWidth)
    }

    @Test
    fun `measurement cache invalidates when style or options change`() {
        val cache = SelectMeasurementCache()
        val base = selectModel {
            option("a", "Alpha")
            option("b", "Beta")
        }
        val updated = selectModel {
            option("a", "Alpha")
            option("b", "Beta-Updated")
            option("c", "Charlie")
        }
        val styleA = SelectStyle(rowPaddingX = 6)
        val styleB = SelectStyle(rowPaddingX = 10)

        cache.measure(
            modelToken = base.token,
            entries = base.entries,
            style = styleA,
            ctx = ctx,
            dpiScale = 1f,
            fontId = null,
            fontSize = null
        )
        val afterBase = cache.computeCount
        cache.measure(
            modelToken = base.token,
            entries = base.entries,
            style = styleB,
            ctx = ctx,
            dpiScale = 1f,
            fontId = null,
            fontSize = null
        )
        val afterStyleChange = cache.computeCount
        cache.measure(
            modelToken = updated.token,
            entries = updated.entries,
            style = styleB,
            ctx = ctx,
            dpiScale = 1f,
            fontId = null,
            fontSize = null
        )
        val afterEntriesChange = cache.computeCount

        assertEquals(1L, afterBase)
        assertEquals(2L, afterStyleChange)
        assertEquals(3L, afterEntriesChange)
    }

    @Test
    fun `measurement includes group indentation and marker column in panel width`() {
        val cache = SelectMeasurementCache()
        val model = selectModel {
            group("Citrus") {
                option("orange", "Orange")
                option("lemon", "Lemon")
            }
        }
        val style = SelectStyle(groupIndentX = 14, markerColumnWidth = 12, markerGap = 8)

        val measurement = cache.measure(
            modelToken = model.token,
            entries = model.entries,
            style = style,
            ctx = ctx,
            dpiScale = 1f,
            fontId = null,
            fontSize = null
        )

        assertTrue(measurement.panelWidth >= style.minPanelWidth)
        assertTrue(measurement.panelWidth > 60)
    }
}
