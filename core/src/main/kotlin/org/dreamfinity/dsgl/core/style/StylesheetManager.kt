package org.dreamfinity.dsgl.core.style

import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class StylesheetSnapshot(
    val version: Long,
    val index: RuleIndex,
    val rootVariables: Map<String, String>
)

data class RuleIndex(
    val typeIndex: Map<String, List<StyleRule>>,
    val classIndex: Map<String, List<StyleRule>>,
    val idIndex: Map<String, List<StyleRule>>,
    val universalIndex: List<StyleRule>,
    val hasAncestorDependentSelectors: Boolean,
    val hasAdjacentSiblingCombinators: Boolean,
    val hasGeneralSiblingCombinators: Boolean
) {
    companion object {
        val EMPTY = RuleIndex(
            typeIndex = emptyMap(),
            classIndex = emptyMap(),
            idIndex = emptyMap(),
            universalIndex = emptyList(),
            hasAncestorDependentSelectors = false,
            hasAdjacentSiblingCombinators = false,
            hasGeneralSiblingCombinators = false
        )
    }
}

object StylesheetManager {
    private data class LoadedSheet(
        val file: File,
        val lastModified: Long,
        val data: StylesheetData
    )

    private var stylesDir: File? = null
    private val loadedSheets: MutableMap<String, LoadedSheet> = ConcurrentHashMap()
    private var lastDirectoryScanMillis: Long = 0L
    private var scannedOnce: Boolean = false
    private var versionCounter: Long = 0L
    private var snapshot: StylesheetSnapshot = StylesheetSnapshot(0L, RuleIndex.EMPTY, emptyMap())

    private const val SCAN_INTERVAL_MS: Long = 500L

    @Synchronized
    fun setStylesDirectory(directory: File?) {
        val normalized = directory?.absoluteFile
        if (stylesDir?.absolutePath == normalized?.absolutePath) return
        stylesDir = normalized
        loadedSheets.clear()
        snapshot = StylesheetSnapshot(0L, RuleIndex.EMPTY, emptyMap())
        versionCounter++
        scannedOnce = false
        lastDirectoryScanMillis = 0L
        println("[DSGL-Style] Styles directory set to: ${normalized?.path ?: "<disabled>"}")
    }

    @Synchronized
    fun forceReload() {
        scannedOnce = false
        lastDirectoryScanMillis = 0L
        pollForChanges(force = true)
    }

    @Synchronized
    fun pollForChanges(force: Boolean = false) {
        val dir = stylesDir
        if (dir == null || !dir.exists() || !dir.isDirectory) {
            if (snapshot.index != RuleIndex.EMPTY || snapshot.rootVariables.isNotEmpty()) {
                loadedSheets.clear()
                snapshot = StylesheetSnapshot(++versionCounter, RuleIndex.EMPTY, emptyMap())
            }
            return
        }

        val now = System.currentTimeMillis()
        if (!force && scannedOnce && now - lastDirectoryScanMillis < SCAN_INTERVAL_MS) {
            return
        }
        lastDirectoryScanMillis = now
        scannedOnce = true

        val currentFiles = dir.walkTopDown()
            .filter { it.isFile && it.extension.equals("dss", ignoreCase = true) }
            .sortedBy { it.relativeTo(dir).path.replace('\\', '/') }
            .toList()

        val currentPaths = currentFiles.map { it.absolutePath }.toSet()
        val removedPaths = loadedSheets.keys.filter { it !in currentPaths }
        var changed = removedPaths.isNotEmpty()
        removedPaths.forEach { loadedSheets.remove(it) }

        currentFiles.forEach { file ->
            val key = file.absolutePath
            val lastModified = file.lastModified()
            val loaded = loadedSheets[key]
            if (force || loaded == null || loaded.lastModified != lastModified) {
                val parsed = runCatching { DssParser.parse(file) }
                    .onFailure { error ->
                        println("[DSGL-Style] Parse error in ${file.path}: ${error.message}")
                    }
                    .getOrNull()

                if (parsed != null) {
                    loadedSheets[key] = LoadedSheet(file, lastModified, parsed)
                    println("[DSGL-Style] Reloaded stylesheet: ${file.path}")
                    parsed.warnings.forEach { warning ->
                        println("[DSGL-Style][Deprecated] ${file.path}: $warning")
                    }
                    changed = true
                }
            }
        }

        if (changed) {
            rebuildSnapshot(dir)
        }
    }

    @Synchronized
    fun snapshot(): StylesheetSnapshot {
        return snapshot
    }

    @Synchronized
    private fun rebuildSnapshot(baseDir: File) {
        val orderedSheets = loadedSheets.values
            .sortedBy { it.file.relativeTo(baseDir).path.replace('\\', '/') }

        var sourceOrder = 0
        val rules = mutableListOf<StyleRule>()
        val rootVars = linkedMapOf<String, String>()

        orderedSheets.forEach { loaded ->
            loaded.data.rootVariables.forEach { (name, value) ->
                rootVars[name] = value
            }
            loaded.data.rules.forEach { rule ->
                rules += rule.copy(
                    sourceOrder = sourceOrder++,
                    fileName = loaded.file.path
                )
            }
        }

        snapshot = StylesheetSnapshot(
            version = ++versionCounter,
            index = buildIndex(rules),
            rootVariables = rootVars.toMap()
        )
    }

    private fun buildIndex(rules: List<StyleRule>): RuleIndex {
        val typeIndex = linkedMapOf<String, MutableList<StyleRule>>()
        val classIndex = linkedMapOf<String, MutableList<StyleRule>>()
        val idIndex = linkedMapOf<String, MutableList<StyleRule>>()
        val universalIndex = mutableListOf<StyleRule>()
        var hasAncestorDependentSelectors = false
        var hasAdjacentSiblingCombinators = false
        var hasGeneralSiblingCombinators = false

        rules.forEach { rule ->
            if (
                rule.selector.steps.any {
                    it.combinatorToLeft == StyleCombinator.Descendant ||
                        it.combinatorToLeft == StyleCombinator.Child
                }
            ) {
                hasAncestorDependentSelectors = true
            }
            rule.selector.steps.forEach { step ->
                when (step.combinatorToLeft) {
                    StyleCombinator.AdjacentSibling -> hasAdjacentSiblingCombinators = true
                    StyleCombinator.GeneralSibling -> hasGeneralSiblingCombinators = true
                    else -> Unit
                }
            }
            val rightMost = rule.selector.rightMostPart()
            val indexed = linkedSetOf<MutableList<StyleRule>>()
            rightMost.id?.let { id ->
                indexed += idIndex.getOrPut(id) { mutableListOf() }
            }
            rightMost.typeName?.let { type ->
                indexed += typeIndex.getOrPut(type) { mutableListOf() }
            }
            rightMost.classes.forEach { className ->
                indexed += classIndex.getOrPut(className) { mutableListOf() }
            }
            if (indexed.isEmpty() || rightMost.universal) {
                universalIndex += rule
            } else {
                indexed.forEach { bucket -> bucket += rule }
            }
        }

        return RuleIndex(
            typeIndex = typeIndex.mapValues { it.value.toList() },
            classIndex = classIndex.mapValues { it.value.toList() },
            idIndex = idIndex.mapValues { it.value.toList() },
            universalIndex = universalIndex.toList(),
            hasAncestorDependentSelectors = hasAncestorDependentSelectors,
            hasAdjacentSiblingCombinators = hasAdjacentSiblingCombinators,
            hasGeneralSiblingCombinators = hasGeneralSiblingCombinators
        )
    }
}
