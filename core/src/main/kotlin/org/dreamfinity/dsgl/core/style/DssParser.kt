package org.dreamfinity.dsgl.core.style

import java.io.File

class DssParseException(
    val path: String,
    val line: Int,
    val column: Int,
    message: String
) : RuntimeException("$path:$line:$column $message")

object DssParser {
    fun parse(file: File): StylesheetData {
        val text = file.readText()
        return parse(text, file.path)
    }

    fun parse(sourceText: String, sourceName: String = "<memory>"): StylesheetData {
        val text = stripBlockComments(sourceText)
        var index = 0
        var sourceOrder = 0
        val rules = mutableListOf<StyleRule>()
        val rootVars = linkedMapOf<String, String>()

        while (true) {
            index = skipWhitespace(text, index)
            if (index >= text.length) break

            val selectorStart = index
            while (index < text.length && text[index] != '{') {
                index++
            }
            if (index >= text.length) {
                throw parseError(sourceName, text, selectorStart, "Expected '{' after selector.")
            }

            val selectorText = text.substring(selectorStart, index).trim()
            if (selectorText.isEmpty()) {
                throw parseError(sourceName, text, selectorStart, "Selector cannot be empty.")
            }
            index++ // '{'

            val declarations = StyleDeclarations()
            index = parseDeclarations(
                sourceName = sourceName,
                text = text,
                fromIndex = index,
                declarations = declarations,
                rootVars = rootVars,
                allowVariables = selectorText == ":root"
            )

            if (selectorText != ":root") {
                val selector = try {
                    StyleSelector.parse(selectorText)
                } catch (ex: IllegalArgumentException) {
                    throw parseError(sourceName, text, selectorStart, ex.message ?: "Invalid selector.")
                }
                rules += StyleRule(
                    selector = selector,
                    declarations = declarations,
                    sourceOrder = sourceOrder++,
                    fileName = sourceName
                )
            }
        }

        return StylesheetData(
            rules = rules,
            rootVariables = rootVars,
            source = sourceName
        )
    }

    private fun parseDeclarations(
        sourceName: String,
        text: String,
        fromIndex: Int,
        declarations: StyleDeclarations,
        rootVars: MutableMap<String, String>,
        allowVariables: Boolean
    ): Int {
        var index = fromIndex
        while (index < text.length) {
            index = skipWhitespace(text, index)
            if (index >= text.length) {
                throw parseError(sourceName, text, index - 1, "Unclosed '{' block.")
            }
            if (text[index] == '}') {
                return index + 1
            }

            val nameStart = index
            while (index < text.length && text[index] != ':' && text[index] != '}') {
                index++
            }
            if (index >= text.length || text[index] == '}') {
                throw parseError(sourceName, text, nameStart, "Expected ':' in declaration.")
            }
            val rawName = text.substring(nameStart, index).trim()
            if (rawName.isEmpty()) {
                throw parseError(sourceName, text, nameStart, "Declaration name cannot be empty.")
            }
            index++ // ':'

            val valueStart = index
            while (index < text.length && text[index] != ';' && text[index] != '}') {
                index++
            }
            if (index > text.length) {
                throw parseError(sourceName, text, valueStart, "Expected declaration value.")
            }
            val rawValue = text.substring(valueStart, index).trim()
            if (rawValue.isEmpty()) {
                throw parseError(sourceName, text, valueStart, "Declaration value cannot be empty.")
            }

            if (rawName.startsWith("--")) {
                if (!allowVariables) {
                    throw parseError(
                        sourceName,
                        text,
                        nameStart,
                        "Variable declarations are only supported inside :root."
                    )
                }
                rootVars[rawName] = rawValue
            } else {
                val property = StyleProperty.fromKeyOrNull(rawName)
                    ?: throw parseError(
                        sourceName,
                        text,
                        nameStart,
                        "Unsupported style property '$rawName'."
                    )
                val expression = parseExpression(rawValue)
                if (expression is StyleExpression.Literal) {
                    try {
                        validateLiteralForProperty(property, expression.value)
                    } catch (ex: Exception) {
                        throw parseError(sourceName, text, valueStart, ex.message ?: "Invalid value.")
                    }
                }
                declarations.set(property, expression)
            }

            if (index < text.length && text[index] == ';') {
                index++
            } else if (index < text.length && text[index] == '}') {
                return index + 1
            }
        }
        throw parseError(sourceName, text, index - 1, "Unclosed '{' block.")
    }

    private fun skipWhitespace(text: String, fromIndex: Int): Int {
        var index = fromIndex
        while (index < text.length && text[index].isWhitespace()) {
            index++
        }
        return index
    }

    private fun parseError(path: String, source: String, index: Int, message: String): DssParseException {
        val safeIndex = index.coerceIn(0, source.length)
        var line = 1
        var col = 1
        for (i in 0 until safeIndex) {
            if (source[i] == '\n') {
                line++
                col = 1
            } else {
                col++
            }
        }
        return DssParseException(path, line, col, message)
    }

    private fun stripBlockComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            if (i + 1 < source.length && source[i] == '/' && source[i + 1] == '*') {
                i += 2
                while (i + 1 < source.length && !(source[i] == '*' && source[i + 1] == '/')) {
                    i++
                }
                i = (i + 2).coerceAtMost(source.length)
                continue
            }
            out.append(source[i])
            i++
        }
        return out.toString()
    }
}