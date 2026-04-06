package org.dreamfinity.dsgl.core.text

import org.dreamfinity.dsgl.core.dom.layout.UiMeasureContext
import org.dreamfinity.dsgl.core.dom.text.ResolvedTextMetrics
import org.dreamfinity.dsgl.core.render.TextRenderMetrics
import kotlin.math.roundToInt

object TextMetricsResolver {
    private const val NORMAL_LINE_HEIGHT_MULTIPLIER: Float = 1.2f

    fun resolve(
        ctx: UiMeasureContext,
        fontId: String?,
        fontSizePx: Int,
        explicitLineHeightPx: Int? = null
    ): TextRenderMetrics {
        val resolvedFontSizePx = fontSizePx.coerceAtLeast(1)
        val nativeMetrics = resolveNativeFontMetrics(ctx, fontId, resolvedFontSizePx)
        val fallbackFontHeightPx = ctx.fontHeight(fontId, resolvedFontSizePx).coerceAtLeast(1)
        val fallbackNormalLineHeightPx = (fallbackFontHeightPx * NORMAL_LINE_HEIGHT_MULTIPLIER)
            .roundToInt()
            .coerceAtLeast(fallbackFontHeightPx)
            .coerceAtLeast(1)

        val nativeLineHeightPx = nativeMetrics?.lineHeightPx ?: fallbackNormalLineHeightPx
        val ascenderPx = nativeMetrics?.ascenderPx ?: (fallbackFontHeightPx * 0.8f)
        val descenderPx = nativeMetrics?.descenderPx
            ?: (nativeLineHeightPx - ascenderPx).coerceAtLeast(0f)
        val lineHeightPx = explicitLineHeightPx?.coerceAtLeast(1) ?: nativeLineHeightPx
        val extraLeadingPx = (lineHeightPx - nativeLineHeightPx).coerceAtLeast(0).toFloat()
        val topLeadingPx = extraLeadingPx / 2f
        val bottomLeadingPx = extraLeadingPx - topLeadingPx

        return TextRenderMetrics(
            fontSizePx = resolvedFontSizePx,
            lineHeightPx = lineHeightPx,
            nativeLineHeightPx = nativeLineHeightPx,
            ascenderPx = ascenderPx,
            descenderPx = descenderPx,
            topLeadingPx = topLeadingPx,
            bottomLeadingPx = bottomLeadingPx
        )
    }

    private fun resolveNativeFontMetrics(
        ctx: UiMeasureContext,
        fontId: String?,
        fontSizePx: Int
    ): NativeFontMetricsPx? {
        val metrics = ctx.fontLineMetrics(fontId, fontSizePx) ?: return null
        if (metrics.emSize <= 0f || metrics.lineHeightEm <= 0f) return null
        val scalePx = fontSizePx / metrics.emSize
        val lineHeightPx = kotlin.math.ceil(metrics.lineHeightEm * scalePx).toInt().coerceAtLeast(1)
        val ascenderPx = (metrics.ascenderEm * scalePx).coerceAtLeast(0f)
        val descenderPx = kotlin.math.abs(metrics.descenderEm * scalePx)
        return NativeFontMetricsPx(
            lineHeightPx = lineHeightPx,
            ascenderPx = ascenderPx,
            descenderPx = descenderPx
        )
    }

    private data class NativeFontMetricsPx(
        val lineHeightPx: Int,
        val ascenderPx: Float,
        val descenderPx: Float
    )
}

fun TextRenderMetrics.toResolvedTextMetrics(): ResolvedTextMetrics {
    return ResolvedTextMetrics(
        fontSizePx = fontSizePx,
        lineHeightPx = lineHeightPx,
        nativeLineHeightPx = nativeLineHeightPx,
        ascenderPx = ascenderPx,
        descenderPx = descenderPx,
        topLeadingPx = topLeadingPx,
        bottomLeadingPx = bottomLeadingPx
    )
}

fun ResolvedTextMetrics.toTextRenderMetrics(): TextRenderMetrics {
    return TextRenderMetrics(
        fontSizePx = fontSizePx,
        lineHeightPx = lineHeightPx,
        nativeLineHeightPx = nativeLineHeightPx,
        ascenderPx = ascenderPx,
        descenderPx = descenderPx,
        topLeadingPx = topLeadingPx,
        bottomLeadingPx = bottomLeadingPx
    )
}
