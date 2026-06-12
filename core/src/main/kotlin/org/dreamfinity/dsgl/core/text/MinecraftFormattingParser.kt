package org.dreamfinity.dsgl.core.text

import org.dreamfinity.dsgl.core.style.TextFormatting

data class TextFormattingFlags(
    val obfuscated: Boolean = false,
    val bold: Boolean = false,
    val strikethrough: Boolean = false,
    val underline: Boolean = false,
    val italic: Boolean = false,
) {
    fun withLegacyCode(code: Char): TextFormattingFlags =
        when (code.lowercaseChar()) {
            'k' -> copy(obfuscated = true)
            'l' -> copy(bold = true)
            'm' -> copy(strikethrough = true)
            'n' -> copy(underline = true)
            'o' -> copy(italic = true)
            else -> this
        }

    companion object {
        val NONE: TextFormattingFlags = TextFormattingFlags()
    }
}

data class ParsedTextSpan(
    val start: Int,
    val end: Int,
    val colorRgb: Int?,
    val flags: TextFormattingFlags,
)

data class ParsedText(
    val plainText: String,
    val spans: List<ParsedTextSpan>,
)

data class ResolvedTextColorSpan(
    val start: Int,
    val end: Int,
    val color: Int,
)

data class TextStyleFlags(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val obfuscated: Boolean = false,
) {
    companion object {
        val NONE: TextStyleFlags = TextStyleFlags()
    }
}

data class ResolvedTextStyleSpan(
    val start: Int,
    val end: Int,
    val color: Int,
    val flags: TextStyleFlags,
)

object MinecraftLegacyColors {
    private val colorsByCode: Map<Char, Int> =
        mapOf(
            '0' to 0x000000,
            '1' to 0x0000AA,
            '2' to 0x00AA00,
            '3' to 0x00AAAA,
            '4' to 0xAA0000,
            '5' to 0xAA00AA,
            '6' to 0xFFAA00,
            '7' to 0xAAAAAA,
            '8' to 0x555555,
            '9' to 0x5555FF,
            'a' to 0x55FF55,
            'b' to 0x55FFFF,
            'c' to 0xFF5555,
            'd' to 0xFF55FF,
            'e' to 0xFFFF55,
            'f' to 0xFFFFFF,
        )

    fun rgb(code: Char): Int? = colorsByCode[code.lowercaseChar()]
}

object MinecraftFormattingParser {
    private data class CacheKey(
        val text: String,
        val mode: TextFormatting,
    )

    private data class SpanStyle(
        val colorRgb: Int? = null,
        val flags: TextFormattingFlags = TextFormattingFlags.NONE,
    )

