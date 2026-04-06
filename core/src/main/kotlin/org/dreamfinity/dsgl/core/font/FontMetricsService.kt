package org.dreamfinity.dsgl.core.font

internal interface FontMetricsService {
    fun resolveFontSize(fontSize: Int?): Int

    fun lineHeight(fontId: FontId?, fontSize: Int?): Int
}
