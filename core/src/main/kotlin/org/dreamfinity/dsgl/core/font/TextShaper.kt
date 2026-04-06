package org.dreamfinity.dsgl.core.font

internal interface TextShaper {
    fun shapeText(
        text: String,
        fontId: FontId?,
        fontSize: Int?,
        formattingMode: String = "plain"
    ): ShapedText

    fun shapeTextRange(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        fontId: FontId?,
        fontSize: Int?,
        formattingMode: String = "plain"
    ): ShapedText

    fun measureText(
        text: String,
        fontId: FontId?,
        fontSize: Int?
    ): Int

    fun measureTextRange(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        fontId: FontId?,
        fontSize: Int?
    ): Int

    fun clearCaches()

    fun resetShapeCacheStats()

    fun shapeCacheStats(): FontRegistry.ShapeCacheStats

    fun resetTextHotPathStats()

    fun textHotPathStats(): FontRegistry.TextHotPathStats
}
