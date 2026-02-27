package org.dreamfinity.dsgl.core.text

object ObfuscationTextSelector {
    fun shouldObfuscateCodepoint(codepoint: Int): Boolean {
        return !TextStyleMetrics.isWhitespaceCodepoint(codepoint)
    }

    fun selectCandidateIndex(
        sourceKey: String,
        lineIndex: Int,
        glyphIndexInLine: Int,
        timeSlice: Long,
        originalCodepoint: Int,
        candidateCount: Int
    ): Int {
        if (candidateCount <= 0) return 0
        var seed = 17
        seed = 31 * seed + sourceKey.hashCode()
        seed = 31 * seed + lineIndex
        seed = 31 * seed + glyphIndexInLine
        seed = 31 * seed + (timeSlice and 0x7FFF_FFFFL).toInt()
        seed = 31 * seed + originalCodepoint
        val positive = seed and Int.MAX_VALUE
        return positive % candidateCount
    }
}