    private const val PREFIX: Char = '\u00A7'
    private const val MAX_CACHE_SIZE: Int = 512
    private val cache: MutableMap<CacheKey, ParsedText> =
        object : LinkedHashMap<CacheKey, ParsedText>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, ParsedText>?): Boolean =
                size > MAX_CACHE_SIZE
        }

    fun clearCache() {
        synchronized(cache) {
            cache.clear()
        }
    }

    fun parse(text: String, mode: TextFormatting): ParsedText {
        if (text.isEmpty() || mode == TextFormatting.None || text.indexOf(PREFIX) < 0) {
            return ParsedText(plainText = text, spans = emptyList())
        }

        val key = CacheKey(text = text, mode = mode)
        synchronized(cache) {
            cache[key]?.let { return it }
        }

        val parsed =
            when (mode) {
                TextFormatting.None -> ParsedText(plainText = text, spans = emptyList())
                TextFormatting.Minecraft -> parseMinecraft(text)
            }

        synchronized(cache) {
            cache[key] = parsed
        }
        return parsed
    }

    fun resolveColorSpans(
        parsed: ParsedText,
        baseColor: Int,
        rangeStart: Int = 0,
        rangeEnd: Int = parsed.plainText.length,
    ): List<ResolvedTextColorSpan> {
        if (rangeEnd <= rangeStart || parsed.spans.isEmpty()) return emptyList()
        val safeStart = rangeStart.coerceIn(0, parsed.plainText.length)
        val safeEnd = rangeEnd.coerceIn(safeStart, parsed.plainText.length)
        if (safeEnd <= safeStart) return emptyList()

        val out = ArrayList<ResolvedTextColorSpan>(4)
        parsed.spans.forEach { span ->
            val start = maxOf(span.start, safeStart)
            val end = minOf(span.end, safeEnd)
            if (end <= start) return@forEach
            val rgb = span.colorRgb ?: return@forEach
            val alpha = (baseColor ushr 24) and 0xFF
            val argb = (alpha shl 24) or (rgb and 0x00FF_FFFF)
            out +=
                ResolvedTextColorSpan(
                    start = start - safeStart,
                    end = end - safeStart,
                    color = argb,
                )
        }
        return out
    }

    fun resolveStyleSpans(
        parsed: ParsedText,
        baseColor: Int,
        baseFlags: TextStyleFlags,
        rangeStart: Int = 0,
        rangeEnd: Int = parsed.plainText.length,
    ): List<ResolvedTextStyleSpan> {
        if (rangeEnd <= rangeStart) return emptyList()
        if (parsed.spans.isEmpty()) return emptyList()
        val safeStart = rangeStart.coerceIn(0, parsed.plainText.length)
        val safeEnd = rangeEnd.coerceIn(safeStart, parsed.plainText.length)
        if (safeEnd <= safeStart) return emptyList()

        val out = ArrayList<ResolvedTextStyleSpan>(4)
        parsed.spans.forEach { span ->
            val start = maxOf(span.start, safeStart)
            val end = minOf(span.end, safeEnd)
            if (end <= start) return@forEach
            val color =
                span.colorRgb?.let { rgb ->
                    val alpha = (baseColor ushr 24) and 0xFF
                    (alpha shl 24) or (rgb and 0x00FF_FFFF)
                } ?: baseColor
            val flags =
                TextStyleFlags(
                    bold = baseFlags.bold || span.flags.bold,
                    italic = baseFlags.italic || span.flags.italic,
                    underline = baseFlags.underline || span.flags.underline,
                    strikethrough = baseFlags.strikethrough || span.flags.strikethrough,
                    obfuscated = baseFlags.obfuscated || span.flags.obfuscated,
                )
            out +=
                ResolvedTextStyleSpan(
                    start = start - safeStart,
                    end = end - safeStart,
                    color = color,
                    flags = flags,
                )
        }
        return out
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun parseMinecraft(text: String): ParsedText {
        val plain = StringBuilder(text.length)
        val spans = ArrayList<ParsedTextSpan>(8)
        var style = SpanStyle()
        var segmentStart = 0
        var index = 0

        fun flush() {
            val end = plain.length
            if (end <= segmentStart) return
            spans +=
                ParsedTextSpan(
                    start = segmentStart,
                    end = end,
                    colorRgb = style.colorRgb,
                    flags = style.flags,
                )
            segmentStart = end
        }

        while (index < text.length) {
            val current = text[index]
            if (current != PREFIX) {
                plain.append(current)
                index += 1
                continue
            }

            if (index == text.lastIndex) {
                plain.append(PREFIX)
                index += 1
                continue
            }

            val code = text[index + 1]
            if (code == PREFIX) {
                plain.append(PREFIX)
                index += 2
                continue
            }

            if (code.equals('x', ignoreCase = true)) {
                val hexRgb = parseHexColor(text, index)
                if (hexRgb != null) {
                    flush()
                    style = SpanStyle(colorRgb = hexRgb, flags = TextFormattingFlags.NONE)
                    index += 14
                    segmentStart = plain.length
                    continue
                }
            }

            val lower = code.lowercaseChar()
            when {
                lower == 'r' -> {
                    flush()
                    style = SpanStyle()
                    index += 2
                    segmentStart = plain.length
                }

                MinecraftLegacyColors.rgb(lower) != null -> {
                    flush()
                    style =
                        SpanStyle(
                            colorRgb = MinecraftLegacyColors.rgb(lower),
                            flags = TextFormattingFlags.NONE,
                        )
                    index += 2
                    segmentStart = plain.length
                }

                lower in setOf('k', 'l', 'm', 'n', 'o') -> {
                    flush()
                    style = style.copy(flags = style.flags.withLegacyCode(lower))
                    index += 2
                    segmentStart = plain.length
                }

                else -> {
                    // Unknown formatting codes are treated literally to keep visible content deterministic.
                    plain.append(PREFIX)
                    plain.append(code)
                    index += 2
                }
            }
        }

        flush()
        val merged = mergeAdjacent(spans)
        if (merged.isEmpty()) {
            return ParsedText(plainText = plain.toString(), spans = emptyList())
        }
        if (merged.all { it.colorRgb == null && it.flags == TextFormattingFlags.NONE }) {
            return ParsedText(plainText = plain.toString(), spans = emptyList())
        }
        return ParsedText(plainText = plain.toString(), spans = merged)
    }

    private fun mergeAdjacent(spans: List<ParsedTextSpan>): List<ParsedTextSpan> {
        if (spans.isEmpty()) return emptyList()
        val out = ArrayList<ParsedTextSpan>(spans.size)
        var current = spans.first()
        for (index in 1 until spans.size) {
            val next = spans[index]
            val mergeable =
                current.end == next.start &&
                    current.colorRgb == next.colorRgb &&
                    current.flags == next.flags
            if (mergeable) {
                current = current.copy(end = next.end)
            } else {
                out += current
                current = next
            }
        }
        out += current
        return out
    }

    private fun parseHexColor(text: String, startIndex: Int): Int? {
        if (startIndex + 13 >= text.length) return null
        val buffer = CharArray(6)
        var offset = 0
        while (offset < 6) {
            val markerIndex = startIndex + 2 + offset * 2
            val digitIndex = markerIndex + 1
            if (text[markerIndex] != PREFIX) return null
            val digit = text[digitIndex]
            val normalized = digit.lowercaseChar()
            val isHex = normalized in '0'..'9' || normalized in 'a'..'f'
            if (!isHex) return null
            buffer[offset] = digit
            offset += 1
        }
        return buffer.concatToString().toIntOrNull(radix = 16)
    }
}
