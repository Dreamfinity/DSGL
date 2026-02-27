package org.dreamfinity.dsgl.core.font

inline fun forEachCodepoint(text: CharSequence, action: (Int) -> Unit) {
    var index = 0
    while (index < text.length) {
        val codepoint = Character.codePointAt(text, index)
        action(codepoint)
        index += Character.charCount(codepoint)
    }
}

inline fun forEachCodepointIndexed(text: CharSequence, action: (codepoint: Int, startIndex: Int, endIndex: Int) -> Unit) {
    var index = 0
    while (index < text.length) {
        val start = index
        val codepoint = Character.codePointAt(text, index)
        index += Character.charCount(codepoint)
        action(codepoint, start, index)
    }
}

fun CharSequence.toCodepointList(): List<Int> {
    val out = ArrayList<Int>(length)
    forEachCodepoint(this) { codepoint ->
        out.add(codepoint)
    }
    return out
}
