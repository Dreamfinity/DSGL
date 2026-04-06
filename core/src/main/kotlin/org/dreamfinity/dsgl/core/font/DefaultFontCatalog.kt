package org.dreamfinity.dsgl.core.font

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

internal class DefaultFontCatalog(
    private val pathIdResolver: (String) -> FontId = FontRegistry::fontIdFromTtfPath,
    private val onCatalogChanged: () -> Unit = {}
) : FontCatalog {
    private val descriptors: MutableMap<FontId, MsdfFontResource> = linkedMapOf()
    private val loaded: MutableMap<FontId, LoadedMsdfFont> = ConcurrentHashMap()
    private val failedLoads: MutableSet<FontId> = Collections.newSetFromMap(ConcurrentHashMap())
    private val parseErrorLogTimes: MutableMap<String, Long> = ConcurrentHashMap()
    private val fontRenderContext = FontRenderContext(AffineTransform(), true, true)

    @Volatile
    private var defaultsRegistered: Boolean = false
    private var resourceClassLoader: ClassLoader = javaClass.classLoader

    @Synchronized
    override fun registerMsdf(
        fontId: FontId,
        metaResourcePath: String,
        atlasResourcePath: String,
        ttfResourcePath: String?,
        source: FontAssetSource
    ) {
        val changed = registerMsdfInternal(
            fontId = fontId,
            metaResourcePath = metaResourcePath,
            atlasResourcePath = atlasResourcePath,
            ttfResourcePath = ttfResourcePath,
            source = source
        )
        if (changed) {
            onCatalogChanged()
        }
    }

    @Synchronized
    override fun registerGeneratedFromTtfPath(
        relativeTtfPath: String,
        fontId: FontId
    ) {
        val changed = registerGeneratedFromTtfPathInternal(relativeTtfPath, fontId)
        if (changed) {
            onCatalogChanged()
        }
    }

    @Synchronized
    override fun registerFileFont(
        fontId: FontId,
        metaFile: Path,
        atlasFile: Path,
        ttfFile: Path?
    ) {
        val changed = registerFileFontInternal(fontId, metaFile, atlasFile, ttfFile)
        if (changed) {
            onCatalogChanged()
        }
    }

    @Synchronized
    override fun discoverAndPreloadFonts(
        externalFontsDir: File?,
        classLoader: ClassLoader
    ): FontPreloadSummary {
        val startedAt = System.nanoTime()
        resourceClassLoader = classLoader
        ensureDefaults()

        val jarDiscovered = descriptors.values.count { it.source == FontAssetSource.Jar }
        val externalPackages = externalFontsDir
            ?.takeIf { it.exists() && it.isDirectory }
            ?.let { FontDiscovery.discoverExternalPackages(it.toPath()) }
            .orEmpty()

        var externalDiscovered = 0
        var externalOverrodeJar = 0
        var invalidExternalPackages = 0
        var descriptorsChanged = false

        externalPackages.forEach { candidate ->
            if (!candidate.isValid) {
                invalidExternalPackages += 1
                logParseErrorRateLimited(
                    key = "external-missing:${candidate.fontId}",
                    message = "[DSGL-MSDF] Skipping external font '${candidate.fontId}', missing: ${
                        candidate.missing.joinToString(", ")
                    }"
                )
                return@forEach
            }
            externalDiscovered += 1
            val previous = descriptors[candidate.fontId]
            if (previous != null && previous.source == FontAssetSource.Jar) {
                externalOverrodeJar += 1
                println("[DSGL-MSDF] Registered font '${candidate.fontId}' from external dir overriding jar")
            }
            descriptorsChanged = registerFileFontInternal(
                fontId = candidate.fontId,
                metaFile = candidate.metaPath,
                atlasFile = candidate.atlasPath,
                ttfFile = candidate.ttfPath
            ) || descriptorsChanged
        }

        if (descriptorsChanged) {
            onCatalogChanged()
        }

        val loadedFonts = preloadRegisteredFonts()
        val totalFonts = descriptors.size
        val failedFonts = (totalFonts - loadedFonts).coerceAtLeast(0)
        val fallbackReady = getExact(FontRegistry.FALLBACK_FONT_ID) != null
        if (!fallbackReady) {
            logParseErrorRateLimited(
                key = "fallback-missing",
                message = "[DSGL-MSDF] Fallback font '${FontRegistry.FALLBACK_FONT_ID}' is not available after preload."
            )
        }

        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        println(
            "[DSGL-MSDF] preload summary: jar=$jarDiscovered external=$externalDiscovered " +
                "override=$externalOverrodeJar invalidExternal=$invalidExternalPackages loaded=$loadedFonts/$totalFonts in ${durationMs}ms"
        )

        return FontPreloadSummary(
            jarDiscovered = jarDiscovered,
            externalDiscovered = externalDiscovered,
            externalOverrodeJar = externalOverrodeJar,
            invalidExternalPackages = invalidExternalPackages,
            loadedFonts = loadedFonts,
            failedFonts = failedFonts,
            totalFonts = totalFonts,
            durationMs = durationMs,
            fallbackReady = fallbackReady
        )
    }

    @Synchronized
    fun preloadRegisteredFonts(): Int {
        ensureDefaults()
        descriptors.keys.sortedBy { it.lowercase() }.forEach { fontId ->
            val descriptor = descriptors[fontId] ?: return@forEach
            loaded[fontId] ?: load(descriptor)
        }
        return loaded.size
    }

    override fun get(fontId: FontId?): LoadedMsdfFont? {
        ensureDefaults()
        val requested = fontId ?: FontRegistry.DEFAULT_FONT_ID
        val requestedDescriptor = descriptors[requested]
        if (requestedDescriptor != null) {
            val loadedFont = loaded[requestedDescriptor.fontId] ?: load(requestedDescriptor)
            if (loadedFont != null) return loadedFont
        }
        if (requested != FontRegistry.DEFAULT_FONT_ID) {
            val defaultDescriptor = descriptors[FontRegistry.DEFAULT_FONT_ID] ?: return null
            return loaded[defaultDescriptor.fontId] ?: load(defaultDescriptor)
        }
        return null
    }

    override fun getExact(fontId: FontId): LoadedMsdfFont? {
        ensureDefaults()
        val descriptor = descriptors[fontId] ?: return null
        return loaded[fontId] ?: load(descriptor)
    }

    override fun allFontIds(): Set<FontId> {
        ensureDefaults()
        return descriptors.keys.toSet()
    }

    override fun registeredFonts(): List<RegisteredFontInfo> {
        ensureDefaults()
        return descriptors.values
            .sortedBy { it.fontId.lowercase() }
            .map { descriptor ->
                RegisteredFontInfo(
                    fontId = descriptor.fontId,
                    source = descriptor.source,
                    metaPath = descriptor.metaPath,
                    atlasPath = descriptor.atlasPath,
                    ttfPath = descriptor.ttfPath
                )
            }
    }

    override fun clearLoadedFonts() {
        loaded.clear()
        failedLoads.clear()
        parseErrorLogTimes.clear()
    }

    @Synchronized
    private fun ensureDefaults() {
        if (defaultsRegistered) return
        registerMsdfInternal(
            fontId = FontRegistry.FONT_MINECRAFT,
            metaResourcePath = "fonts/minecraft/MinecraftDefault-Regular-meta.json",
            atlasResourcePath = "fonts/minecraft/MinecraftDefault-Regular-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/minecraft/MinecraftDefault-Regular.ttf",
            source = FontAssetSource.Jar
        )
        registerMsdfInternal(
            fontId = FontRegistry.FONT_UBUNTU,
            metaResourcePath = "fonts/ubuntu/Ubuntu-Regular-meta.json",
            atlasResourcePath = "fonts/ubuntu/Ubuntu-Regular-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/ubuntu/Ubuntu-Regular.ttf",
            source = FontAssetSource.Jar
        )
        registerMsdfInternal(
            fontId = FontRegistry.FONT_NOTO_SANS,
            metaResourcePath = "fonts/noto/Noto_Sans/NotoSans-Regular-meta.json",
            atlasResourcePath = "fonts/noto/Noto_Sans/NotoSans-Regular-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/noto/Noto_Sans/NotoSans-Regular.ttf",
            source = FontAssetSource.Jar
        )
        registerMsdfInternal(
            fontId = FontRegistry.FONT_JB_MONO,
            metaResourcePath = "fonts/jetbrains_mono/JetBrainsMono-Regular-meta.json",
            atlasResourcePath = "fonts/jetbrains_mono/JetBrainsMono-Regular-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/jetbrains_mono/JetBrainsMono-Regular.ttf",
            source = FontAssetSource.Jar
        )
        registerMsdfInternal(
            fontId = FontRegistry.TELEGRAFICO,
            metaResourcePath = "fonts/telegrafico/telegrafico-meta.json",
            atlasResourcePath = "fonts/telegrafico/telegrafico-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/telegrafico/telegrafico.ttf",
            source = FontAssetSource.Jar
        )
        loadGeneratedFontManifest()
        defaultsRegistered = true
    }

    private fun registerMsdfInternal(
        fontId: FontId,
        metaResourcePath: String,
        atlasResourcePath: String,
        ttfResourcePath: String?,
        source: FontAssetSource
    ): Boolean {
        val normalizedMeta = normalizeRelativePath(metaResourcePath)
        val normalizedAtlas = normalizeRelativePath(atlasResourcePath)
        val normalizedTtf = ttfResourcePath?.let(::normalizeRelativePath)
            ?: (normalizedMeta.removeSuffix("-meta.json") + ".ttf")

        val descriptor = MsdfFontResource(
            fontId = fontId,
            source = source,
            pathMode = FontPathMode.Resource,
            metaPath = normalizedMeta,
            atlasPath = normalizedAtlas,
            ttfPath = normalizedTtf
        )
        return updateDescriptor(fontId, descriptor)
    }

    private fun registerGeneratedFromTtfPathInternal(relativeTtfPath: String, fontId: FontId): Boolean {
        val normalized = normalizeRelativePath(relativeTtfPath)
        require(normalized.endsWith(".ttf", ignoreCase = true)) {
            "Expected .ttf path, got '$relativeTtfPath'"
        }
        val base = normalized.removeSuffix(".ttf")
        return registerMsdfInternal(
            fontId = fontId,
            metaResourcePath = "fonts/$base-meta.json",
            atlasResourcePath = "fonts/$base-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/$normalized",
            source = FontAssetSource.Jar
        )
    }

    private fun registerFileFontInternal(
        fontId: FontId,
        metaFile: Path,
        atlasFile: Path,
        ttfFile: Path?
    ): Boolean {
        val descriptor = MsdfFontResource(
            fontId = fontId,
            source = FontAssetSource.External,
            pathMode = FontPathMode.FileSystem,
            metaPath = metaFile.toAbsolutePath().normalize().toString(),
            atlasPath = atlasFile.toAbsolutePath().normalize().toString(),
            ttfPath = ttfFile?.toAbsolutePath()?.normalize()?.toString()
        )
        return updateDescriptor(fontId, descriptor)
    }

    private fun updateDescriptor(fontId: FontId, descriptor: MsdfFontResource): Boolean {
        val previous = descriptors[fontId]
        if (previous == descriptor && loaded[fontId] != null && !failedLoads.contains(fontId)) {
            return false
        }
        descriptors[fontId] = descriptor
        loaded.remove(fontId)
        failedLoads.remove(fontId)
        return true
    }

    private fun readBytes(path: String, mode: FontPathMode): ByteArray? {
        return when (mode) {
            FontPathMode.Resource -> {
                val stream = resourceClassLoader.getResourceAsStream(path) ?: return null
                stream.use { it.readBytes() }
            }

            FontPathMode.FileSystem -> {
                runCatching { Files.readAllBytes(Paths.get(path)) }.getOrNull()
            }
        }
    }

    private fun loadGeneratedFontManifest() {
        val manifestPath = "fonts/generated-fonts.txt"
        val stream = resourceClassLoader.getResourceAsStream(manifestPath) ?: return
        stream.use { input ->
            val content = input.bufferedReader(StandardCharsets.UTF_8).readText()
            val ttfs = FontDiscovery.parseGeneratedFontIndex(content)
            ttfs.forEach { relativeTtf ->
                val id = pathIdResolver(relativeTtf)
                if (descriptors.containsKey(id)) return@forEach
                registerGeneratedFromTtfPathInternal(relativeTtf, fontId = id)
            }
        }
    }

    private fun load(descriptor: MsdfFontResource): LoadedMsdfFont? {
        if (failedLoads.contains(descriptor.fontId)) return null

        val metaBytes = readBytes(descriptor.metaPath, descriptor.pathMode)
        if (metaBytes == null) {
            failFontLoad(descriptor, "Missing meta '${descriptor.metaPath}'")
            return null
        }

        val atlasBytes = readBytes(descriptor.atlasPath, descriptor.pathMode)
        if (atlasBytes == null) {
            failFontLoad(descriptor, "Missing atlas '${descriptor.atlasPath}'")
            return null
        }

        val metaRaw = String(metaBytes, StandardCharsets.UTF_8)
        val meta = runCatching { MsdfFontMetaParser.parse(metaRaw) }
            .onFailure { error ->
                failFontLoad(
                    descriptor,
                    "Failed to parse metadata '${descriptor.metaPath}': ${error.message ?: error.javaClass.simpleName}"
                )
            }
            .getOrNull() ?: return null

        val awtBase = descriptor.ttfPath?.let { ttfPath ->
            val ttfBytes = readBytes(ttfPath, descriptor.pathMode)
            if (ttfBytes == null) {
                failFontLoad(descriptor, "Missing TTF '$ttfPath'")
                return null
            }
            runCatching { loadAwtFont(ttfBytes) }
                .onFailure { error ->
                    failFontLoad(
                        descriptor,
                        "Failed to load TTF '$ttfPath': ${error.message ?: error.javaClass.simpleName}"
                    )
                }
                .getOrNull() ?: return null
        }

        val loadedFont = LoadedMsdfFont(
            descriptor = descriptor,
            meta = meta,
            awtBaseFont = awtBase,
            preferredMissingGlyphIndex = computeGlyphIndexForCodepoint(awtBase, 0xFFFD),
            preferredQuestionGlyphIndex = computeGlyphIndexForCodepoint(awtBase, '?'.code),
            atlasPayload = AtlasPayload(atlasBytes)
        )
        loaded[descriptor.fontId] = loadedFont
        failedLoads.remove(descriptor.fontId)
        return loadedFont
    }

    private fun failFontLoad(descriptor: MsdfFontResource, reason: String) {
        failedLoads.add(descriptor.fontId)
        logParseErrorRateLimited(
            key = "font-load:${descriptor.fontId}",
            message = "[DSGL-MSDF] Failed to load font '${descriptor.fontId}' (${descriptor.source.name.lowercase()}): $reason"
        )
    }

    private fun normalizeRelativePath(path: String): String {
        return FontDiscovery.normalizeRelativePath(path)
    }

    private fun loadAwtFont(ttfBytes: ByteArray): Font {
        return ByteArrayInputStream(ttfBytes).use { input ->
            Font.createFont(Font.TRUETYPE_FONT, input)
        }
    }

    private fun computeGlyphIndexForCodepoint(font: Font?, codepoint: Int): Int? {
        if (font == null || !Character.isValidCodePoint(codepoint)) return null
        val text = String(Character.toChars(codepoint))
        return runCatching {
            val vector = font.createGlyphVector(fontRenderContext, text)
            if (vector.numGlyphs <= 0) {
                null
            } else {
                vector.getGlyphCode(0).takeIf { it >= 0 }
            }
        }.getOrNull()
    }

    private fun logParseErrorRateLimited(key: String, message: String) {
        val now = System.currentTimeMillis()
        val previous = parseErrorLogTimes[key] ?: 0L
        if (now - previous < 3_000L) return
        parseErrorLogTimes[key] = now
        println(message)
    }
}
