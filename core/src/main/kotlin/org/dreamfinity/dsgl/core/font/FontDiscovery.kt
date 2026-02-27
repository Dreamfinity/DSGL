package org.dreamfinity.dsgl.core.font

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

enum class FontAssetSource {
    Jar,
    External
}

internal data class FontPackageCandidate(
    val fontId: String,
    val ttfPath: Path,
    val metaPath: Path,
    val atlasPath: Path,
    val missing: List<String>
) {
    val isValid: Boolean
        get() = missing.isEmpty()
}

internal object FontDiscovery {
    fun fontIdFromRelativeTtfPath(relativeTtfPath: String): String {
        val normalized = normalizeRelativePath(relativeTtfPath)
        require(normalized.endsWith(".ttf", ignoreCase = true)) {
            "Expected .ttf path, got '$relativeTtfPath'"
        }
        return normalized.removeSuffix(".ttf")
    }

    fun parseGeneratedFontIndex(content: String): List<String> {
        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.endsWith(".ttf", ignoreCase = true) }
            .toList()
    }

    fun resolveSourcePriority(
        jarFontIds: Collection<String>,
        externalFontIds: Collection<String>
    ): Map<String, FontAssetSource> {
        val result = linkedMapOf<String, FontAssetSource>()
        jarFontIds.forEach { result[it] = FontAssetSource.Jar }
        externalFontIds.forEach { result[it] = FontAssetSource.External }
        return result
    }

    fun discoverExternalPackages(root: Path): List<FontPackageCandidate> {
        if (!root.isDirectory()) return emptyList()
        val candidates = ArrayList<FontPackageCandidate>(16)
        Files.walk(root).use { stream ->
            stream
                .filter { it.isRegularFile() && it.fileName.toString().endsWith(".ttf", ignoreCase = true) }
                .forEach { ttfPath ->
                    val relative = normalizeRelativePath(root.relativize(ttfPath).toString())
                    val fontId = fontIdFromRelativeTtfPath(relative)
                    val baseRelative = relative.removeSuffix(".ttf")
                    val meta = root.resolve("$baseRelative-meta.json")
                    val atlas = root.resolve("$baseRelative-mtsdf.png")
                    val missing = mutableListOf<String>()
                    if (!meta.isRegularFile()) missing += "$fontId-meta.json"
                    if (!atlas.isRegularFile()) missing += "$fontId-mtsdf.png"
                    candidates += FontPackageCandidate(
                        fontId = fontId,
                        ttfPath = ttfPath.toAbsolutePath().normalize(),
                        metaPath = meta.toAbsolutePath().normalize(),
                        atlasPath = atlas.toAbsolutePath().normalize(),
                        missing = missing
                    )
                }
        }
        return candidates.sortedBy { it.fontId.lowercase() }
    }

    internal fun normalizeRelativePath(path: String): String {
        return path.replace('\\', '/')
            .trimStart('/', '\\')
            .replace(File.separatorChar, '/')
    }
}
