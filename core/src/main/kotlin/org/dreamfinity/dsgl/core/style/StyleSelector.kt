package org.dreamfinity.dsgl.core.style

enum class StyleCombinator {
    Descendant,
    Child,
    AdjacentSibling,
    GeneralSibling
}

data class StyleSpecificity(
    val idCount: Int = 0,
    val classLikeCount: Int = 0,
    val typeCount: Int = 0
) : Comparable<StyleSpecificity> {
    override fun compareTo(other: StyleSpecificity): Int {
        if (idCount != other.idCount) return idCount.compareTo(other.idCount)
        if (classLikeCount != other.classLikeCount) return classLikeCount.compareTo(other.classLikeCount)
        return typeCount.compareTo(other.typeCount)
    }

    operator fun plus(other: StyleSpecificity): StyleSpecificity {
        return StyleSpecificity(
            idCount = idCount + other.idCount,
            classLikeCount = classLikeCount + other.classLikeCount,
            typeCount = typeCount + other.typeCount
        )
    }
}

data class StyleSelectorPart(
    val typeName: String? = null,
    val classes: Set<String> = emptySet(),
    val id: String? = null,
    val pseudoState: StylePseudoState? = null,
    val universal: Boolean = false
) {
    init {
        require(
            universal || typeName != null || id != null || classes.isNotEmpty() || pseudoState != null
        ) { "Selector part must not be empty." }
    }

    fun specificity(): StyleSpecificity {
        return StyleSpecificity(
            idCount = if (id != null) 1 else 0,
            classLikeCount = classes.size + if (pseudoState != null) 1 else 0,
            typeCount = if (typeName != null) 1 else 0
        )
    }
}

data class StyleSelectorStep(
    val part: StyleSelectorPart,
    val combinatorToLeft: StyleCombinator? = null
)

