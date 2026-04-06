package org.dreamfinity.dsgl.core.text

import org.dreamfinity.dsgl.core.dom.layout.FontLineMetrics
import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.render.RenderCommand
import kotlin.test.Test
import kotlin.test.assertEquals

class TextMetricsResolverTests {
    private val fallbackCtx = object : UiMeasureContext {
        override val fontHeight: Int = 10

        override fun measureText(text: String): Int = text.length * 6

        override fun fontHeight(fontId: String?, fontSize: Int?): Int {
            val size = (fontSize ?: 16).coerceAtLeast(1)
            return (size * 0.625f).toInt().coerceAtLeast(1)
        }

        override fun paint(commands: List<RenderCommand>) = Unit
    }

    private val nativeMetricsCtx = object : UiMeasureContext {
        override val fontHeight: Int = 10

        override fun measureText(text: String): Int = text.length * 6

        override fun fontHeight(fontId: String?, fontSize: Int?): Int {
            val size = (fontSize ?: 16).coerceAtLeast(1)
            return (size * 0.625f).toInt().coerceAtLeast(1)
        }

        override fun fontLineMetrics(fontId: String?, fontSize: Int?): FontLineMetrics {
            return FontLineMetrics(
                emSize = 1f,
                lineHeightEm = 0.9166667f,
                ascenderEm = 0.75f,
                descenderEm = -0.16666667f
            )
        }

        override fun paint(commands: List<RenderCommand>) = Unit
    }

    @Test
    fun `fallback path preserves normal line-height semantics`() {
        val metrics = TextMetricsResolver.resolve(
            ctx = fallbackCtx,
            fontId = null,
            fontSizePx = 16
        )

        assertEquals(16, metrics.fontSizePx)
        assertEquals(12, metrics.nativeLineHeightPx)
        assertEquals(12, metrics.lineHeightPx)
        assertEquals(0f, metrics.topLeadingPx)
        assertEquals(0f, metrics.bottomLeadingPx)
    }

    @Test
    fun `native line metrics drive line height ascender and descender`() {
        val metrics = TextMetricsResolver.resolve(
            ctx = nativeMetricsCtx,
            fontId = "minecraft",
            fontSizePx = 16
        )

        assertEquals(15, metrics.nativeLineHeightPx)
        assertEquals(15, metrics.lineHeightPx)
        assertEquals(12f, metrics.ascenderPx)
        assertEquals(2.6666667f, metrics.descenderPx)
    }

    @Test
    fun `explicit line height override preserves native metrics and adds leading`() {
        val metrics = TextMetricsResolver.resolve(
            ctx = nativeMetricsCtx,
            fontId = "minecraft",
            fontSizePx = 16,
            explicitLineHeightPx = 24
        )

        assertEquals(15, metrics.nativeLineHeightPx)
        assertEquals(24, metrics.lineHeightPx)
        assertEquals(4.5f, metrics.topLeadingPx)
        assertEquals(4.5f, metrics.bottomLeadingPx)
    }
}
