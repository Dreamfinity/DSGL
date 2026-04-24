package org.dreamfinity.dsgl.core.font

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.imageio.ImageIO

enum class FontPathMode {
    Resource,
    FileSystem,
}

data class MsdfFontResource(
    val fontId: FontId,
    val source: FontAssetSource,
    val pathMode: FontPathMode,
    val metaPath: String,
    val atlasPath: String,
    val ttfPath: String?,
)

data class FontTextureHandle(
    val textureId: Int,
    val width: Int,
    val height: Int,
)

data class LoadedMsdfFont(
    val descriptor: MsdfFontResource,
    val meta: MsdfFontMeta,
    val awtBaseFont: Font?,
    val preferredMissingGlyphIndex: Int?,
    val preferredQuestionGlyphIndex: Int?,
    val atlasPayload: AtlasPayload,
    var handle: FontTextureHandle? = null,
)

data class AtlasBitmap(
    val width: Int,
    val height: Int,
    var rgbaBytes: ByteArray,
)

class AtlasPayload internal constructor(
    private var encodedPngBytes: ByteArray?,
) {
    @Volatile
    private var decodedBitmap: AtlasBitmap? = null
    var isLoadedToGPU: Boolean = false

    fun markLoadedToGPUTexture() {
        isLoadedToGPU = true
        decodedBitmap = null
        encodedPngBytes = null
    }

    fun ensureDecoded(): AtlasBitmap {
        decodedBitmap?.let { return it }
        synchronized(this) {
            decodedBitmap?.let { return it }
            val bytes = encodedPngBytes ?: error("Missing atlas payload bytes")
            val decoded = decodeAtlasBitmap(bytes)
            decodedBitmap = decoded
            encodedPngBytes = null
            return decoded
        }
    }

    fun encodedByteSize(): Int = encodedPngBytes?.size ?: 0

    private fun decodeAtlasBitmap(atlasPngBytes: ByteArray): AtlasBitmap {
        decodeDeflatedRgba(atlasPngBytes)?.let { return it }
        return decodePng(atlasPngBytes)
    }

    private fun decodeDeflatedRgba(bytes: ByteArray): AtlasBitmap? {
        val inflater = Inflater(true)
        return try {
            ByteArrayInputStream(bytes).use { input ->
                InflaterInputStream(input, inflater).use { zipped ->
                    DataInputStream(zipped).use { data ->
                        data.readInt()
                        val width = data.readInt()
                        val height = data.readInt()
                        if (width <= 0 || height <= 0) return null
                        val rgbaBytes = ByteArray(width * height * 4)
                        data.readFully(rgbaBytes)
                        AtlasBitmap(width = width, height = height, rgbaBytes = rgbaBytes)
                    }
                }
            }
        } catch (_: Throwable) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun decodePng(bytes: ByteArray): AtlasBitmap {
        val image =
            ByteArrayInputStream(bytes).use { input ->
                ImageIO.read(input)
            } ?: error("Atlas payload is neither deflated rgba nor PNG")

        val width = image.width.coerceAtLeast(1)
        val height = image.height.coerceAtLeast(1)
        val argb = IntArray(width * height)
        image.getRGB(0, 0, width, height, argb, 0, width)
        val rgba = ByteArray(width * height * 4)
        var out = 0

        for (srcY in (height - 1) downTo 0) {
            val rowStart = srcY * width
            for (x in 0 until width) {
                val pixel = argb[rowStart + x]
                rgba[out++] = ((pixel ushr 16) and 0xFF).toByte()
                rgba[out++] = ((pixel ushr 8) and 0xFF).toByte()
                rgba[out++] = (pixel and 0xFF).toByte()
                rgba[out++] = ((pixel ushr 24) and 0xFF).toByte()
            }
        }
        return AtlasBitmap(width = width, height = height, rgbaBytes = rgba)
    }
}

data class RegisteredFontInfo(
    val fontId: FontId,
    val source: FontAssetSource,
    val metaPath: String,
    val atlasPath: String,
    val ttfPath: String?,
)

data class FontPreloadSummary(
    val jarDiscovered: Int,
    val externalDiscovered: Int,
    val externalOverrodeJar: Int,
    val invalidExternalPackages: Int,
    val loadedFonts: Int,
    val failedFonts: Int,
    val totalFonts: Int,
    val durationMs: Long,
    val fallbackReady: Boolean,
)

data class ShapedGlyph(
    val fontId: FontId,
    val glyphIndex: Int,
    val x: Float,
    val y: Float,
    val advance: Float,
    val charStart: Int,
    val charEnd: Int,
    val sourceCodepoint: Int,
)

data class ShapedTextRun(
    val fontId: FontId,
    val charStart: Int,
    val charEnd: Int,
    val glyphs: List<ShapedGlyph>,
    val advance: Float,
)

data class ShapedText(
    val glyphs: List<ShapedGlyph>,
    val runs: List<ShapedTextRun>,
    val width: Float,
)

object FontRegistry {
    const val FONT_MINECRAFT: String = "minecraft"
    const val FONT_UBUNTU: String = "ubuntu"
    const val FONT_NOTO_SANS: String = "noto-sans"
    const val FONT_JB_MONO: String = "JetBrains Mono"
    const val TELEGRAFICO: String = "telegrafico"
    const val DEFAULT_FONT_ID: String = FONT_MINECRAFT
    const val FALLBACK_FONT_ID: String = FONT_NOTO_SANS
    const val DEFAULT_FONT_SIZE: Int = 9
    private const val GLYPH_INDEX_NULL_SENTINEL: Int = Int.MIN_VALUE

    private data class ShapeCacheKey(
        val primaryFontId: FontId,
        val fallbackFontId: FontId?,
        val fontPx: Int,
        val text: String,
        val rangeStart: Int,
        val rangeEnd: Int,
        val directionFlags: Int,
        val formattingMode: String,
    )

    private data class FontCodepointKey(
        val fontId: FontId,
        val codepoint: Int,
    )

    private data class MutableShapingSegment(
        val font: LoadedMsdfFont,
        val text: StringBuilder = StringBuilder(),
        val sourceStartByChar: MutableList<Int> = ArrayList(),
        val sourceEndByChar: MutableList<Int> = ArrayList(),
        var charStart: Int = Int.MAX_VALUE,
        var charEnd: Int = 0,
    )

    private val descriptors: MutableMap<FontId, MsdfFontResource> = linkedMapOf()
    private val loaded: MutableMap<FontId, LoadedMsdfFont> = ConcurrentHashMap()
    private val failedLoads: MutableSet<FontId> = Collections.newSetFromMap(ConcurrentHashMap())
    private val parseErrorLogTimes: MutableMap<String, Long> = ConcurrentHashMap()
    private val shapeCache: MutableMap<ShapeCacheKey, ShapedText> =
        object : LinkedHashMap<ShapeCacheKey, ShapedText>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ShapeCacheKey, ShapedText>?): Boolean =
                size > 1024
        }
    private val derivedFontCache: MutableMap<String, Font> = ConcurrentHashMap()
    private val fontRenderContext: FontRenderContext = FontRenderContext(AffineTransform(), true, true)
    private val shapeCacheRequests = AtomicLong(0L)
    private val shapeCacheHits = AtomicLong(0L)
    private val shapeCacheMisses = AtomicLong(0L)
    private val canDisplayByFontCodepoint: MutableMap<FontCodepointKey, Boolean> = ConcurrentHashMap()
    private val glyphIndexByFontCodepoint: MutableMap<FontCodepointKey, Int> = ConcurrentHashMap()
    private val requiresReplacementGlyphByFontCodepoint: MutableMap<FontCodepointKey, Boolean> = ConcurrentHashMap()
    private val shapeTextRangeCalls = AtomicLong(0L)
    private val shapeSegmentCalls = AtomicLong(0L)
    private val requiresReplacementGlyphCalls = AtomicLong(0L)
    private val requiresReplacementGlyphCacheHits = AtomicLong(0L)
    private val requiresReplacementGlyphCacheMisses = AtomicLong(0L)
    private val requiresReplacementGlyphEvaluations = AtomicLong(0L)
    private val canDisplayCalls = AtomicLong(0L)
    private val canDisplayCacheHits = AtomicLong(0L)
    private val canDisplayCacheMisses = AtomicLong(0L)
    private val canDisplayAwtCalls = AtomicLong(0L)
    private val glyphIndexForCodepointCalls = AtomicLong(0L)
    private val glyphIndexCacheHits = AtomicLong(0L)
    private val glyphIndexCacheMisses = AtomicLong(0L)
    private val glyphIndexVectorBuildCalls = AtomicLong(0L)

    private var defaultsRegistered: Boolean = false
    private var resourceClassLoader: ClassLoader = javaClass.classLoader

    data class ShapeCacheStats(
        val requests: Long,
        val hits: Long,
        val misses: Long,
        val entries: Int,
        val maxEntries: Int,
    )

    data class TextHotPathStats(
        val shapeTextRangeCalls: Long,
        val shapeSegmentCalls: Long,
        val requiresReplacementGlyphCalls: Long,
        val requiresReplacementGlyphCacheHits: Long,
        val requiresReplacementGlyphCacheMisses: Long,
        val requiresReplacementGlyphEvaluations: Long,
        val canDisplayCalls: Long,
        val canDisplayCacheHits: Long,
        val canDisplayCacheMisses: Long,
        val canDisplayAwtCalls: Long,
        val glyphIndexForCodepointCalls: Long,
        val glyphIndexCacheHits: Long,
        val glyphIndexCacheMisses: Long,
        val glyphIndexVectorBuildCalls: Long,
    )

    @Synchronized
    fun discoverAndPreloadFonts(
        externalFontsDir: File?,
        classLoader: ClassLoader = javaClass.classLoader,
    ): FontPreloadSummary {
        val startedAt = System.nanoTime()
        resourceClassLoader = classLoader
        ensureDefaults()

        val jarDiscovered = descriptors.values.count { it.source == FontAssetSource.Jar }
        val externalPackages =
            externalFontsDir
                ?.takeIf { it.exists() && it.isDirectory }
                ?.let { FontDiscovery.discoverExternalPackages(it.toPath()) }
                .orEmpty()

        var externalDiscovered = 0
        var externalOverrodeJar = 0
        var invalidExternalPackages = 0

        externalPackages.forEach { candidate ->
            if (!candidate.isValid) {
                invalidExternalPackages += 1
                logParseErrorRateLimited(
                    key = "external-missing:${candidate.fontId}",
                    message = "[DSGL-MSDF] Skipping external font '${candidate.fontId}', missing: ${
                        candidate.missing.joinToString(
                            ", ",
                        )
                    }",
                )
                return@forEach
            }
            externalDiscovered += 1
            val previous = descriptors[candidate.fontId]
            if (previous != null && previous.source == FontAssetSource.Jar) {
                externalOverrodeJar += 1
                println("[DSGL-MSDF] Registered font '${candidate.fontId}' from external dir overriding jar")
            }
            registerFileFont(
                fontId = candidate.fontId,
                metaFile = candidate.metaPath,
                atlasFile = candidate.atlasPath,
                ttfFile = candidate.ttfPath,
            )
        }

        val loadedFonts = preloadRegisteredFonts()
        val totalFonts = descriptors.size
        val failedFonts = (totalFonts - loadedFonts).coerceAtLeast(0)
        val fallbackReady = getExact(FALLBACK_FONT_ID) != null
        if (!fallbackReady) {
            logParseErrorRateLimited(
                key = "fallback-missing",
                message = "[DSGL-MSDF] Fallback font '$FALLBACK_FONT_ID' is not available after preload.",
            )
        }

        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        println(
            "[DSGL-MSDF] preload summary: jar=$jarDiscovered external=$externalDiscovered " +
                "override=$externalOverrodeJar invalidExternal=$invalidExternalPackages " +
                "loaded=$loadedFonts/$totalFonts in ${durationMs}ms",
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
            fallbackReady = fallbackReady,
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

    @Synchronized
    fun registerMsdf(
        fontId: FontId,
        metaResourcePath: String,
        atlasResourcePath: String,
        ttfResourcePath: String? = null,
        source: FontAssetSource = FontAssetSource.Jar,
    ) {
        val normalizedMeta = normalizeRelativePath(metaResourcePath)
        val normalizedAtlas = normalizeRelativePath(atlasResourcePath)
        val normalizedTtf =
            ttfResourcePath?.let(::normalizeRelativePath)
                ?: (normalizedMeta.removeSuffix("-meta.json") + ".ttf")

        descriptors[fontId] =
            MsdfFontResource(
                fontId = fontId,
                source = source,
                pathMode = FontPathMode.Resource,
                metaPath = normalizedMeta,
                atlasPath = normalizedAtlas,
                ttfPath = normalizedTtf,
            )
        onDescriptorChanged(fontId)
    }

    @Synchronized
    fun registerGeneratedFromTtfPath(relativeTtfPath: String, fontId: FontId = fontIdFromTtfPath(relativeTtfPath)) {
        val normalized = normalizeRelativePath(relativeTtfPath)
        require(normalized.endsWith(".ttf", ignoreCase = true)) {
            "Expected .ttf path, got '$relativeTtfPath'"
        }
        val base = normalized.removeSuffix(".ttf")
        registerMsdf(
            fontId = fontId,
            metaResourcePath = "fonts/$base-meta.json",
            atlasResourcePath = "fonts/$base-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/$normalized",
            source = FontAssetSource.Jar,
        )
    }

    fun get(fontId: FontId?): LoadedMsdfFont? {
        ensureDefaults()
        val requested = fontId ?: DEFAULT_FONT_ID
        val requestedDescriptor = descriptors[requested]
        if (requestedDescriptor != null) {
            val loadedFont = loaded[requestedDescriptor.fontId] ?: load(requestedDescriptor)
            if (loadedFont != null) return loadedFont
        }
        if (requested != DEFAULT_FONT_ID) {
            val fallbackDescriptor = descriptors[DEFAULT_FONT_ID] ?: return null
            return loaded[fallbackDescriptor.fontId] ?: load(fallbackDescriptor)
        }
        return null
    }

    fun allFontIds(): Set<FontId> {
        ensureDefaults()
        return descriptors.keys.toSet()
    }

    fun registeredFonts(): List<RegisteredFontInfo> {
        ensureDefaults()
        return descriptors.values
            .sortedBy { it.fontId.lowercase() }
            .map { descriptor ->
                RegisteredFontInfo(
                    fontId = descriptor.fontId,
                    source = descriptor.source,
                    metaPath = descriptor.metaPath,
                    atlasPath = descriptor.atlasPath,
                    ttfPath = descriptor.ttfPath,
                )
            }
    }

    fun clearLoadedCache() {
        loaded.clear()
        failedLoads.clear()
        parseErrorLogTimes.clear()
        synchronized(shapeCache) {
            shapeCache.clear()
        }
        canDisplayByFontCodepoint.clear()
        glyphIndexByFontCodepoint.clear()
        requiresReplacementGlyphByFontCodepoint.clear()
        derivedFontCache.clear()
        resetShapeCacheStats()
        resetTextHotPathStats()
    }

    fun measureText(text: String, fontId: FontId?, fontSize: Int?): Int {
        if (text.isEmpty()) return 0
        if (text.contains('\n')) {
            return text.lineSequence().maxOfOrNull { line -> measureText(line, fontId, fontSize) } ?: 0
        }
        return shapeText(text, fontId, fontSize)
            .width
            .toInt()
            .coerceAtLeast(0)
    }

    fun shapeText(
        text: String,
        fontId: FontId?,
        fontSize: Int?,
        formattingMode: String = "plain",
    ): ShapedText =
        shapeTextRange(
            text = text,
            startIndex = 0,
            endIndexExclusive = text.length,
            fontId = fontId,
            fontSize = fontSize,
            formattingMode = formattingMode,
        )

    fun shapeTextRange(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        fontId: FontId?,
        fontSize: Int?,
        formattingMode: String = "plain",
    ): ShapedText {
        shapeTextRangeCalls.incrementAndGet()
        val (safeStart, safeEnd) = normalizeRange(text, startIndex, endIndexExclusive)
        if (safeEnd <= safeStart) {
            return ShapedText(glyphs = emptyList(), runs = emptyList(), width = 0f)
        }
        ensureDefaults()
        val fontPx = resolveFontSize(fontSize)
        val primary = get(fontId)
        if (primary == null) {
            return ShapedText(
                glyphs = emptyList(),
                runs = emptyList(),
                width = fallbackMeasureText(text, fontSize).toFloat(),
            )
        }
        val fallback =
            getExact(FALLBACK_FONT_ID)
                ?.takeIf { it.descriptor.fontId != primary.descriptor.fontId && it.awtBaseFont != null }
        val cacheKey =
            ShapeCacheKey(
                primaryFontId = primary.descriptor.fontId,
                fallbackFontId = fallback?.descriptor?.fontId,
                fontPx = fontPx,
                text = text,
                rangeStart = safeStart,
                rangeEnd = safeEnd,
                directionFlags = Font.LAYOUT_LEFT_TO_RIGHT,
                formattingMode = formattingMode,
            )
        synchronized(shapeCache) {
            shapeCacheRequests.incrementAndGet()
            shapeCache[cacheKey]?.let {
                shapeCacheHits.incrementAndGet()
                return it
            }
        }
        shapeCacheMisses.incrementAndGet()
        val shaped =
            shapeSingleLine(
                text = text,
                startIndex = safeStart,
                endIndexExclusive = safeEnd,
                primary = primary,
                fallback = fallback,
                fontPx = fontPx,
            )
        synchronized(shapeCache) {
            shapeCache[cacheKey] = shaped
        }
        return shaped
    }

    fun lineHeight(fontId: FontId?, fontSize: Int?): Int {
        val font = get(fontId) ?: return resolveFontSize(fontSize)
        return font.meta.lineHeightPx(resolveFontSize(fontSize))
    }

    fun resolveFontSize(fontSize: Int?): Int = (fontSize ?: DEFAULT_FONT_SIZE).coerceAtLeast(1)

    fun fontIdFromTtfPath(relativeTtfPath: String): FontId = FontDiscovery.fontIdFromRelativeTtfPath(relativeTtfPath)

    fun predecodeAtlases(fontIds: Collection<FontId>): Int {
        ensureDefaults()
        var warmed = 0
        fontIds.forEach { fontId ->
            val loadedFont = get(fontId) ?: return@forEach
            runCatching { loadedFont.atlasPayload.ensureDecoded() }
                .onSuccess { warmed += 1 }
                .onFailure { error ->
                    logParseErrorRateLimited(
                        key = "atlas-predecode:$fontId",
                        message =
                            "[DSGL-MSDF] Failed atlas predecode for '$fontId': " +
                                "${error.message ?: error.javaClass.simpleName}",
                    )
                }
        }
        return warmed
    }

    fun shapeCacheStats(): ShapeCacheStats {
        val entries = synchronized(shapeCache) { shapeCache.size }
        return ShapeCacheStats(
            requests = shapeCacheRequests.get(),
            hits = shapeCacheHits.get(),
            misses = shapeCacheMisses.get(),
            entries = entries,
            maxEntries = 1024,
        )
    }

    fun resetShapeCacheStats() {
        shapeCacheRequests.set(0L)
        shapeCacheHits.set(0L)
        shapeCacheMisses.set(0L)
    }

    fun textHotPathStats(): TextHotPathStats =
        TextHotPathStats(
            shapeTextRangeCalls = shapeTextRangeCalls.get(),
            shapeSegmentCalls = shapeSegmentCalls.get(),
            requiresReplacementGlyphCalls = requiresReplacementGlyphCalls.get(),
            requiresReplacementGlyphCacheHits = requiresReplacementGlyphCacheHits.get(),
            requiresReplacementGlyphCacheMisses = requiresReplacementGlyphCacheMisses.get(),
            requiresReplacementGlyphEvaluations = requiresReplacementGlyphEvaluations.get(),
            canDisplayCalls = canDisplayCalls.get(),
            canDisplayCacheHits = canDisplayCacheHits.get(),
            canDisplayCacheMisses = canDisplayCacheMisses.get(),
            canDisplayAwtCalls = canDisplayAwtCalls.get(),
            glyphIndexForCodepointCalls = glyphIndexForCodepointCalls.get(),
            glyphIndexCacheHits = glyphIndexCacheHits.get(),
            glyphIndexCacheMisses = glyphIndexCacheMisses.get(),
            glyphIndexVectorBuildCalls = glyphIndexVectorBuildCalls.get(),
        )

    fun resetTextHotPathStats() {
        shapeTextRangeCalls.set(0L)
        shapeSegmentCalls.set(0L)
        requiresReplacementGlyphCalls.set(0L)
        requiresReplacementGlyphCacheHits.set(0L)
        requiresReplacementGlyphCacheMisses.set(0L)
        requiresReplacementGlyphEvaluations.set(0L)
        canDisplayCalls.set(0L)
        canDisplayCacheHits.set(0L)
        canDisplayCacheMisses.set(0L)
        canDisplayAwtCalls.set(0L)
        glyphIndexForCodepointCalls.set(0L)
        glyphIndexCacheHits.set(0L)
        glyphIndexCacheMisses.set(0L)
        glyphIndexVectorBuildCalls.set(0L)
    }

    private fun fallbackMeasureText(text: String, fontSize: Int?): Int {
        val px = resolveFontSize(fontSize)
        return (text.codePointCount(0, text.length) * (px * 0.6f)).toInt().coerceAtLeast(0)
    }

    @Synchronized
    private fun ensureDefaults() {
        if (defaultsRegistered) return
        registerMsdf(
            fontId = FONT_MINECRAFT,
            metaResourcePath = "fonts/minecraft/MinecraftDefault-Regular-meta.json",
            atlasResourcePath = "fonts/minecraft/MinecraftDefault-Regular-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/minecraft/MinecraftDefault-Regular.ttf",
            source = FontAssetSource.Jar,
        )
        registerMsdf(
            fontId = FONT_UBUNTU,
            metaResourcePath = "fonts/ubuntu/Ubuntu-Regular-meta.json",
            atlasResourcePath = "fonts/ubuntu/Ubuntu-Regular-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/ubuntu/Ubuntu-Regular.ttf",
            source = FontAssetSource.Jar,
        )
        registerMsdf(
            fontId = FONT_NOTO_SANS,
            metaResourcePath = "fonts/noto/Noto_Sans/NotoSans-Regular-meta.json",
            atlasResourcePath = "fonts/noto/Noto_Sans/NotoSans-Regular-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/noto/Noto_Sans/NotoSans-Regular.ttf",
            source = FontAssetSource.Jar,
        )
        registerMsdf(
            fontId = FONT_JB_MONO,
            metaResourcePath = "fonts/jetbrains_mono/JetBrainsMono-Regular-meta.json",
            atlasResourcePath = "fonts/jetbrains_mono/JetBrainsMono-Regular-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/jetbrains_mono/JetBrainsMono-Regular.ttf",
            source = FontAssetSource.Jar,
        )
        registerMsdf(
            fontId = TELEGRAFICO,
            metaResourcePath = "fonts/telegrafico/telegrafico-meta.json",
            atlasResourcePath = "fonts/telegrafico/telegrafico-mtsdf.rgba.deflate",
            ttfResourcePath = "fonts/telegrafico/telegrafico.ttf",
            source = FontAssetSource.Jar,
        )
        loadGeneratedFontManifest()
        defaultsRegistered = true
    }

    @Synchronized
    private fun registerFileFont(
        fontId: FontId,
        metaFile: Path,
        atlasFile: Path,
        ttfFile: Path,
    ) {
        descriptors[fontId] =
            MsdfFontResource(
                fontId = fontId,
                source = FontAssetSource.External,
                pathMode = FontPathMode.FileSystem,
                metaPath =
                    metaFile
                        .toAbsolutePath()
                        .normalize()
                        .toString(),
                atlasPath =
                    atlasFile
                        .toAbsolutePath()
                        .normalize()
                        .toString(),
                ttfPath =
                    ttfFile
                        .toAbsolutePath()
                        .normalize()
                        .toString(),
            )
        onDescriptorChanged(fontId)
    }

    private fun onDescriptorChanged(fontId: FontId) {
        loaded.remove(fontId)
        failedLoads.remove(fontId)
        synchronized(shapeCache) {
            shapeCache.clear()
        }
        canDisplayByFontCodepoint.clear()
        glyphIndexByFontCodepoint.clear()
        requiresReplacementGlyphByFontCodepoint.clear()
        derivedFontCache.keys
            .filter { key -> key.startsWith("$fontId@") }
            .forEach(derivedFontCache::remove)
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
        val meta =
            runCatching { MsdfFontMetaParser.parse(metaRaw) }
                .onFailure { error ->
                    val reason = error.message ?: error.javaClass.simpleName
                    failFontLoad(
                        descriptor,
                        "Failed to parse metadata '${descriptor.metaPath}': $reason",
                    )
                }.getOrNull() ?: return null

        val awtBase =
            descriptor.ttfPath?.let { ttfPath ->
                val ttfBytes = readBytes(ttfPath, descriptor.pathMode)
                if (ttfBytes == null) {
                    failFontLoad(descriptor, "Missing TTF '$ttfPath'")
                    return null
                }
                runCatching { loadAwtFont(ttfBytes) }
                    .onFailure { error ->
                        failFontLoad(
                            descriptor,
                            "Failed to load TTF '$ttfPath': ${error.message ?: error.javaClass.simpleName}",
                        )
                    }.getOrNull() ?: return null
            }

        val loadedFont =
            LoadedMsdfFont(
                descriptor = descriptor,
                meta = meta,
                awtBaseFont = awtBase,
                preferredMissingGlyphIndex = computeGlyphIndexForCodepoint(awtBase, 0xFFFD),
                preferredQuestionGlyphIndex = computeGlyphIndexForCodepoint(awtBase, '?'.code),
                atlasPayload = AtlasPayload(atlasBytes),
            )
        loaded[descriptor.fontId] = loadedFont
        failedLoads.remove(descriptor.fontId)
        return loadedFont
    }

    private fun failFontLoad(descriptor: MsdfFontResource, reason: String) {
        failedLoads.add(descriptor.fontId)
        logParseErrorRateLimited(
            key = "font-load:${descriptor.fontId}",
            message =
                "[DSGL-MSDF] Failed to load font '${descriptor.fontId}' " +
                    "(${descriptor.source.name.lowercase()}): $reason",
        )
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

    private fun normalizeRelativePath(path: String): String = FontDiscovery.normalizeRelativePath(path)

    private fun loadGeneratedFontManifest() {
        val manifestPath = "fonts/generated-fonts.txt"
        val stream = resourceClassLoader.getResourceAsStream(manifestPath) ?: return
        stream.use { input ->
            val content = input.bufferedReader(StandardCharsets.UTF_8).readText()
            val ttfs = FontDiscovery.parseGeneratedFontIndex(content)
            ttfs.forEach { relativeTtf ->
                val id = fontIdFromTtfPath(relativeTtf)
                if (descriptors.containsKey(id)) return@forEach
                registerGeneratedFromTtfPath(relativeTtf, fontId = id)
            }
        }
    }

    private fun shapeSingleLine(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        primary: LoadedMsdfFont,
        fallback: LoadedMsdfFont?,
        fontPx: Int,
    ): ShapedText {
        val segments =
            buildShapingSegments(
                text = text,
                startIndex = startIndex,
                endIndexExclusive = endIndexExclusive,
                primary = primary,
                fallback = fallback,
            )
        if (segments.isEmpty()) {
            return ShapedText(glyphs = emptyList(), runs = emptyList(), width = 0f)
        }
        val allGlyphs = ArrayList<ShapedGlyph>((endIndexExclusive - startIndex).coerceAtLeast(8))
        val runs = ArrayList<ShapedTextRun>(segments.size)
        var penX = 0f

        segments.forEach { segment ->
            val shapedRun =
                shapeSegment(
                    sourceText = text,
                    sourceRangeStart = startIndex,
                    segment = segment,
                    fontPx = fontPx,
                    penX = penX,
                )
            allGlyphs += shapedRun.glyphs
            runs += shapedRun
            penX += shapedRun.advance
        }
        return ShapedText(
            glyphs = allGlyphs,
            runs = runs,
            width = penX.coerceAtLeast(0f),
        )
    }

    private fun buildShapingSegments(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        primary: LoadedMsdfFont,
        fallback: LoadedMsdfFont?,
    ): List<MutableShapingSegment> {
        val out = ArrayList<MutableShapingSegment>(4)
        var segment: MutableShapingSegment? = null
        var index = startIndex
        while (index < endIndexExclusive) {
            val start = index
            val codepoint = Character.codePointAt(text, index)
            index += Character.charCount(codepoint)
            val end = index
            val localStart = start - startIndex
            val localEnd = end - startIndex

            val primaryNeedsReplacement = requiresReplacementGlyph(primary, codepoint)
            val fallbackNeedsReplacement = fallback?.let { requiresReplacementGlyph(it, codepoint) } ?: true
            val selectedFont =
                when {
                    !primaryNeedsReplacement -> primary
                    fallback != null && !fallbackNeedsReplacement -> fallback
                    fallback != null -> fallback
                    else -> primary
                }
            val replacementNeeded =
                when (selectedFont.descriptor.fontId) {
                    primary.descriptor.fontId -> primaryNeedsReplacement
                    else -> fallbackNeedsReplacement
                }

            if (segment == null || segment.font.descriptor.fontId != selectedFont.descriptor.fontId) {
                segment = MutableShapingSegment(font = selectedFont)
                out += segment
            }
            if (localStart < segment.charStart) segment.charStart = localStart
            if (localEnd > segment.charEnd) segment.charEnd = localEnd
            if (replacementNeeded) {
                segment.text.append('\uFFFD')
                segment.sourceStartByChar += localStart
                segment.sourceEndByChar += localEnd
            } else {
                segment.text.appendCodePoint(codepoint)
                val charCount = Character.charCount(codepoint)
                repeat(charCount) {
                    segment.sourceStartByChar += localStart
                    segment.sourceEndByChar += localEnd
                }
            }
        }
        return out
    }

    private fun shapeSegment(
        sourceText: String,
        sourceRangeStart: Int,
        segment: MutableShapingSegment,
        fontPx: Int,
        penX: Float,
    ): ShapedTextRun {
        shapeSegmentCalls.incrementAndGet()
        val font = deriveAwtFont(segment.font, fontPx)
        if (font == null || segment.text.isEmpty()) {
            return ShapedTextRun(
                fontId = segment.font.descriptor.fontId,
                charStart = if (segment.charStart == Int.MAX_VALUE) 0 else segment.charStart,
                charEnd = segment.charEnd,
                glyphs = emptyList(),
                advance = 0f,
            )
        }
        val chars =
            segment.text
                .toString()
                .toCharArray()
        val glyphVector =
            runCatching {
                font.layoutGlyphVector(
                    fontRenderContext,
                    chars,
                    0,
                    chars.size,
                    Font.LAYOUT_LEFT_TO_RIGHT,
                )
            }.getOrElse {
                font.createGlyphVector(fontRenderContext, segment.text.toString())
            }
        val glyphCount = glyphVector.numGlyphs
        val positions = glyphVector.getGlyphPositions(0, glyphCount + 1, null)
        val runGlyphs = ArrayList<ShapedGlyph>(glyphCount)
        for (i in 0 until glyphCount) {
            val charIndex =
                glyphVector
                    .getGlyphCharIndex(i)
                    .coerceIn(0, (segment.sourceStartByChar.size - 1).coerceAtLeast(0))
            val sourceStart = segment.sourceStartByChar.getOrElse(charIndex) { 0 }
            val sourceEnd = segment.sourceEndByChar.getOrElse(charIndex) { sourceStart + 1 }
            val sourceGlobalStart = sourceRangeStart + sourceStart
            val sourceCodepoint =
                if (charIndex in chars.indices) {
                    Character.codePointAt(chars, charIndex, chars.size)
                } else if (sourceGlobalStart in sourceText.indices) {
                    Character.codePointAt(sourceText, sourceGlobalStart)
                } else {
                    '?'.code
                }
            val x = (penX + positions[i * 2])
            val y = positions[i * 2 + 1]
            val advance = (positions[(i + 1) * 2] - positions[i * 2])
            runGlyphs +=
                ShapedGlyph(
                    fontId = segment.font.descriptor.fontId,
                    glyphIndex = glyphVector.getGlyphCode(i),
                    x = x,
                    y = y,
                    advance = advance,
                    charStart = sourceStart,
                    charEnd = sourceEnd,
                    sourceCodepoint = sourceCodepoint,
                )
        }
        val runAdvance = positions[glyphCount * 2].coerceAtLeast(0f)
        return ShapedTextRun(
            fontId = segment.font.descriptor.fontId,
            charStart = if (segment.charStart == Int.MAX_VALUE) 0 else segment.charStart,
            charEnd = segment.charEnd,
            glyphs = runGlyphs,
            advance = runAdvance,
        )
    }

    private fun deriveAwtFont(font: LoadedMsdfFont, fontPx: Int): Font? {
        val base = font.awtBaseFont ?: return null
        val key = "${font.descriptor.fontId}@${fontPx.coerceAtLeast(1)}"
        return derivedFontCache.getOrPut(key) {
            base.deriveFont(fontPx.coerceAtLeast(1).toFloat())
        }
    }

    private fun canDisplay(font: LoadedMsdfFont, codepoint: Int): Boolean {
        canDisplayCalls.incrementAndGet()
        val key = FontCodepointKey(font.descriptor.fontId, codepoint)
        canDisplayByFontCodepoint[key]?.let { cached ->
            canDisplayCacheHits.incrementAndGet()
            return cached
        }
        canDisplayCacheMisses.incrementAndGet()
        val awt = font.awtBaseFont ?: return false
        canDisplayAwtCalls.incrementAndGet()
        val resolved = runCatching { awt.canDisplay(codepoint) }.getOrDefault(false)
        canDisplayByFontCodepoint[key] = resolved
        return resolved
    }

    private fun requiresReplacementGlyph(font: LoadedMsdfFont, codepoint: Int): Boolean {
        requiresReplacementGlyphCalls.incrementAndGet()
        val key = FontCodepointKey(font.descriptor.fontId, codepoint)
        requiresReplacementGlyphByFontCodepoint[key]?.let { cached ->
            requiresReplacementGlyphCacheHits.incrementAndGet()
            return cached
        }
        requiresReplacementGlyphCacheMisses.incrementAndGet()
        requiresReplacementGlyphEvaluations.incrementAndGet()
        val resolved =
            if (!Character.isValidCodePoint(codepoint)) {
                true
            } else if (!canDisplay(font, codepoint)) {
                true
            } else {
                val glyphIndex = glyphIndexForCodepoint(font, codepoint)
                if (glyphIndex == null || glyphIndex < 0) {
                    true
                } else {
                    val missingIndex = font.preferredMissingGlyphIndex
                    if (missingIndex != null && glyphIndex == missingIndex) {
                        true
                    } else {
                        glyphIndex == 0 && (missingIndex == null || missingIndex == 0)
                    }
                }
            }
        requiresReplacementGlyphByFontCodepoint[key] = resolved
        return resolved
    }

    private fun glyphIndexForCodepoint(font: LoadedMsdfFont, codepoint: Int): Int? {
        glyphIndexForCodepointCalls.incrementAndGet()
        if (!Character.isValidCodePoint(codepoint)) return null
        val key = FontCodepointKey(font.descriptor.fontId, codepoint)
        glyphIndexByFontCodepoint[key]?.let { cached ->
            glyphIndexCacheHits.incrementAndGet()
            return if (cached == GLYPH_INDEX_NULL_SENTINEL) null else cached
        }
        glyphIndexCacheMisses.incrementAndGet()
        glyphIndexVectorBuildCalls.incrementAndGet()
        val resolved = computeGlyphIndexForCodepoint(font.awtBaseFont, codepoint)
        glyphIndexByFontCodepoint[key] = resolved ?: GLYPH_INDEX_NULL_SENTINEL
        return resolved
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

    private fun loadAwtFont(ttfBytes: ByteArray): Font =
        ByteArrayInputStream(ttfBytes).use { input ->
            Font.createFont(Font.TRUETYPE_FONT, input)
        }

    private fun normalizeRange(text: String, startIndex: Int, endIndexExclusive: Int): Pair<Int, Int> {
        var start = startIndex.coerceIn(0, text.length)
        var end = endIndexExclusive.coerceIn(start, text.length)
        if (start > 0 &&
            start < text.length &&
            Character.isLowSurrogate(text[start]) &&
            Character.isHighSurrogate(text[start - 1])
        ) {
            start -= 1
        }
        if (end > start &&
            end < text.length &&
            Character.isLowSurrogate(text[end]) &&
            Character.isHighSurrogate(text[end - 1])
        ) {
            end -= 1
        }
        return start to end
    }

    private fun getExact(fontId: FontId): LoadedMsdfFont? {
        ensureDefaults()
        val descriptor = descriptors[fontId] ?: return null
        return loaded[fontId] ?: load(descriptor)
    }

    private fun logParseErrorRateLimited(key: String, message: String) {
        val now = System.currentTimeMillis()
        val previous = parseErrorLogTimes[key] ?: 0L
        if (now - previous < 3_000L) return
        parseErrorLogTimes[key] = now
        println(message)
    }
}
