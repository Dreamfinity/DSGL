package org.dreamfinity.dsgl.core.style

import org.dreamfinity.dsgl.core.dom.DOMNode
import java.util.Collections
import java.util.EnumMap
import java.util.WeakHashMap
import kotlin.math.roundToInt

object StyleEngine {
    private data class AnonymousInspectorTarget(val path: String)

    data class StyleApplyReport(
        val layoutDirty: Boolean,
        val visualDirty: Boolean,
        val visitedNodes: Int,
        val cacheHits: Int,
        val recomputedNodes: Int
    )

    private data class MutableApplyMetrics(
        var visitedNodes: Int = 0,
        var cacheHits: Int = 0,
        var recomputedNodes: Int = 0
    )

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
        val open: Boolean,
        val stylesheetVersion: Long,
        val themeVersion: Long,
        val defaultsHash: Int,
        val parentInheritedHash: Int,
        val ancestorSelectorHash: Int,
        val siblingSelectorHash: Int,
        val rootFontSizePx: Int,
        val viewportWidthPx: Int,
        val viewportHeightPx: Int,
        val scope: StyleApplicationScope
    )

    private data class CachedStyle(
        val key: CacheKey,
        val style: ComputedStyle
    )

    private enum class StyleOrigin(val precedence: Int) {
        Stylesheet(0),
        Inline(1),
        Inspector(2)
    }

    private data class CascadeWinner(
        val expression: StyleExpression,
        val important: Boolean,
        val specificity: StyleSpecificity,
        val sourceOrder: Int,
        val origin: StyleOrigin,
        val sourceKind: StyleSourceKind,
        val sourceLabel: String
    )

    private data class NodeApplyFlags(
        val layoutDirty: Boolean,
        val visualDirty: Boolean
    )

    private data class NodeApplyResult(
        val flags: NodeApplyFlags,
        val cacheHit: Boolean
    )

    private enum class StylePassMode {
        Full,
        Targeted
    }

    private val cache: MutableMap<DOMNode, CachedStyle> = WeakHashMap()
    private val themeVariables: MutableMap<String, String> = linkedMapOf()
    private val inspectorOverrides: MutableMap<Any, StyleDeclarations> = linkedMapOf()
    private val inspectorOverrideVersions: MutableMap<Any, Int> = linkedMapOf()
    private val anonymousTargetCache: MutableMap<DOMNode, Any> = WeakHashMap()
    private val pseudoDirtyNodes: MutableSet<DOMNode> =
        Collections.newSetFromMap(WeakHashMap<DOMNode, Boolean>())
    private val selectorDirtyNodes: MutableSet<DOMNode> =
        Collections.newSetFromMap(WeakHashMap<DOMNode, Boolean>())

    private var themeVersion: Long = 0L
    private var inspectorOverridesVersion: Long = 0L
    private var pseudoStateVersion: Long = 0L
    private var selectorStateVersion: Long = 0L
    private var pseudoDirtyGlobal: Boolean = false
    private var selectorDirtyGlobal: Boolean = false
    private var lastAppliedStylesheetVersion: Long = Long.MIN_VALUE
    private var lastAppliedThemeVersion: Long = Long.MIN_VALUE
    private var lastAppliedInspectorOverridesVersion: Long = Long.MIN_VALUE
    private var lastAppliedPseudoStateVersion: Long = Long.MIN_VALUE
    private var lastAppliedSelectorStateVersion: Long = Long.MIN_VALUE
    private var viewportWidthPx: Int = 0
    private var viewportHeightPx: Int = 0
    private var lastApplyReport: StyleApplyReport = StyleApplyReport(
        layoutDirty = false,
        visualDirty = false,
        visitedNodes = 0,
        cacheHits = 0,
        recomputedNodes = 0
    )
    private const val DEFAULT_ROOT_FONT_SIZE_PX = 16

    fun inspectorOverrideTarget(node: DOMNode): Any {
        node.key?.let { return it }
        val cached = anonymousTargetCache[node]
        if (cached != null) return cached
        val created = AnonymousInspectorTarget(anonymousInspectorPath(node))
        anonymousTargetCache[node] = created
        return created
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

    fun setViewportSize(width: Int, height: Int) {
        val normalizedWidth = width.coerceAtLeast(0)
        val normalizedHeight = height.coerceAtLeast(0)
        if (viewportWidthPx == normalizedWidth && viewportHeightPx == normalizedHeight) return
        viewportWidthPx = normalizedWidth
        viewportHeightPx = normalizedHeight
        cache.clear()
    }

    internal fun viewportWidthPx(): Int = viewportWidthPx

    internal fun viewportHeightPx(): Int = viewportHeightPx

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
        bumpInspectorOverrideVersion(nodeKey)
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
            existing.remove(property)
            if (existing.values.isEmpty()) {
                inspectorOverrides.remove(nodeKey)
            }
        }
        bumpInspectorOverrideVersion(nodeKey)
        cache.clear()
    }

    fun clearInspectorOverride(node: DOMNode, property: StyleProperty? = null) {
        clearInspectorOverride(inspectorOverrideTarget(node), property)
    }

    fun clearAllInspectorOverrides() {
        if (inspectorOverrides.isEmpty()) return
        inspectorOverrides.clear()
        inspectorOverrideVersions.clear()
        inspectorOverridesVersion += 1
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

    fun resolveInspectorExpression(expression: StyleExpression): Result<String> {
        return runCatching {
            val snapshot = StylesheetManager.snapshot()
            val variables = resolvedVariables(snapshot, StyleApplicationScope.Application)
            resolveExpressionToLiteral(expression, variables)
        }
    }

    fun resolveInspectorVariable(name: String): Result<String> {
        return resolveInspectorExpression(StyleExpression.VariableRef(name))
    }

    fun inspect(node: DOMNode): StyleInspection {
        val snapshot = StylesheetManager.snapshot()
        val defaults = node.captureStyleDefaults()
        val variables = resolvedVariables(snapshot, StyleApplicationScope.Application)
        val candidates = matchingCandidates(node, snapshot.index)
        val matchedRules = candidates.map { "${selectorLabel(it.selector)} @ ${it.fileName}" }
        val inspector = inspectorOverrides[inspectorOverrideTarget(node)]
        val parentComputed = node.parent?.appliedComputedStyleSnapshot()
        val winners = resolveCascadeWinners(
            node = node,
            candidates = candidates,
            inline = node.inlineStyleDeclarations,
            inspector = inspector
        )
        val rootFontSizePx = rootFontSizeFor(node)

        val sources = linkedMapOf<StyleProperty, StylePropertySource>()
        StyleProperty.entries.forEach { property ->
            sources[property] = StylePropertySource(
                property = property,
                kind = StyleSourceKind.Default,
                source = "default"
            )
        }

        var result = defaults.toComputedStyle()
        for (property in StyleProperty.entries) {
            val winner = winners[property]
            if (winner != null) {
                val applied = runCatching {
                    applyProperty(
                        current = result,
                        parentComputed = parentComputed,
                        property = property,
                        expression = winner.expression,
                        variables = variables,
                        rootFontSizePx = rootFontSizePx
                    )
                }.onFailure { error ->
                    println("[DSGL-Style] Failed to apply '${property.key}': ${error.message}")
                }.getOrNull()
                if (applied != null) {
                    result = applied
                }
                sources[property] = StylePropertySource(
                    property = property,
                    kind = winner.sourceKind,
                    source = winner.sourceLabel
                )
            } else if (StylePropertyRegistry.isInherited(property) && parentComputed != null) {
                result = inheritProperty(result, parentComputed, property)
                sources[property] = StylePropertySource(
                    property = property,
                    kind = StyleSourceKind.Inherited,
                    source = "inherited"
                )
            }
        }

        return StyleInspection(
            computed = result,
            propertySources = sources.toMap(),
            matchedRules = matchedRules
        )
    }

    fun applyStylesRecursively(
        root: DOMNode,
        scope: StyleApplicationScope = StyleApplicationScope.Application
    ): Boolean {
        return applyStylesRecursivelyDetailed(root, scope).layoutDirty
    }

    fun applyStylesRecursivelyDetailed(
        root: DOMNode,
        scope: StyleApplicationScope = StyleApplicationScope.Application
    ): StyleApplyReport {
        val snapshot = snapshotForScope(scope)
        val metrics = MutableApplyMetrics()
        val variables = resolvedVariables(snapshot, scope)
        val allowInspectorOverrides = scope == StyleApplicationScope.Application
        val result = if (scope == StyleApplicationScope.Application && shouldApplyTargetedPseudoPass(snapshot)) {
            applyStylesToDirtySubtrees(root, snapshot, variables, metrics)
        } else {
            applyStylesRecursively(
                root = root,
                snapshot = snapshot,
                variables = variables,
                metrics = metrics,
                parentComputed = null,
                rootFontSizePx = DEFAULT_ROOT_FONT_SIZE_PX,
                passMode = StylePassMode.Full,
                scope = scope,
                allowInspectorOverrides = allowInspectorOverrides
            )
        }
        if (scope == StyleApplicationScope.Application) {
            pseudoDirtyNodes.clear()
            selectorDirtyNodes.clear()
            pseudoDirtyGlobal = false
            selectorDirtyGlobal = false
            lastAppliedStylesheetVersion = snapshot.version
            lastAppliedThemeVersion = themeVersion
            lastAppliedInspectorOverridesVersion = inspectorOverridesVersion
            lastAppliedPseudoStateVersion = pseudoStateVersion
            lastAppliedSelectorStateVersion = selectorStateVersion
        }

        val report = StyleApplyReport(
            layoutDirty = result.layoutDirty,
            visualDirty = result.visualDirty,
            visitedNodes = metrics.visitedNodes,
            cacheHits = metrics.cacheHits,
            recomputedNodes = metrics.recomputedNodes
        )
        lastApplyReport = report
        return report
    }

    fun lastStyleApplyReport(): StyleApplyReport = lastApplyReport

    fun currentStyleRevision(
        scope: StyleApplicationScope = StyleApplicationScope.Application
    ): Long {
        val base = when (scope) {
            StyleApplicationScope.Application -> {
                (StylesheetManager.snapshot().version shl 2) xor
                    (themeVersion shl 1) xor
                    inspectorOverridesVersion
            }

            StyleApplicationScope.SystemOverlay -> 0L
        }
        return base xor
            (pseudoStateVersion shl 3) xor
            (selectorStateVersion shl 4) xor
            (viewportWidthPx.toLong() shl 5) xor
            (viewportHeightPx.toLong() shl 6) xor
            (scope.ordinal.toLong() shl 20)
    }

    fun markPseudoStateChanged(node: DOMNode? = null) {
        pseudoStateVersion += 1L
        if (node == null) {
            pseudoDirtyGlobal = true
            pseudoDirtyNodes.clear()
        } else {
            pseudoDirtyNodes += node
        }
    }

    fun markSelectorStateChanged(node: DOMNode? = null) {
        selectorStateVersion += 1L
        if (node == null) {
            selectorDirtyGlobal = true
            selectorDirtyNodes.clear()
        } else {
            selectorDirtyNodes += node
        }
    }

    private fun shouldApplyTargetedPseudoPass(snapshot: StylesheetSnapshot): Boolean {
        if (pseudoDirtyGlobal || selectorDirtyGlobal) return false
        if (pseudoDirtyNodes.isEmpty() && selectorDirtyNodes.isEmpty()) return false
        return snapshot.version == lastAppliedStylesheetVersion &&
            themeVersion == lastAppliedThemeVersion &&
            inspectorOverridesVersion == lastAppliedInspectorOverridesVersion &&
            (pseudoStateVersion != lastAppliedPseudoStateVersion ||
                selectorStateVersion != lastAppliedSelectorStateVersion)
    }

    private fun applyStylesToDirtySubtrees(
        root: DOMNode,
        snapshot: StylesheetSnapshot,
        variables: Map<String, String>,
        metrics: MutableApplyMetrics
    ): NodeApplyFlags {
        val dirty = expandDirtyNodesForCombinators(
            root = root,
            snapshot = snapshot,
            rawDirty = pseudoDirtyNodes + selectorDirtyNodes
        )
            .filter { it.isDescendantOfOrSame(root) }
            .sortedBy { it.depth() }
        if (dirty.isEmpty()) {
            // Dirty markers may reference detached/template nodes after reconcile.
            // Fall back to full tree style application to keep frame output deterministic.
            return applyStylesRecursively(
                root = root,
                snapshot = snapshot,
                variables = variables,
                metrics = metrics,
                parentComputed = null,
                rootFontSizePx = DEFAULT_ROOT_FONT_SIZE_PX,
                passMode = StylePassMode.Full,
                scope = StyleApplicationScope.Application,
                allowInspectorOverrides = true
            )
        }

        val effectiveRoots = ArrayList<DOMNode>(dirty.size)
        dirty.forEach { candidate ->
            if (effectiveRoots.none { candidate.isDescendantOfOrSame(it) }) {
                effectiveRoots += candidate
            }
        }

        var flags = NodeApplyFlags(layoutDirty = false, visualDirty = false)
        effectiveRoots.forEach { subtreeRoot ->
            val subtreeFlags = applyStylesRecursively(
                root = subtreeRoot,
                snapshot = snapshot,
                variables = variables,
                metrics = metrics,
                parentComputed = subtreeRoot.parent?.appliedComputedStyleSnapshot(),
                rootFontSizePx = rootFontSizeFor(subtreeRoot),
                passMode = StylePassMode.Targeted,
                scope = StyleApplicationScope.Application,
                allowInspectorOverrides = true
            )
            flags = NodeApplyFlags(
                layoutDirty = flags.layoutDirty || subtreeFlags.layoutDirty,
                visualDirty = flags.visualDirty || subtreeFlags.visualDirty
            )
        }
        return flags
    }

    private fun applyStylesRecursively(
        root: DOMNode,
        snapshot: StylesheetSnapshot,
        variables: Map<String, String>,
        metrics: MutableApplyMetrics,
        parentComputed: ComputedStyle?,
        rootFontSizePx: Int,
        passMode: StylePassMode,
        scope: StyleApplicationScope,
        allowInspectorOverrides: Boolean
    ): NodeApplyFlags {
        val nodeResult = applyStyleToNode(
            node = root,
            snapshot = snapshot,
            variables = variables,
            metrics = metrics,
            parentComputed = parentComputed,
            rootFontSizePx = rootFontSizePx,
            scope = scope,
            allowInspectorOverrides = allowInspectorOverrides
        )
        var flags = nodeResult.flags
        val canSkipSubtree = passMode == StylePassMode.Targeted &&
            nodeResult.cacheHit &&
            !snapshot.index.hasAncestorDependentSelectors
        if (canSkipSubtree) {
            return flags
        }
        val nodeComputed = root.appliedComputedStyleSnapshot()
        val nextRootFontSizePx = if (root.parent == null) {
            nodeComputed?.fontSize ?: rootFontSizePx
        } else {
            rootFontSizePx
        }
        root.children.forEach { child ->
            val childFlags = applyStylesRecursively(
                root = child,
                snapshot = snapshot,
                variables = variables,
                metrics = metrics,
                parentComputed = nodeComputed,
                rootFontSizePx = nextRootFontSizePx,
                passMode = passMode,
                scope = scope,
                allowInspectorOverrides = allowInspectorOverrides
            )
            flags = NodeApplyFlags(
                layoutDirty = flags.layoutDirty || childFlags.layoutDirty,
                visualDirty = flags.visualDirty || childFlags.visualDirty
            )
        }
        return flags
    }

    private fun applyStyleToNode(
        node: DOMNode,
        snapshot: StylesheetSnapshot,
        variables: Map<String, String>,
        metrics: MutableApplyMetrics,
        parentComputed: ComputedStyle?,
        rootFontSizePx: Int,
        scope: StyleApplicationScope,
        allowInspectorOverrides: Boolean
    ): NodeApplyResult {
        metrics.visitedNodes += 1
        val defaults = node.captureStyleDefaults()
        val key = CacheKey(
            typeName = node.styleType,
            nodeId = selectorNodeId(node),
            classesHash = node.styleClasses.hashCode(),
            inlineHash = node.inlineStyleDeclarations.toStableHash(),
            inspectorHash = if (allowInspectorOverrides) inspectorOverrideHash(node) else 0,
            hovered = node.styleHovered,
            active = node.styleActive,
            focused = node.styleFocused,
            disabled = node.styleDisabled,
            open = node.styleOpen,
            stylesheetVersion = snapshot.version,
            themeVersion = if (scope == StyleApplicationScope.Application) themeVersion else 0L,
            defaultsHash = defaults.hashCode(),
            parentInheritedHash = inheritedHash(parentComputed),
            ancestorSelectorHash = if (snapshot.index.hasAncestorDependentSelectors) ancestorSelectorHash(node) else 0,
            siblingSelectorHash = if (
                snapshot.index.hasAdjacentSiblingCombinators || snapshot.index.hasGeneralSiblingCombinators
            ) {
                previousSiblingSelectorHash(node)
            } else {
                0
            },
            rootFontSizePx = rootFontSizePx,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            scope = scope
        )

        val cached = cache[node]
        if (cached != null && cached.key == key) {
            metrics.cacheHits += 1
            val result = node.applyComputedStyle(cached.style)
            return NodeApplyResult(
                flags = NodeApplyFlags(
                    layoutDirty = result.layoutDirty,
                    visualDirty = result.visualDirty
                ),
                cacheHit = true
            )
        }

        val computed = computeStyle(
            node = node,
            defaults = defaults,
            snapshot = snapshot,
            variables = variables,
            parentComputed = parentComputed,
            rootFontSizePx = rootFontSizePx,
            allowInspectorOverrides = allowInspectorOverrides
        )
        cache[node] = CachedStyle(key = key, style = computed)
        metrics.recomputedNodes += 1
        val result = node.applyComputedStyle(computed)
        return NodeApplyResult(
            flags = NodeApplyFlags(
                layoutDirty = result.layoutDirty,
                visualDirty = result.visualDirty
            ),
            cacheHit = false
        )
    }

    private fun computeStyle(
        node: DOMNode,
        defaults: ComputedStyleDefaults,
        snapshot: StylesheetSnapshot,
        variables: Map<String, String>,
        parentComputed: ComputedStyle?,
        rootFontSizePx: Int,
        allowInspectorOverrides: Boolean
    ): ComputedStyle {
        val candidates = matchingCandidates(node, snapshot.index)
        val winners = resolveCascadeWinners(
            node = node,
            candidates = candidates,
            inline = node.inlineStyleDeclarations,
            inspector = if (allowInspectorOverrides) inspectorOverrides[inspectorOverrideTarget(node)] else null
        )
        var result = defaults.toComputedStyle()
        for (property in StyleProperty.entries) {
            val winner = winners[property]
            if (winner != null) {
                val applied = runCatching {
                    applyProperty(
                        current = result,
                        parentComputed = parentComputed,
                        property = property,
                        expression = winner.expression,
                        variables = variables,
                        rootFontSizePx = rootFontSizePx
                    )
                }.onFailure { error ->
                    println("[DSGL-Style] Failed to apply '${property.key}': ${error.message}")
                }.getOrNull()
                if (applied != null) {
                    result = applied
                }
            } else if (StylePropertyRegistry.isInherited(property) && parentComputed != null) {
                result = inheritProperty(result, parentComputed, property)
            }
        }
        return result
    }

    private fun resolveCascadeWinners(
        node: DOMNode,
        candidates: List<StyleRule>,
        inline: StyleDeclarations,
        inspector: StyleDeclarations?
    ): EnumMap<StyleProperty, CascadeWinner> {
        val winners = EnumMap<StyleProperty, CascadeWinner>(StyleProperty::class.java)

        candidates.forEach { rule ->
            rule.declarations.values.forEach { (property, expression) ->
                val important = rule.declarations.isImportant(property)
                val candidate = CascadeWinner(
                    expression = expression,
                    important = important,
                    specificity = rule.selector.specificity,
                    sourceOrder = rule.sourceOrder,
                    origin = StyleOrigin.Stylesheet,
                    sourceKind = StyleSourceKind.Selector,
                    sourceLabel = buildString {
                        append(selectorLabel(rule.selector))
                        append(" @ ")
                        append(rule.fileName)
                        if (important) append(" !important")
                    }
                )
                if (shouldReplace(winners[property], candidate)) {
                    winners[property] = candidate
                }
            }
        }

        inline.values.forEach { (property, expression) ->
            val important = inline.isImportant(property)
            val candidate = CascadeWinner(
                expression = expression,
                important = important,
                specificity = StyleSpecificity(idCount = 1, classLikeCount = 0, typeCount = 0),
                sourceOrder = Int.MAX_VALUE - 1,
                origin = StyleOrigin.Inline,
                sourceKind = StyleSourceKind.Inline,
                sourceLabel = if (important) "inline !important" else "inline"
            )
            if (shouldReplace(winners[property], candidate)) {
                winners[property] = candidate
            }
        }

        inspector?.values?.forEach { (property, expression) ->
            val important = inspector.isImportant(property)
            val candidate = CascadeWinner(
                expression = expression,
                important = important,
                specificity = StyleSpecificity(idCount = 1, classLikeCount = 0, typeCount = 0),
                sourceOrder = Int.MAX_VALUE,
                origin = StyleOrigin.Inspector,
                sourceKind = StyleSourceKind.InspectorOverride,
                sourceLabel = if (important) "inspector !important" else "inspector"
            )
            if (shouldReplace(winners[property], candidate)) {
                winners[property] = candidate
            }
        }

        return winners
    }

    private fun shouldReplace(current: CascadeWinner?, candidate: CascadeWinner): Boolean {
        if (current == null) return true
        if (candidate.origin.precedence != current.origin.precedence) {
            return candidate.origin.precedence > current.origin.precedence
        }
        if (candidate.important != current.important) {
            return candidate.important
        }
        val specificityCmp = candidate.specificity.compareTo(current.specificity)
        if (specificityCmp != 0) {
            return specificityCmp > 0
        }
        if (candidate.sourceOrder != current.sourceOrder) {
            return candidate.sourceOrder > current.sourceOrder
        }
        return true
    }

    private fun snapshotForScope(scope: StyleApplicationScope): StylesheetSnapshot {
        return when (scope) {
            StyleApplicationScope.Application -> StylesheetManager.snapshot()
            StyleApplicationScope.SystemOverlay -> StylesheetSnapshot(
                version = Long.MIN_VALUE,
                index = RuleIndex.EMPTY,
                rootVariables = emptyMap()
            )
        }
    }

    private fun resolvedVariables(snapshot: StylesheetSnapshot, scope: StyleApplicationScope): Map<String, String> {
        if (scope == StyleApplicationScope.SystemOverlay) {
            return emptyMap()
        }
        val variables = linkedMapOf<String, String>()
        variables.putAll(snapshot.rootVariables)
        variables.putAll(themeVariables)
        return variables
    }

    private fun matchingCandidates(node: DOMNode, index: RuleIndex): List<StyleRule> {
        return gatherCandidates(node, index)
            .filter { selectorMatches(node, it.selector) }
            .sortedBy { it.sourceOrder }
    }

    private fun gatherCandidates(node: DOMNode, index: RuleIndex): List<StyleRule> {
        val out = linkedSetOf<StyleRule>()
        out.addAll(index.universalIndex)
        selectorNodeId(node)?.let { id ->
            index.idIndex[id]?.let { out.addAll(it) }
        }
        index.typeIndex[node.styleType]?.let { out.addAll(it) }
        if (node.parent == null) {
            index.typeIndex[ROOT_SELECTOR_INTERNAL]?.let { out.addAll(it) }
        }
        node.styleClasses.forEach { className ->
            index.classIndex[className]?.let { out.addAll(it) }
        }
        return out.toList()
    }

    private fun selectorMatches(node: DOMNode, selector: StyleSelector): Boolean {
        var current: DOMNode? = node
        val steps = selector.steps
        for (index in steps.indices.reversed()) {
            val step = steps[index]
            val targetNode = current ?: return false
            if (!selectorPartMatches(targetNode, step.part)) return false
            if (index == 0) return true
            current = when (step.combinatorToLeft ?: StyleCombinator.Descendant) {
                StyleCombinator.Child -> targetNode.parent
                StyleCombinator.Descendant -> findAncestorMatching(
                    from = targetNode.parent,
                    part = steps[index - 1].part
                )
                StyleCombinator.AdjacentSibling -> targetNode.previousSiblingOrNull()
                StyleCombinator.GeneralSibling -> findPreviousSiblingMatching(
                    from = targetNode.previousSiblingOrNull(),
                    part = steps[index - 1].part
                )
            }
            if (current == null) return false
        }
        return true
    }

    private fun findAncestorMatching(from: DOMNode?, part: StyleSelectorPart): DOMNode? {
        var current = from
        while (current != null) {
            if (selectorPartMatches(current, part)) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun findPreviousSiblingMatching(from: DOMNode?, part: StyleSelectorPart): DOMNode? {
        var current = from
        while (current != null) {
            if (selectorPartMatches(current, part)) {
                return current
            }
            current = current.previousSiblingOrNull()
        }
        return null
    }

    private fun selectorPartMatches(node: DOMNode, part: StyleSelectorPart): Boolean {
        if (part.id != null && part.id != selectorNodeId(node)) return false
        part.typeName?.let { typeName ->
            val typeMatches = when (typeName) {
                ROOT_SELECTOR_INTERNAL -> node.parent == null
                else -> typeName == node.styleType
            }
            if (!typeMatches) return false
        }
        if (part.classes.isNotEmpty() && !node.styleClasses.containsAll(part.classes)) return false
        val pseudo = part.pseudoState ?: return true
        return when (pseudo) {
            StylePseudoState.HOVER -> node.styleHovered && !node.styleDisabled
            StylePseudoState.ACTIVE -> node.styleActive && !node.styleDisabled
            StylePseudoState.FOCUS -> node.styleFocused && !node.styleDisabled
            StylePseudoState.DISABLED -> node.styleDisabled
            StylePseudoState.OPEN -> node.styleOpen && !node.styleDisabled
        }
    }

    private fun selectorLabel(selector: StyleSelector): String {
        return selector.steps.joinToString(separator = " ") { step ->
            val part = buildString {
                when {
                    step.part.universal -> append("*")
                    step.part.typeName != null -> append(step.part.typeName)
                }
                step.part.id?.let { append('#').append(it) }
                step.part.classes.forEach { className -> append('.').append(className) }
                when (step.part.pseudoState) {
                    StylePseudoState.HOVER -> append(":hover")
                    StylePseudoState.ACTIVE -> append(":active")
                    StylePseudoState.FOCUS -> append(":focus")
                    StylePseudoState.DISABLED -> append(":disabled")
                    StylePseudoState.OPEN -> append(":open")
                    null -> Unit
                }
            }
            val prefix = when (step.combinatorToLeft) {
                StyleCombinator.Child -> ">"
                StyleCombinator.Descendant -> ""
                StyleCombinator.AdjacentSibling -> "+"
                StyleCombinator.GeneralSibling -> "~"
                null -> ""
            }
            if (prefix.isEmpty()) part else "$prefix $part"
        }.trim()
    }

    private fun selectorNodeId(node: DOMNode): String? {
        return node.styleId ?: (node.key as? String)
    }

    private fun ancestorSelectorHash(node: DOMNode): Int {
        var current = node.parent
        var result = 1
        while (current != null) {
            result = 31 * result + selectorStateHash(current)
            current = current.parent
        }
        return result
    }

    private fun previousSiblingSelectorHash(node: DOMNode): Int {
        val parent = node.parent ?: return 0
        var result = 1
        for (sibling in parent.children) {
            if (sibling === node) break
            result = 31 * result + selectorStateHash(sibling)
        }
        return result
    }

    private fun selectorStateHash(node: DOMNode): Int {
        var result = 1
        result = 31 * result + node.styleType.hashCode()
        result = 31 * result + (selectorNodeId(node)?.hashCode() ?: 0)
        result = 31 * result + node.styleClasses.hashCode()
        result = 31 * result + if (node.styleHovered) 1 else 0
        result = 31 * result + if (node.styleActive) 1 else 0
        result = 31 * result + if (node.styleFocused) 1 else 0
        result = 31 * result + if (node.styleDisabled) 1 else 0
        result = 31 * result + if (node.styleOpen) 1 else 0
        return result
    }

    private fun rootFontSizeFor(node: DOMNode): Int {
        var current: DOMNode = node
        while (current.parent != null) {
            current = current.parent!!
        }
        return current.appliedComputedStyleSnapshot()?.fontSize
            ?: current.fontSize
            ?: DEFAULT_ROOT_FONT_SIZE_PX
    }

    private fun inheritProperty(current: ComputedStyle, parent: ComputedStyle, property: StyleProperty): ComputedStyle {
        return when (property) {
            StyleProperty.FOREGROUND_COLOR -> current.copy(foregroundColor = parent.foregroundColor)
            StyleProperty.FONT_ID -> current.copy(fontId = parent.fontId)
            StyleProperty.FONT_SIZE -> current.copy(fontSize = parent.fontSize, fontSizeValue = parent.fontSizeValue)
            StyleProperty.LINE_HEIGHT -> current.copy(lineHeight = parent.lineHeight)
            StyleProperty.FONT_WEIGHT -> current.copy(fontWeight = parent.fontWeight)
            StyleProperty.FONT_STYLE -> current.copy(fontStyle = parent.fontStyle)
            else -> current
        }
    }

    private const val ROOT_SELECTOR_INTERNAL = "dsgl-root"

    private fun inheritedHash(parentComputed: ComputedStyle?): Int {
        if (parentComputed == null) return 0
        var result = 1
        result = 31 * result + parentComputed.foregroundColor
        result = 31 * result + (parentComputed.fontId?.hashCode() ?: 0)
        result = 31 * result + (parentComputed.fontSize ?: 0)
        result = 31 * result + parentComputed.lineHeight.hashCode()
        result = 31 * result + parentComputed.fontWeight.hashCode()
        result = 31 * result + parentComputed.fontStyle.hashCode()
        return result
    }

    private fun applyProperty(
        current: ComputedStyle,
        parentComputed: ComputedStyle?,
        property: StyleProperty,
        expression: StyleExpression,
        variables: Map<String, String>,
        rootFontSizePx: Int
    ): ComputedStyle {
        val literal = resolveExpressionToLiteral(expression, variables)
        return when (property) {
            StyleProperty.MARGIN -> current.copy(
                margin = parseSpacingLengthShorthand(
                    raw = literal,
                    allowNegative = true
                )
            )
            StyleProperty.PADDING -> current.copy(
                padding = parseSpacingLengthShorthand(
                    raw = literal,
                    allowNegative = false
                )
            )
            StyleProperty.BACKGROUND_COLOR -> current.copy(backgroundColor = parseColor(literal))
            StyleProperty.BACKGROUND_IMAGE -> current.copy(backgroundImage = parseStringLiteral(literal))
            StyleProperty.BORDER_COLOR -> current.copy(borderColor = parseColor(literal))
            StyleProperty.BORDER_WIDTH -> current.copy(
                borderWidth = parseLengthLiteral(literal, allowNegative = false)
            )
            StyleProperty.BORDER_RADIUS -> current.copy(
                borderRadius = parseLengthLiteral(literal, allowNegative = false)
            )
            StyleProperty.FOREGROUND_COLOR -> current.copy(foregroundColor = parseColor(literal))
            StyleProperty.FONT_ID -> current.copy(fontId = parseStringLiteral(literal))
            StyleProperty.FONT_SIZE -> {
                val fontSizeValue = parseLengthLiteral(literal, allowNegative = false)
                current.copy(
                    fontSize = resolveFontSizePx(
                        fontSizeValue = fontSizeValue,
                        current = current,
                        parentComputed = parentComputed,
                        rootFontSizePx = rootFontSizePx
                    ).coerceAtLeast(1),
                    fontSizeValue = fontSizeValue
                )
            }
            StyleProperty.LINE_HEIGHT -> current.copy(
                lineHeight = StylePropertyRegistry.parseLineHeightLiteral(property, literal)
            )
            StyleProperty.FONT_WEIGHT -> current.copy(fontWeight = parseFontWeight(literal))
            StyleProperty.FONT_STYLE -> current.copy(fontStyle = parseFontStyle(literal))
            StyleProperty.TEXT_DECORATION -> current.copy(textDecoration = parseTextDecoration(literal))
            StyleProperty.OBFUSCATED -> current.copy(obfuscated = parseBooleanLike(literal))
            StyleProperty.WIDTH -> current.copy(
                width = parseLengthLiteral(literal, allowNegative = false)
            )
            StyleProperty.HEIGHT -> current.copy(
                height = parseLengthLiteral(literal, allowNegative = false)
            )
            StyleProperty.MIN_WIDTH -> current.copy(
                minWidth = parseOptionalConstraintLengthLiteral(literal)
            )
            StyleProperty.MIN_HEIGHT -> current.copy(
                minHeight = parseOptionalConstraintLengthLiteral(literal)
            )
            StyleProperty.MAX_WIDTH -> current.copy(
                maxWidth = parseOptionalConstraintLengthLiteral(literal)
            )
            StyleProperty.MAX_HEIGHT -> current.copy(
                maxHeight = parseOptionalConstraintLengthLiteral(literal)
            )
            StyleProperty.ALIGN -> current.copy(align = parseAlign(literal))
            StyleProperty.DISPLAY -> current.copy(display = parseDisplay(literal))
            StyleProperty.POSITION -> current.copy(position = parsePosition(literal))
            StyleProperty.LEFT -> current.copy(left = parseOptionalCssLength(literal))
            StyleProperty.TOP -> current.copy(top = parseOptionalCssLength(literal))
            StyleProperty.RIGHT -> current.copy(right = parseOptionalCssLength(literal))
            StyleProperty.BOTTOM -> current.copy(bottom = parseOptionalCssLength(literal))
            StyleProperty.Z_INDEX -> current.copy(zIndex = parseIntLike(literal))
            StyleProperty.OVERFLOW -> {
                val overflowAxes = parseOverflowShorthand(literal)
                current.copy(
                    overflow = if (overflowAxes.overflowX == overflowAxes.overflowY) {
                        overflowAxes.overflowX
                    } else {
                        Overflow.Visible
                    },
                    overflowX = overflowAxes.overflowX,
                    overflowY = overflowAxes.overflowY
                )
            }
            StyleProperty.OVERFLOW_X -> current.copy(overflowX = parseOverflow(literal))
            StyleProperty.OVERFLOW_Y -> current.copy(overflowY = parseOverflow(literal))
            StyleProperty.FLEX_DIRECTION -> current.copy(flexDirection = parseFlexDirection(literal))
            StyleProperty.JUSTIFY_CONTENT -> current.copy(justifyContent = parseJustifyContent(literal))
            StyleProperty.ALIGN_ITEMS -> current.copy(alignItems = parseAlignItems(literal))
            StyleProperty.JUSTIFY_ITEMS -> current.copy(justifyItems = parseJustifyItems(literal))
            StyleProperty.GAP -> current.copy(
                gap = parseLengthLiteral(literal, allowNegative = false)
            )
            StyleProperty.FLEX_GROW -> current.copy(flexGrow = parseFloatLike(literal).coerceAtLeast(0f))
            StyleProperty.FLEX_SHRINK -> current.copy(flexShrink = parseFloatLike(literal).coerceAtLeast(0f))
            StyleProperty.FLEX_BASIS -> current.copy(
                flexBasis = parseOptionalCssLength(literal)?.also { length ->
                    if (length.value < 0f) {
                        error("Negative length is not allowed: '$literal'.")
                    }
                }
            )
            StyleProperty.GRID_COLUMNS -> current.copy(gridColumns = parseIntLike(literal).coerceAtLeast(1))
            StyleProperty.GRID_ROWS -> current.copy(gridRows = parseOptionalInt(literal)?.coerceAtLeast(1))
            StyleProperty.GRID_AUTO_FLOW -> current.copy(gridAutoFlow = parseGridAutoFlow(literal))
            StyleProperty.GRID_COLUMN_SPAN -> current.copy(gridColumnSpan = parseIntLike(literal).coerceAtLeast(1))
            StyleProperty.GRID_ROW_SPAN -> current.copy(gridRowSpan = parseIntLike(literal).coerceAtLeast(1))
            StyleProperty.TEXT_WRAP -> current.copy(textWrap = parseTextWrap(literal))
            StyleProperty.TEXT_FORMATTING -> current.copy(textFormatting = parseTextFormatting(literal))
            StyleProperty.TRANSFORM -> current.copy(transform = parseTransform(literal))
            StyleProperty.TRANSFORM_ORIGIN -> current.copy(transformOrigin = parseTransformOrigin(literal))
            StyleProperty.OPACITY -> current.copy(opacity = parseOpacity(literal))
        }
    }

    private fun parseLengthLiteral(literal: String, allowNegative: Boolean): CssLength {
        val parsed = parseCssLength(literal)
        if (!allowNegative && parsed.value < 0f) {
            error("Negative length is not allowed: '$literal'.")
        }
        return parsed
    }

    private fun parseOptionalConstraintLengthLiteral(literal: String): CssLength? {
        val parsed = parseOptionalCssLength(literal)
        if (parsed != null && parsed.value < 0f) {
            error("Negative length is not allowed: '$literal'.")
        }
        return parsed
    }

    private fun resolveFontSizePx(
        fontSizeValue: CssLength,
        current: ComputedStyle,
        parentComputed: ComputedStyle?,
        rootFontSizePx: Int
    ): Int {
        val inheritedFontPx = (
            parentComputed?.fontSize
                ?: current.fontSize
                ?: 16
            ).toFloat()
        val context = LengthResolveContext(
            viewportWidthPx = viewportWidthPx.toFloat(),
            viewportHeightPx = viewportHeightPx.toFloat(),
            containingBlockWidthPx = viewportWidthPx.toFloat(),
            containingBlockHeightPx = viewportHeightPx.toFloat(),
            rootFontSizePx = rootFontSizePx.toFloat(),
            currentFontSizePx = inheritedFontPx,
            inheritedFontSizePx = inheritedFontPx
        )
        return fontSizeValue
            .resolvePx(context, LengthPercentBase.InheritedFontSize)
            .roundToInt()
            .coerceAtLeast(1)
    }

    private fun inspectorOverrideHash(node: DOMNode): Int {
        return inspectorOverrideVersions[inspectorOverrideTarget(node)] ?: 0
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
        value.values.forEach { (property, expression) ->
            copy.set(property, expression, value.isImportant(property))
        }
        return copy
    }

    private fun bumpInspectorOverrideVersion(nodeKey: Any) {
        val next = ((inspectorOverrideVersions[nodeKey] ?: 0) + 1).coerceAtLeast(1)
        inspectorOverrideVersions[nodeKey] = next
        inspectorOverridesVersion += 1
    }

    private fun DOMNode.depth(): Int {
        var count = 0
        var current = parent
        while (current != null) {
            count++
            current = current.parent
        }
        return count
    }

    private fun DOMNode.isDescendantOfOrSame(ancestor: DOMNode): Boolean {
        var current: DOMNode? = this
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }

    private fun DOMNode.previousSiblingOrNull(): DOMNode? {
        val parentNode = parent ?: return null
        val index = parentNode.children.indexOf(this)
        if (index <= 0) return null
        return parentNode.children[index - 1]
    }

    private fun expandDirtyNodesForCombinators(
        root: DOMNode,
        snapshot: StylesheetSnapshot,
        rawDirty: Collection<DOMNode>
    ): Set<DOMNode> {
        if (rawDirty.isEmpty()) return emptySet()
        if (!snapshot.index.hasAdjacentSiblingCombinators && !snapshot.index.hasGeneralSiblingCombinators) {
            return rawDirty.filterTo(linkedSetOf()) { it.isDescendantOfOrSame(root) }
        }

        val expanded = linkedSetOf<DOMNode>()
        rawDirty.forEach { candidate ->
            if (!candidate.isDescendantOfOrSame(root)) return@forEach
            expanded += candidate
            val parentNode = candidate.parent ?: return@forEach
            val siblings = parentNode.children
            val index = siblings.indexOf(candidate)
            if (index < 0) return@forEach

            if (snapshot.index.hasAdjacentSiblingCombinators) {
                val adjacent = siblings.getOrNull(index + 1)
                if (adjacent != null) {
                    expanded += adjacent
                }
            }
            if (snapshot.index.hasGeneralSiblingCombinators) {
                for (siblingIndex in (index + 1) until siblings.size) {
                    expanded += siblings[siblingIndex]
                }
            }
        }
        return expanded
    }
}
