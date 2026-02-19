package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.DOMNode
import java.util.WeakHashMap

object StyleEngine {
    private data class CacheKey(
        val typeName: String,
        val nodeId: String?,
        val classesHash: Int,
        val inlineHash: Int,
        val hovered: Boolean,
        val active: Boolean,
        val focused: Boolean,
        val disabled: Boolean,
        val stylesheetVersion: Long,
        val themeVersion: Long,
        val defaultsHash: Int
    )

    private data class CachedStyle(
        val key: CacheKey,
        val style: ComputedStyle
    )

    private val cache: MutableMap<DOMNode, CachedStyle> = WeakHashMap()
    private val themeVariables: MutableMap<String, String> = linkedMapOf()
    private var themeVersion: Long = 0L

    fun setThemeVariables(values: Map<String, String>) {
        themeVariables.clear()
        themeVariables.putAll(values)
        themeVersion++
        cache.clear()
    }

    fun clearCache() {
        cache.clear()
    }

    fun setStylesDirectory(directory: java.io.File?) {
        StylesheetManager.setStylesDirectory(directory)
        cache.clear()
    }

    fun forceReloadStylesheets() {
        StylesheetManager.forceReload()
        cache.clear()
    }

    fun pollStylesheets() {
        StylesheetManager.pollForChanges()
    }

    fun applyStylesRecursively(root: DOMNode): Boolean {
        val snapshot = StylesheetManager.snapshot()
        return applyStylesRecursively(root, snapshot)
    }

    private fun applyStylesRecursively(root: DOMNode, snapshot: StylesheetSnapshot): Boolean {
        var layoutDirty = applyStyleToNode(root, snapshot)
        root.children.forEach { child ->
            layoutDirty = applyStylesRecursively(child, snapshot) || layoutDirty
        }
        return layoutDirty
    }

    private fun applyStyleToNode(node: DOMNode, snapshot: StylesheetSnapshot): Boolean {
        val defaults = node.captureStyleDefaults()
        val key = CacheKey(
            typeName = node.styleType,
            nodeId = node.styleId,
            classesHash = node.styleClasses.hashCode(),
            inlineHash = node.inlineStyleDecls.toStableHash(),
            hovered = node.styleHovered,
            active = node.styleActive,
            focused = node.styleFocused,
            disabled = node.styleDisabled,
            stylesheetVersion = snapshot.version,
            themeVersion = themeVersion,
            defaultsHash = defaults.hashCode()
        )

        val cached = cache[node]
        if (cached != null && cached.key == key) {
            return node.applyComputedStyle(cached.style)
        }

        val computed = computeStyle(
            node = node,
            defaults = defaults,
            snapshot = snapshot
        )
        cache[node] = CachedStyle(key = key, style = computed)
        return node.applyComputedStyle(computed)
    }

    private fun computeStyle(
        node: DOMNode,
        defaults: ComputedStyleDefaults,
        snapshot: StylesheetSnapshot
    ): ComputedStyle {
        val merged = StyleDecls()
        val candidates = gatherCandidates(node, snapshot.index)
            .filter { selectorMatches(node, it.selector) }
            .sortedWith(
                compareBy<StyleRule> { it.selector.precedenceBucket() }
                    .thenBy { it.sourceOrder }
            )

        candidates.forEach { merged.mergeFrom(it.declarations) }
        merged.mergeFrom(node.inlineStyleDecls)

        val variables = linkedMapOf<String, String>()
        variables.putAll(snapshot.rootVariables)
        variables.putAll(themeVariables)

        var result = defaults.toComputedStyle()
        merged.values.forEach { (property, expr) ->
            val applied = runCatching {
                applyProperty(result, property, expr, variables)
            }.onFailure { error ->
                println("[DSGL-Style] Failed to apply '${property.key}': ${error.message}")
            }.getOrNull()
            if (applied != null) {
                result = applied
            }
        }
        return result
    }

    private fun gatherCandidates(node: DOMNode, index: RuleIndex): List<StyleRule> {
        val out = ArrayList<StyleRule>(16)
        node.styleId?.let { id ->
            index.idIndex[id]?.let { out.addAll(it) }
        }
        index.typeIndex[node.styleType]?.let { out.addAll(it) }
        node.styleClasses.forEach { className ->
            index.classIndex[className]?.let { out.addAll(it) }
            val key = node.styleType + "|" + className
            index.typeClassIndex[key]?.let { out.addAll(it) }
        }
        return out
    }

    private fun selectorMatches(node: DOMNode, selector: StyleSelector): Boolean {
        if (selector.id != null && selector.id != node.styleId) return false
        if (selector.typeName != null && selector.typeName != node.styleType) return false
        if (selector.className != null && selector.className !in node.styleClasses) return false
        val pseudo = selector.pseudoState ?: return true
        return when (pseudo) {
            StylePseudoState.HOVER -> node.styleHovered && !node.styleDisabled
            StylePseudoState.ACTIVE -> node.styleActive && !node.styleDisabled
            StylePseudoState.FOCUS -> node.styleFocused && !node.styleDisabled
            StylePseudoState.DISABLED -> node.styleDisabled
        }
    }

    private fun applyProperty(
        current: ComputedStyle,
        property: StyleProperty,
        expression: StyleExpression,
        variables: Map<String, String>
    ): ComputedStyle {
        val literal = resolveExpressionToLiteral(expression, variables)
        return when (property) {
            StyleProperty.MARGIN -> current.copy(margin = parseSpacingShorthand(literal))
            StyleProperty.PADDING -> current.copy(padding = parseSpacingShorthand(literal))
            StyleProperty.BACKGROUND_COLOR -> current.copy(backgroundColor = parseColor(literal))
            StyleProperty.BACKGROUND_IMAGE -> current.copy(backgroundImage = parseStringLiteral(literal))
            StyleProperty.BORDER_COLOR -> current.copy(borderColor = parseColor(literal))
            StyleProperty.BORDER_WIDTH -> current.copy(borderWidth = parseIntLike(literal).coerceAtLeast(0))
            StyleProperty.BORDER_RADIUS -> current.copy(borderRadius = parseIntLike(literal).coerceAtLeast(0))
            StyleProperty.FOREGROUND_COLOR -> current.copy(foregroundColor = parseColor(literal))
            StyleProperty.FONT_SIZE -> current.copy(fontSize = parseIntLike(literal).coerceAtLeast(1))
            StyleProperty.WIDTH -> current.copy(width = parseIntLike(literal).coerceAtLeast(0))
            StyleProperty.HEIGHT -> current.copy(height = parseIntLike(literal).coerceAtLeast(0))
            StyleProperty.ALIGN -> current.copy(align = parseAlign(literal))
        }
    }
}
