package org.dreamfinity.dsgl.core.dom.text

/**
 * Authoritative resolved text metrics for one DOM node in one measure/render pass.
 *
 * This model is the single source of truth for text sizing in core:
 * width measurement, line-space reservation, and text rendering command payload.
 */
data class ResolvedTextMetrics(
    val fontSizePx: Int,
    val lineHeightPx: Int,
    val nativeLineHeightPx: Int,
    val ascenderPx: Float,
    val descenderPx: Float,
    val topLeadingPx: Float,
    val bottomLeadingPx: Float
)