data class StyleSelector(
    val steps: List<StyleSelectorStep>
) {
    init {
        require(steps.isNotEmpty()) { "Selector must contain at least one step." }
        require(steps.first().combinatorToLeft == null) { "First selector step cannot have combinator." }
        require(steps.drop(1).all { it.combinatorToLeft != null }) { "Selector chain is missing combinator." }
    }

    val specificity: StyleSpecificity = steps.fold(StyleSpecificity()) { acc, step -> acc + step.part.specificity() }
    val hasCombinators: Boolean = steps.size > 1

    val typeName: String?
        get() = if (steps.size == 1) steps.first().part.typeName else null
    val className: String?
        get() {
            if (steps.size != 1) return null
            val classes = steps.first().part.classes
            return if (classes.size == 1) classes.first() else null
        }
    val id: String?
        get() = if (steps.size == 1) steps.first().part.id else null
    val pseudoState: StylePseudoState?
        get() = if (steps.size == 1) steps.first().part.pseudoState else null

    fun rightMostPart(): StyleSelectorPart = steps.last().part

    companion object {
        private val identRegex = Regex("^[a-zA-Z][a-zA-Z0-9_-]*$")
        private val classRegex = Regex("^[a-zA-Z0-9_-]+$")
        private val idRegex = Regex("^[a-zA-Z0-9_-]+$")

        fun parse(rawSelector: String): StyleSelector {
            val trimmed = rawSelector.trim()
            require(trimmed.isNotEmpty()) { "Selector cannot be empty." }
            require(trimmed != ":root") { ":root is not a node selector." }

            val steps = ArrayList<StyleSelectorStep>(4)
            var index = 0
            var pendingCombinator: StyleCombinator? = null

            while (index < trimmed.length) {
                val startIndex = index
                while (index < trimmed.length && trimmed[index].isWhitespace()) {
                    index++
                }
                if (index >= trimmed.length) break
                val hadWhitespace = index > startIndex
                if (hadWhitespace && steps.isNotEmpty() && pendingCombinator == null) {
                    pendingCombinator = StyleCombinator.Descendant
                }

                if (trimmed[index] == '>' || trimmed[index] == '+' || trimmed[index] == '~') {
                    require(steps.isNotEmpty()) { "Selector cannot start with a combinator." }
                    pendingCombinator = when (trimmed[index]) {
                        '>' -> StyleCombinator.Child
                        '+' -> StyleCombinator.AdjacentSibling
                        '~' -> StyleCombinator.GeneralSibling
                        else -> error("Unsupported combinator")
                    }
                    index++
                    continue
                }

                val tokenStart = index
                while (
                    index < trimmed.length &&
                    !trimmed[index].isWhitespace() &&
                    trimmed[index] != '>' &&
                    trimmed[index] != '+' &&
                    trimmed[index] != '~'
                ) {
                    index++
                }
                val token = trimmed.substring(tokenStart, index)
                val step = StyleSelectorStep(
                    part = parsePartToken(token, rawSelector),
                    combinatorToLeft = if (steps.isEmpty()) null else pendingCombinator ?: StyleCombinator.Descendant
                )
                steps += step
                pendingCombinator = null
            }

            require(pendingCombinator == null) { "Selector '$rawSelector' cannot end with a combinator." }
            return StyleSelector(steps)
        }

        private fun parsePartToken(token: String, rawSelector: String): StyleSelectorPart {
            require(token.isNotBlank()) { "Invalid selector '$rawSelector'." }
            var index = 0
            var universal = false
            var typeName: String? = null
            var id: String? = null
            var pseudoState: StylePseudoState? = null
            val classes = linkedSetOf<String>()

            if (token[index] == '*') {
                universal = true
                index++
            } else if (token[index].isLetter()) {
                val typeStart = index
                while (index < token.length && (token[index].isLetterOrDigit() || token[index] == '_' || token[index] == '-')) {
                    index++
                }
                val typeCandidate = token.substring(typeStart, index)
                require(identRegex.matches(typeCandidate)) { "Invalid type selector '$rawSelector'." }
                typeName = typeCandidate.lowercase()
            }

            while (index < token.length) {
                when (val marker = token[index]) {
                    '.' -> {
                        index++
                        val classStart = index
                        while (index < token.length && (token[index].isLetterOrDigit() || token[index] == '_' || token[index] == '-')) {
                            index++
                        }
                        val classValue = token.substring(classStart, index)
                        require(classRegex.matches(classValue)) { "Invalid class selector '$rawSelector'." }
                        classes += classValue
                    }

                    '#' -> {
                        index++
                        val idStart = index
                        while (index < token.length && (token[index].isLetterOrDigit() || token[index] == '_' || token[index] == '-')) {
                            index++
                        }
                        val idValue = token.substring(idStart, index)
                        require(idRegex.matches(idValue)) { "Invalid id selector '$rawSelector'." }
                        id = idValue
                    }

                    ':' -> {
                        index++
                        val pseudoStart = index
                        while (index < token.length && token[index].isLetter()) {
                            index++
                        }
                        val parsedPseudo = when (val pseudoValue = token.substring(pseudoStart, index).lowercase()) {
                            "hover" -> StylePseudoState.HOVER
                            "active" -> StylePseudoState.ACTIVE
                            "focus" -> StylePseudoState.FOCUS
                            "disabled" -> StylePseudoState.DISABLED
                            "open" -> StylePseudoState.OPEN
                            else -> throw IllegalArgumentException("Unsupported pseudo-state '$pseudoValue' in '$rawSelector'.")
                        }
                        pseudoState = parsedPseudo
                    }

                    else -> throw IllegalArgumentException("Unexpected token '$marker' in selector '$rawSelector'.")
                }
            }

            return StyleSelectorPart(
                typeName = typeName,
                classes = classes,
                id = id,
                pseudoState = pseudoState,
                universal = universal
            )
        }
    }
}

data class StyleRule(
    val selector: StyleSelector,
    val declarations: StyleDeclarations,
    val sourceOrder: Int,
    val fileName: String
)

data class StylesheetData(
    val rules: List<StyleRule>,
    val rootVariables: Map<String, String>,
    val source: String,
    val warnings: List<String> = emptyList()
)
