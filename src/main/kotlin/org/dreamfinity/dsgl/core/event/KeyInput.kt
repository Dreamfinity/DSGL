package org.dreamfinity.dsgl.core.event

object KeyInput {
    fun applyShift(ch: Char, shiftDown: Boolean): Char {
        if (!shiftDown) return ch
        if (ch in 'a'..'z') return ch.uppercaseChar()
        if (ch in 'A'..'Z') return ch
        return when (ch) {
            '1' -> '!'
            '2' -> '@'
            '3' -> '#'
            '4' -> '$'
            '5' -> '%'
            '6' -> '^'
            '7' -> '&'
            '8' -> '*'
            '9' -> '('
            '0' -> ')'
            '-' -> '_'
            '=' -> '+'
            '[' -> '{'
            ']' -> '}'
            ';' -> ':'
            '\'' -> '"'
            ',' -> '<'
            '.' -> '>'
            '/' -> '?'
            '`' -> '~'
            '\\' -> '|'
            else -> ch
        }
    }
}
