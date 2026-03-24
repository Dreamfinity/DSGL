package org.dreamfinity.dsgl.core.dom.layout

import org.dreamfinity.dsgl.core.render.RenderCommand

data class FontLineMetrics(
    val emSize: Float,
    val lineHeightEm: Float,
    val ascenderEm: Float,
    val descenderEm: Float
)

/**
 * Version-agnostic text measurement context. Implemented by platform adapters.
 */
interface UiMeasureContext {
    /** Font height in pixels for the active UI font. */
    val fontHeight: Int

    /** Measures text width in pixels. */
    fun measureText(text: String): Int

    /** Font height for a specific font/style combination. */
    fun fontHeight(fontId: String?, fontSize: Int?): Int = fontHeight

    /**
     * Optional native font line metrics. When unavailable, callers should fall back to fontHeight-based heuristics.
     */
    fun fontLineMetrics(fontId: String?, fontSize: Int?): FontLineMetrics? = null

    /** Measures text width for a specific font/style combination. */
    fun measureText(text: String, fontId: String?, fontSize: Int?): Int = measureText(text)

    /**
     * Measures a [startIndex, endIndexExclusive) range width inside [text] for a specific font/style combination.
     * Default implementation keeps compatibility by delegating through substring-based measurement.
     */
    fun measureTextRange(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        fontId: String?,
        fontSize: Int?
    ): Int {
        val safeStart = startIndex.coerceIn(0, text.length)
        val safeEnd = endIndexExclusive.coerceIn(safeStart, text.length)
        if (safeEnd <= safeStart) return 0
        return measureText(text.substring(safeStart, safeEnd), fontId, fontSize)
    }

    /** Executes render commands on the host. */
    fun paint(commands: List<RenderCommand>)
}
