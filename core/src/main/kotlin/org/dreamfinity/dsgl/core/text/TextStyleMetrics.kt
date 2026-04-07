package org.dreamfinity.dsgl.core.text

object TextStyleMetrics {
    fun boldExtraPxForRange(
        plainText: String,
        spans: List<ParsedTextSpan>,
        baseFlags: TextStyleFlags,
        rangeStart: Int = 0,
        rangeEnd: Int = plainText.length
    ): Int {
        return 0
    }

    fun boldExtraPxForRangeInText(
        plainText: String,
        spans: List<ParsedTextSpan>,
        baseFlags: TextStyleFlags,
        rangeStart: Int = 0,
        rangeEnd: Int = plainText.length
    ): Int {
        return 0
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
