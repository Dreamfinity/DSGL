package org.dreamfinity.dsgl.core.font

internal class DefaultFontMetricsService(
    private val catalog: FontCatalog
) : FontMetricsService {
    override fun resolveFontSize(fontSize: Int?): Int {
        return (fontSize ?: FontRegistry.DEFAULT_FONT_SIZE).coerceAtLeast(1)
    }

    override fun lineHeight(fontId: FontId?, fontSize: Int?): Int {
        val font = catalog.get(fontId) ?: return resolveFontSize(fontSize)
        return font.meta.lineHeightPx(resolveFontSize(fontSize))
    }
}
