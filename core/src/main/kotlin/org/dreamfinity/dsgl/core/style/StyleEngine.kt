package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.DOMNode
import java.util.*

object StyleEngine {
    private data class AnonymousInspectorTarget(val path: String)

    private data class CacheKey(
        val typeName: String,
        val nodeId: String?,
        val classesHash: Int,
        val inlineHash: Int,
        val inspectorHash: Int,
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
    private val inspectorOverrides: MutableMap<Any, StyleDeclarations> = linkedMapOf()
    private var themeVersion: Long = 0L

    fun inspectorOverrideTarget(node: DOMNode): Any {
        return node.key ?: AnonymousInspectorTarget(anonymousInspectorPath(node))
    }

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

    fun setInspectorOverride(nodeKey: Any, property: StyleProperty, expression: StyleExpression) {
        if (expression is StyleExpression.Literal) {
            validateLiteralForProperty(property, expression.value)
        }
        val perNode = inspectorOverrides.getOrPut(nodeKey) { StyleDeclarations() }
        perNode.set(property, expression)
        cache.clear()
    }

    fun setInspectorOverride(node: DOMNode, property: StyleProperty, expression: StyleExpression) {
        setInspectorOverride(inspectorOverrideTarget(node), property, expression)
    }

    fun setInspectorOverrideLiteral(nodeKey: Any, property: StyleProperty, literal: String): Result<Unit> {
        return runCatching {
            validateLiteralForProperty(property, literal)
            setInspectorOverride(nodeKey, property, StyleExpression.Literal(literal))
        }
    }

    fun setInspectorOverrideLiteral(node: DOMNode, property: StyleProperty, literal: String): Result<Unit> {
        return setInspectorOverrideLiteral(inspectorOverrideTarget(node), property, literal)
    }

    fun clearInspectorOverride(nodeKey: Any, property: StyleProperty? = null) {
        val existing = inspectorOverrides[nodeKey] ?: return
        if (property == null) {
            inspectorOverrides.remove(nodeKey)
        } else {
            existing.values.remove(property)
            if (existing.values.isEmpty()) {
                inspectorOverrides.remove(nodeKey)
            }
        }
        cache.clear()
    }

    fun clearInspectorOverride(node: DOMNode, property: StyleProperty? = null) {
        clearInspectorOverride(inspectorOverrideTarget(node), property)
    }

    fun clearAllInspectorOverrides() {
        if (inspectorOverrides.isEmpty()) return
        inspectorOverrides.clear()
        cache.clear()
    }

    fun inspectorOverridesFor(nodeKey: Any?): StyleDeclarations? {
        if (nodeKey == null) return null
        return inspectorOverrides[nodeKey]?.let(::copyStyleDeclarations)
    }

    fun inspectorOverridesFor(node: DOMNode): StyleDeclarations? {
        return inspectorOverridesFor(inspectorOverrideTarget(node))
    }

    fun inspectorOverrideFor(nodeKey: Any?, property: StyleProperty): StyleExpression? {
        if (nodeKey == null) return null
        return inspectorOverrides[nodeKey]?.get(property)
    }

    fun inspectorOverrideFor(node: DOMNode, property: StyleProperty): StyleExpression? {
        return inspectorOverrideFor(inspectorOverrideTarget(node), property)
    }

    fun inspect(node: DOMNode): StyleInspection {
        val snapshot = StylesheetManager.snapshot()
        val defaults = node.captureStyleDefaults()
        val variables = resolvedVariables(snapshot)
        val candidates = matchingCandidates(node, snapshot.index)
        val merged = StyleDeclarations()
        val sources = linkedMapOf<StyleProperty, StylePropertySource>()
        val matchedRules = ArrayList<String>(candidates.size)

        StyleProperty.entries.forEach { property ->
            sources[property] = StylePropertySource(
                property = property,
                kind = StyleSourceKind.Default,
                source = "default"
            )
        }

        candidates.forEach { rule ->
            merged.mergeFrom(rule.declarations)
            rule.declarations.values.keys.forEach { property ->
                sources[property] = StylePropertySource(
                    property = property,
                    kind = StyleSourceKind.Selector,
                    source = "${selectorLabel(rule.selector)} @ ${rule.fileName}"
                )
            }
            matchedRules += "${selectorLabel(rule.selector)} @ ${rule.fileName}"
        }

        node.inlineStyleDeclarations.values.keys.forEach { property ->
            sources[property] = StylePropertySource(
                property = property,
                kind = StyleSourceKind.Inline,
                source = "inline"
            )
        }
        merged.mergeFrom(node.inlineStyleDeclarations)
        val inspector = inspectorOverrides[inspectorOverrideTarget(node)]
        inspector?.values?.keys?.forEach { property ->
            sources[property] = StylePropertySource(
                property = property,
                kind = StyleSourceKind.InspectorOverride,
                source = "inspector"
            )
        }
        if (inspector != null) {
            merged.mergeFrom(inspector)
        }

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

        return StyleInspection(
            computed = result,
            propertySources = sources.toMap(),
            matchedRules = matchedRules
        )
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
            inlineHash = node.inlineStyleDeclarations.toStableHash(),
            inspectorHash = inspectorOverrideHash(node),
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
        val merged = StyleDeclarations()
        val candidates = matchingCandidates(node, snapshot.index)

        candidates.forEach { merged.mergeFrom(it.declarations) }
        merged.mergeFrom(node.inlineStyleDeclarations)
        inspectorOverrides[inspectorOverrideTarget(node)]?.let { merged.mergeFrom(it) }

        val variables = resolvedVariables(snapshot)

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

    private fun matchingCandidates(node: DOMNode, index: RuleIndex): List<StyleRule> {
        return gatherCandidates(node, index)
            .filter { selectorMatches(node, it.selector) }
            .sortedWith(
                compareBy<StyleRule> { it.selector.precedenceBucket() }
                    .thenBy { it.sourceOrder }
            )
    }

    private fun resolvedVariables(snapshot: StylesheetSnapshot): Map<String, String> {
        val variables = linkedMapOf<String, String>()
        variables.putAll(snapshot.rootVariables)
        variables.putAll(themeVariables)
        return variables
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

    private fun selectorLabel(selector: StyleSelector): String {
        val base = when {
            selector.id != null -> "#${selector.id}"
            selector.typeName != null && selector.className != null -> "${selector.typeName}.${selector.className}"
            selector.className != null -> ".${selector.className}"
            selector.typeName != null -> selector.typeName
            else -> "*"
        }
        val pseudo = when (selector.pseudoState) {
            StylePseudoState.HOVER -> ":hover"
            StylePseudoState.ACTIVE -> ":active"
            StylePseudoState.FOCUS -> ":focus"
            StylePseudoState.DISABLED -> ":disabled"
            null -> ""
        }
        return base + pseudo
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
            StyleProperty.DISPLAY -> current.copy(display = parseDisplay(literal))
            StyleProperty.FLEX_DIRECTION -> current.copy(flexDirection = parseFlexDirection(literal))
            StyleProperty.JUSTIFY_CONTENT -> current.copy(justifyContent = parseJustifyContent(literal))
            StyleProperty.ALIGN_ITEMS -> current.copy(alignItems = parseAlignItems(literal))
            StyleProperty.JUSTIFY_ITEMS -> current.copy(justifyItems = parseJustifyItems(literal))
            StyleProperty.GAP -> current.copy(gap = parseIntLike(literal).coerceAtLeast(0))
            StyleProperty.FLEX_GROW -> current.copy(flexGrow = parseFloatLike(literal).coerceAtLeast(0f))
            StyleProperty.FLEX_SHRINK -> current.copy(flexShrink = parseFloatLike(literal).coerceAtLeast(0f))
            StyleProperty.FLEX_BASIS -> current.copy(flexBasis = parseOptionalInt(literal)?.coerceAtLeast(0))
            StyleProperty.GRID_COLUMNS -> current.copy(gridColumns = parseIntLike(literal).coerceAtLeast(1))
            StyleProperty.GRID_ROWS -> current.copy(gridRows = parseOptionalInt(literal)?.coerceAtLeast(1))
            StyleProperty.GRID_AUTO_FLOW -> current.copy(gridAutoFlow = parseGridAutoFlow(literal))
            StyleProperty.GRID_COLUMN_SPAN -> current.copy(gridColumnSpan = parseIntLike(literal).coerceAtLeast(1))
            StyleProperty.GRID_ROW_SPAN -> current.copy(gridRowSpan = parseIntLike(literal).coerceAtLeast(1))
            StyleProperty.TEXT_WRAP -> current.copy(textWrap = parseTextWrap(literal))
            StyleProperty.TRANSFORM -> current.copy(transform = parseTransform(literal))
            StyleProperty.TRANSFORM_ORIGIN -> current.copy(transformOrigin = parseTransformOrigin(literal))
            StyleProperty.OPACITY -> current.copy(opacity = parseOpacity(literal))
        }
    }

    private fun inspectorOverrideHash(node: DOMNode): Int {
        return inspectorOverrides[inspectorOverrideTarget(node)]?.toStableHash() ?: 0
    }

    private fun anonymousInspectorPath(node: DOMNode): String {
        val parts = ArrayList<String>(8)
        var current: DOMNode? = node
        while (current != null) {
            val parent = current.parent
            val index = parent?.children?.indexOf(current)?.coerceAtLeast(0) ?: 0
            parts += "${current.styleType}[$index]"
            current = parent
        }
        parts.reverse()
        return parts.joinToString(separator = "/")
    }

    private fun copyStyleDeclarations(value: StyleDeclarations): StyleDeclarations {
        val copy = StyleDeclarations()
        copy.values.putAll(value.values)
        return copy
    }
}
