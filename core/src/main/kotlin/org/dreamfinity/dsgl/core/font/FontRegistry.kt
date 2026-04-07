package org.dreamfinity.dsgl.core.font

import java.io.File
import java.nio.file.Path

enum class FontPathMode {
    Resource,
    FileSystem
}

data class MsdfFontResource(
    val fontId: FontId,
    val source: FontAssetSource,
    val pathMode: FontPathMode,
    val metaPath: String,
    val atlasPath: String,
    val ttfPath: String?
)

data class FontTextureHandle(
    val textureId: Int,
    val width: Int,
    val height: Int
)

data class LoadedMsdfFont(
    val descriptor: MsdfFontResource,
    val meta: MsdfFontMeta,
    val awtBaseFont: java.awt.Font?,
    val preferredMissingGlyphIndex: Int?,
    val preferredQuestionGlyphIndex: Int?,
    val atlasPayload: AtlasPayload,
    var handle: FontTextureHandle? = null
)

data class AtlasBitmap(
    val width: Int,
    val height: Int,
    var rgbaBytes: ByteArray
)

class AtlasPayload internal constructor(
    private var encodedPngBytes: ByteArray?
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
            val bytes = encodedPngBytes ?: throw IllegalStateException("Missing atlas payload bytes")
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
        val inflater = java.util.zip.Inflater(true)
        return try {
            java.io.ByteArrayInputStream(bytes).use { input ->
                java.util.zip.InflaterInputStream(input, inflater).use { zipped ->
                    java.io.DataInputStream(zipped).use { data ->
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
        val image = java.io.ByteArrayInputStream(bytes).use { input ->
            javax.imageio.ImageIO.read(input)
        } ?: throw IllegalStateException("Atlas payload is neither deflated rgba nor PNG")

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
    val ttfPath: String?
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
    val fallbackReady: Boolean
)

data class ShapedGlyph(
    val fontId: FontId,
    val glyphIndex: Int,
    val x: Float,
    val y: Float,
    val advance: Float,
    val charStart: Int,
    val charEnd: Int,
    val sourceCodepoint: Int
)

data class ShapedTextRun(
    val fontId: FontId,
    val charStart: Int,
    val charEnd: Int,
    val glyphs: List<ShapedGlyph>,
    val advance: Float
)

data class ShapedText(
    val glyphs: List<ShapedGlyph>,
    val runs: List<ShapedTextRun>,
    val width: Float
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

    data class ShapeCacheStats(
        val requests: Long,
        val hits: Long,
        val misses: Long,
        val entries: Int,
        val maxEntries: Int
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
        val glyphIndexVectorBuildCalls: Long
    )

    private val catalog: FontCatalog by lazy {
        DefaultFontCatalog(
            pathIdResolver = ::fontIdFromTtfPath,
            onCatalogChanged = { shaper.clearCaches() }
        )
    }
    private val metrics: FontMetricsService by lazy { DefaultFontMetricsService(catalog) }
    private val shaper: TextShaper by lazy { AwtTextShaper(catalog, metrics) }

    @Synchronized
    fun discoverAndPreloadFonts(
        externalFontsDir: File?,
        classLoader: ClassLoader = javaClass.classLoader
    ): FontPreloadSummary {
        return catalog.discoverAndPreloadFonts(externalFontsDir, classLoader)
    }

    @Synchronized
    fun preloadRegisteredFonts(): Int {
        return catalog.preloadRegisteredFonts()
    }

    @Synchronized
    fun registerMsdf(
        fontId: FontId,
        metaResourcePath: String,
        atlasResourcePath: String,
        ttfResourcePath: String? = null,
        source: FontAssetSource = FontAssetSource.Jar
    ) {
        catalog.registerMsdf(
            fontId = fontId,
            metaResourcePath = metaResourcePath,
            atlasResourcePath = atlasResourcePath,
            ttfResourcePath = ttfResourcePath,
            source = source
        )
    }

    @Synchronized
    fun registerGeneratedFromTtfPath(
        relativeTtfPath: String,
        fontId: FontId = fontIdFromTtfPath(relativeTtfPath)
    ) {
        catalog.registerGeneratedFromTtfPath(relativeTtfPath, fontId)
    }

    fun get(fontId: FontId?): LoadedMsdfFont? {
        return catalog.get(fontId)
    }

    fun allFontIds(): Set<FontId> {
        return catalog.allFontIds()
    }

    fun registeredFonts(): List<RegisteredFontInfo> {
        return catalog.registeredFonts()
    }

    fun clearLoadedCache() {
        catalog.clearLoadedFonts()
        shaper.clearCaches()
    }

    fun measureText(text: String, fontId: FontId?, fontSize: Int?): Int {
        return shaper.measureText(text, fontId, fontSize)
    }

    fun measureTextRange(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        fontId: FontId?,
        fontSize: Int?
    ): Int {
        return shaper.measureTextRange(text, startIndex, endIndexExclusive, fontId, fontSize)
    }

    fun shapeText(
        text: String,
        fontId: FontId?,
        fontSize: Int?,
        formattingMode: String = "plain"
    ): ShapedText {
        return shaper.shapeText(text, fontId, fontSize, formattingMode)
    }

    fun shapeTextRange(
        text: String,
        startIndex: Int,
        endIndexExclusive: Int,
        fontId: FontId?,
        fontSize: Int?,
        formattingMode: String = "plain"
    ): ShapedText {
        return shaper.shapeTextRange(text, startIndex, endIndexExclusive, fontId, fontSize, formattingMode)
    }

    fun lineHeight(fontId: FontId?, fontSize: Int?): Int {
        return metrics.lineHeight(fontId, fontSize)
    }

    fun resolveFontSize(fontSize: Int?): Int {
        return metrics.resolveFontSize(fontSize)
    }

    fun fontIdFromTtfPath(relativeTtfPath: String): FontId {
        return FontDiscovery.fontIdFromRelativeTtfPath(relativeTtfPath)
    }

    fun predecodeAtlases(fontIds: Collection<FontId>): Int {
        var warmed = 0
        fontIds.forEach { fontId ->
            val loadedFont = get(fontId) ?: return@forEach
            runCatching { loadedFont.atlasPayload.ensureDecoded() }
                .onSuccess { warmed += 1 }
                .onFailure { error ->
                    println(
                        "[DSGL-MSDF] Failed atlas predecode for '$fontId': ${
                            error.message ?: error.javaClass.simpleName
                        }"
                    )
                }
        }
        return warmed
    }

    fun shapeCacheStats(): ShapeCacheStats {
        return shaper.shapeCacheStats()
    }

    fun resetShapeCacheStats() {
        shaper.resetShapeCacheStats()
    }

    fun textHotPathStats(): TextHotPathStats {
        return shaper.textHotPathStats()
    }

    fun resetTextHotPathStats() {
        shaper.resetTextHotPathStats()
    }

    private fun registerFileFont(
        fontId: FontId,
        metaFile: Path,
        atlasFile: Path,
        ttfFile: Path
    ) {
        catalog.registerFileFont(fontId, metaFile, atlasFile, ttfFile)
    }
}
