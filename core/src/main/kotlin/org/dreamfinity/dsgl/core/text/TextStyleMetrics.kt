package org.dreamfinity.dsgl.core.text

const val BOLD_ADVANCE_EXTRA_PX: Int = 1

object TextStyleMetrics {
    fun boldExtraPxForRange(
        plainText: String,
        spans: List<ParsedTextSpan>,
        baseFlags: TextStyleFlags,
        rangeStart: Int = 0,
        rangeEnd: Int = plainText.length
    ): Int {
        return boldExtraPxForRangeInText(
            plainText = plainText,
            spans = spans,
            baseFlags = baseFlags,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd
        )
    }

    fun boldExtraPxForRangeInText(
        plainText: String,
        spans: List<ParsedTextSpan>,
        baseFlags: TextStyleFlags,
        rangeStart: Int = 0,
        rangeEnd: Int = plainText.length
    ): Int {
        if (plainText.isEmpty()) return 0
        val safeStart = rangeStart.coerceIn(0, plainText.length)
        val safeEnd = rangeEnd.coerceIn(safeStart, plainText.length)
        if (safeEnd <= safeStart) return 0

        var spanIndex = 0
        var extra = 0
        var absoluteIndex = safeStart
        while (absoluteIndex < safeEnd) {
            val codepoint = Character.codePointAt(plainText, absoluteIndex)
            while (spanIndex < spans.size && absoluteIndex >= spans[spanIndex].end) {
                spanIndex += 1
            }
            val spanBold = if (spanIndex < spans.size) {
                val span = spans[spanIndex]
                absoluteIndex >= span.start && absoluteIndex < span.end && span.flags.bold
            } else {
                false
            }
            val bold = baseFlags.bold || spanBold
            if (bold && !isWhitespaceCodepoint(codepoint)) {
                extra += BOLD_ADVANCE_EXTRA_PX
            }
            absoluteIndex += Character.charCount(codepoint).coerceAtLeast(1)
        }
        return extra
    }

    fun isWhitespaceCodepoint(codepoint: Int): Boolean {
        return codepoint == ' '.code ||
                codepoint == '\n'.code ||
                codepoint == '\r'.code ||
                codepoint == '\t'.code ||
                codepoint == 0x00A0 ||
                Character.isWhitespace(codepoint)
    }
}
