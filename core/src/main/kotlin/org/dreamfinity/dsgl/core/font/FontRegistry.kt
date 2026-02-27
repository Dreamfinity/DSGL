package org.dreamfinity.dsgl.core.font

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class MsdfFontResource(
    val fontId: FontId,
    val metaResourcePath: String,
    val atlasResourcePath: String
)

data class LoadedMsdfFont(
    val descriptor: MsdfFontResource,
    val meta: MsdfFontMeta
)

object FontRegistry {
    const val FONT_MINECRAFT: String = "minecraft"
    const val FONT_UBUNTU: String = "ubuntu"
    const val FONT_JB_MONO: String = "JetBrains Mono"
    const val TELEGRAFICO: String = "telegrafico"
    const val DEFAULT_FONT_ID: String = FONT_MINECRAFT
    const val DEFAULT_FONT_SIZE: Int = 9

    private val descriptors: MutableMap<FontId, MsdfFontResource> = linkedMapOf()
    private val loaded: MutableMap<FontId, LoadedMsdfFont> = ConcurrentHashMap()
    private val failedLoads: MutableSet<FontId> = Collections.newSetFromMap(ConcurrentHashMap())
    private val parseErrorLogTimes: MutableMap<String, Long> = ConcurrentHashMap()
    private var defaultsRegistered: Boolean = false

    @Synchronized
    fun registerMsdf(fontId: FontId, metaResourcePath: String, atlasResourcePath: String) {
        val normalized = normalizePath(metaResourcePath)
        val normalizedAtlas = normalizePath(atlasResourcePath)
        descriptors[fontId] = MsdfFontResource(
            fontId = fontId,
            metaResourcePath = normalized,
            atlasResourcePath = normalizedAtlas
        )
        loaded.remove(fontId)
        failedLoads.remove(fontId)
    }

    @Synchronized
    fun registerGeneratedFromTtfPath(relativeTtfPath: String, fontId: FontId = fontIdFromTtfPath(relativeTtfPath)) {
        val normalized = normalizePath(relativeTtfPath)
        require(normalized.endsWith(".ttf", ignoreCase = true)) {
            "Expected .ttf path, got '$relativeTtfPath'"
        }
        val base = normalized.removeSuffix(".ttf")
        val metaPath = "$base-meta.json"
        val atlasPath = "$base-mtsdf.png"
        registerMsdf(
            fontId = fontId,
            metaResourcePath = "fonts/$metaPath",
            atlasResourcePath = "fonts/$atlasPath"
        )
    }

    fun get(fontId: FontId?): LoadedMsdfFont? {
        ensureDefaults()
        val requested = fontId ?: DEFAULT_FONT_ID
        val requestedDescriptor = descriptors[requested]
        if (requestedDescriptor != null) {
            val loadedFont = loaded[requestedDescriptor.fontId] ?: load(requestedDescriptor)
            if (loadedFont != null) {
                return loadedFont
            }
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

    fun clearLoadedCache() {
        loaded.clear()
        failedLoads.clear()
        parseErrorLogTimes.clear()
    }

    fun measureText(text: String, fontId: FontId?, fontSize: Int?): Int {
        if (text.isEmpty()) return 0
        val font = get(fontId) ?: return fallbackMeasureText(text, fontSize)
        return font.meta.measureTextWidth(text, resolveFontSize(fontSize))
    }

    fun lineHeight(fontId: FontId?, fontSize: Int?): Int {
        val font = get(fontId) ?: return resolveFontSize(fontSize)
        return font.meta.lineHeightPx(resolveFontSize(fontSize))
    }

    fun resolveFontSize(fontSize: Int?): Int {
        return (fontSize ?: DEFAULT_FONT_SIZE).coerceIn(6, 96)
    }

    fun fontIdFromTtfPath(relativeTtfPath: String): FontId {
        val normalized = normalizePath(relativeTtfPath).removeSuffix(".ttf")
        return normalized.lowercase()
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
            atlasResourcePath = "fonts/minecraft/MinecraftDefault-Regular-mtsdf.png"
        )
        registerMsdf(
            fontId = FONT_UBUNTU,
            metaResourcePath = "fonts/ubuntu/Ubuntu-Regular-meta.json",
            atlasResourcePath = "fonts/ubuntu/Ubuntu-Regular-mtsdf.png"
        )
        registerMsdf(
            fontId = FONT_JB_MONO,
            metaResourcePath = "fonts/jetbrains_mono/JetBrainsMono-Regular-meta.json",
            atlasResourcePath = "fonts/jetbrains_mono/JetBrainsMono-Regular-mtsdf.png"
        )
        registerMsdf(
            fontId = FONT_JB_MONO,
            metaResourcePath = "fonts/telegrafico/telegrafico-meta.json",
            atlasResourcePath = "fonts/telegrafico/telegrafico-mtsdf.png"
        )
        loadGeneratedFontManifest()
        defaultsRegistered = true
    }

    private fun load(descriptor: MsdfFontResource): LoadedMsdfFont? {
        if (failedLoads.contains(descriptor.fontId)) return null
        val metaRaw = readResourceText(descriptor.metaResourcePath) ?: return null
        val meta = runCatching { MsdfFontMetaParser.parse(metaRaw) }
            .onFailure { error ->
                failedLoads.add(descriptor.fontId)
                logParseErrorRateLimited(
                    key = "meta:${descriptor.fontId}",
                    message = buildString {
                        append("[DSGL-MSDF] Failed to parse font metadata for fontId='")
                        append(descriptor.fontId)
                        append("' meta='")
                        append(descriptor.metaResourcePath)
                        append("' atlas='")
                        append(descriptor.atlasResourcePath)
                        append("': ")
                        append(error.message ?: error.javaClass.simpleName)
                    }
                )
            }
            .getOrNull() ?: return null
        val loadedFont = LoadedMsdfFont(descriptor = descriptor, meta = meta)
        loaded[descriptor.fontId] = loadedFont
        failedLoads.remove(descriptor.fontId)
        return loadedFont
    }

    private fun readResourceText(path: String): String? {
        val stream = javaClass.classLoader.getResourceAsStream(path) ?: return null
        return stream.use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                reader.readText()
            }
        }
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimStart('/')
    }

    private fun loadGeneratedFontManifest() {
        val manifestPath = "fonts/generated-fonts.txt"
        val stream = javaClass.classLoader.getResourceAsStream(manifestPath) ?: return
        stream.use { input ->
            val content = input.bufferedReader(StandardCharsets.UTF_8).readText()
            content.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && it.endsWith(".ttf", ignoreCase = true) }
                .forEach { relativeTtf ->
                    val id = fontIdFromTtfPath(relativeTtf)
                    if (descriptors.containsKey(id)) return@forEach
                    registerGeneratedFromTtfPath(relativeTtf, fontId = id)
                }
        }
    }

    private fun logParseErrorRateLimited(key: String, message: String) {
        val now = System.currentTimeMillis()
        val previous = parseErrorLogTimes[key] ?: 0L
        if (now - previous < 3_000L) return
        parseErrorLogTimes[key] = now
        println(message)
    }
}
