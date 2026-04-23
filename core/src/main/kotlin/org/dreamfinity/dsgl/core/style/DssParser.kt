package org.dreamfinity.dsgl.core.style

import java.io.File

class DssParseException(
    val path: String,
    val line: Int,
    val column: Int,
    message: String,
) : RuntimeException("$path:$line:$column $message")

object DssParser {
    private val importantSuffixRegex = Regex("(?i)\\s*!important\\s*$")
    private const val DEPRECATED_FOREGROUND_COLOR_WARNING_KEY = "deprecated.property.foreground-color"
    private const val ROOT_SELECTOR_ALIAS = "dsgl-root"

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
        val warnings = ParseWarnings()

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
            index =
                parseDeclarations(
                    sourceName = sourceName,
                    text = text,
                    fromIndex = index,
                    declarations = declarations,
                    rootVars = rootVars,
                    allowVariables = selectorText == ":root",
                    warnings = warnings,
                )

            val selector =
                when (selectorText) {
                    ":root" -> {
                        if (declarations.values.isEmpty()) {
                            null
                        } else {
                            StyleSelector.parse(ROOT_SELECTOR_ALIAS)
                        }
                    }
                    else ->
                        try {
                            StyleSelector.parse(selectorText)
                        } catch (ex: IllegalArgumentException) {
                            throw parseError(sourceName, text, selectorStart, ex.message ?: "Invalid selector.")
                        }
                }
            if (selector != null) {
                rules +=
                    StyleRule(
                        selector = selector,
                        declarations = declarations,
                        sourceOrder = sourceOrder++,
                        fileName = sourceName,
                    )
            }
        }

        return StylesheetData(
            rules = rules,
            rootVariables = rootVars,
            source = sourceName,
            warnings = warnings.messages(),
        )
    }

    private fun parseDeclarations(
        sourceName: String,
        text: String,
        fromIndex: Int,
        declarations: StyleDeclarations,
        rootVars: MutableMap<String, String>,
        allowVariables: Boolean,
        warnings: ParseWarnings,
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
                        "Variable declarations are only supported inside :root.",
                    )
                }
                rootVars[rawName] = rawValue
            } else {
                val normalizedName = rawName.trim().lowercase()
                if (normalizedName == "foreground-color" || normalizedName == "foregroundcolor") {
                    warnings.warnOnce(
                        DEPRECATED_FOREGROUND_COLOR_WARNING_KEY,
                        "Property 'foreground-color' is deprecated; use 'color'.",
                    )
                }
                val property =
                    StyleProperty.fromKeyOrNull(rawName)
                        ?: throw parseError(
                            sourceName,
                            text,
                            nameStart,
                            "Unsupported style property '$rawName'.",
                        )
                val important = importantSuffixRegex.containsMatchIn(rawValue)
                val normalizedValue = if (important) importantSuffixRegex.replace(rawValue, "") else rawValue
                if (normalizedValue.isEmpty()) {
                    throw parseError(sourceName, text, valueStart, "Declaration value cannot be empty.")
                }
                val expression = parseExpression(normalizedValue)
                if (expression is StyleExpression.Literal) {
                    try {
                        validateLiteralForProperty(
                            property = property,
                            literal = expression.value,
                            warningReporter = warnings,
                        )
                    } catch (ex: Exception) {
                        throw parseError(sourceName, text, valueStart, ex.message ?: "Invalid value.")
                    }
                }
                declarations.set(property, expression, important = important)
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

    private fun parseError(
        path: String,
        source: String,
        index: Int,
        message: String,
    ): DssParseException {
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

    private class ParseWarnings : StyleWarningReporter {
        private val byKey: LinkedHashMap<String, String> = linkedMapOf()

        override fun warnOnce(key: String, message: String) {
            byKey.putIfAbsent(key, message)
        }

        fun messages(): List<String> = byKey.values.toList()
    }
}
