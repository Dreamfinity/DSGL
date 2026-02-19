package org.dreamfinity.dsgl.core.style

data class StyleSelector(
    val typeName: String? = null,
    val className: String? = null,
    val id: String? = null,
    val pseudoState: StylePseudoState? = null
) {
    init {
        val typeCount = listOfNotNull(typeName, className, id).size
        require(typeCount > 0) { "Selector must contain type, class, or id." }
    }

    fun precedenceBucket(): Int {
        return when {
            id != null -> 4
            typeName != null && className != null -> 3
            className != null -> 2
            typeName != null -> 1
            else -> 0
        }
    }

    companion object {
        private val identRegex = Regex("^[a-zA-Z][a-zA-Z0-9_-]*$")
        private val classRegex = Regex("^[a-zA-Z0-9_-]+$")
        private val idRegex = Regex("^[a-zA-Z0-9_-]+$")

        fun parse(rawSelector: String): StyleSelector {
            val trimmed = rawSelector.trim()
            require(trimmed.isNotEmpty()) { "Selector cannot be empty." }
            require(trimmed != ":root") { ":root is not a node selector." }

            val pseudo = parsePseudo(trimmed)
            val selectorCore = if (pseudo == null) {
                trimmed
            } else {
                trimmed.substring(0, trimmed.lastIndexOf(':')).trim()
            }

            require(selectorCore.isNotEmpty()) { "Invalid selector '$rawSelector'." }

            if (selectorCore.startsWith("#")) {
                val idValue = selectorCore.substring(1)
                require(idRegex.matches(idValue)) { "Invalid id selector '$rawSelector'." }
                return StyleSelector(id = idValue, pseudoState = pseudo)
            }
            if (selectorCore.startsWith(".")) {
                val classValue = selectorCore.substring(1)
                require(classRegex.matches(classValue)) { "Invalid class selector '$rawSelector'." }
                return StyleSelector(className = classValue, pseudoState = pseudo)
            }

            val dotIndex = selectorCore.indexOf('.')
            return if (dotIndex >= 0) {
                val typePart = selectorCore.substring(0, dotIndex).trim()
                val classPart = selectorCore.substring(dotIndex + 1).trim()
                require(typePart.isNotEmpty() && classPart.isNotEmpty()) {
                    "Invalid type+class selector '$rawSelector'."
                }
                require(identRegex.matches(typePart)) { "Invalid type selector '$rawSelector'." }
                require(classRegex.matches(classPart)) { "Invalid class selector '$rawSelector'." }
                StyleSelector(typeName = typePart.lowercase(), className = classPart, pseudoState = pseudo)
            } else {
                require(identRegex.matches(selectorCore)) { "Invalid type selector '$rawSelector'." }
                StyleSelector(typeName = selectorCore.lowercase(), pseudoState = pseudo)
            }
        }

        private fun parsePseudo(value: String): StylePseudoState? {
            val idx = value.lastIndexOf(':')
            if (idx <= 0 || idx >= value.length - 1) return null
            val suffix = value.substring(idx + 1).trim().lowercase()
            return when (suffix) {
                "hover" -> StylePseudoState.HOVER
                "active" -> StylePseudoState.ACTIVE
                "focus" -> StylePseudoState.FOCUS
                "disabled" -> StylePseudoState.DISABLED
                else -> null
            }
        }
    }
}

data class StyleRule(
    val selector: StyleSelector,
    val declarations: StyleDecls,
    val sourceOrder: Int,
    val fileName: String
)

data class StylesheetData(
    val rules: List<StyleRule>,
    val rootVariables: Map<String, String>,
    val source: String
)
